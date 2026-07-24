import os
import uuid
import subprocess
import json
import pickle
import joblib

import numpy as np

import torch
import torch.nn as nn
import torch.nn.functional as F
import torchaudio

from transformers import Wav2Vec2Processor, Wav2Vec2Model

# =========================
# CONFIG
# =========================
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

# [PERBAIKAN 1]: Disesuaikan dengan arsitektur model di Kaggle
EMB_DIM = 1024       
# [PERBAIKAN 2]: Disesuaikan dengan metadata Kaggle
N_CLUSTERS = 4096    
SAMPLE_RATE = 16000

UPLOAD_DIR = "uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)

# =========================
# LOAD WAV2VEC
# =========================
processor = Wav2Vec2Processor.from_pretrained("facebook/wav2vec2-base-960h")
wav2vec = Wav2Vec2Model.from_pretrained("facebook/wav2vec2-base-960h").to(DEVICE)
wav2vec.eval()

# =========================
# MODEL ARCHITECTURE
# =========================
class HybridEncoder(nn.Module):
    def __init__(self, pooled_dim, hist_dim):
        super().__init__()
        total_dim = pooled_dim + hist_dim
        self.proj = nn.Sequential(
            nn.LayerNorm(total_dim),
            nn.Linear(total_dim, EMB_DIM)
        )

    def forward(self, pooled, hist):
        x = torch.cat([pooled, hist], dim=1)
        x = self.proj(x)
        # Normalisasi sudah dilakukan di dalam model
        return F.normalize(x, dim=1)

# =========================
# LOAD MODEL & DATABASE
# =========================
model = HybridEncoder(3072, N_CLUSTERS).to(DEVICE)
model.load_state_dict(torch.load("models/model.pt", map_location=DEVICE))
model.eval()

db_embeddings = torch.load("models/db_embeddings.pt", map_location=DEVICE)
db_embeddings = F.normalize(db_embeddings, dim=1)

with open("models/db_keys.pkl", "rb") as f:
    db_keys = pickle.load(f)

with open("models/db_groups.pkl", "rb") as f:
    db_groups = pickle.load(f)

kmeans = joblib.load("models/kmeans.pkl")

# =========================
# GROUP MAPPING
# =========================
group_to_keys = {}
for key, group in zip(db_keys, db_groups):
    if group not in group_to_keys:
        group_to_keys[group] = []
    group_to_keys[group].append(key)

# =========================
# LOAD METADATA
# =========================
with open("metadata.json", "r", encoding="utf-8") as f:
    raw_metadata = json.load(f)

metadata = {}
for item_id, item in raw_metadata.items():
    verse_key = item["verse_key"]
    metadata[verse_key] = {
        "surah": item["surah_number"],
        "ayah": item["ayah_number"],
        "verse_key": verse_key,
        "arabic": item["text"],
        "translation": item["arti"]
    }

# =========================
# AUDIO UTILS
# =========================
def convert_audio_to_wav(input_path, output_path):
    command = [
        "ffmpeg", "-y", "-i", input_path,
        "-ac", "1", "-ar", str(SAMPLE_RATE), "-vn", output_path
    ]
    try:
        subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError as e:
        error_message = e.stderr.decode(errors="ignore") if e.stderr else "Unknown ffmpeg error"
        raise RuntimeError(f"FFmpeg conversion failed:\n{error_message}")

def load_audio(path):
    try:
        wav, sr = torchaudio.load(path)
    except Exception as e:
        raise RuntimeError(f"Failed to load audio:\n{str(e)}")

    if wav.shape[0] > 1:
        wav = wav.mean(dim=0, keepdim=True)

    if sr != SAMPLE_RATE:
        wav = torchaudio.functional.resample(wav, sr, SAMPLE_RATE)

    wav = wav / (wav.abs().max() + 1e-6)
    return wav.squeeze(0)

@torch.no_grad()
def extract_frames(wav):
    inp = processor(
        wav.numpy(), 
        sampling_rate=SAMPLE_RATE, 
        return_tensors="pt"
    ).input_values.to(DEVICE)

    with torch.autocast(device_type=DEVICE, enabled=(DEVICE == "cuda")):
        out = wav2vec(inp).last_hidden_state

    return out.squeeze(0).cpu()

# =========================
# MAIN INFERENCE
# =========================
@torch.no_grad()
def predict_audio(audio_path, top_k_groups=3):
    unique_id = str(uuid.uuid4())
    converted_path = os.path.join(UPLOAD_DIR, f"{unique_id}.wav")

    try:
        # 1. Convert & Load Audio
        convert_audio_to_wav(audio_path, converted_path)
        wav = load_audio(converted_path)

        # 2. Extract Frames
        frames = extract_frames(wav)

        # 3. Pooling (Mean, Std, Max, Min)
        mean_pool = frames.mean(dim=0)
        std_pool = frames.std(dim=0)
        max_pool = frames.max(dim=0).values
        min_pool = frames.min(dim=0).values
        pooled = torch.cat([mean_pool, std_pool, max_pool, min_pool], dim=0)

        # 4. Histogram (Full Frame - [PERBAIKAN 3] Subsampling Dihapus)
        frames_np = frames.numpy()
        ids = kmeans.predict(frames_np)
        h = np.bincount(ids, minlength=N_CLUSTERS)

        # Catatan: Pastikan ini sesuai dengan prapemrosesan dataset Anda!
        h = np.log1p(h)
        h = h / (np.linalg.norm(h) + 1e-6)

        # 5. Model Forward Pass
        pooled = pooled.unsqueeze(0).to(DEVICE)
        h = torch.tensor(h).float().unsqueeze(0).to(DEVICE)

        # [PERBAIKAN 4]: Hapus double-normalization. Forward pass sudah mereturn normalized tensor.
        query_emb = model(pooled, h)

        # 6. Similarity Search (Cosine)
        sims = torch.matmul(query_emb, db_embeddings.T).squeeze(0)

        # 7. Group Ranking (Max Pooling Aggregation)
        group_scores = {}
        for i, key in enumerate(db_keys):
            g = db_groups[i]
            score = sims[i].item()
            
            if g not in group_scores or score > group_scores[g]:
                group_scores[g] = score

        ranked_groups = sorted(
            group_scores.items(),
            key=lambda x: x[1],
            reverse=True
        )

        # 8. Formatting Result Output
        predictions = []
        for group_id, score in ranked_groups[:top_k_groups]:
            matched_keys = group_to_keys[group_id]
            verses = []
            
            for key in matched_keys:
                if key in metadata:
                    verses.append(metadata[key])

            predictions.append({
                "group_id": int(group_id),
                "score": float(score),
                "total_verses": len(verses),
                "verses": verses
            })

        return {"predictions": predictions}

    except Exception as e:
        print(f"\n[PREDICT ERROR]\n{str(e)}")
        raise RuntimeError(f"Prediction failed:\n{str(e)}")

    finally:
        # Cleanup
        try:
            if os.path.exists(converted_path):
                os.remove(converted_path)
        except Exception as cleanup_error:
            print("Cleanup warning:", cleanup_error)

# =========================
# TEST
# =========================
if __name__ == "__main__":
    # Ganti dengan path audio yang valid
    result = predict_audio("alafasy_55_71.wav")
    print(json.dumps(result, indent=2, ensure_ascii=False))
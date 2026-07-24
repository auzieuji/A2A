from fastapi import FastAPI, UploadFile, File
import shutil
import os

from predict import predict_audio

app = FastAPI()

UPLOAD_DIR = "uploads"

os.makedirs(UPLOAD_DIR, exist_ok=True)

@app.get("/")
def root():

    return {
        "message": "Quran Audio Search API Running"
    }

@app.post("/predict")
async def predict(file: UploadFile = File(...)):

    file_path = os.path.join(
        UPLOAD_DIR,
        file.filename
    )

    # save uploaded file
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(
            file.file,
            buffer
        )

    # inference
    result = predict_audio(
        file_path,
        top_k_groups=1
    )

    return result
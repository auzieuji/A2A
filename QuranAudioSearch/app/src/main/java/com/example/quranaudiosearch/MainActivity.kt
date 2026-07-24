package com.example.quranaudiosearch

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

import com.example.quranaudiosearch.data.PredictionResponse
import com.example.quranaudiosearch.data.Verse
import com.example.quranaudiosearch.network.RetrofitClient
import com.google.android.material.button.MaterialButton

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var resultCard: CardView

    private lateinit var surahText: TextView
    private lateinit var arabicText: TextView
    private lateinit var translationText: TextView
    private lateinit var scoreText: TextView

    private lateinit var similarSummaryText: TextView
    private lateinit var similarDetailText: TextView

    private lateinit var recordButton: MaterialButton

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordedFilePath = ""
    private var isExpanded = false
    private lateinit var recordingCard: CardView
    private lateinit var recordingTimer: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var recordingSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val uploadButton =
            findViewById<Button>(R.id.uploadButton)

        progressBar =
            findViewById(R.id.progressBar)

        resultCard =
            findViewById(R.id.resultCard)

        surahText =
            findViewById(R.id.surahText)

        arabicText =
            findViewById(R.id.arabicText)

        translationText =
            findViewById(R.id.translationText)

        scoreText =
            findViewById(R.id.scoreText)

        similarSummaryText =
            findViewById(R.id.similarSummaryText)

        similarDetailText =
            findViewById(R.id.similarDetailText)

        recordButton =
            findViewById(R.id.recordButton)

        recordButton.setOnClickListener {

            if (checkAudioPermission()) {

                if (!isRecording) {

                    startRecording()

                } else {

                    stopRecording()
                }

            } else {

                requestAudioPermission()
            }
        }

        recordingCard =
            findViewById(R.id.recordingCard)

        recordingTimer =
            findViewById(R.id.recordingTimer)

        uploadButton.setOnClickListener {
            pickAudioFile()
        }
    }

    // =========================
    // RECORDING
    // =========================

    private fun checkAudioPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO
            ),
            1001
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 1001) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                Toast.makeText(
                    this,
                    "Permission granted",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                Toast.makeText(
                    this,
                    "Microphone permission denied",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val timerRunnable = object : Runnable {

        @SuppressLint("DefaultLocale")
        override fun run() {

            recordingSeconds++

            val minutes = recordingSeconds / 60
            val seconds = recordingSeconds % 60

            recordingTimer.text =
                String.format(
                    "%02d:%02d",
                    minutes,
                    seconds
                )

            handler.postDelayed(this, 1000)
        }
    }

    private fun startRecording() {

        try {

            val file = File(
                cacheDir,
                "recorded_audio.3gp"
            )

            recordedFilePath = file.absolutePath

            mediaRecorder = MediaRecorder()

            mediaRecorder?.apply {

                setAudioSource(
                    MediaRecorder.AudioSource.MIC
                )

                setOutputFormat(
                    MediaRecorder.OutputFormat.THREE_GPP
                )

                setAudioEncoder(
                    MediaRecorder.AudioEncoder.AMR_NB
                )

                setOutputFile(
                    recordedFilePath
                )

                prepare()

                start()
            }

            isRecording = true

            recordingCard.visibility = View.VISIBLE

            recordingSeconds = 0

            recordingTimer.text = "00:00"

            handler.post(timerRunnable)

            recordButton.text = "Stop Recording"

            recordButton.setBackgroundColor(
                getColor(android.R.color.holo_red_dark)
            )

            Toast.makeText(
                this,
                "Recording started",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            Log.e(
                "RECORD_ERROR",
                e.stackTraceToString()
            )

            Toast.makeText(
                this,
                "Failed to start recording",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopRecording() {

        try {

            mediaRecorder?.apply {

                stop()
                release()
            }

            mediaRecorder = null

            isRecording = false

            handler.removeCallbacks(timerRunnable)

            recordingCard.visibility = View.GONE

            recordButton.text = "Mulai Merekam"

            recordButton.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    getColor(R.color.teal_700)
                )

            Toast.makeText(
                this,
                "Recording finished",
                Toast.LENGTH_SHORT
            ).show()

            uploadAudio(
                Uri.fromFile(
                    File(recordedFilePath)
                )
            )

        } catch (e: Exception) {

            Log.e(
                "RECORD_ERROR",
                e.stackTraceToString()
            )

            Toast.makeText(
                this,
                "Failed to stop recording",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================
    // FILE PICKER
    // =========================

    private val audioPicker =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {

                val uri = result.data?.data

                if (uri != null) {
                    uploadAudio(uri)
                } else {

                    Toast.makeText(
                        this,
                        "No file selected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private fun pickAudioFile() {

        val intent = Intent(Intent.ACTION_GET_CONTENT)

        intent.type = "audio/*"

        audioPicker.launch(intent)
    }

    // =========================
    // SIMILAR VERSES
    // =========================

    private fun setupSimilarVerses(
        verses: List<Verse>
    ) {

        if (verses.size <= 1) {

            similarSummaryText.visibility =
                View.GONE

            similarDetailText.visibility =
                View.GONE

            return
        }

        // skip first because first = top prediction
        val others = verses.drop(1)

        // =========================
        // GROUP BY SURAH
        // =========================

        val grouped =
            others.groupBy { it.surah }

        val detailBuilder = StringBuilder()

        for ((surah, ayatList) in grouped) {

            val ayatNumbers =
                ayatList
                    .map { it.ayah }
                    .sorted()

            detailBuilder.append(
                "QS $surah: "
            )

            detailBuilder.append(
                ayatNumbers.joinToString(", ")
            )

            detailBuilder.append("\n")
        }

        // =========================
        // SUMMARY TEXT
        // =========================

        val summary =
            "Kami menemukan ayat lain dengan lafadz serupa. " +
                    "Lihat selengkapnya di sini"

        val spannable =
            android.text.SpannableString(summary)

        val clickablePart =
            "Lihat selengkapnya di sini"

        val start =
            summary.indexOf(clickablePart)

        val end =
            start + clickablePart.length

        spannable.setSpan(

            object :
                android.text.style.ClickableSpan() {

                override fun onClick(widget: View) {

                    if (
                        similarDetailText.visibility
                        == View.GONE
                    ) {

                        similarDetailText.visibility =
                            View.VISIBLE

                    } else {

                        similarDetailText.visibility =
                            View.GONE
                    }
                }

                override fun updateDrawState(ds: android.text.TextPaint) {

                    super.updateDrawState(ds)

                    ds.color =
                        android.graphics.Color.parseColor("#1565C0")

                    ds.isUnderlineText = false
                }

            },

            start,
            end,

            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        similarSummaryText.text =
            spannable

        similarSummaryText.movementMethod =
            android.text.method.LinkMovementMethod.getInstance()

        similarSummaryText.highlightColor =
            android.graphics.Color.TRANSPARENT

        similarSummaryText.visibility =
            View.VISIBLE

        similarDetailText.text =
            detailBuilder.toString()

        similarDetailText.visibility =
            View.GONE
    }

    // =========================
    // UPLOAD AUDIO
    // =========================

    private fun uploadAudio(uri: Uri) {

        try {

            progressBar.visibility =
                View.VISIBLE

            resultCard.visibility =
                View.GONE

            val inputStream =
                contentResolver.openInputStream(uri)

            if (inputStream == null) {

                showError(
                    "File Error",
                    "Cannot open audio file"
                )

                return
            }

            val file = File(
                cacheDir,
                "temp_audio.wav"
            )

            val outputStream =
                FileOutputStream(file)

            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()

            val requestFile: RequestBody =
                file.asRequestBody(
                    "audio/wav".toMediaTypeOrNull()
                )

            val body =
                MultipartBody.Part.createFormData(
                    "file",
                    file.name,
                    requestFile
                )

            RetrofitClient.apiService
                .uploadAudio(body)
                .enqueue(object :
                    Callback<PredictionResponse> {

                    override fun onResponse(
                        call: Call<PredictionResponse>,
                        response: Response<PredictionResponse>
                    ) {

                        progressBar.visibility =
                            View.GONE

                        Log.d(
                            "API_RESPONSE",
                            "Code: ${response.code()}"
                        )

                        if (response.isSuccessful) {

                            val result =
                                response.body()

                            if (
                                result != null &&
                                result.predictions.isNotEmpty()
                            ) {

                                val topPrediction =
                                    result.predictions[0]

                                if (
                                    topPrediction.verses.isNotEmpty()
                                ) {

                                    val verse =
                                        topPrediction.verses[0]

                                    resultCard.visibility =
                                        View.VISIBLE

                                    surahText.text =
                                        "QS ${verse.surah}:${verse.ayah}"

                                    arabicText.text =
                                        verse.arabic

                                    translationText.text =
                                        verse.translation

                                    scoreText.text =
                                        "Similarity Score: %.4f"
                                            .format(
                                                topPrediction.score
                                            )

                                    setupSimilarVerses(
                                        topPrediction.verses
                                    )

                                } else {

                                    showError(
                                        "No Verse",
                                        "Prediction exists but no verse data"
                                    )
                                }

                            } else {

                                showError(
                                    "No Prediction",
                                    "Audio tidak berhasil dikenali"
                                )
                            }

                        } else {

                            Log.e(
                                "API_ERROR",
                                "Code: ${response.code()}"
                            )

                            showError(
                                "API Error",
                                "Server returned ${response.code()}"
                            )
                        }
                    }

                    override fun onFailure(
                        call: Call<PredictionResponse>,
                        t: Throwable
                    ) {

                        progressBar.visibility =
                            View.GONE

                        Log.e(
                            "API_FAILURE",
                            t.stackTraceToString()
                        )

                        showError(
                            "Connection Failed",
                            t.message
                                ?: "Unknown network error"
                        )
                    }
                })

        } catch (e: Exception) {

            progressBar.visibility =
                View.GONE

            Log.e(
                "APP_ERROR",
                e.stackTraceToString()
            )

            showError(
                "Error",
                e.message ?: "Unknown error"
            )
        }
    }

    // =========================
    // SHOW ERROR
    // =========================

    private fun showError(
        title: String,
        message: String
    ) {

        resultCard.visibility =
            View.VISIBLE

        surahText.text =
            title

        arabicText.text =
            "-"

        translationText.text =
            message

        scoreText.text =
            ""

        similarSummaryText.visibility =
            View.GONE

        similarDetailText.visibility =
            View.GONE

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
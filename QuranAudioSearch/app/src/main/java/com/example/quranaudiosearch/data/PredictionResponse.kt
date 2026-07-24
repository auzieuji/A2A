package com.example.quranaudiosearch.data

data class Verse(
    val surah: Int,
    val ayah: Int,
    val verse_key: String,
    val arabic: String,
    val translation: String
)

data class Prediction(
    val group_id: Int,
    val score: Double,
    val total_verses: Int,
    val verses: List<Verse>
)

data class PredictionResponse(
    val predictions: List<Prediction>
)
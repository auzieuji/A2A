package com.example.quranaudiosearch.network

import com.example.quranaudiosearch.data.PredictionResponse

import okhttp3.MultipartBody

import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("/predict")
    fun uploadAudio(
        @Part file: MultipartBody.Part
    ): Call<PredictionResponse>
}
package com.realwear.imagettext

import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class OCRApiService(private val context: Context) {
    
    private val httpClient = OkHttpClient()
    
    // API credentials
    private val API_URL = "https://apis-freeocr-ai.p.rapidapi.com/ocr"
    private val API_HOST = "apis-freeocr-ai.p.rapidapi.com"
    
    private fun getApiKey(): String {
        return SettingsManager.getApiKey(context)
    }

    suspend fun extractTextFromImage(imageFile: File): String? {
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    imageFile.name,
                    okhttp3.RequestBody.create(
                        "image/jpeg".toMediaTypeOrNull(),
                        imageFile
                    )
                )
                .build()

            val request = Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .addHeader("X-RapidAPI-Host", API_HOST)
                .addHeader("X-RapidAPI-Key", getApiKey())
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                parseOCRResponse(responseBody)
            } else {
                "API Error: ${response.code}"
            }
        } catch (e: Exception) {
            "Network Error: ${e.message}"
        }
    }

    private fun parseOCRResponse(jsonResponse: String?): String? {
        return try {
            if (jsonResponse == null) return null

            // Parse JSON response
            // The API returns JSON with "text" field containing extracted text
            val json = JSONObject(jsonResponse)
            
            // Extract the "text" field from response
            when {
                json.has("text") -> json.getString("text")
                json.has("result") -> json.getString("result")
                json.has("data") -> {
                    val data = json.getJSONObject("data")
                    if (data.has("text")) data.getString("text") else null
                }
                else -> "Unexpected API response format"
            }
        } catch (e: Exception) {
            "Failed to parse response: ${e.message}"
        }
    }
}

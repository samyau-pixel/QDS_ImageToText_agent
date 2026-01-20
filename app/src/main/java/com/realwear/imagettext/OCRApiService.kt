package com.realwear.imagettext

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class OCRApiService(private val context: Context) {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // API credentials
    private val API_URL = "https://apis-freeocr-ai.p.rapidapi.com/ocr"
    private val API_HOST = "apis-freeocr-ai.p.rapidapi.com"
    
    private fun getApiKey(): String {
        return SettingsManager.getApiKey(context)
    }

    suspend fun extractTextFromImage(imageFile: File): String? {
        return try {
            Log.d("OCRApiService", "Starting image to text conversion for: ${imageFile.absolutePath}")
            Log.d("OCRApiService", "File size: ${imageFile.length()} bytes")
            Log.d("OCRApiService", "File exists: ${imageFile.exists()}")
            
            if (!imageFile.exists()) {
                Log.e("OCRApiService", "Image file does not exist: ${imageFile.absolutePath}")
                return "Error: Image file not found"
            }

            // Create multipart request body - matching curl: --form image=@file
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

            Log.d("OCRApiService", "Request body content type: ${requestBody.contentType()}")
            Log.d("OCRApiService", "Request body size: ${requestBody.contentLength()} bytes")

            // Build request matching curl command
            val request = Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .header("x-rapidapi-host", API_HOST)  // Matching curl header name
                .header("x-rapidapi-key", getApiKey())  // Matching curl header name
                .header("User-Agent", "ImageTText/1.0")
                .build()

            Log.d("OCRApiService", "Sending POST request to: $API_URL")
            Log.d("OCRApiService", "Headers: x-rapidapi-host=$API_HOST")
            Log.d("OCRApiService", "Headers: x-rapidapi-key=${getApiKey().take(10)}...")
            
            val response = httpClient.newCall(request).execute()
            
            Log.d("OCRApiService", "Response code: ${response.code}")
            Log.d("OCRApiService", "Response headers: ${response.headers}")

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d("OCRApiService", "Success response body: $responseBody")
                parseOCRResponse(responseBody)
            } else {
                val errorBody = response.body?.string()
                Log.e("OCRApiService", "API Error - Code: ${response.code}")
                Log.e("OCRApiService", "API Error - Message: ${response.message}")
                Log.e("OCRApiService", "API Error - Body: $errorBody")
                "API Error: ${response.code} - ${response.message}"
            }
        } catch (e: Exception) {
            Log.e("OCRApiService", "Exception: ${e.javaClass.simpleName}", e)
            Log.e("OCRApiService", "Exception message: ${e.message}")
            Log.e("OCRApiService", "Exception cause: ${e.cause}")
            e.printStackTrace()
            "Network Error: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun parseOCRResponse(jsonResponse: String?): String? {
        return try {
            if (jsonResponse == null) return null

            Log.d("OCRApiService", "Parsing response: $jsonResponse")
            
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
                else -> {
                    Log.w("OCRApiService", "Unexpected API response format")
                    "Unexpected API response format"
                }
            }
        } catch (e: Exception) {
            Log.e("OCRApiService", "Failed to parse response: ${e.message}", e)
            "Failed to parse response: ${e.message}"
        }
    }
}

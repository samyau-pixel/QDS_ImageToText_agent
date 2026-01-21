package com.realwear.imagettext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var captureButton: Button
    private lateinit var settingsButton: Button
    private lateinit var sendButton: Button
    private lateinit var rackButton: Button
    private lateinit var label1Button: Button
    private lateinit var label2Button: Button
    private lateinit var rackResult: EditText
    private lateinit var label1Result: EditText
    private lateinit var label2Result: EditText
    
    private lateinit var apiService: OCRApiService
    private lateinit var httpClient: OkHttpClient
    private var currentPhotoUri: android.net.Uri? = null
    private var currentResultView: EditText? = null  // Track which result box to fill
    private var serverURL: String = ""
    
    companion object {
        private const val CAMERA_REQUEST_CODE = 100
        private const val PERMISSION_REQUEST_CODE = 200
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize API service with saved API key
        apiService = OCRApiService(this)
        
        // Load server URL from settings
        val serverIP = SettingsManager.getServerIP(this)
        serverURL = "http://$serverIP:3000/upload"
        Log.d("ImageTText", "Server URL: $serverURL")
        
        // Initialize HTTP client for CSV upload
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Initialize UI components
        captureButton = findViewById(R.id.captureButton)
        settingsButton = findViewById(R.id.settingsButton)
        sendButton = findViewById(R.id.sendButton)
        rackButton = findViewById(R.id.rackButton)
        label1Button = findViewById(R.id.label1Button)
        label2Button = findViewById(R.id.label2Button)
        rackResult = findViewById(R.id.rackResult)
        label1Result = findViewById(R.id.label1Result)
        label2Result = findViewById(R.id.label2Result)

        // Check and request permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                PERMISSION_REQUEST_CODE
            )
        }

        // Set capture button listener
        captureButton.setOnClickListener {
            currentResultView = null
            launchCameraPhotoCapture()
        }

        // Set Rack button listener
        rackButton.setOnClickListener {
            currentResultView = rackResult
            rackResult.setText("Processing...")
            launchCameraPhotoCapture()
        }

        // Set Label_1 button listener
        label1Button.setOnClickListener {
            currentResultView = label1Result
            label1Result.setText("Processing...")
            launchCameraPhotoCapture()
        }

        // Set Label_2 button listener
        label2Button.setOnClickListener {
            currentResultView = label2Result
            label2Result.setText("Processing...")
            launchCameraPhotoCapture()
        }

        // Set Send to Storage button listener
        sendButton.setOnClickListener {
            exportAndSendCSV()
        }

        // Set settings button listener
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun launchCameraPhotoCapture() {
        Log.d("ImageTText", "launchCameraPhotoCapture called")
        
        // Check permissions first
        if (!allPermissionsGranted()) {
            Log.e("ImageTText", "Permissions not granted")
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                PERMISSION_REQUEST_CODE
            )
            return
        }

        try {
            Log.d("ImageTText", "Creating camera intent - Option 1: Raw Bitmap Photo (RealWear style)")
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, CAMERA_REQUEST_CODE)
            Log.d("ImageTText", "Camera intent started")
        } catch (e: Exception) {
            Log.e("ImageTText", "Error launching camera: ${e.message}", e)
            if (currentResultView != null) {
                currentResultView!!.setText("Error: ${e.message}")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("ImageTText", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                // Option 1: Raw Bitmap Photo - Get the bitmap from the intent data
                val photo: Bitmap? = data?.extras?.getParcelable("data")
                
                if (photo != null) {
                    Log.d("ImageTText", "Photo received, size: ${photo.width}x${photo.height}")

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            // Save bitmap to temporary file with high quality
                            val tempFile = File(cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
                            Log.d("ImageTText", "Saving photo to: ${tempFile.absolutePath}")
                            
                            val fos = FileOutputStream(tempFile)
                            val success = photo.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                            fos.close()
                            
                            Log.d("ImageTText", "Bitmap compressed: $success")
                            Log.d("ImageTText", "File size: ${tempFile.length()} bytes")
                            
                            if (tempFile.exists() && tempFile.length() > 0) {
                                Log.d("ImageTText", "Temp file created successfully, sending to OCR")
                                sendImageToOCR(tempFile)
                            } else {
                                runOnUiThread {
                                    val resultView = currentResultView
                                    if (resultView != null) {
                                        resultView.setText("Error: Photo file creation failed")
                                    }
                                    Log.e("ImageTText", "Temp file not created or empty")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImageTText", "Error saving bitmap: ${e.message}", e)
                            runOnUiThread {
                                val resultView = currentResultView
                                if (resultView != null) {
                                    resultView.setText("Error saving photo: ${e.message}")
                                }
                            }
                        }
                    }
                } else {
                    Log.e("ImageTText", "No photo data received")
                    if (currentResultView != null) {
                        currentResultView!!.setText("Error: No photo data received")
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageTText", "Error processing photo: ${e.message}", e)
                if (currentResultView != null) {
                    currentResultView!!.setText("Error: ${e.message}")
                }
            }
        } else {
            Log.d("ImageTText", "Camera cancelled or failed")
            if (currentResultView != null) {
                currentResultView!!.setText("Cancelled")
            }
        }
    }

    private suspend fun sendImageToOCR(imageFile: File) {
        try {
            val result = apiService.extractTextFromImage(imageFile)

            runOnUiThread {
                val resultView = currentResultView
                if (resultView != null) {
                    if (result != null && result.isNotEmpty()) {
                        resultView.setText(result)
                    } else {
                        resultView.setText("No text detected")
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                val resultView = currentResultView
                if (resultView != null) {
                    resultView.setText("Error: ${e.message}")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("ImageTText", "Permission result received: requestCode=$requestCode")
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("ImageTText", "All permissions granted")
            } else {
                Log.d("ImageTText", "Some permissions denied")
            }
        }
    }

    private fun exportAndSendCSV() {
        Log.d("ImageTText", "exportAndSendCSV called")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Create CSV content
                val csvContent = StringBuilder()
                csvContent.append("Field,Value\n")
                csvContent.append("Timestamp,${getCurrentTimestamp()}\n")
                csvContent.append("Rack,${rackResult.text}\n")
                csvContent.append("Label_1,${label1Result.text}\n")
                csvContent.append("Label_2,${label2Result.text}\n")
                
                // Create temporary CSV file
                val csvFile = File(cacheDir, "data_${System.currentTimeMillis()}.csv")
                csvFile.writeText(csvContent.toString())
                
                Log.d("ImageTText", "CSV file created: ${csvFile.absolutePath}")
                Log.d("ImageTText", "CSV content:\n$csvContent")
                
                // Upload to server
                uploadCSVToServer(csvFile)
                
            } catch (e: Exception) {
                Log.e("ImageTText", "Error exporting CSV: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error exporting CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uploadCSVToServer(csvFile: File) {
        try {
            Log.d("ImageTText", "Uploading CSV to: $serverURL")
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("csv_file", csvFile.name, csvFile.asRequestBody("text/csv".toMediaTypeOrNull()))
                .addFormDataPart("device_name", "RealWear-ImageTText")
                .addFormDataPart("timestamp", getCurrentTimestamp())
                .build()
            
            val request = Request.Builder()
                .url(serverURL)
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            Log.d("ImageTText", "Upload response code: ${response.code}")
            Log.d("ImageTText", "Upload response: ${response.body?.string()}")
            
            runOnUiThread {
                if (response.isSuccessful) {
                    Toast.makeText(this, "Data sent successfully!", Toast.LENGTH_SHORT).show()
                    Log.d("ImageTText", "CSV uploaded successfully")
                } else {
                    Toast.makeText(this, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    Log.e("ImageTText", "Upload failed with code: ${response.code}")
                }
            }
            
            response.close()
        } catch (e: Exception) {
            Log.e("ImageTText", "Error uploading CSV: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "Error uploading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}

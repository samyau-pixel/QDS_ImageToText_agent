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

    private lateinit var settingsButton: Button
    private lateinit var saveButton: Button
    private lateinit var previewButton: Button
    private lateinit var rackButton: Button
    private lateinit var label1Button: Button
    private lateinit var label2Button: Button
    private lateinit var rackResult: EditText
    private lateinit var label1Result: EditText
    private lateinit var label2Result: EditText
    
    private lateinit var apiService: OCRApiService
    private lateinit var httpClient: OkHttpClient
    private lateinit var csvManager: CSVManager
    private var currentPhotoUri: android.net.Uri? = null
    private var currentResultView: EditText? = null  // Track which result box to fill
    private var serverURL: String = ""
    
    // Track photo paths for current session
    private var rackPhotoPath: String? = null
    private var label1PhotoPath: String? = null
    private var label2PhotoPath: String? = null
    
    companion object {
        private const val CAMERA_REQUEST_CODE = 100
        private const val PERMISSION_REQUEST_CODE = 200
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide the action bar
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_main)

        // Initialize API service with saved API key
        apiService = OCRApiService(this)
        csvManager = CSVManager(this)
        
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
        settingsButton = findViewById(R.id.settingsButton)
        saveButton = findViewById(R.id.saveButton)
        previewButton = findViewById(R.id.previewButton)
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

        // Set Save button listener
        saveButton.setOnClickListener {
            saveCurrentEntry()
        }

        // Set Preview button listener
        previewButton.setOnClickListener {
            showPreview()
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
                            // Determine which field this photo is for
                            val fieldName = when (currentResultView) {
                                rackResult -> "rack"
                                label1Result -> "label1"
                                label2Result -> "label2"
                                else -> "general"
                            }
                            
                            // Save photo to persistent storage with meaningful name
                            val timestamp = System.currentTimeMillis()
                            val photoFilename = "${fieldName}_photo_$timestamp.jpg"
                            val photoFile = csvManager.savePhoto(photo, photoFilename)
                            
                            if (photoFile != null) {
                                Log.d("ImageTText", "Photo saved to: ${photoFile.absolutePath}")
                                
                                // Track the photo path based on which field it's for
                                when (currentResultView) {
                                    rackResult -> rackPhotoPath = photoFile.absolutePath
                                    label1Result -> label1PhotoPath = photoFile.absolutePath
                                    label2Result -> label2PhotoPath = photoFile.absolutePath
                                }
                                
                                // Send to OCR for text extraction
                                sendImageToOCR(photoFile)
                            } else {
                                runOnUiThread {
                                    val resultView = currentResultView
                                    if (resultView != null) {
                                        resultView.setText("Error: Photo save failed")
                                    }
                                    Log.e("ImageTText", "Failed to save photo")
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

    private fun uploadCSVToServer(csvFile: File): Boolean {
        var uploadSuccess = false
        try {
            Log.d("ImageTText", "Uploading CSV and photos to: $serverURL")
            
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("csv_file", csvFile.name, csvFile.asRequestBody("text/csv".toMediaTypeOrNull()))
                .addFormDataPart("device_name", "RealWear-ImageTText")
                .addFormDataPart("timestamp", getCurrentTimestamp())
            
            // Add all photos from the photo directory
            val photoDir = csvManager.getPhotoDirectory()
            if (photoDir.exists() && photoDir.isDirectory) {
                val photoFiles = photoDir.listFiles()
                if (photoFiles != null) {
                    for (photoFile in photoFiles) {
                        if (photoFile.isFile && photoFile.name.endsWith(".jpg")) {
                            Log.d("ImageTText", "Adding photo to upload: ${photoFile.name}")
                            requestBodyBuilder.addFormDataPart(
                                "photos",
                                photoFile.name,
                                photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                            )
                        }
                    }
                }
            }
            
            val requestBody = requestBodyBuilder.build()
            
            val request = Request.Builder()
                .url(serverURL)
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            Log.d("ImageTText", "Upload response code: ${response.code}")
            Log.d("ImageTText", "Upload response: ${response.body?.string()}")
            
            uploadSuccess = response.isSuccessful
            
            runOnUiThread {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Data sent successfully!", Toast.LENGTH_SHORT).show()
                    Log.d("ImageTText", "CSV and photos uploaded successfully")
                } else {
                    Toast.makeText(this@MainActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    Log.e("ImageTText", "Upload failed with code: ${response.code}")
                }
            }
            
            response.close()
        } catch (e: Exception) {
            Log.e("ImageTText", "Error uploading CSV: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Error uploading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        return uploadSuccess
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    private fun saveCurrentEntry() {
        try {
            val rackNumber = rackResult.text.toString().trim()
            val label1 = label1Result.text.toString().trim()
            val label2 = label2Result.text.toString().trim()
            
            if (rackNumber.isEmpty() && label1.isEmpty() && label2.isEmpty()) {
                Toast.makeText(this, "Please enter at least one value", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Add entry to CSV
            val success = csvManager.addEntry(
                rackNumber = rackNumber,
                label1 = label1,
                label1PhotoPath = label1PhotoPath,
                label2 = label2,
                label2PhotoPath = label2PhotoPath
            )
            
            if (success) {
                Toast.makeText(this, "Entry saved!", Toast.LENGTH_SHORT).show()
                clearCurrentEntry()
            } else {
                Toast.makeText(this, "Error saving entry", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ImageTText", "Error saving entry: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearCurrentEntry() {
        rackResult.setText("")
        label1Result.setText("")
        label2Result.setText("")
        rackPhotoPath = null
        label1PhotoPath = null
        label2PhotoPath = null
        Log.d("ImageTText", "Current entry cleared")
    }

    private fun showPreview() {
        try {
            if (!csvManager.hasEntries()) {
                Toast.makeText(this, "No data to preview", Toast.LENGTH_SHORT).show()
                return
            }
            
            val intent = Intent(this, PreviewActivity::class.java)
            startActivity(intent)
            Log.d("ImageTText", "Preview activity launched")
        } catch (e: Exception) {
            Log.e("ImageTText", "Error showing preview: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAndSendCSV() {
        try {
            if (!csvManager.hasEntries()) {
                Toast.makeText(this, "No data to send", Toast.LENGTH_SHORT).show()
                return
            }
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val csvFile = csvManager.getCSVFile()
                    val uploadSuccess = uploadCSVToServer(csvFile)
                    
                    if (uploadSuccess) {
                        runOnUiThread {
                            // Clear everything only after successful upload
                            csvManager.clearAll()
                            clearCurrentEntry()
                            Toast.makeText(this@MainActivity, "Data cleared for next batch", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w("ImageTText", "Upload failed - keeping data for retry")
                    }
                } catch (e: Exception) {
                    Log.e("ImageTText", "Error in export flow: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImageTText", "Error exporting: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}

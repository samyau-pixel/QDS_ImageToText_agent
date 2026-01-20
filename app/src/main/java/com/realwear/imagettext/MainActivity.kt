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
import android.widget.TextView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var captureButton: Button
    private lateinit var settingsButton: Button
    private lateinit var resultTextView: TextView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var apiService: OCRApiService
    private var currentPhotoUri: android.net.Uri? = null
    
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

        // Initialize UI components
        captureButton = findViewById(R.id.captureButton)
        settingsButton = findViewById(R.id.settingsButton)
        resultTextView = findViewById(R.id.resultTextView)
        progressBar = findViewById(R.id.progressBar)

        // Check and request permissions
        if (allPermissionsGranted()) {
            resultTextView.text = "Ready to capture photo"
        } else {
            resultTextView.text = "Requesting permissions..."
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                PERMISSION_REQUEST_CODE
            )
        }

        // Set capture button listener
        captureButton.setOnClickListener {
            launchCameraPhotoCapture()
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
            resultTextView.text = "Requesting camera permission..."
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                PERMISSION_REQUEST_CODE
            )
            return
        }

        try {
            Log.d("ImageTText", "Creating camera intent - Option 1: Raw Bitmap Photo (RealWear style)")
            // Option 1: Raw Bitmap Photo - Simple approach, no storage permissions needed
            // Following RealWear's CameraActivity.java example
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            resultTextView.text = "Opening camera..."
            startActivityForResult(intent, CAMERA_REQUEST_CODE)
            Log.d("ImageTText", "Camera intent started")
        } catch (e: Exception) {
            Log.e("ImageTText", "Error launching camera: ${e.message}", e)
            resultTextView.text = "Error: ${e.message}"
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
                    resultTextView.text = "Photo captured. Processing..."
                    progressBar.visibility = View.VISIBLE

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
                                    progressBar.visibility = View.GONE
                                    resultTextView.text = "Error: Photo file creation failed"
                                    Log.e("ImageTText", "Temp file not created or empty")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImageTText", "Error saving bitmap: ${e.message}", e)
                            runOnUiThread {
                                progressBar.visibility = View.GONE
                                resultTextView.text = "Error saving photo: ${e.message}"
                            }
                        }
                    }
                } else {
                    Log.e("ImageTText", "No photo data received")
                    resultTextView.text = "Error: No photo data received from camera"
                }
            } catch (e: Exception) {
                Log.e("ImageTText", "Error processing photo: ${e.message}", e)
                resultTextView.text = "Error: ${e.message}"
            }
        } else {
            Log.d("ImageTText", "Camera cancelled or failed")
            resultTextView.text = "Camera cancelled"
        }
    }

    private suspend fun sendImageToOCR(imageFile: File) {
        try {
            val result = apiService.extractTextFromImage(imageFile)

            runOnUiThread {
                progressBar.visibility = View.GONE

                if (result != null && result.isNotEmpty()) {
                    resultTextView.text = result
                } else {
                    resultTextView.text = "No text detected in image"
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                progressBar.visibility = View.GONE
                resultTextView.text = "Error processing image: ${e.message}"
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
                resultTextView.text = "Ready to capture photo"
            } else {
                Log.d("ImageTText", "Some permissions denied")
                resultTextView.text = "Permissions required. Please enable camera and storage permissions."
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}

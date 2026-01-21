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
    private lateinit var rackButton: Button
    private lateinit var label1Button: Button
    private lateinit var label2Button: Button
    private lateinit var rackResult: TextView
    private lateinit var label1Result: TextView
    private lateinit var label2Result: TextView
    
    private lateinit var apiService: OCRApiService
    private var currentPhotoUri: android.net.Uri? = null
    private var currentResultView: TextView? = null  // Track which result box to fill
    
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
            rackResult.text = "Processing..."
            launchCameraPhotoCapture()
        }

        // Set Label_1 button listener
        label1Button.setOnClickListener {
            currentResultView = label1Result
            label1Result.text = "Processing..."
            launchCameraPhotoCapture()
        }

        // Set Label_2 button listener
        label2Button.setOnClickListener {
            currentResultView = label2Result
            label2Result.text = "Processing..."
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
                currentResultView!!.text = "Error: ${e.message}"
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
                                        resultView.text = "Error: Photo file creation failed"
                                    }
                                    Log.e("ImageTText", "Temp file not created or empty")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImageTText", "Error saving bitmap: ${e.message}", e)
                            runOnUiThread {
                                val resultView = currentResultView
                                if (resultView != null) {
                                    resultView.text = "Error saving photo: ${e.message}"
                                }
                            }
                        }
                    }
                } else {
                    Log.e("ImageTText", "No photo data received")
                    if (currentResultView != null) {
                        currentResultView!!.text = "Error: No photo data received"
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageTText", "Error processing photo: ${e.message}", e)
                if (currentResultView != null) {
                    currentResultView!!.text = "Error: ${e.message}"
                }
            }
        } else {
            Log.d("ImageTText", "Camera cancelled or failed")
            if (currentResultView != null) {
                currentResultView!!.text = "Cancelled"
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
                        resultView.text = result
                    } else {
                        resultView.text = "No text detected"
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                val resultView = currentResultView
                if (resultView != null) {
                    resultView.text = "Error: ${e.message}"
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

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}

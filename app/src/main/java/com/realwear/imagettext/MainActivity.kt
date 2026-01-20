package com.realwear.imagettext

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
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
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

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
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        captureButton = findViewById(R.id.captureButton)
        settingsButton = findViewById(R.id.settingsButton)
        resultTextView = findViewById(R.id.resultTextView)
        progressBar = findViewById(R.id.progressBar)

        // Check and request permissions
        if (allPermissionsGranted()) {
            resultTextView.text = "Ready to capture photo"
        } else {
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
            resultTextView.text = "Permissions required. Please enable camera and storage permissions."
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                PERMISSION_REQUEST_CODE
            )
            return
        }

        try {
            Log.d("ImageTText", "Creating camera intent")
            // Create camera intent
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

            // Check if camera intent can be resolved
            val cameraActivity = captureIntent.resolveActivity(packageManager)
            if (cameraActivity != null) {
                Log.d("ImageTText", "Camera app found: $cameraActivity")
                
                // Create content values for the photo
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "photo_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
                }

                // Insert into MediaStore to get URI
                currentPhotoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                Log.d("ImageTText", "Photo URI created: $currentPhotoUri")
                
                if (currentPhotoUri != null) {
                    captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                    captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    
                    Log.d("ImageTText", "Starting camera activity")
                    resultTextView.text = "Opening camera..."
                    startActivityForResult(captureIntent, CAMERA_REQUEST_CODE)
                } else {
                    Log.e("ImageTText", "Failed to create photo URI")
                    resultTextView.text = "Error: Could not create photo file"
                }
            } else {
                Log.e("ImageTText", "No camera app available")
                resultTextView.text = "Error: No camera application available"
            }
        } catch (e: Exception) {
            Log.e("ImageTText", "Error launching camera: ${e.message}", e)
            resultTextView.text = "Error: ${e.message}"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            resultTextView.text = "Photo captured. Processing..."
            progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                currentPhotoUri?.let { uri ->
                    val photoFile = File(getRealPathFromURI(uri))
                    if (photoFile.exists()) {
                        sendImageToOCR(photoFile)
                    } else {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            resultTextView.text = "Error: Photo file not found"
                        }
                    }
                }
            }
        }
    }

    private fun getRealPathFromURI(uri: android.net.Uri): String {
        return when (uri.scheme) {
            "content" -> {
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    } else {
                        uri.path ?: ""
                    }
                } ?: (uri.path ?: "")
            }
            else -> uri.path ?: ""
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

    private fun startCamera() {
        // This function is kept for compatibility but not used
    }

    private fun captureImage() {
        // This function is kept for compatibility but not used
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

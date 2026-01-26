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
import androidx.appcompat.app.AlertDialog
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
    private lateinit var importButton: Button
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
    private var currentTemplate: Template? = null  // Track the current template
    private var currentFieldConfigs: List<FieldConfig> = emptyList()  // Track field configurations
    
    // Track photo paths for current session
    private var rackPhotoPath: String? = null
    private var label1PhotoPath: String? = null
    private var label2PhotoPath: String? = null
    private var dynamicFieldPhotoPaths = mutableMapOf<Int, String>()  // Track photos for dynamic fields by EditText ID
    
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
        importButton = findViewById(R.id.importButton)
        rackButton = findViewById(R.id.rackButton)
        label1Button = findViewById(R.id.label1Button)
        label2Button = findViewById(R.id.label2Button)
        rackResult = findViewById(R.id.rackResult)
        label1Result = findViewById(R.id.label1Result)
        label2Result = findViewById(R.id.label2Result)
        
        // Load template if available
        loadTemplateFields()

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
            // Check if this is a multiple-choice field
            if (currentFieldConfigs.isNotEmpty() && currentFieldConfigs[0].isMultipleChoice) {
                ChoiceFieldHelper.showChoiceDialog(
                    this,
                    currentFieldConfigs[0].fieldName,
                    currentFieldConfigs[0].choices,
                    rackResult
                )
            } else {
                // Normal behavior: capture from camera
                currentResultView = rackResult
                // If re-capturing, delete old photo first
                if (!rackPhotoPath.isNullOrEmpty()) {
                    try {
                        val oldFile = File(rackPhotoPath!!)
                        if (oldFile.exists()) {
                            oldFile.delete()
                            Log.d("ImageTText", "Deleted old rack photo: $rackPhotoPath")
                        }
                    } catch (e: Exception) {
                        Log.e("ImageTText", "Error deleting old photo: ${e.message}")
                    }
                    rackPhotoPath = null
                }
                rackResult.setText("Processing...")
                launchCameraPhotoCapture()
            }
        }

        // Set Label_1 button listener
        label1Button.setOnClickListener {
            // Check if this is a multiple-choice field
            if (currentFieldConfigs.size > 1 && currentFieldConfigs[1].isMultipleChoice) {
                ChoiceFieldHelper.showChoiceDialog(
                    this,
                    currentFieldConfigs[1].fieldName,
                    currentFieldConfigs[1].choices,
                    label1Result
                )
            } else {
                // Normal behavior: capture from camera
                currentResultView = label1Result
                // If re-capturing, delete old photo first
                if (!label1PhotoPath.isNullOrEmpty()) {
                    try {
                        val oldFile = File(label1PhotoPath!!)
                        if (oldFile.exists()) {
                            oldFile.delete()
                            Log.d("ImageTText", "Deleted old label1 photo: $label1PhotoPath")
                        }
                    } catch (e: Exception) {
                        Log.e("ImageTText", "Error deleting old photo: ${e.message}")
                    }
                    label1PhotoPath = null
                }
                label1Result.setText("Processing...")
                launchCameraPhotoCapture()
            }
        }

        // Set Label_2 button listener
        label2Button.setOnClickListener {
            // Check if this is a multiple-choice field
            if (currentFieldConfigs.size > 2 && currentFieldConfigs[2].isMultipleChoice) {
                ChoiceFieldHelper.showChoiceDialog(
                    this,
                    currentFieldConfigs[2].fieldName,
                    currentFieldConfigs[2].choices,
                    label2Result
                )
            } else {
                // Normal behavior: capture from camera
                currentResultView = label2Result
                // If re-capturing, delete old photo first
                if (!label2PhotoPath.isNullOrEmpty()) {
                    try {
                        val oldFile = File(label2PhotoPath!!)
                        if (oldFile.exists()) {
                            oldFile.delete()
                            Log.d("ImageTText", "Deleted old label2 photo: $label2PhotoPath")
                        }
                    } catch (e: Exception) {
                        Log.e("ImageTText", "Error deleting old photo: ${e.message}")
                    }
                    label2PhotoPath = null
                }
                label2Result.setText("Processing...")
                launchCameraPhotoCapture()
            }
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

        // Set import button listener
        importButton.setOnClickListener {
            shouldReloadTemplate = true
            val intent = Intent(this, TemplateImportActivity::class.java)
            startActivity(intent)
        }
    }

    private var shouldReloadTemplate = false

    override fun onResume() {
        super.onResume()
        // Only reload template fields if we explicitly came from TemplateImportActivity
        if (shouldReloadTemplate) {
            loadTemplateFields()
            shouldReloadTemplate = false
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
                                    else -> {
                                        // For dynamic fields, track by EditText ID
                                        currentResultView?.id?.let { editTextId ->
                                            dynamicFieldPhotoPaths[editTextId] = photoFile.absolutePath
                                            Log.d("ImageTText", "Tracked dynamic field photo: ID=$editTextId, Path=${photoFile.absolutePath}")
                                        }
                                    }
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
            // Collect all field values including dynamic ones
            val fieldValues = mutableMapOf<String, String>()
            val fieldPhotoPaths = mutableMapOf<String, String>()
            
            // Get template columns or use defaults if no template
            val template = currentTemplate
            val templateColumns = template?.columns ?: listOf("Rack Number", "Label1", "Label2")
            
            // Only collect values for fields that exist in the template
            if (templateColumns.size >= 1) {
                fieldValues[templateColumns[0]] = rackResult.text.toString().trim()
                if (!rackPhotoPath.isNullOrEmpty()) {
                    fieldPhotoPaths[templateColumns[0]] = rackPhotoPath ?: ""
                }
            }
            if (templateColumns.size >= 2) {
                fieldValues[templateColumns[1]] = label1Result.text.toString().trim()
                if (!label1PhotoPath.isNullOrEmpty()) {
                    fieldPhotoPaths[templateColumns[1]] = label1PhotoPath ?: ""
                }
            }
            if (templateColumns.size >= 3) {
                fieldValues[templateColumns[2]] = label2Result.text.toString().trim()
                if (!label2PhotoPath.isNullOrEmpty()) {
                    fieldPhotoPaths[templateColumns[2]] = label2PhotoPath ?: ""
                }
            }
            
            // Get dynamic field values (for fields beyond the first 3)
            val fieldsContainer = findViewById<android.widget.LinearLayout>(R.id.fieldsContainer)
            for (i in 3 until fieldsContainer.childCount) {
                val view = fieldsContainer.getChildAt(i)
                if (view is android.widget.LinearLayout && view.tag == "dynamic_field") {
                    for (j in 0 until view.childCount) {
                        val child = view.getChildAt(j)
                        if (child is android.widget.EditText) {
                            // Get the field name from template
                            if (i < templateColumns.size) {
                                val fieldName = templateColumns[i]
                                fieldValues[fieldName] = child.text.toString().trim()
                                
                                // Check if this field has a photo path tracked
                                if (dynamicFieldPhotoPaths.containsKey(child.id)) {
                                    fieldPhotoPaths[fieldName] = dynamicFieldPhotoPaths[child.id] ?: ""
                                }
                            }
                            break
                        }
                    }
                }
            }
            
            // Check if at least one field has data
            if (fieldValues.all { it.value.isEmpty() }) {
                Toast.makeText(this, "Please enter at least one value", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Add entry to CSV with all fields
            val success = csvManager.addDynamicEntry(fieldValues, fieldPhotoPaths)
            
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
        dynamicFieldPhotoPaths.clear()
        
        // Also clear dynamic field EditTexts
        val fieldsContainer = findViewById<android.widget.LinearLayout>(R.id.fieldsContainer)
        for (i in 3 until fieldsContainer.childCount) {
            val view = fieldsContainer.getChildAt(i)
            if (view is android.widget.LinearLayout && view.tag == "dynamic_field") {
                for (j in 0 until view.childCount) {
                    val child = view.getChildAt(j)
                    if (child is android.widget.EditText) {
                        child.setText("")
                        break
                    }
                }
            }
        }
        
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

    private fun loadTemplateFields() {
        val template = TemplateManager.loadTemplate(this)
        
        if (template != null) {
            currentTemplate = template  // Store the current template
            currentFieldConfigs = template.fieldConfigs  // Store the field configurations
            Log.d("ImageTText", "Loading template: ${template.name} with columns: ${template.columns}")
            
            val fieldsContainer = findViewById<android.widget.LinearLayout>(R.id.fieldsContainer)
            
            // Always remove any previously added dynamic fields first
            val viewsToRemove = mutableListOf<android.view.View>()
            for (i in 0 until fieldsContainer.childCount) {
                val view = fieldsContainer.getChildAt(i)
                if (view.tag == "dynamic_field") {
                    viewsToRemove.add(view)
                }
            }
            for (view in viewsToRemove) {
                fieldsContainer.removeView(view)
            }
            
            // Clear old dynamic field data
            dynamicFieldPhotoPaths.clear()
            
            // Update CSV headers to match template columns
            csvManager.reinitializeCSVWithTemplate(template.columns)
            
            // Show/hide the first 3 buttons based on template column count
            rackButton.visibility = if (template.columns.size >= 1) View.VISIBLE else View.GONE
            label1Button.visibility = if (template.columns.size >= 2) View.VISIBLE else View.GONE
            label2Button.visibility = if (template.columns.size >= 3) View.VISIBLE else View.GONE
            
            // Also hide the corresponding result fields
            rackResult.visibility = if (template.columns.size >= 1) View.VISIBLE else View.GONE
            label1Result.visibility = if (template.columns.size >= 2) View.VISIBLE else View.GONE
            label2Result.visibility = if (template.columns.size >= 3) View.VISIBLE else View.GONE
            
            // Clear text in hidden result fields
            if (template.columns.size < 1) rackResult.setText("")
            if (template.columns.size < 2) label1Result.setText("")
            if (template.columns.size < 3) label2Result.setText("")
            
            // Update the button labels with template field names (without choice info)
            if (template.columns.size >= 1) {
                val fieldName = if (currentFieldConfigs.isNotEmpty()) {
                    currentFieldConfigs[0].fieldName
                } else {
                    template.columns[0]
                }
                rackButton.text = fieldName
                Log.d("ImageTText", "Updated rackButton to: $fieldName")
            }
            if (template.columns.size >= 2) {
                val fieldName = if (currentFieldConfigs.size > 1) {
                    currentFieldConfigs[1].fieldName
                } else {
                    template.columns[1]
                }
                label1Button.text = fieldName
                Log.d("ImageTText", "Updated label1Button to: $fieldName")
            }
            if (template.columns.size >= 3) {
                val fieldName = if (currentFieldConfigs.size > 2) {
                    currentFieldConfigs[2].fieldName
                } else {
                    template.columns[2]
                }
                label2Button.text = fieldName
                Log.d("ImageTText", "Updated label2Button to: $fieldName")
            }
            
            // Create additional fields for columns beyond the first 3
            if (template.columns.size > 3) {
                for (i in 3 until template.columns.size) {
                    val fieldConfig = if (i < currentFieldConfigs.size) {
                        currentFieldConfigs[i]
                    } else {
                        FieldConfig.parse(template.columns[i])
                    }
                    createDynamicField(fieldsContainer, fieldConfig, i)
                }
            }
            
            Toast.makeText(this, "Template loaded: ${template.name} (${template.columns.size} fields)", Toast.LENGTH_SHORT).show()
        } else {
            currentTemplate = null
            currentFieldConfigs = emptyList()
            Log.d("ImageTText", "No template loaded, using default fields")
        }
    }

    private fun createDynamicField(container: android.widget.LinearLayout, fieldConfig: FieldConfig, index: Int) {
        val fieldLayout = android.widget.LinearLayout(this)
        fieldLayout.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(80)
        ).apply {
            setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }
        fieldLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        fieldLayout.tag = "dynamic_field"
        
        // Create button
        val button = android.widget.Button(this)
        button.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            0.5f
        )
        button.text = fieldConfig.fieldName
        button.textSize = 14f
        button.setTextColor(android.graphics.Color.WHITE)
        button.setBackgroundResource(if (index % 2 == 0) R.drawable.button_rack else R.drawable.button_label1)
        button.id = android.view.View.generateViewId()
        
        // Create EditText
        val editText = android.widget.EditText(this)
        val editLayoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            1.5f
        )
        editLayoutParams.leftMargin = dpToPx(6)
        editText.layoutParams = editLayoutParams
        editText.hint = "Empty"
        editText.setTextColor(android.graphics.Color.WHITE)
        editText.setHintTextColor(android.graphics.Color.parseColor("#4A4A4A"))
        editText.gravity = android.view.Gravity.CENTER
        editText.textSize = 16f
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT
        editText.setBackgroundResource(if (index % 2 == 0) R.drawable.edittext_dark else R.drawable.edittext_light)
        editText.id = android.view.View.generateViewId()
        
        // Add click listener to button
        button.setOnClickListener {
            if (fieldConfig.isMultipleChoice) {
                // For multiple-choice fields, show dialog
                ChoiceFieldHelper.showChoiceDialog(
                    this,
                    fieldConfig.fieldName,
                    fieldConfig.choices,
                    editText
                )
            } else {
                // For normal fields, capture from camera
                currentResultView = editText
                
                // If re-capturing, delete old photo first
                val editTextId = editText.id
                if (dynamicFieldPhotoPaths.containsKey(editTextId)) {
                    try {
                        val oldFile = File(dynamicFieldPhotoPaths[editTextId]!!)
                        if (oldFile.exists()) {
                            oldFile.delete()
                            Log.d("ImageTText", "Deleted old dynamic field photo: ${dynamicFieldPhotoPaths[editTextId]}")
                        }
                    } catch (e: Exception) {
                        Log.e("ImageTText", "Error deleting old photo: ${e.message}")
                    }
                    dynamicFieldPhotoPaths.remove(editTextId)
                }
                
                editText.setText("Processing...")
                launchCameraPhotoCapture()
            }
        }
        
        fieldLayout.addView(button)
        fieldLayout.addView(editText)
        container.addView(fieldLayout)
        
        Log.d("ImageTText", "Created dynamic field: ${fieldConfig.fieldName} at index $index (isMultipleChoice: ${fieldConfig.isMultipleChoice})")
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * this.resources.displayMetrics.density).toInt()
    }

}

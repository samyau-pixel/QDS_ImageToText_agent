package com.realwear.imagettext

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class PreviewActivity : AppCompatActivity() {
    
    private lateinit var csvManager: CSVManager
    private lateinit var tableLayout: TableLayout
    private lateinit var closeButton: Button
    private lateinit var sendButton: Button
    private lateinit var httpClient: OkHttpClient
    private var serverURL: String = "192.168.1.65"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide the action bar
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_preview)
        
        csvManager = CSVManager(this)
        tableLayout = findViewById(R.id.dataTable)
        closeButton = findViewById(R.id.closeButton)
        sendButton = findViewById(R.id.sendButton)
        
        // Initialize HTTP client
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        // Get server URL from SharedPreferences
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        serverURL = sharedPref.getString("server_ip", "192.168.1.65") ?: "192.168.1.65"
        
        displayData()
        
        closeButton.setOnClickListener {
            finish()
        }
        
        sendButton.setOnClickListener {
            exportAndSendCSV()
        }
    }
    
    private fun exportAndSendCSV() {
        if (!csvManager.hasEntries()) {
            Toast.makeText(this, "No data to send", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val csvFile = csvManager.getCSVFile()
                val success = uploadCSVToServer(csvFile)
                
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this@PreviewActivity, "Data sent successfully!", Toast.LENGTH_SHORT).show()
                        csvManager.clearAll()
                        finish()
                    } else {
                        Toast.makeText(this@PreviewActivity, "Failed to send data", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@PreviewActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("PreviewActivity", "Export error: ${e.message}", e)
            }
        }
    }
    
    private fun uploadCSVToServer(csvFile: File): Boolean {
        return try {
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("csv_file", csvFile.name, csvFile.asRequestBody("text/csv".toMediaTypeOrNull()))
                .addFormDataPart("device_name", "RealWear")
                .addFormDataPart("timestamp", getCurrentTimestamp())
            
            // Add all photos from photo directory
            val photoDir = csvManager.getPhotoDirectory()
            if (photoDir.exists() && photoDir.isDirectory) {
                photoDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        multipartBuilder.addFormDataPart(
                            "photos",
                            file.name,
                            file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        )
                    }
                }
            }
            
            val requestBody = multipartBuilder.build()
            
            val request = Request.Builder()
                .url("http://$serverURL:3000/upload")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful
            
            Log.d("PreviewActivity", "Upload response: ${response.code}")
            response.body?.string()?.let { Log.d("PreviewActivity", "Response body: $it") }
            
            success
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Upload error: ${e.message}", e)
            false
        }
    }
    
    private fun getCurrentTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }
    
    private fun displayData() {
        try {
            val csvContent = csvManager.getCSVContent()
            
            if (csvContent.isEmpty() || csvContent.lines().size <= 1) {
                // Show empty message
                val emptyRow = TableRow(this)
                val emptyText = TextView(this)
                emptyText.text = "No data to preview"
                emptyText.setPadding(16, 16, 16, 16)
                emptyRow.addView(emptyText)
                tableLayout.addView(emptyRow)
                return
            }
            
            val lines = csvContent.lines()
            
            // Add header row
            if (lines.isNotEmpty()) {
                val headerRow = TableRow(this)
                val headerParams = TableLayout.LayoutParams()
                headerParams.setMargins(0, 4, 0, 4)
                headerRow.layoutParams = headerParams
                val headers = lines[0].split(",")
                for (header in headers) {
                    val headerCell = TextView(this)
                    headerCell.text = header.trim().replace("\"", "")
                    headerCell.setPadding(12, 12, 12, 12)
                    headerCell.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.table_header_cell)
                    headerCell.setTextColor(0xFFFFFFFF.toInt()) // White text
                    headerCell.textSize = 12f
                    val params = TableRow.LayoutParams()
                    params.setMargins(4, 4, 4, 4)
                    headerCell.layoutParams = params
                    headerRow.addView(headerCell)
                }
                tableLayout.addView(headerRow)
            }
            
            // Add data rows
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.trim().isEmpty()) continue
                
                val dataRow = TableRow(this)
                dataRow.isClickable = true
                val rowParams = TableLayout.LayoutParams()
                rowParams.setMargins(0, 4, 0, 4)
                dataRow.layoutParams = rowParams
                
                // Alternate row background colors for better readability
                val isEvenRow = (i - 1) % 2 == 0
                val cellBackground = if (isEvenRow) {
                    androidx.core.content.ContextCompat.getDrawable(this, R.drawable.table_cell_white)
                } else {
                    androidx.core.content.ContextCompat.getDrawable(this, R.drawable.table_cell_blue)
                }
                
                // Parse CSV line carefully to handle quoted values
                val cells = parseCSVLine(line)
                
                for (cleanCell in cells) {
                    if (cleanCell.endsWith(".jpg", ignoreCase = true) || 
                        cleanCell.endsWith(".jpeg", ignoreCase = true) || 
                        cleanCell.endsWith(".png", ignoreCase = true)) {
                        // Try to find and display the photo
                        val photoFile = findPhotoFile(cleanCell)
                        if (photoFile != null && photoFile.exists()) {
                            val photoView = android.widget.ImageView(this)
                            val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                            if (bitmap != null) {
                                photoView.setImageBitmap(bitmap)
                                photoView.layoutParams = TableRow.LayoutParams(150, 150)
                                photoView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                                photoView.setPadding(4, 4, 4, 4)
                                val photoParams = TableRow.LayoutParams()
                                photoParams.setMargins(4, 4, 4, 4)
                                photoView.layoutParams = photoParams
                                photoView.setBackgroundColor(0xFFEEEEEE.toInt())
                                dataRow.addView(photoView)
                            } else {
                                // Fallback to text if bitmap is null
                                val textView = TextView(this)
                                textView.text = cleanCell
                                textView.setPadding(12, 12, 12, 12)
                                textView.background = cellBackground
                                textView.setTextColor(0xFF000000.toInt())
                                textView.textSize = 11f
                                val params = TableRow.LayoutParams()
                                params.setMargins(4, 4, 4, 4)
                                textView.layoutParams = params
                                dataRow.addView(textView)
                            }
                        } else {
                            // File not found, show filename as text
                            val textView = TextView(this)
                            textView.text = cleanCell
                            textView.setPadding(12, 12, 12, 12)
                            textView.background = cellBackground
                            textView.setTextColor(0xFF000000.toInt())
                            textView.textSize = 11f
                            val params = TableRow.LayoutParams()
                            params.setMargins(4, 4, 4, 4)
                            textView.layoutParams = params
                            dataRow.addView(textView)
                        }
                    } else {
                        // Regular text cell
                        val cellView = TextView(this)
                        cellView.text = cleanCell
                        cellView.setPadding(12, 12, 12, 12)
                        cellView.background = cellBackground
                        cellView.setTextColor(0xFF000000.toInt())
                        cellView.textSize = 11f
                        val params = TableRow.LayoutParams()
                        params.setMargins(4, 4, 4, 4)
                        cellView.layoutParams = params
                        cellView.setOnClickListener {
                            // Light animation on tap
                            cellView.setBackgroundColor(0xFFDDDDDD.toInt())
                        }
                        dataRow.addView(cellView)
                    }
                }
                tableLayout.addView(dataRow)
            }
            
            Log.d("PreviewActivity", "Displayed ${lines.size - 1} data rows")
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error displaying data: ${e.message}", e)
            val errorRow = TableRow(this)
            val errorText = TextView(this)
            errorText.text = "Error loading data: ${e.message}"
            errorText.setPadding(16, 16, 16, 16)
            errorRow.addView(errorText)
            tableLayout.addView(errorRow)
        }
    }
    
    private fun parseCSVLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        var current = StringBuilder()
        var insideQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> {
                    insideQuotes = !insideQuotes
                    current.append(char)
                }
                char == ',' && !insideQuotes -> {
                    cells.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        cells.add(current.toString())
        return cells
    }
    
    private fun findPhotoFile(filename: String): java.io.File? {
        return try {
            val photoDir = csvManager.getPhotoDirectory()
            val photoFile = java.io.File(photoDir, filename)
            if (photoFile.exists() && photoFile.isFile) {
                photoFile
            } else {
                Log.w("PreviewActivity", "Photo not found: $filename")
                null
            }
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error finding photo: ${e.message}", e)
            null
        }
    }
}

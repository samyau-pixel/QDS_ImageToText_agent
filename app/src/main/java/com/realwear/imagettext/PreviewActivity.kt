package com.realwear.imagettext

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PreviewActivity : AppCompatActivity() {
    
    private lateinit var csvManager: CSVManager
    private lateinit var tableLayout: TableLayout
    private lateinit var closeButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        
        csvManager = CSVManager(this)
        tableLayout = findViewById(R.id.dataTable)
        closeButton = findViewById(R.id.closeButton)
        
        displayData()
        
        closeButton.setOnClickListener {
            finish()
        }
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
                val headers = lines[0].split(",")
                for (header in headers) {
                    val headerCell = TextView(this)
                    headerCell.text = header.trim().replace("\"", "")
                    headerCell.setPadding(8, 8, 8, 8)
                    headerCell.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.table_header_cell)
                    headerCell.setTextColor(0xFFFFFFFF.toInt()) // White text
                    headerCell.textSize = 12f
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
                                photoView.setBackgroundColor(0xFFEEEEEE.toInt())
                                dataRow.addView(photoView)
                            } else {
                                // Fallback to text if bitmap is null
                                val textView = TextView(this)
                                textView.text = cleanCell
                                textView.setPadding(8, 8, 8, 8)
                                textView.background = cellBackground
                                textView.setTextColor(0xFF000000.toInt())
                                textView.textSize = 11f
                                dataRow.addView(textView)
                            }
                        } else {
                            // File not found, show filename as text
                            val textView = TextView(this)
                            textView.text = cleanCell
                            textView.setPadding(8, 8, 8, 8)
                            textView.background = cellBackground
                            textView.setTextColor(0xFF000000.toInt())
                            textView.textSize = 11f
                            dataRow.addView(textView)
                        }
                    } else {
                        // Regular text cell
                        val cellView = TextView(this)
                        cellView.text = cleanCell
                        cellView.setPadding(8, 8, 8, 8)
                        cellView.background = cellBackground
                        cellView.setTextColor(0xFF000000.toInt())
                        cellView.textSize = 11f
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

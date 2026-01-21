package com.realwear.imagettext

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CSVManager(private val context: Context) {
    private val csvDir = File(context.getExternalFilesDir(null), "csv_data")
    private val photoDir = File(context.getExternalFilesDir(null), "photos")
    private val csvFile = File(csvDir, "entries.csv")
    
    init {
        csvDir.mkdirs()
        photoDir.mkdirs()
        initializeCSV()
    }
    
    private fun initializeCSV() {
        if (!csvFile.exists()) {
            csvFile.writeText("Rack Number,Label1,Label1_photo,Label2,Label2_photo,Timestamp\n")
            Log.d("CSVManager", "CSV file created at: ${csvFile.absolutePath}")
        }
    }
    
    fun addEntry(
        rackNumber: String,
        label1: String,
        label1PhotoPath: String?,
        label2: String,
        label2PhotoPath: String?
    ): Boolean {
        return try {
            val timestamp = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US).format(Date())
            
            // Use just the filename for photos in CSV, not full path
            val label1PhotoName = label1PhotoPath?.let { File(it).name } ?: ""
            val label2PhotoName = label2PhotoPath?.let { File(it).name } ?: ""
            
            val entry = "$rackNumber,$label1,\"$label1PhotoName\",$label2,\"$label2PhotoName\",$timestamp\n"
            csvFile.appendText(entry)
            
            Log.d("CSVManager", "Entry added: $entry")
            true
        } catch (e: Exception) {
            Log.e("CSVManager", "Error adding entry: ${e.message}", e)
            false
        }
    }
    
    fun getCSVContent(): String {
        return if (csvFile.exists()) {
            csvFile.readText()
        } else {
            ""
        }
    }
    
    fun getCSVFile(): File = csvFile
    
    fun getPhotoDirectory(): File = photoDir
    
    fun savePhoto(bitmap: android.graphics.Bitmap, filename: String): File? {
        return try {
            val photoFile = File(photoDir, filename)
            val fos = java.io.FileOutputStream(photoFile)
            val success = bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, fos)
            fos.close()
            
            if (success && photoFile.exists()) {
                Log.d("CSVManager", "Photo saved: ${photoFile.absolutePath}")
                photoFile
            } else {
                Log.e("CSVManager", "Failed to save photo")
                null
            }
        } catch (e: Exception) {
            Log.e("CSVManager", "Error saving photo: ${e.message}", e)
            null
        }
    }
    
    fun clearAll() {
        try {
            csvFile.delete()
            photoDir.deleteRecursively()
            photoDir.mkdirs()
            initializeCSV()
            Log.d("CSVManager", "All data cleared")
        } catch (e: Exception) {
            Log.e("CSVManager", "Error clearing data: ${e.message}", e)
        }
    }
    
    fun hasEntries(): Boolean {
        return if (csvFile.exists()) {
            val lines = csvFile.readLines()
            lines.size > 1 // More than just the header
        } else {
            false
        }
    }
}

package com.realwear.imagettext

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TemplateImportActivity : AppCompatActivity() {

    private lateinit var templateListView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var backButton: Button
    private lateinit var httpClient: OkHttpClient
    private var templates = mutableListOf<String>()
    private var serverIP: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_template_import)

        // Hide the action bar
        supportActionBar?.hide()

        // Initialize HTTP client
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Initialize UI components
        templateListView = findViewById(R.id.templateListView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        backButton = findViewById(R.id.backButton)

        // Set back button listener
        backButton.setOnClickListener {
            finish()
        }

        // Load server IP
        serverIP = SettingsManager.getServerIP(this)

        // Load templates
        loadTemplates()
    }

    private fun loadTemplates() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val templatesURL = "http://$serverIP:3000/api/templates"

                val request = Request.Builder()
                    .url(templatesURL)
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@launch
                    val jsonObject = JSONObject(responseBody)
                    val templatesArray = jsonObject.optJSONArray("templates") ?: return@launch

                    templates.clear()
                    for (i in 0 until templatesArray.length()) {
                        val template = templatesArray.getJSONObject(i)
                        templates.add(template.getString("name"))
                    }

                    runOnUiThread {
                        if (templates.isEmpty()) {
                            statusText.text = "No templates available"
                            progressBar.visibility = android.view.View.GONE
                        } else {
                            progressBar.visibility = android.view.View.GONE
                            displayTemplates()
                        }
                    }
                } else {
                    runOnUiThread {
                        statusText.text = "Failed to fetch templates"
                        progressBar.visibility = android.view.View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("TemplateImport", "Error fetching templates: ${e.message}", e)
                runOnUiThread {
                    statusText.text = "Error: ${e.message}"
                    progressBar.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun displayTemplates() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, templates)
        templateListView.adapter = adapter

        templateListView.setOnItemClickListener { _, _, position, _ ->
            downloadTemplate(templates[position])
        }
    }

    private fun downloadTemplate(templateName: String) {
        progressBar.visibility = android.view.View.VISIBLE
        statusText.text = "Downloading template..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val downloadURL = "http://$serverIP:3000/download-template/$templateName"

                val request = Request.Builder()
                    .url(downloadURL)
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val csvContent = response.body?.string() ?: ""
                    
                    // Parse CSV headers from first row
                    val headers = parseCSVHeaders(csvContent)
                    
                    runOnUiThread {
                        progressBar.visibility = android.view.View.GONE
                        statusText.text = "Template '$templateName' imported successfully!"
                        Toast.makeText(this@TemplateImportActivity, "Template imported", Toast.LENGTH_SHORT).show()
                        Log.d("TemplateImport", "Template headers: $headers")
                        
                        // Save template to SharedPreferences
                        saveTemplate(templateName, headers)
                        
                        // Finish activity after a short delay to show success message
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            finish()
                        }, 1500)
                    }
                } else {
                    runOnUiThread {
                        progressBar.visibility = android.view.View.GONE
                        statusText.text = "Failed to download template"
                        Toast.makeText(this@TemplateImportActivity, "Download failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TemplateImport", "Error downloading template: ${e.message}", e)
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    statusText.text = "Error: ${e.message}"
                    Toast.makeText(this@TemplateImportActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseCSVHeaders(csvContent: String): List<String> {
        val lines = csvContent.trim().split("\n")
        if (lines.isEmpty()) return emptyList()
        
        // Parse first row as headers
        val headers = lines[0].split(",").map { it.trim() }
        return headers
    }

    private fun saveTemplate(templateName: String, headers: List<String>) {
        // Use TemplateManager to save the template
        TemplateManager.saveTemplate(this, templateName, headers)
        Log.d("TemplateImport", "Template saved: $templateName with ${headers.size} columns")
    }
}

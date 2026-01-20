package com.realwear.imagettext

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var resetButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize UI components
        apiKeyEditText = findViewById(R.id.apiKeyEditText)
        saveButton = findViewById(R.id.saveButton)
        resetButton = findViewById(R.id.resetButton)

        // Load current API key
        val currentApiKey = SettingsManager.getApiKey(this)
        apiKeyEditText.setText(currentApiKey)

        // Save button listener
        saveButton.setOnClickListener {
            val newApiKey = apiKeyEditText.text.toString().trim()
            if (newApiKey.isNotEmpty()) {
                SettingsManager.setApiKey(this, newApiKey)
                Toast.makeText(this, "API Key saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "API Key cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        // Reset button listener
        resetButton.setOnClickListener {
            apiKeyEditText.setText("e98b288d2bmsh06ace0e29bf7d83p1ae9fajsn9f0ef84e9691")
            Toast.makeText(this, "Reset to default API Key", Toast.LENGTH_SHORT).show()
        }
    }
}

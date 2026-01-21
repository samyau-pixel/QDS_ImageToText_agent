package com.realwear.imagettext

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyEditText: EditText
    private lateinit var serverIPEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var resetButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize UI components
        apiKeyEditText = findViewById(R.id.apiKeyEditText)
        serverIPEditText = findViewById(R.id.serverIPEditText)
        saveButton = findViewById(R.id.saveButton)
        resetButton = findViewById(R.id.resetButton)

        // Load current API key and Server IP
        val currentApiKey = SettingsManager.getApiKey(this)
        val currentServerIP = SettingsManager.getServerIP(this)
        apiKeyEditText.setText(currentApiKey)
        serverIPEditText.setText(currentServerIP)

        // Save button listener
        saveButton.setOnClickListener {
            val newApiKey = apiKeyEditText.text.toString().trim()
            val newServerIP = serverIPEditText.text.toString().trim()
            
            if (newApiKey.isNotEmpty() && newServerIP.isNotEmpty()) {
                SettingsManager.setApiKey(this, newApiKey)
                SettingsManager.setServerIP(this, newServerIP)
                Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            }
        }

        // Reset button listener
        resetButton.setOnClickListener {
            apiKeyEditText.setText("e98b288d2bmsh06ace0e29bf7d83p1ae9fajsn9f0ef84e9691")
            serverIPEditText.setText("192.168.1.65")
            Toast.makeText(this, "Reset to default values", Toast.LENGTH_SHORT).show()
        }
    }
}

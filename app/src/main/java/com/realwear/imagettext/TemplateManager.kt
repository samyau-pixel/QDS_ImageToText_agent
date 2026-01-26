package com.realwear.imagettext

import android.content.Context
import android.util.Log

object TemplateManager {
    
    fun loadTemplate(context: Context): Template? {
        val sharedPref = context.getSharedPreferences("TemplateManager", Context.MODE_PRIVATE)
        val templateName = sharedPref.getString("templateName", null) ?: return null
        val columnCount = sharedPref.getInt("templateColumns", 0)
        
        if (columnCount == 0) return null
        
        val columns = mutableListOf<String>()
        val fieldConfigs = mutableListOf<FieldConfig>()
        
        for (i in 0 until columnCount) {
            val column = sharedPref.getString("templateColumn_$i", null)
            if (column != null) {
                columns.add(column)
                // Parse the column definition to extract choice information
                val fieldConfig = FieldConfig.parse(column)
                fieldConfigs.add(fieldConfig)
            }
        }
        
        return Template(templateName, columns, fieldConfigs)
    }
    
    fun saveTemplate(context: Context, templateName: String, columns: List<String>) {
        val sharedPref = context.getSharedPreferences("TemplateManager", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("templateName", templateName)
            putInt("templateColumns", columns.size)
            for (i in columns.indices) {
                putString("templateColumn_$i", columns[i])
            }
            apply()
        }
        Log.d("TemplateManager", "Template saved: $templateName with ${columns.size} columns")
    }
    
    fun clearTemplate(context: Context) {
        val sharedPref = context.getSharedPreferences("TemplateManager", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()
    }
    
    fun hasTemplate(context: Context): Boolean {
        return loadTemplate(context) != null
    }
}

data class Template(
    val name: String,
    val columns: List<String>,
    val fieldConfigs: List<FieldConfig> = emptyList()
)

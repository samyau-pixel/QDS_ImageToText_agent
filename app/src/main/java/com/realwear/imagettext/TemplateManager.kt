package com.realwear.imagettext

import android.content.Context

object TemplateManager {
    
    fun loadTemplate(context: Context): Template? {
        val sharedPref = context.getSharedPreferences("TemplateManager", Context.MODE_PRIVATE)
        val templateName = sharedPref.getString("templateName", null) ?: return null
        val columnCount = sharedPref.getInt("templateColumns", 0)
        
        if (columnCount == 0) return null
        
        val columns = mutableListOf<String>()
        for (i in 0 until columnCount) {
            val column = sharedPref.getString("templateColumn_$i", null)
            if (column != null) {
                columns.add(column)
            }
        }
        
        return Template(templateName, columns)
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
    val columns: List<String>
)

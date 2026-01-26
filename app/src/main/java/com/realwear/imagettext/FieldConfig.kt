package com.realwear.imagettext

/**
 * Represents the configuration of a single field, including metadata about
 * whether it's a multiple-choice field and what options are available.
 */
data class FieldConfig(
    val fieldName: String,
    val isMultipleChoice: Boolean = false,
    val choices: List<String> = emptyList()
) {
    companion object {
        /**
         * Parses a field definition that may contain choice information.
         * Examples:
         * - "Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04)" -> choices = [3FR01A01, 3FR01A02, ...]
         * - "U(1-24)" -> choices = [1, 2, 3, ..., 24]
         * - "Normal Field" -> isMultipleChoice = false, choices = []
         */
        fun parse(fieldDefinition: String): FieldConfig {
            val trimmed = fieldDefinition.trim()
            
            // Check if the field contains parentheses
            val openParen = trimmed.indexOf('(')
            val closeParen = trimmed.lastIndexOf(')')
            
            if (openParen == -1 || closeParen == -1 || openParen >= closeParen) {
                // No valid parentheses, treat as normal field
                return FieldConfig(trimmed, false, emptyList())
            }
            
            val fieldName = trimmed.substring(0, openParen).trim()
            val choiceContent = trimmed.substring(openParen + 1, closeParen).trim()
            
            if (choiceContent.isEmpty()) {
                return FieldConfig(fieldName, false, emptyList())
            }
            
            // Check if it's a range (e.g., "1-24")
            if (choiceContent.contains("-") && !choiceContent.contains("/")) {
                val parts = choiceContent.split("-")
                if (parts.size == 2) {
                    val start = parts[0].trim().toIntOrNull()
                    val end = parts[1].trim().toIntOrNull()
                    
                    if (start != null && end != null && start <= end) {
                        val choices = (start..end).map { it.toString() }
                        return FieldConfig(fieldName, true, choices)
                    }
                }
            }
            
            // Check if it's a slash-separated list (e.g., "3FR01A01/3FR01A02/...")
            if (choiceContent.contains("/")) {
                val choices = choiceContent.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                if (choices.isNotEmpty()) {
                    return FieldConfig(fieldName, true, choices)
                }
            }
            
            // If we couldn't parse the content, treat the whole thing as the field name
            return FieldConfig(trimmed, false, emptyList())
        }
    }
}

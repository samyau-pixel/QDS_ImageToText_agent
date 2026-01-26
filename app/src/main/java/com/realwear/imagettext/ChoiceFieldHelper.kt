package com.realwear.imagettext

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText

/**
 * Utility class for handling multiple-choice field dialogs and input methods.
 */
object ChoiceFieldHelper {
    
    /**
     * Shows a dialog with choice options and sets the selected value in the EditText.
     * 
     * @param context The context for showing the dialog
     * @param fieldName The display name of the field
     * @param choices The list of available choices
     * @param editText The EditText to populate with the selected choice
     */
    fun showChoiceDialog(
        context: Context,
        fieldName: String,
        choices: List<String>,
        editText: EditText
    ) {
        if (choices.isEmpty()) return
        
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select $fieldName")
        
        val choicesArray = choices.toTypedArray()
        builder.setItems(choicesArray) { dialog, which ->
            editText.setText(choicesArray[which])
        }
        
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.show()
    }
}

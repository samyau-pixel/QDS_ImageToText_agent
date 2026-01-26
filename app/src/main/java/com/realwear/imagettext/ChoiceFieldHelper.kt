package com.realwear.imagettext

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView

/**
 * Utility class for handling multiple-choice field dialogs and input methods.
 */
object ChoiceFieldHelper {
    
    /**
     * Shows a fancy dialog with choice options and sets the selected value in the EditText.
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
        
        // Create custom adapter for fancy styling
        val adapter = FancyChoiceAdapter(context, choicesArray)
        
        builder.setAdapter(adapter) { dialog, which ->
            editText.setText(choicesArray[which])
            dialog.dismiss()
        }
        
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        
        // Style the dialog
        dialog.setOnShowListener { dialogInterface ->
            // Style the title
            val title = dialog.window?.decorView?.findViewById<TextView>(android.R.id.title)
            title?.apply {
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            // Style the background
            dialog.window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2A3E"))
                    cornerRadius = 20f
                }
            )
        }
        
        dialog.show()
    }
    
    /**
     * Custom adapter for fancy choice list styling
     */
    private class FancyChoiceAdapter(
        context: Context,
        private val items: Array<String>
    ) : ArrayAdapter<String>(context, 0, items) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val textView = if (convertView is TextView) {
                convertView
            } else {
                TextView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 60)
                    )
                }
            }
            
            textView.apply {
                text = items[position]
                textSize = 16f
                setTextColor(Color.WHITE)
                
                // Set padding
                setPadding(
                    dpToPx(context, 20),
                    dpToPx(context, 15),
                    dpToPx(context, 20),
                    dpToPx(context, 15)
                )
                
                // Set background with gradient effect
                val background = GradientDrawable().apply {
                    setColor(
                        if (position % 2 == 0)
                            Color.parseColor("#3D3D5C")
                        else
                            Color.parseColor("#4A4A6F")
                    )
                    cornerRadius = 12f
                }
                setBackground(background)
                
                // Center align text
                gravity = android.view.Gravity.CENTER
            }
            
            return textView
        }
    }
    
    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

# Multiple-Choice Field Feature Implementation

## Overview

This feature adds support for multiple-choice fields in CSV templates. When a template is loaded, the app now recognizes field definitions that include choice options in parentheses:

- **Slash-separated choices**: `Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04)` - Shows a choice dialog with those 4 options
- **Range format**: `U(1-24)` - Shows a choice dialog with options 1 through 24
- **Regular fields**: `Model` or `IP` - Works as before (camera capture mode)

## Files Created

### 1. **FieldConfig.kt** (NEW)
A data class that represents a field's configuration, including:
- `fieldName`: The display name of the field (without choice syntax)
- `isMultipleChoice`: Boolean flag indicating if it's a choice field
- `choices`: List of available choices

**Key method**: `FieldConfig.parse(fieldDefinition: String)`
- Parses field definitions like "Location(3FR01A01/3FR01A02/...)" or "U(1-24)"
- Returns a `FieldConfig` object with parsed choices
- Supports both slash-separated and range formats

### 2. **ChoiceFieldHelper.kt** (NEW)
Utility class for displaying choice dialogs:
- `showChoiceDialog()`: Displays an AlertDialog with choice options
- User selects an option, which populates the associated EditText

## Files Modified

### 1. **TemplateManager.kt**
**Changes:**
- Updated `Template` data class to include `fieldConfigs: List<FieldConfig>`
- Modified `loadTemplate()` to parse each column definition using `FieldConfig.parse()`
- Added `saveTemplate()` static function for saving templates (extracted from TemplateImportActivity)
- Template data now includes full field configuration with choice information

### 2. **TemplateImportActivity.kt**
**Changes:**
- Simplified `saveTemplate()` to use the new `TemplateManager.saveTemplate()` function
- Field definitions with choice syntax are now automatically parsed when templates are loaded

### 3. **MainActivity.kt**
**Major Changes:**

**New Variables:**
- `currentFieldConfigs: List<FieldConfig>` - Tracks field configurations including choice info

**Updated Methods:**

1. **loadTemplateFields()**
   - Now stores `currentFieldConfigs` from the loaded template
   - Displays field names without choice syntax (e.g., "Location" instead of "Location(...)")
   - Passes `FieldConfig` objects to `createDynamicField()`
   - Log entries now show choice field status

2. **Button Click Listeners (Rack, Label1, Label2)**
   - Check if field is multiple-choice via `currentFieldConfigs`
   - If multiple-choice: Show choice dialog
   - If normal field: Launch camera capture
   - Example:
   ```kotlin
   if (currentFieldConfigs.isNotEmpty() && currentFieldConfigs[0].isMultipleChoice) {
       ChoiceFieldHelper.showChoiceDialog(...)
   } else {
       launchCameraPhotoCapture()
   }
   ```

3. **createDynamicField()**
   - Now accepts `FieldConfig` instead of just `String`
   - Implements same logic as the button listeners
   - Multiple-choice fields show dialogs
   - Normal fields capture from camera

## How It Works

### Template Loading Flow
1. CSV template is downloaded from server
2. First row is parsed as column headers
3. Each header is processed by `FieldConfig.parse()`
   - `Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04)` → FieldConfig with 4 choices
   - `U(1-24)` → FieldConfig with 24 choices (1-24)
   - `Model` → FieldConfig with no choices (normal field)
4. Template is saved with all field configurations
5. UI is built with proper field types

### Field Interaction
- **Multiple-choice field**: User clicks button → Choice dialog appears → User selects option → EditText populated
- **Normal field**: User clicks button → Camera launches → OCR extracts text → EditText populated

## Example Templates

### Simple Mixed Template
```
Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04),U(1-24),Model,IP
```

When loaded:
- **Button 1 "Location"**: Shows dialog with 4 location choices
- **Button 2 "U"**: Shows dialog with numbers 1-24
- **Button 3 "Model"**: Launches camera for OCR
- **Button 4 "IP"**: Launches camera for OCR

### Template with Ranges
```
Priority(Low/Medium/High),Quantity(1-100),Description
```

When loaded:
- **Priority**: Dialog with Low, Medium, High
- **Quantity**: Dialog with 1-100
- **Description**: Camera capture

## Supported Choice Formats

### Slash-Separated (List)
`fieldname(option1/option2/option3/option4)`

Examples:
- `Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04)`
- `Status(Active/Inactive/Pending)`
- `Priority(Low/Medium/High)`

### Range Format (Numeric)
`fieldname(start-end)`

Examples:
- `U(1-24)`
- `Hour(0-23)`
- `Priority(1-5)`

### Normal Field
`fieldname`

Examples:
- `Model`
- `IP`
- `SerialNumber`

## CSV File Handling
When saving entries, all field types (choice and normal) are treated the same in the CSV output:
- Choice field values are saved as the selected text
- Normal field values are saved as OCR-extracted or manually entered text
- No distinction is made in the CSV file between field types

## Testing the Feature

1. Create a test CSV template with choice fields:
```
Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04),U(1-24),Model,IP
```

2. Upload it to the server's templates folder

3. In the app:
   - Click "Import" button
   - Select the template
   - See buttons labeled with field names (without choice syntax)
   - Click "Location" button → Choice dialog with 4 locations
   - Click "U" button → Choice dialog with 1-24
   - Click "Model" or "IP" → Camera launches (normal behavior)

## Error Handling

The parser includes error handling:
- Invalid parentheses are ignored
- Invalid range formats fall back to normal field
- Empty choice content is treated as normal field
- Non-numeric range endpoints are handled gracefully

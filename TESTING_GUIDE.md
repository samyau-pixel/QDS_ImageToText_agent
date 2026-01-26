# Testing Guide - Multiple-Choice Fields Feature

## Setup for Testing

### 1. Prepare Test Template

Create a CSV file named `test_multiChoice.csv` with the following content:

```csv
Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04),U(1-24),Model,IP
```

This template contains:
- **Location**: Multiple-choice field with 4 options
- **U**: Multiple-choice field with range 1-24
- **Model**: Normal field (camera capture)
- **IP**: Normal field (camera capture)

### 2. Upload to Server

Place the CSV file in:
```
server/templates/test_multiChoice.csv
```

Ensure the Node.js server is running and the app can connect to it.

### 3. Build and Deploy

Build the APK:
```bash
./gradlew assembleDebug
```

Or use the provided build task.

## Test Cases

### Test 1: Template Import
**Steps:**
1. Launch the app
2. Click "Import" button
3. Wait for template list to load
4. Select "test_multiChoice" template
5. Verify message "Template 'test_multiChoice' imported successfully!"

**Expected Result:** ✓ Template imports and dialog closes

---

### Test 2: Display Field Names
**Steps:**
1. After importing template, observe the button labels
2. Check button 1 label: Should show "Location"
3. Check button 2 label: Should show "U"
4. Check button 3 label: Should show "Model"
5. Check button 4 label: Should show "IP"

**Expected Result:** ✓ All buttons show correct field names (without choice syntax)

---

### Test 3: Multiple-Choice Dialog (Location)
**Steps:**
1. Click the "Location" button
2. Verify a dialog appears with title "Select Location"
3. Verify dialog contains 4 options:
   - 3FR01A01
   - 3FR01A02
   - 3FR01A03
   - 3FR01A04
4. Select "3FR01A02"
5. Verify EditText now shows "3FR01A02"

**Expected Result:** ✓ Choice dialog appears and value is populated correctly

---

### Test 4: Multiple-Choice Dialog (Range 1-24)
**Steps:**
1. Click the "U" button
2. Verify a dialog appears with title "Select U"
3. Scroll through options, verify they show: 1, 2, 3, ... 22, 23, 24
4. Select "15"
5. Verify EditText now shows "15"

**Expected Result:** ✓ Range generates 24 options correctly

---

### Test 5: Normal Field (Camera Capture)
**Steps:**
1. Click the "Model" button
2. Verify camera launches (shows "Processing...")
3. Take a photo or cancel
4. If photo taken, verify OCR result appears in EditText

**Expected Result:** ✓ Camera launches as before (unchanged behavior)

---

### Test 6: Save Entry with Mixed Fields
**Steps:**
1. Set values for all fields:
   - Location: Select "3FR01A01"
   - U: Select "12"
   - Model: Capture from camera
   - IP: Capture from camera
2. Click "Save" button
3. Verify CSV file is updated

**Expected Result:** ✓ Entry saves with all field values in CSV

---

### Test 7: CSV Output Validation
**Steps:**
1. After saving entry, navigate to upload folder
2. Open the CSV file
3. Verify the entry row contains all values

**Expected Output Example:**
```csv
Location,U,Model,IP,Timestamp
3FR01A01,12,ABC-MODEL,192.168.1.1,01/26/2026 14:30
```

**Expected Result:** ✓ CSV contains correct values in correct order

---

### Test 8: Multiple Entries
**Steps:**
1. Save 3 different entries with different choice selections
2. Verify CSV contains all 3 entries with correct values

**Example CSV:**
```csv
Location,U,Model,IP,Timestamp
3FR01A01,12,Model1,IP1,01/26/2026 14:30
3FR01A02,5,Model2,IP2,01/26/2026 14:35
3FR01A04,24,Model3,IP3,01/26/2026 14:40
```

**Expected Result:** ✓ Multiple entries save correctly

---

### Test 9: Button Re-use
**Steps:**
1. Set values for all fields
2. Click Location button again
3. Select different option (3FR01A03)
4. Verify EditText updates to new value
5. Repeat for U field with different number

**Expected Result:** ✓ Can change values multiple times

---

### Test 10: Template Reload
**Steps:**
1. Load test_multiChoice template
2. Set some field values
3. Leave app or reload (via Import button)
4. Verify new template loads correctly
5. Check that field names are still correct

**Expected Result:** ✓ Template reloads without issues

---

## Edge Cases to Test

### Edge Case 1: Whitespace in Options
Create template: `Status( Active / Inactive / Pending )`

**Test:** 
- Click button, verify options appear as "Active", "Inactive", "Pending" (whitespace trimmed)

---

### Edge Case 2: Large Range
Create template: `Priority(1-100)`

**Test:**
- Click button, scroll through dialog
- Verify all 100 options are generated
- Select option 50, verify it shows "50"

---

### Edge Case 3: Long Option Names
Create template: `Location(Office-Building-A-Floor-3/Office-Building-B-Floor-2)`

**Test:**
- Click button
- Verify long option names display correctly in dialog
- Can select them successfully

---

## Debugging

If any test fails, check:

1. **Check logcat output:**
   ```
   adb logcat | grep ImageTText
   ```

2. **Look for parsing errors:**
   - Search for: `"Parsing error"` or `"Failed to parse"`

3. **Check field config output:**
   - Look for: `"FieldConfig"` in logs to see parsed field information

4. **Verify template loading:**
   - Search for: `"Loading template"` to see field configurations

5. **Camera capture issues:**
   - Verify camera permissions are granted
   - Check if device has camera available

## Performance Notes

- Choice dialogs should appear instantly (no network calls)
- Large ranges (1-100) may take a few milliseconds to generate
- Normal fields should behave identically to before feature was added

## Cleanup After Testing

To test again with a fresh state:

1. Uninstall app: `adb uninstall com.realwear.imagettext`
2. Clear template cache: Delete SharedPreferences data
3. Rebuild and reinstall: `./gradlew installDebug`
4. Re-import template

# Quick Reference - Multiple-Choice Fields

## What Was Added

Your app now recognizes special syntax in CSV template headers to create multiple-choice fields.

## Template Syntax Examples

### Before (Just Fields)
```csv
Location,Status,Model,IP
```
Result: All 4 fields use camera capture

### After (With Choices)
```csv
Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04),Status(Active/Inactive/Pending),Model,IP
```
Result:
- **Location** → Click → Dialog shows 4 location options
- **Status** → Click → Dialog shows 3 status options  
- **Model** → Click → Camera launches
- **IP** → Click → Camera launches

---

## Field Definition Patterns

### Pattern 1: Slash-Separated Choices
```
Location(Warehouse-A/Warehouse-B/Warehouse-C)
```
- Creates dialog with 3 options
- Useful for predefined lists
- Options can contain any characters except "/"

### Pattern 2: Range (Numbers Only)
```
Priority(1-5)
```
- Creates dialog with numbers 1, 2, 3, 4, 5
- Format: `FieldName(start-end)`
- Both start and end must be numbers
- Automatically generates all numbers in range

### Pattern 3: Normal Field
```
SerialNumber
```
- No parentheses = camera capture
- Existing behavior unchanged

---

## Complete Template Examples

### Example 1: Warehouse Tracking
```csv
Location(Warehouse-A/Warehouse-B/Warehouse-C),Status(In-Stock/Low-Stock/Out-of-Stock),Quantity(1-999),Notes
```

Buttons (in app):
1. "Location" → Choice dialog (3 warehouses)
2. "Status" → Choice dialog (3 statuses)
3. "Quantity" → Choice dialog (1-999)
4. "Notes" → Camera capture

### Example 2: Equipment Inspection
```csv
Equipment(Device-A/Device-B/Device-C),Condition(Good/Fair/Poor),Confidence(1-10),Inspector,Comments
```

Buttons (in app):
1. "Equipment" → Choice (3 devices)
2. "Condition" → Choice (3 conditions)
3. "Confidence" → Choice (1-10)
4. "Inspector" → Camera
5. "Comments" → Camera

### Example 3: Daily Checklist
```csv
Date(1-31),Month(1-12),Priority(High/Normal/Low),Item,Notes
```

Buttons (in app):
1. "Date" → Choice (1-31)
2. "Month" → Choice (1-12)
3. "Priority" → Choice (3 options)
4. "Item" → Camera
5. "Notes" → Camera

---

## User Interaction

### For Multiple-Choice Fields
```
User clicks button → Dialog appears → User selects option → Value saved
```

Dialog shows all available options in a scrollable list. User taps to select.

### For Normal Fields  
```
User clicks button → Camera launches → User captures image → OCR extracts text → Value saved
```

Same behavior as before the feature was added.

---

## Key Points

✓ Field name (before parentheses) appears on the button
✓ Choice syntax is NOT shown on the button
✓ Can mix choice and camera-capture fields freely
✓ All values save to CSV identically
✓ Fully backward compatible with old templates
✓ Automatically parses when template is loaded
✓ Works with unlimited number of fields (buttons 1-3 or dynamic fields 4+)

---

## File Format Details

### In CSV File
```csv
Location(Loc-A/Loc-B),Priority(1-5),Model
```

### In App UI
```
Button 1: "Location"     ← Not "Location(Loc-A/Loc-B)"
Button 2: "Priority"     ← Not "Priority(1-5)"
Button 3: "Model"        ← Normal field, no change
```

---

## Why This Feature?

**Without this feature:**
- All fields require camera + OCR
- Must manually type if value is from limited set
- Slower data entry for predefined options

**With this feature:**
- Predefined options are instant (no camera)
- Fewer user errors (select vs type)
- Faster data entry
- Cleaner UI (button shows clean name)

---

## Common Use Cases

1. **Warehouse Management**
   - Locations: `Location(Shelf-A/Shelf-B/Shelf-C)`
   - Stock Status: `Status(Full/Partial/Empty)`

2. **Quality Inspection**
   - Grade: `Grade(A/B/C/D)`
   - Defect Level: `DefectLevel(0-10)`

3. **Equipment Tracking**
   - Equipment ID: `Equipment(EQ-001/EQ-002/EQ-003)`
   - Status: `Status(Active/Maintenance/Retired)`

4. **Form Collection**
   - Date: `Date(1-31)`
   - Time: `Hour(0-23)`
   - Priority: `Priority(Low/Medium/High)`

---

## How to Update an Existing Template

### Original Template
```csv
Status,Priority,Model,Notes
```

### With Choices Added
```csv
Status(Open/Closed/Pending),Priority(1-5),Model,Notes
```

Just add parentheses to the fields that should have choices. Camera-capture fields stay the same.

---

## Data Flow Diagram

```
CSV Template File
    ↓
    (Downloaded & Parsed)
    ↓
FieldConfig Objects
    ├─ Location: isMultipleChoice=true, choices=[Loc-A, Loc-B, Loc-C]
    ├─ Status: isMultipleChoice=true, choices=[Open, Closed]
    └─ Model: isMultipleChoice=false, choices=[]
    ↓
    (UI Created)
    ↓
Buttons on Screen
    ├─ "Location" button
    ├─ "Status" button
    └─ "Model" button
    ↓
User Clicks Button
    ↓
    ┌─────────────────────┐
    │ Is it a choice?     │
    └─────────────────────┘
         /          \
       YES           NO
       /              \
   Dialog         Camera
   /                  \
Select          Capture & OCR
 |                    |
Save Value        Save Text
 |                    |
 └────────┬───────────┘
          ↓
      CSV File
```

---

## Testing Your Template

1. Create test CSV with choices
2. Upload to server
3. Open app and import template
4. Verify buttons show correct field names
5. Test each button:
   - Choice field → Dialog appears
   - Normal field → Camera launches
6. Select values and save entry
7. Verify CSV output is correct

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Buttons show choice syntax | Template not re-imported or parsing failed |
| All fields are cameras | Template has no choice syntax or syntax is wrong |
| Choice dialog doesn't appear | Field config not properly loaded |
| Range creates wrong numbers | Check hyphen format: `(1-24)` not `(1..24)` |
| Slash options missing | Check no extra spaces: `(A/B/C)` not `(A / B / C)` |

---

## Files Modified

- ✓ `FieldConfig.kt` (NEW) - Parser for choice syntax
- ✓ `ChoiceFieldHelper.kt` (NEW) - Dialog display
- ✓ `TemplateManager.kt` - Store field configs
- ✓ `TemplateImportActivity.kt` - Use new save function
- ✓ `MainActivity.kt` - Handle choice fields

## Compilation Status

✓ All files compile without errors
✓ No warnings
✓ Ready to build and test

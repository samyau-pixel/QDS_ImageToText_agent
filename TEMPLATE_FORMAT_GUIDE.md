# Template Format Guide - Multiple-Choice Fields

## Quick Reference

### Field Definition Patterns

| Pattern | Example | Behavior |
|---------|---------|----------|
| Normal field | `Model` | Click → Camera launches → OCR extracts text |
| Slash-separated | `Location(3FR01A01/3FR01A02/3FR01A03)` | Click → Dialog shows 3 options to choose from |
| Range format | `Priority(1-5)` | Click → Dialog shows 1, 2, 3, 4, 5 to choose from |

## Example Templates

### Example 1: Warehouse Inventory
```csv
Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04),Status(In Stock/Out of Stock/Damaged),Model,SerialNumber
```

**Result:**
- Field 1 "Location": Multiple choice (4 options)
- Field 2 "Status": Multiple choice (3 options)
- Field 3 "Model": Camera capture
- Field 4 "SerialNumber": Camera capture

### Example 2: Form with Mixed Fields
```csv
Date(1-31),Month(1-12),Year(2020-2030),Name,Email
```

**Result:**
- Field 1 "Date": Multiple choice (1-31)
- Field 2 "Month": Multiple choice (1-12)
- Field 3 "Year": Multiple choice (2020-2030)
- Field 4 "Name": Camera capture
- Field 5 "Email": Camera capture

### Example 3: Priority Tracking
```csv
Priority(Critical/High/Medium/Low),Assigned(John/Jane/Bob),Status(Open/In Progress/Closed),Notes
```

**Result:**
- Field 1 "Priority": Multiple choice (4 options)
- Field 2 "Assigned": Multiple choice (3 options)
- Field 3 "Status": Multiple choice (3 options)
- Field 4 "Notes": Camera capture

### Example 4: Equipment Inspection
```csv
Equipment(Device A/Device B/Device C),Condition(Good/Fair/Poor),Confidence(1-10),Photos
```

**Result:**
- Field 1 "Equipment": Multiple choice (3 devices)
- Field 2 "Condition": Multiple choice (3 states)
- Field 3 "Confidence": Multiple choice (1-10)
- Field 4 "Photos": Camera capture

## Creation Instructions

### Step 1: Open CSV Template File
Create or edit your template CSV file with headers in the first row.

### Step 2: Add Choice Information
For each field that should be a multiple-choice field, add parentheses with options:

**For lists (use forward slashes):**
```
Location(3FR01A01/3FR01A02/3FR01A03/3FR01A04)
Status(Active/Inactive/Pending)
```

**For ranges (use hyphen):**
```
Hour(0-23)
Day(1-31)
Priority(1-10)
```

### Step 3: Keep Normal Fields Simple
Fields without parentheses will use camera capture:
```
Model
SerialNumber
Description
```

### Step 4: Upload Template
Save the CSV file and upload it to the server's templates folder. The app will automatically parse and use the choice information.

## Tips

1. **Field Names**: The text before the parentheses becomes the button label
   - `Location(opt1/opt2)` → Button labeled "Location"
   - The options are not shown on the button

2. **Special Characters**: For now, avoid special characters in field names and options
   - Good: `Location(3FR01A01/3FR01A02)`
   - Avoid: `Location@(3FR/01@A/01)`

3. **Whitespace**: Spaces around slashes and hyphens are automatically trimmed
   - `Location(3FR01A01 / 3FR01A02 / 3FR01A03)` works fine
   - `Priority(1 - 5)` works fine

4. **Option Order**: Options appear in the dialog in the order you specify
   - `Status(First/Second/Third)` → Dialog shows "First", "Second", "Third"

5. **Large Option Lists**: You can have as many options as needed
   - `Priority(1-100)` creates 100 options
   - `Locations(LHQ/NYC/LAX/DEN/SEA/...)` creates as many locations as you add

## CSV Output Format

When entries are saved to CSV, all field values (choice and non-choice) are saved the same way:
```csv
Location,Status,Model,SerialNumber,Timestamp
3FR01A01,In Stock,ABC-123,SN12345,01/26/2026 10:30
```

The CSV doesn't distinguish between choice and camera-captured fields - they're all just text values.

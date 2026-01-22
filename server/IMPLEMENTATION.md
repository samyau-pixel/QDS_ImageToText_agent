# Server-Side Excel Processing Implementation

## Overview

When a user clicks "download batch", the server now:
1. Creates a temporary copy of the batch folder
2. Runs the Python script on the temporary copy
3. Returns the generated Excel file with embedded images
4. Cleans up the temporary folder

## Files Created

### 1. Python Script: `combine_excel_image.py`
Converts CSV to Excel and embeds images:
- Reads CSV file from batch folder
- Finds all image files (.jpg, .jpeg, .png, .gif, .bmp)
- Creates Excel workbook
- Embeds images into cells at referenced positions
- Saves as `output.xlsx`

**Usage:**
```bash
python combine_excel_image.py <folder_path> <output_excel_path>
```

### 2. Python Dependencies: `requirements.txt`
```
pandas>=1.3.0
openpyxl>=3.6.0
```

**Install:**
```bash
pip install -r requirements.txt
```

## Files Modified

### `server.js`

**Added:**
1. `const { execSync } = require('child_process');` - For executing Python script

2. Helper functions:
   - `copyDirectory(src, dest)` - Copies batch folder to temporary location
   - `deleteDirectory(dir)` - Removes temporary folder after processing

3. Updated `/download-batch/:batch` endpoint:
   - Creates unique temporary work directory
   - Copies batch to temporary location
   - Executes Python script on temporary copy
   - Sends generated Excel file to user
   - Cleans up temporary folder after file is sent
   - Falls back to ZIP if Python processing fails

## Workflow

```
User clicks "Download Batch"
    ↓
GET /download-batch/batch_XXXX
    ↓
Server validates batch exists
    ↓
Create temporary folder: temp_<timestamp>_<random>
    ↓
Copy batch folder → temporary folder
    ↓
Execute Python script:
  python combine_excel_image.py <temp_folder> <temp_folder>/output.xlsx
    ↓
Python processes CSV and embeds images
    ↓
Server sends output.xlsx to user
    ↓
After file sent, delete temporary folder
    ↓
Done - original batch untouched
```

## Key Features

✅ **Non-destructive** - Original batch folder remains unchanged
✅ **Temporary processing** - Uses separate temp directory
✅ **Automatic cleanup** - Deletes temp folder after use
✅ **Error handling** - Falls back to ZIP if Python fails
✅ **Detailed logging** - Console output for debugging
✅ **Cross-platform** - Detects Windows vs Unix Python paths
✅ **Secure** - Path validation prevents directory traversal

## Python Script Details

The `combine_excel_image.py` script:

1. **Reads CSV** - Finds and opens CSV file in batch folder
2. **Creates Excel** - Converts CSV to Excel format using pandas
3. **Finds Images** - Scans folder for image files
4. **Embeds Images** - Uses openpyxl to embed images into cells
5. **Sizes Cells** - Sets row height (100) and column width (20)
6. **Cleans Values** - Removes image filenames from cells after embedding
7. **Saves File** - Writes output.xlsx to specified path

## Server Changes Summary

| Part | Change | Purpose |
|------|--------|---------|
| Import | Added `execSync` | Execute Python subprocess |
| Helper | Added `copyDirectory()` | Copy batch to temp folder |
| Helper | Added `deleteDirectory()` | Clean up temp folder |
| Endpoint | Modified `/download-batch` | Temp copy + process + cleanup |

## Setup Instructions

1. **Install Python dependencies:**
   ```bash
   cd c:\Dev\ImageTText\server
   pip install -r requirements.txt
   ```

2. **Start server:**
   ```bash
   node server.js
   ```

3. **Upload batch and download:**
   - Use Android app to upload CSV + photos
   - Click "download batch" in web interface
   - Receive formatted Excel file with embedded images

## Testing

When a batch is downloaded, check server logs:
```
=== Download Batch Started ===
Batch: batch_2026-01-22T04-10-26-284Z
Source path: C:\Dev\ImageTText\server\uploads\batch_2026-01-22T04-10-26-284Z
Creating temporary work directory: temp_1674408600000_abc123def
Copying batch to temporary location...
Output Excel path: temp_1674408600000_abc123def/output.xlsx
Executing: "C:\Dev\ImageTText\ImageTText\Scripts\python.exe" "..." 
Python script completed successfully
Excel file created: 1234567 bytes
File sent to client, cleaning up temporary directory...
Cleanup complete
```

## Error Handling

If Python processing fails:
- Server logs the error
- Falls back to ZIP download
- Original batch remains untouched
- User still receives a file (ZIP instead of Excel)

## Directory Structure

```
server/
├── server.js                    ← Modified
├── combine_excel_image.py       ← NEW
├── requirements.txt             ← NEW
├── uploads/
│   └── batch_XXXX/             ← Original batch (untouched)
│       ├── data.csv
│       ├── photo1.jpg
│       └── ...
└── temp_uploads/
    └── temp_<timestamp>_<rand>/  ← Temporary copy (auto-deleted)
        ├── data.csv
        ├── photo1.jpg
        ├── ...
        └── output.xlsx           ← Generated here, sent to user
```

## API Response

**Before:**
- Content-Type: `application/zip`
- File: `batch_XXXX.zip`

**After:**
- Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- File: `batch_XXXX_formatted.xlsx`
- Contains: CSV data with embedded images

---

**Status:** ✅ Ready to use

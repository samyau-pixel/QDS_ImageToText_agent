# ImageTText CSV Upload Server

Simple Node.js server to receive and view CSV files from the RealWear ImageTText Android app.

## Features

- ✅ Receive CSV files from Android device
- ✅ Web interface to view uploaded CSV files
- ✅ Auto-refresh file list (every 5 seconds)
- ✅ Download CSV files
- ✅ Beautiful, responsive UI
- ✅ Timestamp and device tracking

## Installation

### Prerequisites
- Node.js (v14 or higher)
- npm (comes with Node.js)

### Setup

1. **Install dependencies:**
   ```bash
   cd server
   npm install
   ```

2. **Start the server:**
   ```bash
   npm start
   ```
   
   The server will start on `http://0.0.0.0:3000`

3. **Access the web interface:**
   - Open browser: `http://localhost:3000`
   - Or from another machine: `http://<your-computer-ip>:3000`

## Configuration

### Update Android App Server URL

In `MainActivity.kt`, change the SERVER_URL to match your computer's IP:

```kotlin
private const val SERVER_URL = "http://192.168.1.100:3000/upload"  // Change IP address
```

**To find your computer's IP:**
- Windows: Run `ipconfig` in Command Prompt, look for "IPv4 Address"
- Mac/Linux: Run `ifconfig` or `hostname -I`

Example: If your IP is `192.168.1.50`, use:
```kotlin
private const val SERVER_URL = "http://192.168.1.50:3000/upload"
```

## How It Works

### Android App Flow
1. User captures image with Rack/Label_1/Label_2 buttons
2. OCR extracts text
3. Click "Send to Storage" button
4. App creates CSV file with format:
   ```
   Field,Value
   Timestamp,2026-01-21 10:30:45
   Rack,Extracted text from Rack...
   Label_1,Extracted text from Label_1...
   Label_2,Extracted text from Label_2...
   ```
5. CSV is uploaded to web server via HTTP POST

### Server Flow
1. Receives multipart form data with CSV file
2. Saves file with timestamp: `YYYY-MM-DDTHH-MM-SS-mmm_filename.csv`
3. Stores in `/server/uploads/` directory
4. Web UI displays all files and allows viewing/downloading

## API Endpoints

### POST /upload
Upload a CSV file
- **Form Data:**
  - `csv_file`: The CSV file
  - `device_name`: Device identifier
  - `timestamp`: Upload timestamp

### GET /api/files
Get list of all uploaded CSV files
- **Response:** JSON array of files with size and modification time

### GET /api/files/:filename
Get content of specific CSV file
- **Response:** JSON with file content

### GET /download/:filename
Download a CSV file

## Troubleshooting

### Server won't start
- Check if port 3000 is already in use
- Try: `lsof -i :3000` (Mac/Linux) or `netstat -ano | findstr :3000` (Windows)

### Can't connect from Android device
- Ensure Android device and computer are on same WiFi network
- Update SERVER_URL to your computer's actual IP (not localhost)
- Check firewall settings - allow port 3000
- Test: Open `http://<your-ip>:3000` on device browser

### Files not showing up
- Check `/server/uploads/` directory exists
- Check file permissions
- Look at server console for error messages

### CSV content shows as single row
- CSV format should use commas as delimiters
- Each field value should be on separate line in the format: `Field,Value`

## Project Structure

```
server/
├── package.json          # Dependencies
├── server.js            # Express server
├── public/
│   └── index.html       # Web UI
└── uploads/             # CSV storage (auto-created)
```

## Security Notes

- Server runs on `0.0.0.0` (all interfaces) - consider restricting to local network only
- Add authentication if exposing to internet
- Validate and sanitize file uploads
- Use HTTPS in production

## License

MIT

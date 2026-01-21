const express = require('express');
const multer = require('multer');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = 3000;

// Enable CORS
app.use(cors());
app.use(express.json());
app.use(express.static('public'));

// Serve uploads directory so photos can be accessed via /uploads/filename.jpg
app.use('/uploads', express.static('uploads'));

// Create uploads directory if it doesn't exist
const uploadsDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadsDir)) {
    fs.mkdirSync(uploadsDir, { recursive: true });
}

// Configure multer for file uploads (allows multiple file types)
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, uploadsDir);
    },
    filename: (req, file, cb) => {
        // For photos, keep original filename without prefix
        // For CSV files, add timestamp prefix for organization
        if (file.fieldname === 'photos') {
            cb(null, file.originalname);
        } else {
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            cb(null, `${timestamp}_${file.originalname}`);
        }
    }
});

const upload = multer({ 
    storage: storage,
    limits: {
        fileSize: 50 * 1024 * 1024  // 50MB max file size
    }
});

// Upload endpoint - handles CSV and multiple photos
app.post('/upload', upload.any(), (req, res) => {
    try {
        console.log('=== Upload Request Received ===');
        console.log('Files count:', req.files ? req.files.length : 0);
        
        let csvFile = null;
        let photoFiles = [];
        
        if (req.files && req.files.length > 0) {
            req.files.forEach(file => {
                console.log(`  File: ${file.fieldname} -> ${file.filename} (${file.size} bytes)`);
                if (file.fieldname === 'csv_file') {
                    csvFile = file;
                } else if (file.fieldname === 'photos') {
                    photoFiles.push(file);
                }
            });
        }
        
        console.log('Device name:', req.body.device_name);
        console.log('Timestamp:', req.body.timestamp);
        console.log(`Summary: CSV=${csvFile ? 'yes' : 'no'}, Photos=${photoFiles.length}`);
        
        res.json({
            success: true,
            message: 'Files uploaded successfully',
            csv_file: csvFile ? csvFile.filename : null,
            photo_count: photoFiles.length,
            photos: photoFiles.map(f => f.filename),
            device_name: req.body.device_name,
            timestamp: req.body.timestamp
        });
    } catch (err) {
        console.error('Upload error:', err);
        res.status(500).json({
            success: false,
            error: err.message,
            message: 'Error uploading files'
        });
    }
});

// Get list of all CSV files
app.get('/api/files', (req, res) => {
    try {
        const files = fs.readdirSync(uploadsDir).map(filename => {
            const filepath = path.join(uploadsDir, filename);
            const stats = fs.statSync(filepath);
            return {
                filename: filename,
                size: stats.size,
                modified: stats.mtime
            };
        });
        
        res.json({
            success: true,
            files: files.sort((a, b) => b.modified - a.modified)
        });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Get specific CSV file content
app.get('/api/files/:filename', (req, res) => {
    try {
        const filepath = path.join(uploadsDir, req.params.filename);
        
        // Security check - prevent directory traversal
        if (!filepath.startsWith(uploadsDir)) {
            return res.status(403).json({ success: false, error: 'Access denied' });
        }
        
        const content = fs.readFileSync(filepath, 'utf8');
        res.json({
            success: true,
            filename: req.params.filename,
            content: content
        });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Download CSV file
app.get('/download/:filename', (req, res) => {
    try {
        const filepath = path.join(uploadsDir, req.params.filename);
        
        // Security check
        if (!filepath.startsWith(uploadsDir)) {
            return res.status(403).json({ success: false, error: 'Access denied' });
        }
        
        res.download(filepath);
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`CSV Server running on http://0.0.0.0:${PORT}`);
    console.log(`Access web interface at http://localhost:${PORT}`);
    console.log(`CSV files stored in: ${uploadsDir}`);
});

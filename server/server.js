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

// Configure multer for temporary uploads (we'll move to batch folders later)
const tempDir = path.join(__dirname, 'temp_uploads');
if (!fs.existsSync(tempDir)) {
    fs.mkdirSync(tempDir, { recursive: true });
}

const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, tempDir);
    },
    filename: (req, file, cb) => {
        // Keep original filenames
        cb(null, file.originalname);
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
        
        // Create a timestamped folder for this batch
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const batchFolder = path.join(uploadsDir, `batch_${timestamp}`);
        
        if (!fs.existsSync(batchFolder)) {
            fs.mkdirSync(batchFolder, { recursive: true });
            console.log(`Created batch folder: ${batchFolder}`);
        }
        
        let csvFile = null;
        let photoFiles = [];
        
        if (req.files && req.files.length > 0) {
            req.files.forEach(file => {
                console.log(`  File: ${file.fieldname} -> ${file.filename} (${file.size} bytes)`);
                
                // Move file from temp to batch folder
                const oldPath = file.path;
                const newPath = path.join(batchFolder, file.filename);
                
                try {
                    fs.renameSync(oldPath, newPath);
                    console.log(`  Moved to: ${newPath}`);
                    
                    if (file.fieldname === 'csv_file') {
                        csvFile = { ...file, path: newPath };
                    } else if (file.fieldname === 'photos') {
                        photoFiles.push({ ...file, path: newPath });
                    }
                } catch (err) {
                    console.error(`  Error moving file: ${err.message}`);
                }
            });
        }
        
        console.log('Device name:', req.body.device_name);
        console.log('Timestamp:', req.body.timestamp);
        console.log(`Summary: Batch=${timestamp}, CSV=${csvFile ? 'yes' : 'no'}, Photos=${photoFiles.length}`);
        
        res.json({
            success: true,
            message: 'Files uploaded successfully',
            batch_folder: `batch_${timestamp}`,
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

// Get list of all CSV files (from batch folders)
app.get('/api/files', (req, res) => {
    try {
        const files = [];
        
        // Read all batch folders
        const items = fs.readdirSync(uploadsDir);
        
        for (const item of items) {
            const itemPath = path.join(uploadsDir, item);
            const stats = fs.statSync(itemPath);
            
            // If it's a batch folder
            if (stats.isDirectory() && item.startsWith('batch_')) {
                // Look for CSV files inside
                const csvFiles = fs.readdirSync(itemPath).filter(f => f.endsWith('.csv'));
                
                for (const csvFile of csvFiles) {
                    const csvPath = path.join(itemPath, csvFile);
                    const csvStats = fs.statSync(csvPath);
                    files.push({
                        filename: csvFile,
                        batch: item,
                        size: csvStats.size,
                        modified: csvStats.mtime
                    });
                }
            }
        }
        
        res.json({
            success: true,
            files: files.sort((a, b) => b.modified - a.modified)
        });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Get specific CSV file content
app.get('/api/files/:batch/:filename', (req, res) => {
    try {
        const filepath = path.join(uploadsDir, req.params.batch, req.params.filename);
        
        // Security check - prevent directory traversal
        if (!filepath.startsWith(uploadsDir)) {
            return res.status(403).json({ success: false, error: 'Access denied' });
        }
        
        const content = fs.readFileSync(filepath, 'utf8');
        res.json({
            success: true,
            filename: req.params.filename,
            batch: req.params.batch,
            content: content
        });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Download CSV file
app.get('/download/:batch/:filename', (req, res) => {
    try {
        const filepath = path.join(uploadsDir, req.params.batch, req.params.filename);
        
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

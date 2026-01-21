const express = require('express');
const multer = require('multer');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const archiver = require('archiver');

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

// Create templates directory if it doesn't exist
const templatesDir = path.join(__dirname, 'templates');
if (!fs.existsSync(templatesDir)) {
    fs.mkdirSync(templatesDir, { recursive: true });
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

// Download entire batch folder as ZIP
app.get('/download-batch/:batch', (req, res) => {
    try {
        const batchPath = path.join(uploadsDir, req.params.batch);
        
        // Security check
        if (!batchPath.startsWith(uploadsDir) || !fs.existsSync(batchPath)) {
            return res.status(403).json({ success: false, error: 'Access denied or batch not found' });
        }
        
        // Create a ZIP file
        res.setHeader('Content-Type', 'application/zip');
        res.setHeader('Content-Disposition', `attachment; filename="${req.params.batch}.zip"`);
        
        const archive = archiver('zip', { zlib: { level: 9 } });
        
        archive.on('error', (err) => {
            console.error('Archive error:', err);
            res.status(500).json({ success: false, error: 'Error creating archive' });
        });
        
        // Pipe archive data to response
        archive.pipe(res);
        
        // Add all files from the batch folder
        archive.directory(batchPath, req.params.batch);
        
        archive.finalize();
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Delete entire batch folder
app.delete('/api/batch/:batch', (req, res) => {
    try {
        const batchPath = path.join(uploadsDir, req.params.batch);
        
        // Security check
        if (!batchPath.startsWith(uploadsDir) || !fs.existsSync(batchPath)) {
            return res.status(403).json({ success: false, error: 'Access denied or batch not found' });
        }
        
        // Remove the batch folder recursively
        fs.rmSync(batchPath, { recursive: true, force: true });
        console.log(`Deleted batch folder: ${batchPath}`);
        
        res.json({
            success: true,
            message: 'Batch folder deleted successfully'
        });
    } catch (err) {
        console.error('Delete error:', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

// Get all templates
app.get('/api/templates', (req, res) => {
    try {
        const templates = [];
        
        if (!fs.existsSync(templatesDir)) {
            return res.json({
                success: true,
                templates: []
            });
        }
        
        const files = fs.readdirSync(templatesDir);
        
        for (const file of files) {
            if (file.endsWith('.csv')) {
                const filePath = path.join(templatesDir, file);
                const stats = fs.statSync(filePath);
                templates.push({
                    name: file,
                    size: stats.size,
                    modified: stats.mtime.getTime()
                });
            }
        }
        
        res.json({
            success: true,
            templates: templates.sort((a, b) => b.modified - a.modified)
        });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Upload template
app.post('/upload-template', upload.single('template'), (req, res) => {
    try {
        if (!req.file) {
            return res.status(400).json({ success: false, error: 'No file uploaded' });
        }
        
        const tempPath = req.file.path;
        const destPath = path.join(templatesDir, req.file.originalname);
        
        // Move file from temp to templates directory
        fs.renameSync(tempPath, destPath);
        
        res.json({
            success: true,
            message: 'Template uploaded successfully',
            filename: req.file.originalname
        });
    } catch (err) {
        console.error('Template upload error:', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

// Download template
app.get('/download-template/:filename', (req, res) => {
    try {
        const filename = req.params.filename;
        const filepath = path.join(templatesDir, filename);
        
        // Security check: prevent directory traversal
        if (!filepath.startsWith(templatesDir) || !filepath.endsWith('.csv')) {
            return res.status(403).json({ success: false, error: 'Invalid template name' });
        }
        
        if (!fs.existsSync(filepath)) {
            return res.status(404).json({ success: false, error: 'Template not found' });
        }
        
        res.download(filepath, filename);
    } catch (err) {
        console.error('Template download error:', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`CSV Server running on http://0.0.0.0:${PORT}`);
    console.log(`Access web interface at http://localhost:${PORT}`);
    console.log(`CSV files stored in: ${uploadsDir}`);
});

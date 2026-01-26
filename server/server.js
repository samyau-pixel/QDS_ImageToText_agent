const express = require('express');
const multer = require('multer');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const archiver = require('archiver');
const { execSync } = require('child_process');
const XLSX = require('xlsx');
const https = require('https');

const app = express();
const PORT = 3000;

// Default RapidAPI Key
let rapidApiKey = 'e98b288d2bmsh06ace0e29bf7d83p1ae9fajsn9f0ef84e9691';
const settingsFile = path.join(__dirname, 'settings.json');

// Load settings from file if exists
function loadSettings() {
    if (fs.existsSync(settingsFile)) {
        try {
            const settings = JSON.parse(fs.readFileSync(settingsFile, 'utf-8'));
            if (settings.rapidApiKey) {
                rapidApiKey = settings.rapidApiKey;
                console.log('Loaded RapidAPI key from settings');
            }
        } catch (err) {
            console.error('Error loading settings:', err.message);
        }
    }
}

// Save settings to file
function saveSettings() {
    try {
        fs.writeFileSync(settingsFile, JSON.stringify({ rapidApiKey }, null, 2));
        console.log('Settings saved');
    } catch (err) {
        console.error('Error saving settings:', err.message);
    }
}

// Load settings on startup
loadSettings();

// Enable CORS
app.use(cors());
app.use(express.json());
app.use(express.static('public'));

// Serve Icon directory
app.use('/Icon', express.static('Icon'));

// Get right_bottom icon dynamically (any PNG file in right_bottom folder)
app.get('/api/icon/right-bottom', (req, res) => {
    try {
        const iconDir = path.join(__dirname, 'Icon', 'right_bottom');
        if (!fs.existsSync(iconDir)) {
            return res.status(404).json({ error: 'Icon folder not found' });
        }
        
        const files = fs.readdirSync(iconDir);
        const pngFiles = files.filter(f => f.toLowerCase().endsWith('.png'));
        
        if (pngFiles.length === 0) {
            return res.status(404).json({ error: 'No PNG files found' });
        }
        
        // Return the first PNG file found
        res.json({ imagePath: `/Icon/right_bottom/${pngFiles[0]}` });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Report error to Freshdesk
app.post('/api/report-error', (req, res) => {
    try {
        const { description } = req.body;
        
        if (!description || !description.trim()) {
            return res.status(400).json({ success: false, error: 'Description is required' });
        }
        
        // Generate subject with current date and time
        const now = new Date();
        const dateStr = `${String(now.getMonth() + 1).padStart(2, '0')}/${String(now.getDate()).padStart(2, '0')}/${now.getFullYear()}`;
        const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        const subject = `ImageTText Issue -- ${dateStr}--${timeStr}`;
        
        // Prepare the Freshdesk ticket data
        const ticketData = {
            description: description.trim(),
            subject: subject,
            email: "sam.yau@quantum.com.hk",
            priority: 1,
            status: 2,
            cc_emails: [],
            type: "Incident",
            tags: ["ImageTTest"],
            responder_id: 50596278
        };
        
        // Make HTTPS request to Freshdesk API
        const options = {
            hostname: 'qds.freshdesk.com',
            path: '/api/v2/tickets',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Basic ' + Buffer.from('rW3cT5BlAL8RQ1V1XZH0:X').toString('base64')
            }
        };
        
        const request = https.request(options, (response) => {
            let data = '';
            
            response.on('data', (chunk) => {
                data += chunk;
            });
            
            response.on('end', () => {
                if (response.statusCode === 201 || response.statusCode === 200) {
                    console.log('Error report sent to Freshdesk successfully');
                    res.json({ success: true, message: 'Error report submitted successfully' });
                } else {
                    console.error('Freshdesk API error:', response.statusCode, data);
                    res.status(response.statusCode).json({ 
                        success: false, 
                        error: `Freshdesk API error: ${response.statusCode}` 
                    });
                }
            });
        });
        
        request.on('error', (err) => {
            console.error('Request error:', err);
            res.status(500).json({ success: false, error: err.message });
        });
        
        request.write(JSON.stringify(ticketData));
        request.end();
        
    } catch (err) {
        console.error('Error reporting error:', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

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

// Helper function to copy a directory recursively
function copyDirectory(src, dest) {
    if (!fs.existsSync(dest)) {
        fs.mkdirSync(dest, { recursive: true });
    }
    
    const files = fs.readdirSync(src);
    files.forEach(file => {
        const srcPath = path.join(src, file);
        const destPath = path.join(dest, file);
        
        if (fs.statSync(srcPath).isDirectory()) {
            copyDirectory(srcPath, destPath);
        } else {
            fs.copyFileSync(srcPath, destPath);
        }
    });
}

// Helper function to delete a directory recursively
function deleteDirectory(dir) {
    if (fs.existsSync(dir)) {
        fs.rmSync(dir, { recursive: true, force: true });
    }
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
        console.log('Report name:', req.body.report_name || 'Not provided');
        
        // Get report name from request
        const originalReportName = req.body.report_name?.trim();
        let batchName = originalReportName;
        
        if (!batchName) {
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            batchName = `batch_${timestamp}`;
        } else {
            // Sanitize the report name (remove special characters, replace spaces with underscores)
            batchName = batchName.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 100);
            // Ensure uniqueness by adding timestamp if needed
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            batchName = `${batchName}_${timestamp}`;
        }
        
        const batchFolder = path.join(uploadsDir, batchName);
        
        if (!fs.existsSync(batchFolder)) {
            fs.mkdirSync(batchFolder, { recursive: true });
            console.log(`Created batch folder: ${batchFolder}`);
        }
        
        // Save metadata with report name
        const metadata = {
            reportName: originalReportName || 'Unnamed Report',
            batchName: batchName,
            deviceName: req.body.device_name || 'Unknown Device',
            uploadTime: new Date().toISOString(),
            timestamp: req.body.timestamp
        };
        fs.writeFileSync(path.join(batchFolder, 'metadata.json'), JSON.stringify(metadata, null, 2));
        console.log(`Saved metadata with report name: ${metadata.reportName}`);
        
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
        console.log(`Summary: Batch=${batchName}, CSV=${csvFile ? 'yes' : 'no'}, Photos=${photoFiles.length}`);
        
        res.json({
            success: true,
            message: 'Files uploaded successfully',
            batch_folder: batchName,
            csv_file: csvFile ? csvFile.filename : null,
            photo_count: photoFiles.length,
            photos: photoFiles.map(f => f.filename),
            report_name: originalReportName,
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

// Get RapidAPI key
app.get('/api/settings/rapidapi-key', (req, res) => {
    try {
        // Return the key (masked for security in logs)
        res.json({ 
            success: true, 
            rapidApiKey: rapidApiKey,
            masked: rapidApiKey.substring(0, 10) + '...' + rapidApiKey.substring(rapidApiKey.length - 5)
        });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Update RapidAPI key
app.post('/api/settings/rapidapi-key', (req, res) => {
    try {
        const { rapidApiKey: newKey } = req.body;
        
        if (!newKey || typeof newKey !== 'string' || newKey.trim().length === 0) {
            return res.status(400).json({ success: false, error: 'Invalid API key' });
        }
        
        rapidApiKey = newKey.trim();
        saveSettings();
        
        console.log(`RapidAPI key updated (${rapidApiKey.substring(0, 10)}...)`);
        
        res.json({ 
            success: true, 
            message: 'API key updated successfully',
            masked: rapidApiKey.substring(0, 10) + '...' + rapidApiKey.substring(rapidApiKey.length - 5)
        });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
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
            
            // If it's a directory (batch folder)
            if (stats.isDirectory()) {
                // Try to read metadata for report name
                let reportName = 'Unnamed Report';
                try {
                    const metadataPath = path.join(itemPath, 'metadata.json');
                    if (fs.existsSync(metadataPath)) {
                        const metadata = JSON.parse(fs.readFileSync(metadataPath, 'utf8'));
                        reportName = metadata.reportName || 'Unnamed Report';
                    }
                } catch (err) {
                    console.warn(`Could not read metadata for ${item}:`, err.message);
                }
                
                // Look for CSV files inside
                try {
                    const csvFiles = fs.readdirSync(itemPath).filter(f => f.endsWith('.csv'));
                    
                    for (const csvFile of csvFiles) {
                        const csvPath = path.join(itemPath, csvFile);
                        const csvStats = fs.statSync(csvPath);
                        files.push({
                            filename: csvFile,
                            batch: item,
                            reportName: reportName,
                            size: csvStats.size,
                            modified: csvStats.mtime
                        });
                    }
                } catch (err) {
                    console.warn(`Error reading files in ${item}:`, err.message);
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
    let tempWorkDir = null;
    
    try {
        const batchPath = path.join(uploadsDir, req.params.batch);
        
        // Security check
        if (!batchPath.startsWith(uploadsDir) || !fs.existsSync(batchPath)) {
            return res.status(403).json({ success: false, error: 'Access denied or batch not found' });
        }
        
        console.log(`\n=== Download Batch Started ===`);
        console.log(`Batch: ${req.params.batch}`);
        console.log(`Source path: ${batchPath}`);
        
        // Create a temporary work directory for processing
        const tempWorkDirName = `temp_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        tempWorkDir = path.join(tempDir, tempWorkDirName);
        
        console.log(`Creating temporary work directory: ${tempWorkDir}`);
        fs.mkdirSync(tempWorkDir, { recursive: true });
        
        // Copy batch folder to temporary location
        console.log(`Copying batch to temporary location...`);
        copyDirectory(batchPath, tempWorkDir);
        
        const outputExcelPath = path.join(tempWorkDir, 'output.xlsx');
        console.log(`Output Excel path: ${outputExcelPath}`);
        
        // Execute Python script
        const pythonScript = path.join(__dirname, 'combine_excel_image.py');
        if (!fs.existsSync(pythonScript)) {
            console.error('Python script not found:', pythonScript);
            deleteDirectory(tempWorkDir);
            return res.status(500).json({ success: false, error: 'Python script not found' });
        }
        
        try {
            // Detect Python executable
            const pythonExecutable = process.platform === 'win32' 
                ? path.join(__dirname, '../ImageTText/Scripts/python.exe')
                : 'python3';
            
            const command = `"${pythonExecutable}" "${pythonScript}" "${tempWorkDir}" "${outputExcelPath}"`;
            console.log(`Executing: ${command}`);
            
            const result = execSync(command, {
                encoding: 'utf-8',
                stdio: ['pipe', 'pipe', 'pipe'],
                maxBuffer: 50 * 1024 * 1024
            });
            
            console.log(`Python script completed successfully`);
            
            // Check if output file was created
            if (!fs.existsSync(outputExcelPath)) {
                console.error('Excel file not created by Python script');
                deleteDirectory(tempWorkDir);
                return res.status(500).json({ success: false, error: 'Failed to generate Excel file' });
            }
            
            const fileSize = fs.statSync(outputExcelPath).size;
            console.log(`Excel file created: ${fileSize} bytes`);
            
            // Send the Excel file to user
            res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
            res.setHeader('Content-Disposition', `attachment; filename="${req.params.batch}_formatted.xlsx"`);
            
            const fileStream = fs.createReadStream(outputExcelPath);
            
            fileStream.on('error', (err) => {
                console.error('Stream error:', err);
                deleteDirectory(tempWorkDir);
                if (!res.headersSent) {
                    res.status(500).json({ success: false, error: 'Error sending file' });
                }
            });
            
            fileStream.pipe(res);
            
            // Cleanup after sending (with delay to ensure file is sent)
            res.on('finish', () => {
                console.log(`File sent to client, cleaning up temporary directory...`);
                deleteDirectory(tempWorkDir);
                console.log(`Cleanup complete`);
            });
            
        } catch (error) {
            console.error('Python execution error:', error.message);
            
            // Cleanup temp directory
            deleteDirectory(tempWorkDir);
            tempWorkDir = null;
            
            // Fallback: return original batch as ZIP
            console.log('Falling back to ZIP download...');
            res.setHeader('Content-Type', 'application/zip');
            res.setHeader('Content-Disposition', `attachment; filename="${req.params.batch}.zip"`);
            
            const archive = archiver('zip', { zlib: { level: 9 } });
            
            archive.on('error', (err) => {
                console.error('Archive error:', err);
                if (!res.headersSent) {
                    res.status(500).json({ success: false, error: 'Error creating archive' });
                }
            });
            
            archive.pipe(res);
            archive.directory(batchPath, req.params.batch);
            archive.finalize();
        }
        
    } catch (err) {
        console.error('General error:', err);
        
        // Cleanup if temp directory still exists
        if (tempWorkDir) {
            deleteDirectory(tempWorkDir);
        }
        
        if (!res.headersSent) {
            res.status(500).json({ success: false, error: err.message });
        }
    }
});

// Delete entire batch folder
// Rename a batch (update report name)
app.put('/api/batch/:batch/rename', (req, res) => {
    try {
        const batchPath = path.join(uploadsDir, req.params.batch);
        const newName = req.body.newName?.trim();
        
        // Security checks
        if (!batchPath.startsWith(uploadsDir) || !fs.existsSync(batchPath)) {
            return res.status(403).json({ success: false, error: 'Access denied or batch not found' });
        }
        
        if (!newName) {
            return res.status(400).json({ success: false, error: 'New name is required' });
        }
        
        // Read current metadata
        const metadataPath = path.join(batchPath, 'metadata.json');
        let metadata = {};
        
        if (fs.existsSync(metadataPath)) {
            try {
                metadata = JSON.parse(fs.readFileSync(metadataPath, 'utf8'));
            } catch (err) {
                console.warn('Could not parse existing metadata:', err.message);
            }
        }
        
        // Update the report name
        metadata.reportName = newName;
        metadata.lastModified = new Date().toISOString();
        
        // Write updated metadata
        fs.writeFileSync(metadataPath, JSON.stringify(metadata, null, 2));
        console.log(`Renamed batch ${req.params.batch} to "${newName}"`);
        
        res.json({
            success: true,
            message: 'Batch renamed successfully',
            reportName: newName
        });
    } catch (err) {
        console.error('Rename error:', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

// Delete a batch
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
        const ext = path.extname(req.file.originalname).toLowerCase();
        const baseName = path.basename(req.file.originalname, ext);
        
        let csvFileName = baseName + '.csv';
        let destPath = path.join(templatesDir, csvFileName);
        
        try {
            if (ext === '.xlsx' || ext === '.xls') {
                // Convert XLSX/XLS to CSV
                const workbook = XLSX.readFile(tempPath);
                const sheetName = workbook.SheetNames[0];
                const worksheet = workbook.Sheets[sheetName];
                
                // Convert to CSV format
                const csv = XLSX.utils.sheet_to_csv(worksheet);
                
                // Write CSV file
                fs.writeFileSync(destPath, csv);
                
                // Delete the uploaded XLSX file
                fs.unlinkSync(tempPath);
                
            } else if (ext === '.csv') {
                // Move CSV file directly
                csvFileName = req.file.originalname;
                destPath = path.join(templatesDir, csvFileName);
                fs.renameSync(tempPath, destPath);
            } else {
                fs.unlinkSync(tempPath);
                return res.status(400).json({ success: false, error: 'Invalid file type. Only CSV and XLSX are supported.' });
            }
            
            res.json({
                success: true,
                message: 'Template uploaded successfully' + (ext === '.xlsx' || ext === '.xls' ? ' and converted to CSV' : ''),
                filename: csvFileName
            });
        } catch (conversionErr) {
            // Clean up temp file if exists
            if (fs.existsSync(tempPath)) {
                fs.unlinkSync(tempPath);
            }
            throw conversionErr;
        }
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

// Reprocess OCR for cells with "Processing..." or "API Error"
app.post('/api/batch/:batch/reprocess-ocr', async (req, res) => {
    let tempWorkDir = null;
    
    try {
        const batchPath = path.join(uploadsDir, req.params.batch);
        
        // Security check
        if (!batchPath.startsWith(uploadsDir) || !fs.existsSync(batchPath)) {
            return res.status(403).json({ success: false, error: 'Access denied or batch not found' });
        }
        
        console.log(`\n=== Reprocess OCR Started ===`);
        console.log(`Batch: ${req.params.batch}`);
        console.log(`Source path: ${batchPath}`);
        
        // Create a temporary work directory for processing
        const tempWorkDirName = `temp_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        tempWorkDir = path.join(tempDir, tempWorkDirName);
        
        console.log(`Creating temporary work directory: ${tempWorkDir}`);
        fs.mkdirSync(tempWorkDir, { recursive: true });
        
        // Copy batch folder to temporary location
        console.log(`Copying batch to temporary location...`);
        copyDirectory(batchPath, tempWorkDir);
        
        // Read CSV file
        const csvFiles = fs.readdirSync(tempWorkDir).filter(f => f.endsWith('.csv'));
        if (csvFiles.length === 0) {
            deleteDirectory(tempWorkDir);
            return res.status(400).json({ success: false, error: 'No CSV file found' });
        }
        
        const csvPath = path.join(tempWorkDir, csvFiles[0]);
        const csvContent = fs.readFileSync(csvPath, 'utf-8');
        
        // Parse CSV more robustly
        const lines = csvContent.trim().split('\n');
        const headers = parseCSVLine(lines[0]);
        
        console.log(`CSV Headers: ${headers.join(', ')}`);
        console.log(`Total rows: ${lines.length - 1}`);
        
        // Get list of available image files
        const imageFiles = {};
        const allFiles = fs.readdirSync(tempWorkDir);
        allFiles.forEach(file => {
            if (file.toLowerCase().endsWith('.jpg') || 
                file.toLowerCase().endsWith('.jpeg') ||
                file.toLowerCase().endsWith('.png')) {
                imageFiles[file] = path.join(tempWorkDir, file);
                console.log(`Found image file: ${file}`);
            }
        });
        
        // Process each row
        const updatedRows = [];
        let processedCount = 0;
        
        for (let i = 1; i < lines.length; i++) {
            if (!lines[i].trim()) continue;
            
            const cells = parseCSVLine(lines[i]);
            const row = {};
            
            // Build row object
            headers.forEach((header, idx) => {
                row[header] = cells[idx] || '';
            });
            
            // Check EACH cell for errors and process with the image on the right
            for (let cellIdx = 0; cellIdx < headers.length; cellIdx++) {
                const cellValue = row[headers[cellIdx]];
                
                // Check if this cell contains an error
                if (cellValue && (cellValue.includes('Processing...') || cellValue.includes('API Error'))) {
                    console.log(`\nRow ${i}, Column ${cellIdx}: Found error in "${headers[cellIdx]}": "${cellValue}"`);
                    
                    // Look for the image in the NEXT column (right side)
                    const nextColIdx = cellIdx + 1;
                    if (nextColIdx < headers.length) {
                        const nextCellValue = row[headers[nextColIdx]];
                        console.log(`  Looking for image in next column "${headers[nextColIdx]}": "${nextCellValue}"`);
                        
                        if (nextCellValue && imageFiles[nextCellValue]) {
                            console.log(`  ✓ Found matching image: ${nextCellValue}`);
                            
                            try {
                                console.log(`\n→ Processing Row ${i}, Column ${cellIdx}:`);
                                console.log(`  Error cell: "${headers[cellIdx]}"`);
                                console.log(`  Old value: "${cellValue}"`);
                                console.log(`  Image: ${nextCellValue}`);
                                console.log(`  Calling OCR API...`);
                                
                                const ocrResult = await callOCRAPI(imageFiles[nextCellValue]);
                                
                                if (ocrResult && ocrResult.trim()) {
                                    row[headers[cellIdx]] = ocrResult.trim();
                                    processedCount++;
                                    console.log(`  ✓ New value: "${ocrResult.trim()}"`);
                                } else {
                                    console.error(`  ✗ OCR returned empty result for ${nextCellValue}`);
                                }
                            } catch (err) {
                                console.error(`  ✗ Error calling OCR API: ${err.message}`);
                            }
                        } else {
                            console.log(`  ✗ No matching image file found in next column`);
                        }
                    }
                }
            }
            
            updatedRows.push(row);
        }
        
        // Write updated CSV
        const headerLine = headers.join(',');
        const dataLines = updatedRows.map(row => 
            headers.map(header => {
                const val = row[header] || '';
                // Escape quotes and wrap in quotes if contains comma or quotes
                if (typeof val === 'string' && (val.includes(',') || val.includes('"') || val.includes('\n'))) {
                    return `"${val.replace(/"/g, '""')}"`;
                }
                return val;
            }).join(',')
        );
        
        fs.writeFileSync(csvPath, [headerLine, ...dataLines].join('\n'));
        console.log(`✓ CSV updated with ${processedCount} cells reprocessed`);
        
        // Generate Excel with images
        const outputExcelPath = path.join(tempWorkDir, 'output.xlsx');
        const pythonScript = path.join(__dirname, 'combine_excel_image.py');
        
        if (!fs.existsSync(pythonScript)) {
            console.error('Python script not found:', pythonScript);
            deleteDirectory(tempWorkDir);
            return res.status(500).json({ success: false, error: 'Python script not found' });
        }
        
        try {
            const pythonExecutable = process.platform === 'win32' 
                ? path.join(__dirname, '../ImageTText/Scripts/python.exe')
                : 'python3';
            
            const command = `"${pythonExecutable}" "${pythonScript}" "${tempWorkDir}" "${outputExcelPath}"`;
            console.log(`Executing: ${command}`);
            
            const result = execSync(command, {
                encoding: 'utf-8',
                stdio: ['pipe', 'pipe', 'pipe'],
                maxBuffer: 50 * 1024 * 1024
            });
            
            console.log(`Python script completed successfully`);
            
            // Check if output file was created
            if (!fs.existsSync(outputExcelPath)) {
                console.error('Excel file not created by Python script');
                deleteDirectory(tempWorkDir);
                return res.status(500).json({ success: false, error: 'Failed to generate Excel file' });
            }
            
            // Copy the output Excel back to the batch folder
            const finalExcelPath = path.join(batchPath, 'output.xlsx');
            fs.copyFileSync(outputExcelPath, finalExcelPath);
            
            // Also update the original CSV in the batch folder
            fs.copyFileSync(csvPath, path.join(batchPath, csvFiles[0]));
            
            console.log(`✓ Excel file created and saved to batch folder`);
            
            // Clean up temp directory
            deleteDirectory(tempWorkDir);
            
            res.json({
                success: true,
                message: 'OCR reprocessing completed successfully',
                processed: processedCount,
                excelFile: 'output.xlsx'
            });
            
        } catch (pythonErr) {
            console.error('Python execution error:', pythonErr.message);
            deleteDirectory(tempWorkDir);
            res.status(500).json({ success: false, error: 'Error executing OCR processing: ' + pythonErr.message });
        }
        
    } catch (err) {
        console.error('Reprocess OCR error:', err);
        if (tempWorkDir && fs.existsSync(tempWorkDir)) {
            deleteDirectory(tempWorkDir);
        }
        res.status(500).json({ success: false, error: err.message });
    }
});

// Helper function to parse CSV lines properly
function parseCSVLine(line) {
    const result = [];
    let current = '';
    let insideQuotes = false;
    
    for (let i = 0; i < line.length; i++) {
        const char = line[i];
        
        if (char === '"') {
            if (insideQuotes && line[i + 1] === '"') {
                current += '"';
                i++;
            } else {
                insideQuotes = !insideQuotes;
            }
        } else if (char === ',' && !insideQuotes) {
            result.push(current.trim());
            current = '';
        } else {
            current += char;
        }
    }
    
    result.push(current.trim());
    return result;
}

// Helper function to call OCR API
function callOCRAPI(imagePath) {
    return new Promise((resolve) => {
        try {
            console.log(`\n[OCR API Call]`);
            console.log(`Image path: ${imagePath}`);
            
            if (!fs.existsSync(imagePath)) {
                console.error(`✗ Image file not found: ${imagePath}`);
                resolve('');
                return;
            }
            
            const imageBuffer = fs.readFileSync(imagePath);
            const fileName = path.basename(imagePath);
            const fileSize = imageBuffer.length;
            console.log(`File: ${fileName} (${fileSize} bytes)`);
            
            // Create proper multipart form data
            const boundary = '----WebKitFormBoundary' + Math.random().toString(36).substring(2, 16);
            
            const formData = [
                `--${boundary}`,
                `Content-Disposition: form-data; name="image"; filename="${fileName}"`,
                `Content-Type: image/jpeg`,
                '',
                imageBuffer.toString('binary'),
                `--${boundary}--`,
                ''
            ].join('\r\n');
            
            const body = Buffer.from(formData, 'binary');
            
            // Use the server-side API key
            const apiKey = rapidApiKey;
            
            if (!apiKey) {
                console.error('✗ RapidAPI key not configured');
                resolve('API Error: Missing API key');
                return;
            }
            
            console.log(`API Key: ${apiKey.substring(0, 10)}...${apiKey.substring(apiKey.length - 5)}`);
            console.log(`Sending POST request to: https://apis-freeocr-ai.p.rapidapi.com/ocr`);
            console.log(`Boundary: ${boundary}`);
            console.log(`Body size: ${body.length} bytes`);
            
            const options = {
                hostname: 'apis-freeocr-ai.p.rapidapi.com',
                port: 443,
                path: '/ocr',
                method: 'POST',
                headers: {
                    'Content-Type': `multipart/form-data; boundary=${boundary}`,
                    'Content-Length': body.length,
                    'x-rapidapi-host': 'apis-freeocr-ai.p.rapidapi.com',
                    'x-rapidapi-key': apiKey,
                    'User-Agent': 'ImageTText/1.0'
                },
                timeout: 60000
            };
            
            const req = https.request(options, (res) => {
                let data = '';
                let dataSize = 0;
                
                res.on('data', (chunk) => {
                    data += chunk.toString('utf-8');
                    dataSize += chunk.length;
                });
                
                res.on('end', () => {
                    try {
                        console.log(`\nOCR Response:`);
                        console.log(`  Status: ${res.statusCode}`);
                        console.log(`  Size: ${dataSize} bytes`);
                        console.log(`  Headers: ${JSON.stringify(res.headers)}`);
                        console.log(`  Body: ${data}`);
                        
                        if (res.statusCode === 200) {
                            const jsonData = JSON.parse(data);
                            console.log(`  Parsed JSON:`, JSON.stringify(jsonData, null, 2));
                            
                            // Extract text from response - try multiple possible fields
                            let extractedText = '';
                            if (jsonData.text) {
                                extractedText = jsonData.text;
                                console.log(`  Found in "text" field`);
                            } else if (jsonData.result) {
                                extractedText = jsonData.result;
                                console.log(`  Found in "result" field`);
                            } else if (jsonData.data && jsonData.data.text) {
                                extractedText = jsonData.data.text;
                                console.log(`  Found in "data.text" field`);
                            } else if (jsonData.output) {
                                extractedText = jsonData.output;
                                console.log(`  Found in "output" field`);
                            } else {
                                console.log(`  Available fields: ${Object.keys(jsonData).join(', ')}`);
                            }
                            
                            console.log(`✓ OCR Extracted Text: "${extractedText}"`);
                            resolve(extractedText);
                        } else {
                            console.error(`✗ OCR API Error - Status: ${res.statusCode}`);
                            console.error(`Response: ${data}`);
                            resolve('');
                        }
                    } catch (err) {
                        console.error(`✗ Error parsing OCR response: ${err.message}`);
                        console.error(`Raw response: ${data}`);
                        resolve('');
                    }
                });
            });
            
            req.on('error', (err) => {
                console.error(`✗ OCR API request error: ${err.message}`);
                resolve('');
            });
            
            req.on('timeout', () => {
                console.error('✗ OCR API request timeout');
                req.destroy();
                resolve('');
            });
            
            req.write(body);
            req.end();
        } catch (err) {
            console.error('✗ OCR API call error:', err.message);
            resolve('');
        }
    });
}

// Delete template
app.delete('/api/templates/:filename', (req, res) => {
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
        
        fs.unlinkSync(filepath);
        
        res.json({
            success: true,
            message: 'Template deleted successfully'
        });
    } catch (err) {
        console.error('Template delete error:', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`CSV Server running on http://0.0.0.0:${PORT}`);
    console.log(`Access web interface at http://localhost:${PORT}`);
    console.log(`CSV files stored in: ${uploadsDir}`);
});

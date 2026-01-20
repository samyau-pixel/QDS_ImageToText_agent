# ImageTText - RealWear OCR Application

A prototype Android application for RealWear devices that captures images using the device camera and extracts text using an AI-powered OCR API.

## Features

- **Camera Integration**: Uses RealWear camera API to capture images
- **OCR Processing**: Sends images to FreeOCR AI API for text extraction
- **Real-time Display**: Shows extracted text on the UI screen
- **Landscape Orientation**: Optimized for RealWear HMT device display

## Architecture

### Components

1. **MainActivity.kt**
   - Manages camera lifecycle and UI
   - Handles camera permissions
   - Triggers image capture and API calls

2. **OCRApiService.kt**
   - Handles HTTP requests to the OCR API
   - Parses JSON responses
   - Manages API credentials

3. **activity_main.xml**
   - Camera preview layout
   - Capture button
   - Result text display area

## Requirements

- Android API Level 29+
- RealWear HMT device
- Internet connection for API calls
- Camera permissions

## Setup Instructions

1. **Open in Android Studio**
   ```
   Open this project folder in Android Studio
   ```

2. **Install Dependencies**
   - Gradle will automatically download all required dependencies
   - Ensure you have the Android SDK set up (API 33+)

3. **API Configuration**
   - The API credentials are currently embedded in `OCRApiService.kt`
   - **IMPORTANT**: Move credentials to a secure location (BuildConfig or encrypted storage) before deployment
   - Update the API key with your own RapidAPI credentials

4. **Build and Deploy**
   ```
   ./gradlew build
   ./gradlew installDebug  # Deploy to connected RealWear device
   ```

## API Integration

The app uses the FreeOCR AI API from RapidAPI:

**Endpoint**: `https://apis-freeocr-ai.p.rapidapi.com/ocr`

**Request Format**:
- Method: POST
- Content-Type: multipart/form-data
- Parameters: image file (JPEG/PNG)
- Headers: X-RapidAPI-Host, X-RapidAPI-Key

**Response Format**:
```json
{
  "text": "Extracted text from image",
  ...
}
```

## Workflow

1. User opens the app on RealWear device
2. Camera preview is displayed
3. User presses "Capture & Extract Text" button
4. App captures image from camera
5. Image is sent to OCR API
6. Extracted text is displayed on screen

## File Structure

```
ImageTText/
├── app/
│   ├── build.gradle                 # App dependencies
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml  # App permissions and activities
│   │       ├── java/
│   │       │   └── com/realwear/imagettext/
│   │       │       ├── MainActivity.kt
│   │       │       └── OCRApiService.kt
│   │       └── res/
│   │           ├── layout/
│   │           │   └── activity_main.xml
│   │           └── values/
│   │               ├── strings.xml
│   │               └── themes.xml
├── build.gradle                     # Project configuration
├── settings.gradle                  # Gradle settings
└── README.md                        # This file
```

## Future Enhancements

- [ ] Add image preview before sending to API
- [ ] Implement local caching for offline text storage
- [ ] Add copy-to-clipboard functionality
- [ ] Support for multiple languages
- [ ] Batch image processing
- [ ] Secure credential storage using Android Keystore
- [ ] Add retry logic for failed API calls
- [ ] Implement logging and analytics
- [ ] Add settings for API configuration

## Troubleshooting

**Camera not opening**:
- Check camera permissions in device settings
- Verify device is a RealWear device

**API calls failing**:
- Verify internet connection
- Check API key validity
- Review server logs

**Text not displayed**:
- Check API response format
- Verify image quality
- Check logcat output for errors

## Dependencies

- AndroidX Camera (for camera integration)
- OkHttp3 (for HTTP requests)
- Retrofit2 (for API communication)
- Gson (for JSON parsing)
- Kotlin Coroutines (for async operations)

## License

This project is for prototype development on RealWear devices.

## References

- [RealWear Developer Docs](https://developer.realwear.com/docs/developer-examples/camera-applet)
- [RealWear GitHub Examples](https://github.com/realwear/Developer-Examples)
- [FreeOCR AI API](https://rapidapi.com/nvcodeandroid/api/apis-freeocr-ai)

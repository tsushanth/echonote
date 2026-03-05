# CoreML Vocal Separation Model Conversion Guide

This directory contains scripts to convert vocal separation models (Demucs, Spleeter) to CoreML format for use in the EchoNote iOS app.

## Quick Start

### Option 1: Demucs (Recommended - Best Quality)

```bash
# Install dependencies
pip install -r requirements.txt

# Convert Demucs model to CoreML
python convert_demucs_to_coreml.py

# This will create: VocalSeparationModel.mlmodel (~70MB)
```

### Option 2: Spleeter (Faster, Smaller)

```bash
# Install dependencies (if not already done)
pip install -r requirements.txt

# Convert Spleeter model to CoreML
python convert_spleeter_to_coreml.py

# This will create: VocalSeparationModel.mlmodel (~50MB)
```

## System Requirements

- Python 3.8 or later
- macOS (recommended) or Linux
- ~2GB free disk space for models and conversion
- ~30 minutes for first run (model download + conversion)

## Installation

1. **Create a virtual environment (recommended):**
   ```bash
   python3 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

2. **Install dependencies:**
   ```bash
   pip install --upgrade pip
   pip install -r requirements.txt
   ```

   **Note:** This will install:
   - PyTorch and TorchAudio
   - Core ML Tools
   - Demucs and/or Spleeter
   - Supporting libraries

## Model Comparison

| Model | Quality | Speed | Size | iOS Recommendation |
|-------|---------|-------|------|-------------------|
| **Demucs** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ~70MB | ✅ **Best for quality** |
| **Spleeter** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ~50MB | ✅ Good balance |

**Recommendation:** Use Demucs (htdemucs) for best vocal separation quality.

## Usage Examples

### Convert Demucs with Custom Settings

```bash
# Use different Demucs variant
python convert_demucs_to_coreml.py --model htdemucs_ft

# Adjust chunk duration (longer = more memory, better quality)
python convert_demucs_to_coreml.py --chunk-duration 15

# Custom output path
python convert_demucs_to_coreml.py --output MyVocalModel.mlmodel

# All options combined
python convert_demucs_to_coreml.py \
  --model htdemucs_ft \
  --chunk-duration 12 \
  --output VocalSeparationModel.mlmodel
```

#### Available Demucs Models:
- `htdemucs` - Default, best quality (recommended)
- `htdemucs_ft` - Fine-tuned variant
- `htdemucs_6s` - 6-stem separation (drums, bass, vocals, etc.)
- `mdx_extra` - Alternative architecture

### Convert Spleeter with Custom Settings

```bash
# Basic conversion
python convert_spleeter_to_coreml.py

# Use 4-stem model (vocals, drums, bass, other)
python convert_spleeter_to_coreml.py --model spleeter:4stems

# Adjust chunk duration
python convert_spleeter_to_coreml.py --chunk-duration 12

# All options
python convert_spleeter_to_coreml.py \
  --model spleeter:2stems \
  --chunk-duration 10 \
  --output VocalSeparationModel.mlmodel
```

## Adding Model to Xcode

After running the conversion script:

1. **Copy the model file:**
   ```bash
   # The script creates: VocalSeparationModel.mlmodel
   # Copy this file
   ```

2. **Add to Xcode:**
   - Open EchoNote.xcodeproj in Xcode
   - Drag `VocalSeparationModel.mlmodel` into the project navigator
   - ✅ Check "Copy items if needed"
   - ✅ Select EchoNote target
   - Click "Finish"

3. **Verify in Xcode:**
   - Click on the .mlmodel file in Xcode
   - You should see:
     - Input: `audio_input`
     - Outputs: `vocals_output`, `instrumental_output`

4. **Build and Test:**
   - Build the project (⌘B)
   - Run on simulator or device
   - Test vocal separation feature

## Adjusting for Your Model

The Swift code in AudioEditorService.swift assumes specific input/output names. If your model uses different names, update these lines:

```swift
// In runModelInference() function:
let inputName = "audio_input"  // Change if needed
let vocalsOutputName = "vocals_output"  // Change if needed
let instrumentalOutputName = "instrumental_output"  // Change if needed
```

**Finding your model's names:**
1. Open the .mlmodel file in Xcode
2. View the "Predictions" tab
3. Note the input and output names
4. Update the Swift code accordingly

## Troubleshooting

### Issue: "Module not found" errors

```bash
# Solution: Reinstall dependencies
pip uninstall -y coremltools torch torchaudio
pip install --upgrade pip
pip install -r requirements.txt
```

### Issue: Model download fails

```bash
# Solution: Check internet connection and try again
# Demucs downloads from GitHub (~300MB)
# Spleeter downloads from Deezer servers (~50MB)
```

### Issue: "Out of memory" during conversion

```bash
# Solution: Reduce chunk duration
python convert_demucs_to_coreml.py --chunk-duration 5

# Or use Spleeter (smaller model)
python convert_spleeter_to_coreml.py
```

### Issue: Conversion takes too long

This is normal! First run includes:
- Model download (5-10 minutes)
- Model loading (2-3 minutes)
- CoreML conversion (5-15 minutes)

Total time: 15-30 minutes depending on your machine.

### Issue: Model file is too large for iOS

The converted models are 50-80MB, which is acceptable for iOS apps. However, if you need to reduce size:

1. **Use quantization:**
   ```python
   # Add to conversion script after mlmodel = ct.convert(...)
   mlmodel = ct.models.neural_network.quantization_utils.quantize_weights(
       mlmodel, nbits=8
   )
   ```

2. **Use Spleeter instead of Demucs** (50MB vs 70MB)

## Testing the Model

After adding to Xcode, test with a simple audio file:

```swift
let editorService = AudioEditorService()

Task {
    do {
        let result = try await editorService.separateVocals(
            sourceURL: recordingURL
        )
        print("✅ Vocals: \(result.vocals)")
        print("✅ Instrumental: \(result.instrumental)")
    } catch AudioEditorError.modelNotFound {
        print("❌ Model not found in bundle")
    } catch {
        print("❌ Error: \(error)")
    }
}
```

## Performance Expectations

On iPhone 12 and newer:
- **Processing time:** 2-5 minutes for 3-minute audio
- **Memory usage:** 200-400MB during processing
- **Quality:** Near studio-quality separation
- **Works offline:** ✅ Fully on-device

On older iPhones (iPhone X, 11):
- **Processing time:** 4-8 minutes for 3-minute audio
- Still works, just slower

## Alternative: Cloud-Based Approach

If on-device processing is too slow or the model is too large, consider using a cloud API:

```swift
// Example using a cloud service
func separateVocalsCloud(audioURL: URL) async throws -> (vocals: URL, instrumental: URL) {
    // Upload audio to cloud service
    // Wait for processing
    // Download separated tracks
    // Much faster (30s for 3-min audio) but requires internet
}
```

Popular services:
- **Replicate.com** - Pay-per-use Demucs API
- **AWS Sagemaker** - Host your own model
- **Custom backend** - Run Demucs on your server

## Credits

- **Demucs**: Facebook Research - https://github.com/facebookresearch/demucs
- **Spleeter**: Deezer Research - https://github.com/deezer/spleeter
- **CoreML Tools**: Apple - https://github.com/apple/coremltools

## License

These scripts are provided as-is for educational purposes. The underlying models (Demucs, Spleeter) have their own licenses (MIT). Please review their respective licenses before commercial use.

## Need Help?

If you encounter issues:
1. Check the error message carefully
2. Verify all dependencies are installed: `pip list`
3. Try with default settings first
4. Check disk space: need ~2GB free
5. Ensure Python 3.8+ : `python --version`

## Next Steps

After successful conversion:
1. ✅ Add model to Xcode project
2. ✅ Build and run
3. ✅ Test vocal separation on real recordings
4. ✅ Adjust chunk size if needed (in AudioEditorService.swift line 386)
5. ✅ Ship to App Store!

Happy converting! 🎵

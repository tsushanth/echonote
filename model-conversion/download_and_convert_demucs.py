#!/usr/bin/env python3
"""
Download a pre-trained Demucs model and attempt CoreML conversion.

Demucs is a state-of-the-art music source separation model from Meta Research.
"""

import torch
import torchaudio
import coremltools as ct
from demucs.pretrained import get_model
import numpy as np
import os

print("=" * 60)
print("Demucs to CoreML Converter")
print("=" * 60)

# Step 1: Download pre-trained model
print("\n1. Downloading pre-trained Demucs model...")
print("   Model: htdemucs (hybrid transformer demucs)")
print("   This may take a few minutes on first run...")

try:
    # htdemucs is the latest and best model (but large ~350MB)
    # htdemucs_ft is fine-tuned version
    # We'll try the smaller 'mdx_extra' model which is better for mobile
    model = get_model('mdx_extra')
    print(f"   ✓ Model loaded: {type(model).__name__}")
    print(f"   Sources: {model.sources}")
    model.eval()
except Exception as e:
    print(f"   ✗ Failed to load model: {e}")
    print("\n   Trying alternative model 'mdx'...")
    try:
        model = get_model('mdx')
        print(f"   ✓ Model loaded: {type(model).__name__}")
        model.eval()
    except Exception as e2:
        print(f"   ✗ Failed: {e2}")
        exit(1)

# Step 2: Prepare model for export
print("\n2. Preparing model for export...")
sample_rate = 44100
segment_duration = 8  # seconds - smaller chunks for mobile
segment_samples = sample_rate * segment_duration
channels = 2  # stereo

# Create dummy input
dummy_input = torch.randn(1, channels, segment_samples)
print(f"   Input shape: {dummy_input.shape}")
print(f"   Segment: {segment_duration}s at {sample_rate}Hz")

# Step 3: Test forward pass
print("\n3. Testing model forward pass...")
try:
    with torch.no_grad():
        output = model(dummy_input)
    print(f"   ✓ Output shape: {output.shape}")
    print(f"   Output contains {output.shape[1]} sources")
except Exception as e:
    print(f"   ✗ Forward pass failed: {e}")
    print("\n   This model may not be suitable for mobile conversion.")
    print("   Consider using a server-side approach or simpler model.")
    exit(1)

# Step 4: Try to trace the model
print("\n4. Attempting to trace model with torch.jit...")
try:
    traced_model = torch.jit.trace(model, dummy_input)
    print("   ✓ Model traced successfully")
except Exception as e:
    print(f"   ✗ Tracing failed: {e}")
    print("\n   Demucs uses complex operations that may not be traceable.")
    print("   Attempting script mode...")
    try:
        traced_model = torch.jit.script(model)
        print("   ✓ Model scripted successfully")
    except Exception as e2:
        print(f"   ✗ Script mode also failed: {e2}")
        exit(1)

# Step 5: Convert to CoreML
print("\n5. Converting to CoreML...")
print("   This may take several minutes...")
try:
    mlmodel = ct.convert(
        traced_model,
        inputs=[
            ct.TensorType(
                name="audio_input",
                shape=dummy_input.shape,
                dtype=np.float32
            )
        ],
        minimum_deployment_target=ct.target.iOS16,
        compute_units=ct.ComputeUnit.ALL
    )

    print("   ✓ CoreML conversion successful!")

    # Add metadata
    mlmodel.author = "Meta Research (Demucs)"
    mlmodel.license = "MIT"
    mlmodel.short_description = "Demucs vocal separation model"
    mlmodel.version = "1.0"

    # Save
    output_path = "DemucsVocalSeparation.mlpackage"
    print(f"\n6. Saving model to {output_path}...")
    mlmodel.save(output_path)

    size_mb = sum(os.path.getsize(os.path.join(dirpath, filename))
                  for dirpath, _, filenames in os.walk(output_path)
                  for filename in filenames) / (1024 * 1024)

    print(f"\n{'=' * 60}")
    print("✅ SUCCESS!")
    print(f"{'=' * 60}")
    print(f"Model: {output_path}")
    print(f"Size: {size_mb:.1f} MB")
    print(f"Input: {channels} channels, {segment_duration}s segments")
    print(f"Output: {output.shape[1]} sources (vocals, bass, drums, other)")
    print(f"\nNext steps:")
    print(f"1. Copy {output_path} to Xcode project")
    print(f"2. Replace VocalSeparationModel.mlpackage")
    print(f"3. Update code to handle {output.shape[1]} outputs")

except Exception as e:
    print(f"   ✗ CoreML conversion failed: {e}")
    print("\n   Common issues:")
    print("   - Demucs uses operations not supported by CoreML")
    print("   - Model architecture is too complex for mobile")
    print("\n   Alternative approaches:")
    print("   1. Use server-side Demucs API")
    print("   2. Use simpler model like Spleeter")
    print("   3. Ship without this feature initially")
    exit(1)

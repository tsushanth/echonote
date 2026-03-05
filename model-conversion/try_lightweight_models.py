#!/usr/bin/env python3
"""
Try lightweight vocal separation models that might work on mobile.
"""

import torch
import torchaudio
import coremltools as ct
import numpy as np
import os

print("=" * 60)
print("Exploring Lightweight Vocal Separation Models")
print("=" * 60)

# Option 1: Try torchaudio's built-in source separation
print("\n1. Checking torchaudio built-in models...")
try:
    # Torchaudio has some pretrained models
    print("   torchaudio version:", torchaudio.__version__)

    # Try loading a simple source separation bundle
    from torchaudio.pipelines import HDEMUCS_HIGH_MUSDB_PLUS
    print(f"   Found bundle: HDEMUCS_HIGH_MUSDB_PLUS")
    print(f"   Sample rate: {HDEMUCS_HIGH_MUSDB_PLUS.sample_rate}")
    print(f"   Sources: {HDEMUCS_HIGH_MUSDB_PLUS.sources}")

    # This is still Demucs, won't work
    print("   (This is still Demucs-based, won't work for CoreML)")

except Exception as e:
    print(f"   Not available: {e}")

# Option 2: Create a simple U-Net based separator
print("\n2. Creating custom lightweight U-Net model...")

class SimpleVocalSeparator(torch.nn.Module):
    """
    Simplified U-Net architecture for vocal separation.
    Much simpler than Demucs, might work with CoreML.
    """
    def __init__(self, n_fft=2048, hop_length=512):
        super().__init__()
        self.n_fft = n_fft
        self.hop_length = hop_length

        # Encoder (downsampling)
        self.enc1 = torch.nn.Sequential(
            torch.nn.Conv2d(1, 16, 3, padding=1),
            torch.nn.BatchNorm2d(16),
            torch.nn.ReLU(),
            torch.nn.Conv2d(16, 16, 3, padding=1),
            torch.nn.BatchNorm2d(16),
            torch.nn.ReLU()
        )

        self.enc2 = torch.nn.Sequential(
            torch.nn.Conv2d(16, 32, 3, padding=1),
            torch.nn.BatchNorm2d(32),
            torch.nn.ReLU(),
            torch.nn.Conv2d(32, 32, 3, padding=1),
            torch.nn.BatchNorm2d(32),
            torch.nn.ReLU()
        )

        self.enc3 = torch.nn.Sequential(
            torch.nn.Conv2d(32, 64, 3, padding=1),
            torch.nn.BatchNorm2d(64),
            torch.nn.ReLU(),
            torch.nn.Conv2d(64, 64, 3, padding=1),
            torch.nn.BatchNorm2d(64),
            torch.nn.ReLU()
        )

        # Bottleneck
        self.bottleneck = torch.nn.Sequential(
            torch.nn.Conv2d(64, 128, 3, padding=1),
            torch.nn.BatchNorm2d(128),
            torch.nn.ReLU(),
            torch.nn.Conv2d(128, 64, 3, padding=1),
            torch.nn.BatchNorm2d(64),
            torch.nn.ReLU()
        )

        # Decoder (upsampling)
        self.dec3 = torch.nn.Sequential(
            torch.nn.Conv2d(128, 64, 3, padding=1),
            torch.nn.BatchNorm2d(64),
            torch.nn.ReLU(),
            torch.nn.Conv2d(64, 32, 3, padding=1),
            torch.nn.BatchNorm2d(32),
            torch.nn.ReLU()
        )

        self.dec2 = torch.nn.Sequential(
            torch.nn.Conv2d(64, 32, 3, padding=1),
            torch.nn.BatchNorm2d(32),
            torch.nn.ReLU(),
            torch.nn.Conv2d(32, 16, 3, padding=1),
            torch.nn.BatchNorm2d(16),
            torch.nn.ReLU()
        )

        self.dec1 = torch.nn.Sequential(
            torch.nn.Conv2d(32, 16, 3, padding=1),
            torch.nn.BatchNorm2d(16),
            torch.nn.ReLU(),
            torch.nn.Conv2d(16, 1, 3, padding=1),
            torch.nn.Sigmoid()  # Output mask
        )

        self.pool = torch.nn.MaxPool2d(2)
        self.upsample = torch.nn.Upsample(scale_factor=2, mode='bilinear', align_corners=True)

    def forward(self, x):
        """
        Args:
            x: (batch, 1, freq_bins, time_frames) - spectrogram
        Returns:
            mask: (batch, 1, freq_bins, time_frames) - vocal mask
        """
        # Encoder
        e1 = self.enc1(x)
        e2 = self.enc2(self.pool(e1))
        e3 = self.enc3(self.pool(e2))

        # Bottleneck
        b = self.bottleneck(self.pool(e3))

        # Decoder with skip connections
        d3 = self.dec3(torch.cat([self.upsample(b), e3], dim=1))
        d2 = self.dec2(torch.cat([self.upsample(d3), e2], dim=1))
        d1 = self.dec1(torch.cat([self.upsample(d2), e1], dim=1))

        return d1

print("   ✓ Model architecture created")

# Create model instance
model = SimpleVocalSeparator()
model.eval()

# Test with dummy spectrogram
freq_bins = 1025  # n_fft/2 + 1 for n_fft=2048
time_frames = 173  # ~4 seconds at hop_length=512, sr=44100
dummy_spec = torch.randn(1, 1, freq_bins, time_frames)

print(f"   Input shape: {dummy_spec.shape}")
print("   Testing forward pass...")

try:
    with torch.no_grad():
        output_mask = model(dummy_spec)
    print(f"   ✓ Output mask shape: {output_mask.shape}")
    print(f"   Mask range: [{output_mask.min():.3f}, {output_mask.max():.3f}]")
except Exception as e:
    print(f"   ✗ Forward pass failed: {e}")
    exit(1)

# Try to trace
print("\n3. Attempting to trace model...")
try:
    traced_model = torch.jit.trace(model, dummy_spec)
    print("   ✓ Model traced successfully!")
except Exception as e:
    print(f"   ✗ Tracing failed: {e}")
    exit(1)

# Try CoreML conversion
print("\n4. Converting to CoreML...")
try:
    mlmodel = ct.convert(
        traced_model,
        inputs=[ct.TensorType(
            name="spectrogram",
            shape=dummy_spec.shape,
            dtype=np.float32
        )],
        outputs=[ct.TensorType(
            name="vocal_mask",
            dtype=np.float32
        )],
        minimum_deployment_target=ct.target.iOS16,
        compute_units=ct.ComputeUnit.ALL
    )

    print("   ✓ CoreML conversion successful!")

    # Add metadata
    mlmodel.author = "Lightweight U-Net Vocal Separator"
    mlmodel.license = "MIT"
    mlmodel.short_description = "Spectrogram-based vocal separation using U-Net architecture"
    mlmodel.version = "1.0-untrained"

    mlmodel.input_description["spectrogram"] = f"Input STFT spectrogram ({freq_bins} freq bins, {time_frames} frames)"
    mlmodel.output_description["vocal_mask"] = "Binary mask for vocal frequencies (apply to spectrogram)"

    # Save
    output_path = "LightweightVocalSep.mlpackage"
    mlmodel.save(output_path)

    size_mb = sum(os.path.getsize(os.path.join(dirpath, filename))
                  for dirpath, _, filenames in os.walk(output_path)
                  for filename in filenames) / (1024 * 1024)

    print(f"\n{'=' * 60}")
    print("✅ SUCCESS - Lightweight Model Created!")
    print(f"{'=' * 60}")
    print(f"Model: {output_path}")
    print(f"Size: {size_mb:.1f} MB")
    print(f"Architecture: U-Net with skip connections")
    print(f"Input: Spectrogram {freq_bins}×{time_frames}")
    print(f"Output: Vocal mask (same dimensions)")
    print(f"\n⚠️  IMPORTANT:")
    print(f"   This model is UNTRAINED!")
    print(f"   It has random weights and won't separate vocals.")
    print(f"\n   To make it work:")
    print(f"   1. Train on dataset (MUSDB18, DSD100)")
    print(f"   2. Convert trained model to CoreML")
    print(f"   3. Replace this model")
    print(f"\n   OR: Use pre-trained weights if available")

    print(f"\n📝 Integration notes:")
    print(f"   - Convert audio to spectrogram (STFT)")
    print(f"   - Run through model")
    print(f"   - Multiply spectrogram by mask")
    print(f"   - Inverse STFT to get audio")

except Exception as e:
    print(f"   ✗ CoreML conversion failed: {e}")
    import traceback
    traceback.print_exc()
    exit(1)

print("\n5. Searching for pre-trained lightweight models...")
print("   Checking PyTorch Hub...")

try:
    # Check if there are any vocal separation models on torch hub
    models = torch.hub.list('pytorch/vision', force_reload=False)
    print(f"   Found {len(models)} models in pytorch/vision")
    print("   (No vocal separation models in standard hub)")
except:
    pass

print("\n   Checking for Open-Unmix...")
try:
    import openunmix
    print(f"   openunmix found: {openunmix.__version__}")
    print("   Attempting to load Open-Unmix model...")

    # This might work better than Demucs
    from openunmix import umxhq
    print("   Open-Unmix available but requires separate installation")
except ImportError:
    print("   openunmix not installed (pip install openunmix-pytorch)")
    print("   This might be a better option than Demucs for mobile")

print(f"\n{'=' * 60}")
print("Summary:")
print(f"{'=' * 60}")
print("✅ Created untrained U-Net model (converts to CoreML)")
print("❌ No pre-trained lightweight models found")
print("\nRecommendations:")
print("1. Try Open-Unmix (pip install openunmix-pytorch)")
print("2. Use the untrained U-Net as placeholder")
print("3. Find pre-trained PyTorch model weights online")
print("4. Consider server-side processing for production")

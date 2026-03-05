#!/usr/bin/env python3
"""
Create a CoreML-compatible wrapper around Demucs model.
"""

import torch
import torchaudio
import coremltools as ct
from demucs.pretrained import get_model
from demucs.apply import apply_model
import numpy as np
import os

print("=" * 60)
print("Demucs Wrapper for CoreML")
print("=" * 60)

# Load model
print("\n1. Loading Demucs model...")
model = get_model('mdx_extra')
model.eval()
print(f"   ✓ Model loaded: {model.sources}")

# Create a wrapper class that can be traced
class DemucsWrapper(torch.nn.Module):
    """Wrapper around Demucs that can be exported to CoreML."""

    def __init__(self, demucs_model):
        super().__init__()
        self.demucs = demucs_model

    def forward(self, audio):
        """
        Args:
            audio: (batch, channels, samples) - stereo audio
        Returns:
            vocals: (batch, channels, samples) - separated vocals
        """
        # Apply the model using demucs's apply_model function
        with torch.no_grad():
            # apply_model expects (batch, channels, samples)
            sources = apply_model(
                self.demucs,
                audio,
                shifts=0,  # No shift augmentation for speed
                split=True,  # Split into chunks
                overlap=0.25,  # 25% overlap
                device='cpu'
            )
            # sources shape: (batch, num_sources, channels, samples)
            # We want just vocals (index 3)
            vocals_idx = self.demucs.sources.index('vocals')
            vocals = sources[:, vocals_idx, :, :]

        return vocals

print("\n2. Creating wrapper...")
wrapper = DemucsWrapper(model)
wrapper.eval()

# Test input
sample_rate = 44100
duration = 8  # seconds
channels = 2
dummy_input = torch.randn(1, channels, sample_rate * duration)

print(f"\n3. Testing wrapper...")
try:
    with torch.no_grad():
        output = wrapper(dummy_input)
    print(f"   ✓ Output shape: {output.shape}")
    print(f"   Input: {dummy_input.shape}")
    print(f"   Output: {output.shape} (vocals only)")
except Exception as e:
    print(f"   ✗ Failed: {e}")
    print("\n   The apply_model function may not be compatible with tracing.")
    print("   Demucs is designed for server-side use, not mobile deployment.")
    exit(1)

print(f"\n4. Attempting to trace...")
try:
    traced = torch.jit.trace(wrapper, dummy_input)
    print("   ✓ Tracing successful!")
except Exception as e:
    print(f"   ✗ Tracing failed: {e}")
    print("\n   Demucs uses dynamic operations that can't be traced.")
    print("\n   Alternative: Use simpler model or server-side processing")
    exit(1)

print(f"\n5. Converting to CoreML...")
try:
    mlmodel = ct.convert(
        traced,
        inputs=[ct.TensorType(name="audio", shape=dummy_input.shape)],
        minimum_deployment_target=ct.target.iOS16,
    )

    output_path = "DemucsVocals.mlpackage"
    mlmodel.save(output_path)
    print(f"   ✓ Saved to {output_path}")

except Exception as e:
    print(f"   ✗ Conversion failed: {e}")
    exit(1)

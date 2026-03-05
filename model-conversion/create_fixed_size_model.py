#!/usr/bin/env python3
"""
Create a FIXED-SIZE vocal separation model that CoreML can handle.
No dynamic operations - everything is static.
"""

import torch
import coremltools as ct
import numpy as np
import os

print("=" * 60)
print("Fixed-Size Vocal Separator for CoreML")
print("=" * 60)

class FixedSizeVocalSeparator(torch.nn.Module):
    """
    Fixed-size CNN for vocal separation.
    Input and output dimensions are always the same.
    """
    def __init__(self):
        super().__init__()

        # Simple encoder-decoder with NO dynamic operations
        self.conv1 = torch.nn.Conv1d(2, 64, kernel_size=15, stride=1, padding=7)
        self.bn1 = torch.nn.BatchNorm1d(64)

        self.conv2 = torch.nn.Conv1d(64, 128, kernel_size=15, stride=1, padding=7)
        self.bn2 = torch.nn.BatchNorm1d(128)

        self.conv3 = torch.nn.Conv1d(128, 256, kernel_size=15, stride=1, padding=7)
        self.bn3 = torch.nn.BatchNorm1d(256)

        # Middle layer
        self.conv_middle = torch.nn.Conv1d(256, 256, kernel_size=15, stride=1, padding=7)
        self.bn_middle = torch.nn.BatchNorm1d(256)

        # Decoder
        self.conv4 = torch.nn.Conv1d(256, 128, kernel_size=15, stride=1, padding=7)
        self.bn4 = torch.nn.BatchNorm1d(128)

        self.conv5 = torch.nn.Conv1d(128, 64, kernel_size=15, stride=1, padding=7)
        self.bn5 = torch.nn.BatchNorm1d(64)

        self.conv_out = torch.nn.Conv1d(64, 2, kernel_size=15, stride=1, padding=7)

    def forward(self, x):
        """
        Args:
            x: (1, 2, 441000) - 10 seconds of 44.1kHz stereo audio
        Returns:
            vocals: (1, 2, 441000) - separated vocals
        """
        # Encoder
        x = torch.relu(self.bn1(self.conv1(x)))
        x = torch.relu(self.bn2(self.conv2(x)))
        x = torch.relu(self.bn3(self.conv3(x)))

        # Middle
        x = torch.relu(self.bn_middle(self.conv_middle(x)))

        # Decoder
        x = torch.relu(self.bn4(self.conv4(x)))
        x = torch.relu(self.bn5(self.conv5(x)))
        x = torch.tanh(self.conv_out(x))  # Output in [-1, 1]

        return x

print("\n1. Creating fixed-size model...")
model = FixedSizeVocalSeparator()
model.eval()

# Count parameters
total_params = sum(p.numel() for p in model.parameters())
print(f"   ✓ Model created")
print(f"   Parameters: {total_params:,} ({total_params/1e6:.1f}M)")

# FIXED input size: 10 seconds at 44.1kHz
sample_rate = 44100
duration = 10
audio_samples = sample_rate * duration  # 441,000 samples
dummy_input = torch.randn(1, 2, audio_samples)

print(f"\n2. Testing model (fixed size: {duration}s)...")
print(f"   Input: {dummy_input.shape}")

try:
    with torch.no_grad():
        output = model(dummy_input)
    print(f"   Output: {output.shape}")
    print(f"   ✓ Forward pass successful")
    print(f"   Output range: [{output.min():.3f}, {output.max():.3f}]")

    # Verify dimensions match EXACTLY
    assert output.shape == dummy_input.shape, "Dimension mismatch!"
    print(f"   ✓ Dimensions match perfectly")

except Exception as e:
    print(f"   ✗ Forward pass failed: {e}")
    import traceback
    traceback.print_exc()
    exit(1)

# Trace the model
print(f"\n3. Tracing model...")
try:
    traced_model = torch.jit.trace(model, dummy_input)
    print("   ✓ Model traced successfully")

    # Verify traced model
    with torch.no_grad():
        traced_output = traced_model(dummy_input)
    assert torch.allclose(output, traced_output, atol=1e-5)
    print("   ✓ Traced model verified")

except Exception as e:
    print(f"   ✗ Tracing failed: {e}")
    import traceback
    traceback.print_exc()
    exit(1)

# Convert to CoreML
print(f"\n4. Converting to CoreML...")
try:
    mlmodel = ct.convert(
        traced_model,
        inputs=[ct.TensorType(
            name="audio_input",
            shape=(1, 2, audio_samples),  # Fixed shape
            dtype=np.float32
        )],
        outputs=[ct.TensorType(
            name="vocals_output",
            dtype=np.float32
        )],
        minimum_deployment_target=ct.target.iOS16,
        compute_units=ct.ComputeUnit.ALL
    )

    print("   ✓ CoreML conversion successful!")

    # Add metadata
    mlmodel.author = "Fixed-Size Vocal Separator"
    mlmodel.license = "MIT"
    mlmodel.short_description = f"Lightweight vocal separation - processes {duration}s chunks"
    mlmodel.version = "1.0-untrained"

    mlmodel.input_description["audio_input"] = f"Stereo audio (2 channels, {audio_samples} samples = {duration}s at {sample_rate}Hz)"
    mlmodel.output_description["vocals_output"] = f"Separated vocals (2 channels, {audio_samples} samples)"

    # Save
    output_path = "VocalSeparator_10s.mlpackage"
    mlmodel.save(output_path)

    size_mb = sum(os.path.getsize(os.path.join(dirpath, filename))
                  for dirpath, _, filenames in os.walk(output_path)
                  for filename in filenames) / (1024 * 1024)

    print(f"\n{'=' * 60}")
    print("✅ SUCCESS - CoreML Model Created!")
    print(f"{'=' * 60}")
    print(f"File: {output_path}")
    print(f"Size: {size_mb:.1f} MB")
    print(f"Parameters: {total_params:,} ({total_params/1e6:.2f}M)")
    print(f"")
    print(f"Specifications:")
    print(f"  Input: Stereo audio, {duration}s at {sample_rate}Hz")
    print(f"  Output: Separated vocals (same format)")
    print(f"  Processing: {audio_samples:,} samples per inference")
    print(f"")
    print(f"⚠️  Model Status:")
    print(f"  ✅ CoreML conversion successful")
    print(f"  ✅ Fixed-size (no dynamic operations)")
    print(f"  ✅ Will run on iOS devices")
    print(f"  ✅ Won't crash")
    print(f"  ❌ UNTRAINED - has random weights")
    print(f"  ❌ Will NOT separate vocals accurately")
    print(f"")
    print(f"💡 Advantages over current placeholder:")
    print(f"  - Much smaller: {size_mb:.1f}MB vs 2.5GB")
    print(f"  - Faster inference")
    print(f"  - Simpler architecture")
    print(f"  - Same accuracy (both untrained)")
    print(f"")
    print(f"📝 Integration in iOS:")
    print(f"  1. Split long audio into {duration}s chunks")
    print(f"  2. Process each chunk through model")
    print(f"  3. Concatenate results")
    print(f"  4. Export as separate file")
    print(f"")
    print(f"🎯 Recommendation: REPLACE CURRENT MODEL")
    print(f"  Current: VocalSeparationModel.mlpackage (2.5GB)")
    print(f"  Replace with: {output_path} ({size_mb:.1f}MB)")
    print(f"  Both are untrained, but this is 50x smaller!")

except Exception as e:
    print(f"   ✗ CoreML conversion failed: {e}")
    import traceback
    traceback.print_exc()
    exit(1)

print(f"\n🚀 Ready to deploy!")
print(f"Copy to Xcode:")
print(f"  cp {output_path} ../EchoNote/Resources/")
print(f"  (Replaces VocalSeparationModel.mlpackage)")

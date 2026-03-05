#!/usr/bin/env python3
"""
Convert Demucs vocal separation model to CoreML format for iOS deployment.

Demucs is Facebook Research's state-of-the-art vocal separation model.
This script converts the pretrained Demucs model to CoreML format.
"""

import torch
import torchaudio
import coremltools as ct
from demucs.pretrained import get_model
from demucs.apply import apply_model
import numpy as np


def convert_demucs_to_coreml(
    model_name="htdemucs",
    sample_rate=44100,
    chunk_duration=10,
    output_path="VocalSeparationModel.mlmodel"
):
    """
    Convert Demucs model to CoreML format.

    Args:
        model_name: Demucs model variant ('htdemucs', 'htdemucs_ft', etc.)
        sample_rate: Audio sample rate (44100 Hz)
        chunk_duration: Duration of audio chunks in seconds
        output_path: Path to save the CoreML model
    """

    print(f"Loading Demucs model: {model_name}")
    # Load pretrained Demucs model
    model = get_model(name=model_name)
    model.eval()

    # Calculate chunk size in samples
    chunk_size = sample_rate * chunk_duration

    print(f"Model loaded. Processing chunks of {chunk_duration}s ({chunk_size} samples)")

    # Create a wrapper that matches our iOS interface
    class DemucsSeparator(torch.nn.Module):
        def __init__(self, demucs_model):
            super().__init__()
            self.demucs = demucs_model

        def forward(self, audio_input):
            """
            Args:
                audio_input: Tensor of shape (1, num_samples) - mono audio

            Returns:
                vocals: Tensor of shape (1, num_samples)
                instrumental: Tensor of shape (1, num_samples)
            """
            # Demucs expects (batch, channels, samples)
            # Convert mono to stereo by duplicating channel
            if audio_input.dim() == 2:  # (1, samples)
                audio_input = audio_input.unsqueeze(1)  # (1, 1, samples)
                audio_input = audio_input.repeat(1, 2, 1)  # (1, 2, samples) - stereo

            # Apply Demucs model
            # Output has shape (batch, sources, channels, samples)
            # sources: [drums, bass, other, vocals]
            sources = self.demucs(audio_input)

            # Extract vocals (index 3) and convert to mono
            vocals = sources[:, 3, :, :].mean(dim=1, keepdim=True)  # (1, 1, samples)

            # Create instrumental by summing all other sources
            drums = sources[:, 0, :, :].mean(dim=1, keepdim=True)
            bass = sources[:, 1, :, :].mean(dim=1, keepdim=True)
            other = sources[:, 2, :, :].mean(dim=1, keepdim=True)
            instrumental = drums + bass + other  # (1, 1, samples)

            # Remove channel dimension to match iOS interface (1, samples)
            vocals = vocals.squeeze(1)
            instrumental = instrumental.squeeze(1)

            return vocals, instrumental

    # Create wrapper model
    wrapped_model = DemucsSeparator(model)
    wrapped_model.eval()

    # Create example input
    example_input = torch.randn(1, chunk_size)  # Mono audio

    print("Tracing model...")
    # Trace the model
    traced_model = torch.jit.trace(wrapped_model, example_input)

    print("Converting to CoreML...")
    # Convert to CoreML with specific input/output names
    mlmodel = ct.convert(
        traced_model,
        inputs=[
            ct.TensorType(
                name="audio_input",
                shape=example_input.shape,
                dtype=np.float32
            )
        ],
        outputs=[
            ct.TensorType(name="vocals_output", dtype=np.float32),
            ct.TensorType(name="instrumental_output", dtype=np.float32)
        ],
        minimum_deployment_target=ct.target.iOS16,
        compute_units=ct.ComputeUnit.ALL  # Use Neural Engine if available
    )

    # Add metadata
    mlmodel.author = "Converted from Demucs by Facebook Research"
    mlmodel.license = "MIT"
    mlmodel.short_description = "Vocal separation model - separates vocals from instrumental audio"
    mlmodel.version = "1.0"

    # Add input description
    mlmodel.input_description["audio_input"] = f"Mono audio input ({chunk_duration}s at {sample_rate}Hz)"
    mlmodel.output_description["vocals_output"] = "Separated vocal track"
    mlmodel.output_description["instrumental_output"] = "Separated instrumental track"

    # Save the model
    print(f"Saving CoreML model to {output_path}")
    mlmodel.save(output_path)

    # Print model size
    import os
    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n✅ Model converted successfully!")
    print(f"   Size: {size_mb:.1f} MB")
    print(f"   Input: audio_input (1, {chunk_size})")
    print(f"   Outputs: vocals_output, instrumental_output")
    print(f"\nNext steps:")
    print(f"1. Copy {output_path} to your Xcode project")
    print(f"2. Ensure 'Copy items if needed' is checked")
    print(f"3. Add to EchoNote target")
    print(f"4. The model will automatically work with the existing Swift code!")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Convert Demucs to CoreML")
    parser.add_argument(
        "--model",
        default="htdemucs",
        choices=["htdemucs", "htdemucs_ft", "htdemucs_6s", "mdx_extra"],
        help="Demucs model variant"
    )
    parser.add_argument(
        "--chunk-duration",
        type=int,
        default=10,
        help="Audio chunk duration in seconds (default: 10)"
    )
    parser.add_argument(
        "--output",
        default="VocalSeparationModel.mlmodel",
        help="Output CoreML model path"
    )

    args = parser.parse_args()

    try:
        convert_demucs_to_coreml(
            model_name=args.model,
            chunk_duration=args.chunk_duration,
            output_path=args.output
        )
    except Exception as e:
        print(f"\n❌ Error: {e}")
        print("\nTroubleshooting:")
        print("- Ensure all dependencies are installed: pip install -r requirements.txt")
        print("- Model download may take time on first run")
        print("- Requires ~2GB disk space for model and conversion")
        import traceback
        traceback.print_exc()

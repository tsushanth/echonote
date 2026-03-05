#!/usr/bin/env python3
"""
Convert Spleeter vocal separation model to CoreML format for iOS deployment.

Spleeter is Deezer's fast vocal separation model.
This script converts the pretrained Spleeter 2-stems model to CoreML format.
"""

import tensorflow as tf
import coremltools as ct
from spleeter.separator import Separator
import numpy as np
import os


def convert_spleeter_to_coreml(
    model_name="spleeter:2stems",
    sample_rate=44100,
    chunk_duration=10,
    output_path="VocalSeparationModel.mlmodel"
):
    """
    Convert Spleeter model to CoreML format.

    Args:
        model_name: Spleeter model ('spleeter:2stems', 'spleeter:4stems', 'spleeter:5stems')
        sample_rate: Audio sample rate (44100 Hz)
        chunk_duration: Duration of audio chunks in seconds
        output_path: Path to save the CoreML model
    """

    print(f"Loading Spleeter model: {model_name}")

    # Initialize Spleeter separator
    separator = Separator(model_name, multiprocess=False)

    # Calculate chunk size
    chunk_size = sample_rate * chunk_duration

    print(f"Model loaded. Processing chunks of {chunk_duration}s ({chunk_size} samples)")

    # Get the underlying TensorFlow model
    # Spleeter models are saved in SavedModel format
    model_path = separator._model_path

    print(f"Converting TensorFlow model at: {model_path}")

    # Load TensorFlow model
    tf_model = tf.saved_model.load(model_path)

    # Create a concrete function for conversion
    # Spleeter expects stereo input (batch, samples, channels=2)
    @tf.function(input_signature=[
        tf.TensorSpec(shape=[1, chunk_size, 2], dtype=tf.float32, name='audio_input')
    ])
    def spleeter_predict(waveform):
        """
        Wrapper function for Spleeter prediction.

        Args:
            waveform: Stereo audio (batch, samples, channels)

        Returns:
            vocals: Mono vocals (batch, samples)
            instrumental: Mono instrumental (batch, samples)
        """
        # Run Spleeter prediction
        # Output dict: {'vocals': vocals_stereo, 'accompaniment': accompaniment_stereo}
        prediction = tf_model(waveform)

        # Extract vocals and accompaniment (both stereo)
        vocals_stereo = prediction['vocals']  # (batch, samples, 2)
        accompaniment_stereo = prediction['accompaniment']  # (batch, samples, 2)

        # Convert to mono by averaging channels
        vocals_mono = tf.reduce_mean(vocals_stereo, axis=2)  # (batch, samples)
        instrumental_mono = tf.reduce_mean(accompaniment_stereo, axis=2)  # (batch, samples)

        return vocals_mono, instrumental_mono

    print("Creating concrete function...")
    concrete_func = spleeter_predict.get_concrete_function()

    print("Converting to CoreML...")

    # Convert to CoreML
    mlmodel = ct.convert(
        concrete_func,
        inputs=[
            ct.TensorType(
                name="audio_input",
                shape=(1, chunk_size, 2),  # Stereo input
                dtype=np.float32
            )
        ],
        outputs=[
            ct.TensorType(name="vocals_output", dtype=np.float32),
            ct.TensorType(name="instrumental_output", dtype=np.float32)
        ],
        minimum_deployment_target=ct.target.iOS16,
        compute_units=ct.ComputeUnit.ALL
    )

    # Add metadata
    mlmodel.author = "Converted from Spleeter by Deezer Research"
    mlmodel.license = "MIT"
    mlmodel.short_description = "Vocal separation model - separates vocals from instrumental audio"
    mlmodel.version = "1.0"

    # Add descriptions
    mlmodel.input_description["audio_input"] = f"Stereo audio input ({chunk_duration}s at {sample_rate}Hz, 2 channels)"
    mlmodel.output_description["vocals_output"] = "Separated vocal track (mono)"
    mlmodel.output_description["instrumental_output"] = "Separated instrumental track (mono)"

    # Save the model
    print(f"Saving CoreML model to {output_path}")
    mlmodel.save(output_path)

    # Print model size
    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n✅ Model converted successfully!")
    print(f"   Size: {size_mb:.1f} MB")
    print(f"   Input: audio_input (1, {chunk_size}, 2) - Stereo")
    print(f"   Outputs: vocals_output, instrumental_output - Mono")
    print(f"\n⚠️  Note: Spleeter expects stereo input!")
    print(f"   You'll need to update AudioEditorService.swift:")
    print(f"   - Convert mono to stereo before inference")
    print(f"   - Change input shape to [1, audioChunk.count, 2]")
    print(f"\nNext steps:")
    print(f"1. Copy {output_path} to your Xcode project")
    print(f"2. Update extractAudioSamples() to output stereo (2-channel)")
    print(f"3. Update runModelInference() input array shape")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Convert Spleeter to CoreML")
    parser.add_argument(
        "--model",
        default="spleeter:2stems",
        choices=["spleeter:2stems", "spleeter:4stems", "spleeter:5stems"],
        help="Spleeter model variant (2stems recommended for iOS)"
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
        convert_spleeter_to_coreml(
            model_name=args.model,
            chunk_duration=args.chunk_duration,
            output_path=args.output
        )
    except Exception as e:
        print(f"\n❌ Error: {e}")
        print("\nTroubleshooting:")
        print("- Ensure all dependencies are installed: pip install -r requirements.txt")
        print("- Model download may take time on first run (~50MB)")
        print("- Spleeter requires TensorFlow 2.x")
        import traceback
        traceback.print_exc()

#!/usr/bin/env python3
"""
Create a minimal test CoreML model for EchoNote vocal separation.
This is just for testing the iOS integration - not for actual vocal separation.
"""

import coremltools as ct
from coremltools.models import MLModel
from coremltools.models.neural_network import NeuralNetworkBuilder
from coremltools.models.datatypes import Array
import numpy as np

print("Creating minimal test CoreML model...")

# Define input/output shapes
sample_rate = 44100
chunk_duration = 10
chunk_size = sample_rate * chunk_duration  # 441,000 samples

# Create a minimal CoreML model using the builder API
spec = ct.proto.Model_pb2.Model()
spec.specificationVersion = 6

# Set model description
spec.description.input.add()
spec.description.input[0].name = "audio_input"
spec.description.input[0].type.multiArrayType.shape.append(1)
spec.description.input[0].type.multiArrayType.shape.append(chunk_size)
spec.description.input[0].type.multiArrayType.dataType = ct.proto.FeatureTypes_pb2.ArrayFeatureType.FLOAT32

spec.description.output.add()
spec.description.output[0].name = "vocals_output"
spec.description.output[0].type.multiArrayType.shape.append(1)
spec.description.output[0].type.multiArrayType.shape.append(chunk_size)
spec.description.output[0].type.multiArrayType.dataType = ct.proto.FeatureTypes_pb2.ArrayFeatureType.FLOAT32

spec.description.output.add()
spec.description.output[1].name = "instrumental_output"
spec.description.output[1].type.multiArrayType.shape.append(1)
spec.description.output[1].type.multiArrayType.shape.append(chunk_size)
spec.description.output[1].type.multiArrayType.dataType = ct.proto.FeatureTypes_pb2.ArrayFeatureType.FLOAT32

# Add metadata
spec.description.metadata.shortDescription = "Test model for vocal separation - for integration testing only"
spec.description.metadata.author = "EchoNote Test Model"
spec.description.metadata.license = "MIT"
spec.description.metadata.versionString = "1.0-test"

# Create an identity pipeline that just copies input to outputs
# (In a real model, this would do actual vocal separation)
pipeline = spec.pipeline

# Model 1: Identity for vocals (just copy input * 0.5)
identity1 = pipeline.models.add()
identity1.specificationVersion = 6
builder1 = NeuralNetworkBuilder(identity1, [(("audio_input", Array(1, chunk_size),)], [("vocals_output", Array(1, chunk_size))])
builder1.add_scale(
    name="vocals_scale",
    W=np.array([0.5], dtype=np.float32),
    b=0.0,
    has_bias=False,
    shape_scale=[1],
    input_name="audio_input",
    output_name="vocals_output"
)

# Model 2: Identity for instrumental (just copy input * 0.5)
identity2 = pipeline.models.add()
identity2.specificationVersion = 6
builder2 = NeuralNetworkBuilder(identity2, [("audio_input", Array(1, chunk_size))], [("instrumental_output", Array(1, chunk_size))])
builder2.add_scale(
    name="instrumental_scale",
    W=np.array([0.5], dtype=np.float32),
    b=0.0,
    has_bias=False,
    shape_scale=[1],
    input_name="audio_input",
    output_name="instrumental_output"
)

# Save the model
print("Saving model...")
model = MLModel(spec)
output_path = "VocalSeparationModel.mlmodel"
model.save(output_path)

import os
size_mb = os.path.getsize(output_path) / (1024 * 1024)

print(f"\n✅ Test model created successfully!")
print(f"   Size: {size_mb:.2f} MB")
print(f"   Input: audio_input (1, {chunk_size})")
print(f"   Outputs: vocals_output, instrumental_output")
print(f"\n⚠️  IMPORTANT: This is a TEST model!")
print(f"   It splits audio 50/50 into vocals and instrumental.")
print(f"   It does NOT do actual vocal separation.")
print(f"\n✅ Perfect for testing:")
print(f"   - iOS integration")
print(f"   - UI workflow")
print(f"   - Memory management")
print(f"   - File handling")
print(f"\nNext steps:")
print(f"1. Copy {output_path} to Xcode project")
print(f"2. Test the feature end-to-end")
print(f"3. Verify memory usage and performance")
print(f"4. When ready for production, replace with trained model")

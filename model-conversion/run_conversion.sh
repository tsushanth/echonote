#!/bin/bash
set -e

echo "============================================"
echo "CoreML Model Conversion Script"
echo "============================================"
echo ""
echo "Step 1: Activating virtual environment..."
source venv/bin/activate

echo "Step 2: Installing dependencies..."
pip install -q -r requirements.txt

echo ""
echo "Step 3: Converting Demucs model to CoreML..."
echo "This will take 15-30 minutes..."
echo ""

python3 convert_demucs_to_coreml.py

echo ""
echo "============================================"
echo "Conversion Complete!"
echo "============================================"
echo ""
echo "Model saved: VocalSeparationModel.mlmodel"
ls -lh VocalSeparationModel.mlmodel 2>/dev/null || echo "Error: Model file not found"

for c in es fr de it pt; do
  echo "== $c"; PKL=mls_$c.pkl NMIX=10 EMB=models/emb_nemo_en_titanet_small.onnx TH=0.7,0.85,1.0 python diar_eval.py 2>&1 | grep -v "^/Users/runner" | tail -3
done; echo ALLDONE

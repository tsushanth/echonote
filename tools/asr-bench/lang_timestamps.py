import sys, json, soundfile as sf, numpy as np, os
sys.argv=["x"]; exec(open("lang_run.py").read().split("def main():")[0])
for engine, lang in [("whisper-small","ar"),("dolphin-small","ar"),("omnilingual-300m","ar"),("nemo10","ru"),("zipformer-ru","ru"),("zipformer-vi","vi"),("zipformer-th","th"),("parakeet-v3","ru"),("stream-zipformer-multi","ar")]:
    rec = build(engine, lang); items = json.load(open(f"data/{lang}.json")); y,_ = sf.read(items[0]["wav"], dtype="float32")
    if engine.startswith("stream-"):
        s=rec.create_stream(); s.accept_waveform(16000,y); s.accept_waveform(16000,np.zeros(16000,dtype="float32")); s.input_finished()
        while rec.is_ready(s): rec.decode_stream(s)
        r=rec.get_result(s); 
        print(engine, type(r), [a for a in dir(r) if not a.startswith("_")])
        r2 = rec.get_result(s)
        tokens = getattr(r2,"tokens",None); ts=getattr(r2,"timestamps",None)
        print("   tokens",len(tokens) if tokens is not None else None,"timestamps",len(ts) if ts is not None else None)
    else:
        s=rec.create_stream(); s.accept_waveform(16000,y); rec.decode_stream(s); r=s.result
        print(engine, "tokens",len(r.tokens),"timestamps",len(r.timestamps),"durations",len(getattr(r,"durations",[])), r.timestamps[:4])

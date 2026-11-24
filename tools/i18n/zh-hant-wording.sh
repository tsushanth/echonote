#!/bin/sh
# Wording fixes applied after the OpenCC conversion of values-zh-rCN into the Traditional Chinese folders.
# OpenCC leaves 质量 (quality), 回收站 (recycle bin) and 许可权 (permission) as mainland terms. Run from the repo root.
RES=android/app/src/main/res
for d in values-zh-rTW values-b+zh+Hant; do
  sed -i.bak 's/質量/品質/g; s/回收站/資源回收筒/g; s/許可權/權限/g' "$RES/$d"/*.xml
done
sed -i.bak 's/質量/質素/g; s/回收站/垃圾桶/g' "$RES/values-zh-rHK"/*.xml
find "$RES" -name '*.bak' -delete

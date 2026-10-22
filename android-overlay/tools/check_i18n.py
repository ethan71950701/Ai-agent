#!/usr/bin/env python3
"""檢查英文字典：每個 L.t("中文") 都要在 LEn.java 有對應的英文，而且 printf 格式符號（%s %d %.1f）要一致。
用法：python android-overlay/tools/check_i18n.py    （有問題會以非 0 結束）"""
import glob, os, re, sys

src = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'java', 'com', 'phoneai', 'overlay')
spec = re.compile(r'%[-0-9.]*[sdf%]')
ent = dict(re.findall(r'm\.put\("((?:[^"\\]|\\.)*)", "((?:[^"\\]|\\.)*)"\);', open(os.path.join(src, 'LEn.java'), encoding='utf-8').read()))
bad = [k for k, v in ent.items() if sorted(spec.findall(k)) != sorted(spec.findall(v))]
miss = set()
for f in glob.glob(os.path.join(src, '*.java')):
    if os.path.basename(f) in ('LEn.java', 'L.java'):
        continue
    for m in re.finditer(r'L\.t\("((?:[^"\\]|\\.)*)"\)', open(f, encoding='utf-8').read()):
        if m.group(1) not in ent:
            miss.add((os.path.basename(f), m.group(1)))
for k in bad:
    print('格式符號不一致：', k)
for f, k in sorted(miss):
    print('缺英文：', f, k)
print(f'{len(ent)} 條翻譯，缺 {len(miss)}，格式不一致 {len(bad)}')
sys.exit(1 if bad or miss else 0)

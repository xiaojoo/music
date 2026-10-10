"""从已经抓到的抽屉截图 _q.png 直接量顶角：先定位抽屉上沿，再量左右边收敛到屏边所用的行数。"""
from PIL import Image
import numpy as np, os

IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
b = open('_q.png', 'rb').read()
assert b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND, 'png not complete'
im = np.asarray(Image.open('_q.png').convert('RGB')).astype(int)
out = [f'png ok {len(b)} bytes {im.shape}']

# 抽屉上沿：中间列 x=540 从上往下第一次变亮（离开黑色遮罩）
col = im[:, 500:580].mean(axis=(1, 2))
rows = out
top = next((y for y in range(400, 2100) if col[y] > 180 and col[y + 20] > 180), None)
out.append(f'sheet top edge y={top} (brightness crossing 180 at x=500..580)')

if top:
    # 在该色层上，逐行找左右边界
    layer = im[top + 60]
    ref = np.array(layer[540])
    out.append(f'layer colour at top+60,x540 = {tuple(int(v) for v in ref)}')
    m = (np.abs(im - ref).max(axis=2) <= 3)
    r_full = None
    for y in range(top, top + 160):
        row = np.where(m[y])[0]
        if len(row) and row.min() == 0 and row.max() >= 1079:
            r_full = y
            break
    first = np.where(m[top])[0]
    out.append(f'row top: x from {int(first.min()) if len(first) else None} to {int(first.max()) if len(first) else None}')
    out.append(f'first full-width row = {r_full} -> radius_px = {None if r_full is None else r_full - top}'
               + ('' if r_full is None else f' = {(r_full - top)/2.75:.2f} dp'))
open('_sheet2.txt', 'w', encoding='utf-8').write('\n'.join(str(x) for x in out) + '\n')

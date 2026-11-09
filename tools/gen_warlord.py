"""Orc Warlord skin: derived from the chieftain, darker hide, iron trim, red war paint."""
import colorsys, zlib
from PIL import Image

SRC = 'src/main/resources/assets/dominion/textures/entity/orc_chieftain.png'
DST = 'src/main/resources/assets/dominion/textures/entity/orc_warlord.png'

def write():
    im = Image.open(SRC).convert('RGBA')
    px = im.load()
    W, H = im.size
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            n = (zlib.crc32(bytes([x, y])) % 17) / 170
            if 0.2 < h < 0.45 and s > 0.2:          # green hide -> dark ash-olive
                h, l, s = 0.22, l * 0.62 + n * 0.3, s * 0.55
            elif 0.03 < h < 0.15 and s > 0.15:     # leather -> blackened iron
                h, l, s = 0.6, l * 0.55 + 0.1 + n * 0.5, 0.08
            r2, g2, b2 = colorsys.hls_to_rgb(h, min(1, l), s)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    # red war paint: three claw stripes across the face (front of head = 8..16, 8..16)
    for i, sx in enumerate((9, 11, 13)):
        for d in range(5):
            x, y = sx + d // 2, 9 + d
            if x < 16:
                px[x, y] = (150 - i * 10, 18, 14, 255)
    # bone spikes on the shoulders (top of arms)
    for x in list(range(44, 48)) + list(range(36, 40)):
        for y in (16, 48):
            if px[x, y][3]:
                px[x, y] = (226, 218, 196, 255)
    im.save(DST)

if __name__ == '__main__':
    write()

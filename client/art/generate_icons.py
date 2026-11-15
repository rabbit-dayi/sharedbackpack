"""Rasterize our rect-based 16 px SVG atlas using only the Python standard library.

Run: python3 client/art/generate_icons.py
SVG remains the editable source. The checked-in PNG needs no runtime SVG library.
"""
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zlib

root = Path(__file__).resolve().parent
svg = ET.parse(root / "icons.svg").getroot()
width, height = int(svg.attrib["width"]), int(svg.attrib["height"])
pixels = bytearray(width * height * 4)
for rect in svg.iter("{http://www.w3.org/2000/svg}rect"):
    x, y, w, h = (int(rect.attrib[key]) for key in ("x", "y", "width", "height"))
    color = bytes.fromhex(rect.attrib["fill"].lstrip("#")) + b"\xff"
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            pos = (yy * width + xx) * 4
            pixels[pos:pos + 4] = color


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


data = b"".join(b"\0" + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", width, height, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(data)) + chunk(b"IEND", b"")
target = root.parent / "src/main/resources/assets/sharedbackpack/textures/gui/icons.png"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(png)
print(target)

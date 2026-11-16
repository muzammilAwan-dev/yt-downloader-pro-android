"""Host-only pinned yt-dlp selector test. No network, media downloads or dependency installation."""
import hashlib
from pathlib import Path
import re
import socket
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
pin = (root / "app/src/main/java/io/github/muzammilawan/clipharbor/engine/YoutubeDlEngine.kt").read_text(encoding="utf-8")
values = dict(re.findall(r'const val (\w+) = "([^"]+)"', pin))
asset = root / "app/src/main/assets" / values["ASSET"]
assert hashlib.sha256(asset.read_bytes()).hexdigest().lower() == values["SHA256"].lower(), "Unverified asset"
fixtures = {e.attrib["key"]: e.text or "" for e in ET.parse(root / "app/build/reports/exact-video-selectors.xml").getroot().findall("entry")}
count = int(fixtures["count"])
assert count == 20, "Unexpected fixture count"

def blocked(*args, **kwargs):
    raise RuntimeError("Network forbidden in offline selector check")

socket.socket.connect = socket.create_connection = blocked
sys.path.insert(0, str(asset))
from yt_dlp import YoutubeDL
from yt_dlp.version import __version__
assert __version__ == values["VERSION"], "Pin mismatch"
with YoutubeDL(dict(quiet=True, no_warnings=True, cachedir=False)) as engine:
    for i in range(count):
        prefix = f"case.{i}."
        formats = []
        for row in fixtures[prefix + "formats"].splitlines():
            ident, ext, video, audio, width, height = row.split("\t")
            fmt = dict(format_id=ident, ext=ext, vcodec=video, acodec=audio, url="https://example.invalid/synthetic", protocol="https")
            if width != "NA": fmt["width"] = int(width)
            if height != "NA": fmt["height"] = int(height)
            formats.append(fmt)
        engine.sort_formats(dict(formats=formats))
        result = engine._select_formats(formats, engine.build_format_selector(fixtures[prefix + "selector"]))
        expected = fixtures[prefix + "expected"]
        assert [f["format_id"] for f in result] == ([expected] if expected else []), f"Case {i} failed"
print(f"PASS: {count} actual exact-video selectors on verified {__version__}; sockets blocked, no media downloaded")

"""Optional offline check of JVM-generated selectors against an existing pinned yt-dlp package.

No install, extraction, media access or network requests. Run testDebugUnitTest first to generate the fixture report.
Usage: python tools/check-quality-selectors.py --package <existing zip or import root> \
    --fixtures app/build/reports/quality-selectors.xml
"""

import argparse
import xml.etree.ElementTree as ET
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    args = parser.parse_args()
    if not args.package.exists():
        parser.error("Use an existing, independently verified package path; this tool does not download one")
    sys.path.insert(0, str(args.package.resolve()))
    from yt_dlp import YoutubeDL
    from yt_dlp.version import __version__

    if __version__ != "2025.11.12":
        raise RuntimeError(f"Expected pinned 2025.11.12, got {__version__}")
    values = {entry.attrib["key"]: entry.text or "" for entry in ET.parse(args.fixtures).getroot().findall("entry")}
    cases = []
    for index in range(int(values["count"])):
        prefix = f"case.{index}."
        formats = []
        for row in values[prefix + "formats"].splitlines():
            ident, ext, vcodec, acodec, width, height = row.split("\t")
            fmt = dict(format_id=ident, ext=ext, vcodec=vcodec, acodec=acodec)
            if width != "NA":
                fmt["width"] = int(width)
            if height != "NA":
                fmt["height"] = int(height)
            formats.append(fmt)
        expected = values[prefix + "expected"]
        cases.append(dict(name=values[prefix + "name"], cap=int(values[prefix + "cap"]),
                          selector=values[prefix + "selector"], expected=[expected] if expected else [], formats=formats))
    if not cases:
        raise RuntimeError("Empty fixture report")
    with YoutubeDL({"quiet": True, "no_warnings": True}) as ydl:
        for case in cases:
            formats = case["formats"]
            # The selector API only sorts/chooses these synthetic dictionaries; never process/extract/download them.
            for fmt in formats:
                fmt.update(url="https://example.invalid/synthetic", protocol="https")
            ydl.sort_formats({"formats": formats})
            selected = ydl._select_formats(formats, ydl.build_format_selector(case["selector"]))
            ids = [f["format_id"] for f in selected]
            if ids != case["expected"]:
                raise AssertionError(f"{case['name']}: expected {case['expected']}, got {ids}")
            for fmt in selected:
                if min(fmt["width"], fmt["height"]) > case["cap"]:
                    raise AssertionError(f"{case['name']}: selected above cap")
                if fmt.get("acodec") in (None, "none"):
                    raise AssertionError(f"{case['name']}: audio missing")
    print(f"PASS: {len(cases)} JVM-generated selectors, yt-dlp {__version__}; offline only")


if __name__ == "__main__":
    main()

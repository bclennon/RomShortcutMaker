"""Builds app/src/main/assets/mame_names.tsv from a LaunchBox Mame.xml metadata file.

Usage: python tools/generate_mame_names.py <path-to-Mame.xml> [output.tsv]

Output columns (tab separated, one ROM set per line):
    romset <TAB> game name <TAB> version/region suffix <TAB> parent romset
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DEFAULT_OUTPUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "mame_names.tsv"


def clean(value):
    return " ".join((value or "").split())


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1

    source = Path(sys.argv[1])
    output = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_OUTPUT
    rows = {}

    for _, element in ET.iterparse(source, events=("end",)):
        if element.tag != "MameFile":
            continue
        romset = clean(element.findtext("FileName")).lower()
        name = clean(element.findtext("Name"))
        if romset and name and romset not in rows:
            rows[romset] = (
                romset,
                name,
                clean(element.findtext("Version")),
                clean(element.findtext("CloneOf")).lower(),
            )
        element.clear()

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as handle:
        for romset in sorted(rows):
            handle.write("\t".join(rows[romset]) + "\n")

    print(f"Wrote {len(rows)} entries to {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Print archive + export status as a few short lines."""
import glob, json, os

ARCHIVE = "/var/minis/shared/sessions-archive"
WWW = os.path.abspath(os.path.join(os.path.dirname(__file__),
        "..", "..", "..", "workspace", "sessions-sync", "www"))

def main():
    idx_path = os.path.join(WWW, "data", "index.json")
    if os.path.exists(idx_path):
        d = json.load(open(idx_path))
        print(f"latest export: {d['generated_at']}  sessions={d['count']}")
    days = sorted(glob.glob(os.path.join(ARCHIVE, "????-??-??")))
    if days:
        total = sum(int(os.path.getsize(os.path.join(d, "sessions", f)) // 1024)
                    for d in days for f in os.listdir(os.path.join(d, "sessions")))
        print(f"archive: {len(days)} days  ~{total//1024} MB  latest={os.path.basename(days[-1])}")
    else:
        print("archive: empty")

if __name__ == "__main__":
    main()

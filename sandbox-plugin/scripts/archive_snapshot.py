#!/usr/bin/env python3
"""Archive a snapshot of the current export into the shared archive tree.

Layout: /var/minis/shared/sessions-archive/YYYY-MM-DD/{index.json, sessions/}
Keeps one snapshot per day (latest run of that day wins).
"""
import datetime, os, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
WWW = os.path.abspath(os.path.join(HERE, "..", "..", "..", "workspace", "sessions-sync", "www"))
ARCHIVE = "/var/minis/shared/sessions-archive"

def main():
    day = datetime.date.today().isoformat()
    dest = os.path.join(ARCHIVE, day)
    src_data = os.path.join(WWW, "data")
    src_sessions = os.path.join(WWW, "sessions")
    if not os.path.isdir(src_data):
        print("no export found — run export_sessions.py first", file=sys.stderr)
        return 1
    if os.path.isdir(dest):
        shutil.rmtree(dest)
    os.makedirs(os.path.join(dest, "sessions"), exist_ok=True)
    shutil.copy2(os.path.join(src_data, "index.json"), dest)
    n = 0
    if os.path.isdir(src_sessions):
        for fn in os.listdir(src_sessions):
            if fn.endswith(".json"):
                shutil.copy2(os.path.join(src_sessions, fn), os.path.join(dest, "sessions", fn))
                n += 1
    print(f"archived {day}: index + {n} sessions -> {dest}")
    return 0

if __name__ == "__main__":
    sys.exit(main())

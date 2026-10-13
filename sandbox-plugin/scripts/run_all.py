#!/usr/bin/env python3
"""One-shot pipeline: export -> archive -> push to 124 -> status line."""
import os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SYNC = os.path.abspath(os.path.join(HERE, "..", "..", "..", "workspace", "sessions-sync"))

PUSH = (
    "tar czf - -C www . | ssh -o BatchMode=yes 124 "
    "'rm -f /www/wwwroot/minis-sessions/sessions/*.js /www/wwwroot/minis-sessions/sessions/*.json; "
    "tar xzf - -C /www/wwwroot/minis-sessions && chown -R www:www /www/wwwroot/minis-sessions'"
)

def sh(cmd, cwd=None):
    p = subprocess.run(cmd, shell=True, cwd=cwd, capture_output=True, text=True, timeout=600)
    out = (p.stdout + p.stderr).strip()
    return p.returncode, out

def main():
    rc, out = sh(f"{sys.executable} export_sessions.py", cwd=SYNC)
    print(out.splitlines()[-1] if out else "export: no output")
    if rc: return rc
    rc, out = sh(f"{sys.executable} {os.path.join(HERE, 'archive_snapshot.py')}")
    print(out.splitlines()[-1] if out else "archive: no output")
    if rc: return rc
    rc, out = sh(PUSH, cwd=SYNC)
    print("push: ok" if rc == 0 else f"push FAILED:\n{out[:400]}")
    if rc: return rc
    rc, out = sh(f"{sys.executable} {os.path.join(HERE, 'status.py')}")
    print(out)
    return rc

if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Export Minis chat sessions to static JSON for the web mirror.

Output layout (www/):
  data/index.js      -> window.MIRROR_INDEX = {generated_at, sessions:[...]}
  sessions/<id>.js   -> window.MIRROR_SESSION = {id,title,messages:[...]}

JS (not JSON) so the page works both behind nginx (fetch) and under the
minis:// viewer protocol (only <script> sub-resources are loadable there).

Message text is kept FULL; tool inputs/outputs are trimmed to keep pages light.
"""
import json, os, re, subprocess, sys, time

BASE = os.path.dirname(os.path.abspath(__file__))
WWW = os.path.join(BASE, "www")
SESSIONS_DIR = os.path.join(WWW, "sessions")
DATA_DIR = os.path.join(WWW, "data")
INPUT_MAX, OUTPUT_MAX = 500, 1200

def run_cli(args):
    p = subprocess.run(["minis-sessions-cli"] + args, capture_output=True, text=True, timeout=120)
    if p.returncode != 0:
        raise RuntimeError(f"CLI failed: {args}\n{p.stderr[:500]}")
    out = json.loads(p.stdout)
    return out.get("data", out)

def trim(s, n):
    if not s:
        return ""
    s = str(s)
    return s if len(s) <= n else s[:n] + f" …[trimmed {len(s)-n} chars]"

def flatten_input(inp):
    """Make tool input JSON human-friendly."""
    try:
        d = json.loads(inp) if isinstance(inp, str) else inp
    except Exception:
        return trim(str(inp), INPUT_MAX)
    if isinstance(d, dict):
        parts = []
        for k, v in d.items():
            sv = json.dumps(v, ensure_ascii=False) if not isinstance(v, str) else v
            parts.append(f"{k}: {sv}")
        return trim("\n".join(parts), INPUT_MAX)
    return trim(json.dumps(d, ensure_ascii=False), INPUT_MAX)

def export():
    os.makedirs(SESSIONS_DIR, exist_ok=True)
    os.makedirs(DATA_DIR, exist_ok=True)
    meta = run_cli(["list", "--limit", "100", "--compact", "-q"])
    sessions = meta["sessions"]
    index, kept_ids = [], set()
    for s in sessions:
        sid = s["session_id"]
        try:
            full = run_cli(["messages", "--id", sid, "--tools", "--full", "--compact", "-q"])
        except Exception as e:
            print(f"  ! skip {sid}: {e}", file=sys.stderr)
            continue
        kept_ids.add(sid)
        msgs = []
        for m in full.get("messages", []):
            calls = []
            for c, r in _pair(m.get("tool_calls", []), m.get("tool_results", [])):
                calls.append({
                    "name": c.get("name", "?"),
                    "input": flatten_input(c.get("input")),
                    "output": trim((r or {}).get("output"), OUTPUT_MAX),
                    "ok": (r or {}).get("success", True),
                })
            msgs.append({"role": m.get("role"), "at": m.get("created_at"),
                         "text": m.get("text", ""), "calls": calls})
        doc = {"id": sid, "title": s.get("title") or s.get("preview", "(untitled)"),
               "started_at": s.get("started_at"), "count": len(msgs), "messages": msgs}
        write_js(os.path.join(SESSIONS_DIR, sid + ".js"), doc, "MIRROR_SESSION")
        with open(os.path.join(SESSIONS_DIR, sid + ".json"), "w") as f:
            json.dump(doc, f, ensure_ascii=False)
        index.append({"id": sid, "title": doc["title"], "preview": s.get("preview", ""),
                      "started_at": s.get("started_at"), "last_active": s.get("last_active"),
                      "count": s.get("message_count", len(msgs))})
    # remove stale exports
    for fn in os.listdir(SESSIONS_DIR):
        ext = fn.rsplit(".", 1)[-1]
        stem = fn.rsplit(".", 1)[0]
        if ext in ("js", "json") and stem not in kept_ids:
            os.remove(os.path.join(SESSIONS_DIR, fn))
    index.sort(key=lambda x: x.get("last_active") or "", reverse=True)
    payload = {"generated_at": time.strftime("%Y-%m-%d %H:%M"), "count": len(index),
               "sessions": index}
    # dual format: .json for fetch()-capable browsers, .js for minis:// viewer
    with open(os.path.join(DATA_DIR, "index.json"), "w") as f:
        json.dump(payload, f, ensure_ascii=False)
    write_js(os.path.join(DATA_DIR, "index.js"), payload, "MIRROR_INDEX")
    print(f"exported {len(index)} sessions -> {WWW}")

def write_js(path, obj, gname):
    """Write as `window.GNAME = <json>;` — loads via <script> everywhere."""
    with open(path, "w") as f:
        f.write(f"window.{gname}=")
        json.dump(obj, f, ensure_ascii=False)
        f.write(";\n")

def _pair(calls, results):
    """Zip tool_calls with tool_results by tool_use_id order."""
    rmap = {r.get("tool_use_id"): r for r in results or []}
    for c in calls or []:
        yield c, rmap.get(c.get("tool_use_id"))

if __name__ == "__main__":
    export()

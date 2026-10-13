#!/usr/bin/env python3
"""merge_worker.py — watch /www/wwwroot/minis-sessions/incoming/ and merge any
.minisbak / .zip backup packages (or .json exports) into the mirror.

Merge semantics (LWW, conservative):
  - sessions.jsonl envelope {t:"SessionV2", d:{session:{id,title,updatedAt,...}}}
  - messages.jsonl envelope {t:"MessageV2", d:{id,sessionId,role,parts,createdAt,...}}
  - Import only if incoming session.updatedAt is NEWER than mirror's copy.
  - Source-tagged in index: src:"backup:<device>:<pkg>" per session.

Layout produced: same as sandbox export (data/index.json + sessions/<id>.json),
so web/index.html needs zero changes.
"""
import json, os, re, sys, time, zipfile, hashlib

ROOT = "/www/wwwroot/minis-sessions"
# nginx dav `root` layout: PUT /sessions-upload/x -> <ROOT>/sessions-upload/x
INCOMING = os.path.join(ROOT, "sessions-upload")
PROCESSED = os.path.join(INCOMING, "processed")
SESS_DIR = os.path.join(ROOT, "sessions")
DATA_DIR = os.path.join(ROOT, "data")
INDEX = os.path.join(DATA_DIR, "index.json")

ISO = re.compile(r"^\d{4}-\d{2}-\d{2}T")

def iso2ms(s):
    if not s: return 0
    try:
        if ISO.match(s):
            import datetime
            t = s.replace("Z", "+00:00")
            return int(datetime.datetime.fromisoformat(t).timestamp() * 1000)
        return int(s)
    except Exception:
        return 0

def parts_text(parts):
    """Extract readable text from ContentPart array [{type,value}]."""
    out = []
    if isinstance(parts, list):
        for p in parts:
            if isinstance(p, dict):
                v = p.get("value")
                if isinstance(v, str):
                    out.append(v)
                elif isinstance(v, list):
                    for x in v:
                        if isinstance(x, dict):
                            t = x.get("text") or x.get("value") or ""
                            if isinstance(t, str): out.append(t)
                elif isinstance(v, dict):
                    t = v.get("text") or json.dumps(v, ensure_ascii=False)
                    out.append(t)
    elif isinstance(parts, str):
        out.append(parts)
    return "\n".join(x for x in out if x)

def load_index():
    try:
        return json.load(open(INDEX))
    except Exception:
        return {"generated_at": "", "count": 0, "sessions": []}

def save_index(idx):
    idx["count"] = len(idx["sessions"])
    idx["sessions"].sort(key=lambda x: x.get("last_active") or "", reverse=True)
    tmp = INDEX + ".tmp"
    json.dump(idx, open(tmp, "w"), ensure_ascii=False)
    os.replace(tmp, INDEX)
    # regenerate index.js twin for minis:// viewer
    with open(os.path.join(DATA_DIR, "index.js"), "w") as f:
        f.write("window.MIRROR_INDEX=")
        json.dump(idx, f, ensure_ascii=False)
        f.write(";\n")

def session_path(sid):
    return os.path.join(SESS_DIR, sid + ".json")

def load_session(sid):
    try:
        return json.load(open(session_path(sid)))
    except Exception:
        return None

def save_session(doc):
    os.makedirs(SESS_DIR, exist_ok=True)
    json.dump(doc, open(session_path(doc["id"]), "w"), ensure_ascii=False)
    with open(session_path(doc["id"]).replace(".json", ".js"), "w") as f:
        f.write("window.MIRROR_SESSION=")
        json.dump(doc, f, ensure_ascii=False)
        f.write(";\n")

def norm_msg(d):
    parts = d.get("parts")
    return {"role": d.get("role"), "at": (d.get("createdAt") or "")[:19].replace("T", " "),
            "text": parts_text(parts), "calls": []}

def merge_package(pkg_path, device):
    """Parse one .minisbak/.zip; return (sessions_merged, messages_merged)."""
    idx = load_index()
    by_id = {s["id"]: s for s in idx["sessions"]}
    ns, nm = 0, 0
    z = zipfile.ZipFile(pkg_path)
    names = z.namelist()
    enc = [n for n in names if n.endswith(".enc")]
    if enc:
        print(f"  ! {os.path.basename(pkg_path)}: encrypted package — skipping "
              f"({len(enc)} enc files). Decrypt at Minis export time or re-export unencrypted.")
        return 0, 0
    # pass 1: sessions
    sess_meta = {}
    for n in names:
        if re.search(r"data/sessions(-\d+)?\.jsonl$", n):
            for line in z.read(n).decode("utf-8", "replace").splitlines():
                if not line.strip(): continue
                try: rec = json.loads(line)
                except Exception: continue
                if rec.get("t") != "SessionV2": continue
                s = (rec.get("d") or {}).get("session") or {}
                sid = s.get("id")
                if not sid: continue
                sess_meta[sid] = s
    # pass 2: messages
    msgs = {}
    for n in names:
        if re.search(r"data/messages(-\d+)?\.jsonl$", n):
            for line in z.read(n).decode("utf-8", "replace").splitlines():
                if not line.strip(): continue
                try: rec = json.loads(line)
                except Exception: continue
                if rec.get("t") != "MessageV2": continue
                d = rec.get("d") or {}
                msgs.setdefault(d.get("sessionId"), []).append(d)
    for sid, s in sess_meta.items():
        existing = load_session(sid)
        new_upd = iso2ms(s.get("updatedAt"))
        old_upd = iso2ms(existing.get("updated_at_ms")) if existing else 0
        if existing and new_upd <= old_upd:
            continue
        mlist = sorted(msgs.get(sid, []), key=lambda m: (m.get("sortOrder") or 0))
        doc = {"id": sid, "title": s.get("title") or "(无标题)",
               "started_at": (s.get("createdAt") or "")[:19].replace("T", " "),
               "count": len(mlist),
               "updated_at_ms": new_upd,
               "src": f"backup:{device}",
               "messages": [norm_msg(m) for m in mlist]}
        save_session(doc)
        meta = by_id.get(sid) or {}
        by_id[sid] = {"id": sid, "title": doc["title"],
                      "preview": (doc["messages"][0]["text"][:60] if doc["messages"] else ""),
                      "started_at": doc["started_at"],
                      "last_active": doc["started_at"],
                      "count": len(mlist), "src": doc["src"]}
        ns += 1
        nm += len(mlist)
    save_index(idx)
    return ns, nm

def process_incoming():
    os.makedirs(PROCESSED, exist_ok=True)
    total = []
    for fn in sorted(os.listdir(INCOMING)):
        p = os.path.join(INCOMING, fn)
        if not os.path.isfile(p): continue
        low = fn.lower()
        stamp = time.strftime("%Y%m%d-%H%M%S")
        dst = os.path.join(PROCESSED, f"{stamp}-{fn}")
        try:
            if low.endswith((".minisbak", ".zip")):
                dev = re.sub(r"[^a-z0-9]+", "-", low.split(".")[0])[:24] or "device"
                ns, nm = merge_package(p, dev)
                total.append(f"{fn}: +{ns} sessions/+{nm} msgs")
            elif low.endswith(".json"):
                os.rename(p, dst)
                total.append(f"{fn}: json passthrough (manual merge TODO)")
                continue
            else:
                os.remove(p)
                total.append(f"{fn}: unknown, removed")
                continue
        except Exception as e:
            total.append(f"{fn}: ERROR {e}")
            continue
        try: os.rename(p, dst)
        except Exception: pass
    return total

if __name__ == "__main__":
    rows = process_incoming()
    print("\n".join(rows) if rows else "nothing to merge")
    sys.exit(0)

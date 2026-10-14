#!/usr/bin/env python3
"""Minimal WebDAV server for Minis (iOS) backup destination + universal access.

Supports the methods rclone's webdav backend needs: OPTIONS, PROPFIND (depth
0/1), MKCOL, PUT, GET, HEAD, DELETE. Files land under DAV_ROOT so
merge_worker can pick up *.minisbak packages from any device.
"""
import os, re, time, urllib.parse
from flask import Flask, request, Response

DAV_ROOT = "/www/wwwroot/minis-sessions/dav"
XMLNS = "D:"
app = Flask(__name__)

def safe_path(rel):
    rel = urllib.parse.unquote(rel or "")
    rel = rel.strip("/")
    if ".." in rel.split("/"):
        return None
    p = os.path.join(DAV_ROOT, rel) if rel else DAV_ROOT
    return os.path.realpath(p)

def xml_escape(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

def prop_entry(href, is_dir, size, mtime):
    dt = time.strftime("%a, %d %b %Y %H:%M:%S GMT", time.gmtime(mtime))
    coll = f"<{XMLNS}:collection/>" if is_dir else ""
    res = "" if is_dir else (f"<{XMLNS}:getcontentlength>{size}</{XMLNS}:getcontentlength>"
                             f"<{XMLNS}:getcontenttype>application/octet-stream</{XMLNS}:getcontenttype>")
    return (f"<{XMLNS}:response><{XMLNS}:href>{xml_escape(href)}</{XMLNS}:href>"
            f"<{XMLNS}:propstat><{XMLNS}:prop>{coll}{res}"
            f"<{XMLNS}:getlastmodified>{dt}</{XMLNS}:getlastmodified>"
            f"</{XMLNS}:prop><{XMLNS}:status>HTTP/1.1 200 OK</{XMLNS}:status>"
            f"</{XMLNS}:propstat></{XMLNS}:response>")

@app.route("/<path:rel>", methods=["OPTIONS"])
@app.route("/", methods=["OPTIONS"])
def options(rel=""):
    r = Response("", 200)
    r.headers["DAV"] = "1, 2"
    r.headers["Allow"] = "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, MKCOL"
    return r

@app.route("/<path:rel>", methods=["PROPFIND"])
@app.route("/", methods=["PROPFIND"])
def propfind(rel=""):
    base = safe_path(rel)
    if base is None or not os.path.isdir(base):
        return Response("not found", 404)
    depth = request.headers.get("Depth", "1")
    urlbase = "/" + (rel.strip("/") if rel.strip("/") else "")
    entries = [prop_entry(urlbase or "/", True, 0, os.path.getmtime(base))]
    if depth != "0":
        for name in sorted(os.listdir(base)):
            p = os.path.join(base, name)
            href = (urlbase.rstrip("/") + "/" + name) if urlbase != "/" else "/" + name
            if os.path.isdir(p):
                entries.append(prop_entry(href, True, 0, os.path.getmtime(p)))
            else:
                entries.append(prop_entry(href, False, os.path.getsize(p), os.path.getmtime(p)))
    body = (f'<?xml version="1.0" encoding="utf-8"?>'
            f'<{XMLNS}:multistatus xmlns:D="DAV:">' + "".join(entries) + f"</{XMLNS}:multistatus>")
    return Response(body, 207, content_type="application/xml; charset=utf-8")

@app.route("/<path:rel>", methods=["MKCOL"])
def mkcol(rel):
    p = safe_path(rel)
    if p is None or not rel.strip("/"):
        return Response("bad path", 403)
    if os.path.exists(p):
        return Response("exists", 405)
    try:
        os.makedirs(p)
        return Response("", 201)
    except Exception as e:
        return Response(str(e), 500)

@app.route("/<path:rel>", methods=["PUT"])
def put_file(rel):
    p = safe_path(rel)
    if p is None or not rel.strip("/"):
        return Response("bad path", 403)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "wb") as f:
        while True:
            chunk = request.stream.read(65536)
            if not chunk:
                break
            f.write(chunk)
    return Response("", 201)

@app.route("/<path:rel>", methods=["GET", "HEAD"])
def get_file(rel):
    p = safe_path(rel)
    if p is None or not os.path.isfile(p):
        return Response("not found", 404)
    with open(p, "rb") as f:
        data = f.read()
    return Response(data, 200, content_type="application/octet-stream")

@app.route("/<path:rel>", methods=["DELETE"])
def delete_file(rel):
    p = safe_path(rel)
    if p is None or p == DAV_ROOT or not os.path.exists(p):
        return Response("not found", 404)
    import shutil
    shutil.rmtree(p) if os.path.isdir(p) else os.remove(p)
    return Response("", 204)

if __name__ == "__main__":
    os.makedirs(DAV_ROOT, exist_ok=True)
    app.run(host="127.0.0.1", port=8307, threaded=True)

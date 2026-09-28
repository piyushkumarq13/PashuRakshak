#!/usr/bin/env python3
"""Align native .so files inside a debug APK to 16KB page boundaries.
Writes aligned .so back into the APK by rewriting the ZIP to a temp file
and atomically replacing the original."""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

def main():
    apk = sys.argv[1]
    python = sys.argv[2]
    aligner = sys.argv[3]
    if not os.path.exists(apk) or os.path.getsize(apk) == 0:
        print("align-apk: skip (empty/missing)")
        return
    tmpdir = tempfile.mkdtemp()
    tmpapk = os.path.join(tmpdir, os.path.basename(apk))
    try:
        with zipfile.ZipFile(apk) as zin:
            zin.extractall(tmpdir)
            infos = zin.infolist()
        for root, _, files in os.walk(tmpdir):
            for fn in files:
                if fn.endswith(".so"):
                    subprocess.run([python, aligner, os.path.join(root, fn)], check=True)
        with zipfile.ZipFile(tmpapk, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in infos:
                src = os.path.join(tmpdir, info.filename)
                if not os.path.exists(src):
                    continue
                new_info = zipfile.ZipInfo(info.filename, date_time=info.date_time)
                new_info.compress_type = info.compress_type
                new_info.external_attr = info.external_attr
                new_info.internal_attr = info.internal_attr
                new_info.create_system = info.create_system
                with open(src, "rb") as f:
                    zout.writestr(new_info, f.read())
        os.replace(tmpapk, apk)
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)
    print("align-apk: done")

if __name__ == "__main__":
    main()

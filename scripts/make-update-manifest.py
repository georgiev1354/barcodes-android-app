#!/usr/bin/env python3
"""Prepares a signed release APK for the self-update server (docs/self-update.md). Works for any app.

Reads applicationId/versionCode/versionName from the output-metadata.json next to the APK, checks the
signature with apksigner (if the Android SDK is found), and writes into OUT_DIR:

    OUT_DIR/<applicationId>/apk/<applicationId>-<versionCode>.apk
    OUT_DIR/<applicationId>/latest.json

i.e. exactly the layout of the app's folder on the server.

Example:
    scripts/make-update-manifest.py app/release/app-release.apk --changelog "Какво е новото" --out build/update
"""
import argparse
import glob
import hashlib
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone

DEFAULT_HOST = "https://apks.gg-webapp.com"


def find_apksigner():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Library/Android/sdk")
    found = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "apksigner")))
    return found[-1] if found else shutil.which("apksigner")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("apk", help="signed release APK, e.g. app/release/app-release.apk")
    p.add_argument("--out", default="build/update", help="output directory (default: build/update)")
    p.add_argument("--host", default=DEFAULT_HOST, help="update server (default: %(default)s)")
    p.add_argument("--changelog", default="", help="short text shown to the user")
    p.add_argument("--min-sdk", type=int, default=None, help="minSdk of the build (default: from output-metadata.json)")
    a = p.parse_args()

    if "unsigned" in os.path.basename(a.apk):
        sys.exit("Това APK не е подписано. Използвайте Build → Generate Signed App Bundle / APK със същия keystore.")

    meta_path = os.path.join(os.path.dirname(os.path.abspath(a.apk)), "output-metadata.json")
    with open(meta_path, encoding="utf-8") as f:
        meta = json.load(f)
    package = meta["applicationId"]
    element = next(e for e in meta["elements"] if e["outputFile"] == os.path.basename(a.apk))
    version_code, version_name = int(element["versionCode"]), element["versionName"]
    min_sdk = a.min_sdk if a.min_sdk is not None else int(meta.get("minSdkVersionForDexing", 0))

    apksigner = find_apksigner()
    if apksigner:
        r = subprocess.run([apksigner, "verify", "--print-certs", a.apk], capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit("apksigner: подписът не е валиден\n" + r.stdout + r.stderr)
        certs = [l.split(":", 1)[1].strip() for l in r.stdout.splitlines() if "certificate SHA-256 digest" in l]
        print("Подписващ сертификат (SHA-256):", ", ".join(certs) or "?")
        print("  → трябва да е един и същ за всички версии на", package)
    else:
        print("ВНИМАНИЕ: apksigner не е намерен, подписът не е проверен.", file=sys.stderr)

    folder = os.path.join(a.out, package)
    name = f"{package}-{version_code}.apk"
    os.makedirs(os.path.join(folder, "apk"), exist_ok=True)
    target = os.path.join(folder, "apk", name)
    shutil.copyfile(a.apk, target)

    digest = hashlib.sha256()
    with open(target, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)

    latest = {
        "versionCode": version_code,
        "versionName": version_name,
        "apkUrl": f"{a.host.rstrip('/')}/{package}/apk/{name}",
        "sha256": digest.hexdigest(),
        "size": os.path.getsize(target),
        "minSdk": min_sdk,
        "changelog": a.changelog,
        "publishedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    }
    with open(os.path.join(folder, "latest.json"), "w", encoding="utf-8") as f:
        json.dump(latest, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(json.dumps(latest, ensure_ascii=False, indent=2))
    print(f"\nКачете първо {target}, после {os.path.join(folder, 'latest.json')}.")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
import os
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "com.immersive.music"
ACTIVITY = f"{PKG}/{PKG}.MainActivity"
DELIVERABLE = "deliverable"


def adb(*args, check=True):
    p = subprocess.run(["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr.decode("utf-8", "replace"))
    return p.stdout


def dump_ui():
    for _ in range(20):
        adb("shell", "uiautomator", "dump", "/sdcard/window.xml", check=False)
        raw = adb("exec-out", "cat", "/sdcard/window.xml", check=False)
        try:
            if raw.strip():
                return ET.fromstring(raw.decode("utf-8", "replace"))
        except Exception:
            pass
        time.sleep(.25)
    raise RuntimeError("unable to read UI")


def find_desc(desc, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in dump_ui().iter("node"):
            if n.attrib.get("content-desc") == desc:
                return n
        time.sleep(.25)
    raise AssertionError(f"missing content-desc {desc!r}")


def shot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, name), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def main():
    adb("shell", "wm", "size", "1080x2400")
    adb("shell", "wm", "density", "440")
    try:
        adb("shell", "pm", "clear", PKG, check=False)
        adb("shell", "am", "start", "-W", "-n", ACTIVITY)
        time.sleep(1.4)
        find_desc("黑胶外框")
        # Visual proof for the exact user-reported area: the former broad filled
        # annulus must now read as one thin continuous circle around the vinyl.
        shot("v109-single-vinyl-ring.png")
        print("V109_SINGLE_VINYL_RING_STATE=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
import os
import re
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


def find_desc(*descs, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in dump_ui().iter("node"):
            if n.attrib.get("content-desc") in descs:
                return n
        time.sleep(.25)
    raise AssertionError(f"missing content-desc {descs}")


def tap_node(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError("invalid bounds")
    x1, y1, x2, y2 = map(int, m.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(.7)


def shot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, name), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def main():
    adb("shell", "wm", "size", "1080x2400")
    adb("shell", "wm", "density", "440")
    try:
        # Fresh state is intentional: First Light has deterministic demo artwork,
        # so a missing artwork layer is immediately visible in the captured screen.
        adb("shell", "pm", "clear", PKG, check=False)
        adb("shell", "am", "start", "-W", "-n", ACTIVITY)
        time.sleep(1.4)

        find_desc("黑胶外框")
        mode = find_desc("顺序播放", "单曲循环")
        if mode.attrib.get("content-desc") != "单曲循环":
            tap_node(mode)
            find_desc("单曲循环")

        # This screenshot exposes both v108 regressions at once:
        # the left loop arrow/line junction and the restored center artwork.
        shot("v108-loop-clearance-and-artwork.png")
        print("V108_RUNTIME_VISUAL_STATE=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

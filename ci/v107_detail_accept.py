#!/usr/bin/env python3
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "com.immersive.music"
ACTIVITY = f"{PKG}/{PKG}.MainActivity"
DELIVERABLE = "deliverable"
FIXTURE_TITLE = "queue-isolation-test"


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


def find_node(*, descs=(), texts=(), timeout=12):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in dump_ui().iter("node"):
            if descs and n.attrib.get("content-desc") in descs:
                return n
            if texts and n.attrib.get("text") in texts:
                return n
        time.sleep(.25)
    raise AssertionError(f"missing node descs={descs} texts={texts}")


def tap_node(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError("invalid node bounds")
    x1, y1, x2, y2 = map(int, m.groups())
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.9)


def shot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, name), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def ensure_playing():
    play = find_node(descs=("播放", "暂停"))
    if play.attrib.get("content-desc") == "暂停":
        return

    tap_node(play)
    try:
        find_node(descs=("暂停",), timeout=4)
        return
    except AssertionError:
        pass

    # A cold restart can preserve the UI state while losing the active player item.
    # Select the seeded fixture explicitly, then verify the real playback state.
    tap_node(find_node(texts=("音乐库",)))
    tap_node(find_node(texts=(FIXTURE_TITLE,), timeout=15))
    find_node(texts=(FIXTURE_TITLE,), timeout=12)
    find_node(descs=("暂停",), timeout=12)


def main():
    # Restore the user's tall-phone class so this detail screenshot is directly
    # comparable to the screenshots that drove the v107 corrections.
    adb("shell", "wm", "size", "1080x2400")
    adb("shell", "wm", "density", "440")
    try:
        adb("shell", "am", "force-stop", PKG, check=False)
        adb("shell", "am", "start", "-W", "-n", ACTIVITY)
        time.sleep(1.5)

        mode = find_node(descs=("顺序播放", "单曲循环"))
        if mode.attrib.get("content-desc") != "单曲循环":
            tap_node(mode)
            find_node(descs=("单曲循环",))

        ensure_playing()

        # Capture the exact state that exposes all four requested details at once:
        # larger loop '1', left/right triangle alignment, centered tonearm head,
        # and the custom NetEase-proportioned pause bars.
        shot("v107-detail-userlike-playing-single-loop.png")
        print("V107_DETAIL_VISUAL_STATE=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

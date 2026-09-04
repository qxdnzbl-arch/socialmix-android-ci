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


def node_exists(*, descs=(), texts=()):
    for n in dump_ui().iter("node"):
        if descs and n.attrib.get("content-desc") in descs:
            return n
        if texts and n.attrib.get("text") in texts:
            return n
    return None


def tap_node(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError("invalid node bounds")
    x1, y1, x2, y2 = map(int, m.groups())
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.9)


def back():
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(.9)


def shot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, name), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def ensure_fixture_in_library():
    tap_node(find_node(texts=("音乐库",)))
    if node_exists(texts=(FIXTURE_TITLE,)) is not None:
        return

    tap_node(find_node(descs=("添加喜欢的音乐",)))
    find_node(texts=("选择手机音乐",), timeout=15)
    tap_node(find_node(texts=(FIXTURE_TITLE,), timeout=15))
    find_node(descs=(f"已添加:{FIXTURE_TITLE}",), timeout=15)
    back()
    find_node(texts=(FIXTURE_TITLE,), timeout=15)


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

    # If a cold restart has no active item, explicitly select the seeded fixture.
    ensure_fixture_in_library()
    tap_node(find_node(texts=(FIXTURE_TITLE,), timeout=15))
    find_node(texts=(FIXTURE_TITLE,), timeout=12)
    find_node(descs=("暂停",), timeout=12)


def main():
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
        find_node(descs=("暂停",), timeout=12)
        find_node(descs=("单曲循环",), timeout=12)
        find_node(descs=("播放列表",), timeout=12)
        find_node(descs=("唱针:唱片上", "唱针:唱片外"), timeout=12)

        shot("v107-detail-userlike-playing-single-loop.png")
        print("V107_DETAIL_VISUAL_STATE=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

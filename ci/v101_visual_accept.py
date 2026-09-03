#!/usr/bin/env python3
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "com.immersive.music"
ACTIVITY = f"{PKG}/{PKG}.MainActivity"
DELIVERABLE = "deliverable"
PROFILES = [
    ("compact-320dp", "720x1280", 360),
    ("userlike-393dp", "1080x2400", 440),
    ("standard-411dp", "1080x2400", 420),
    ("large-600dp", "1440x2400", 384),
]


def run(*args, check=True):
    p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if check and p.returncode != 0:
        raise RuntimeError(
            f"command failed ({p.returncode}): {' '.join(args)}\n"
            f"stdout={p.stdout.decode('utf-8', 'replace')}\n"
            f"stderr={p.stderr.decode('utf-8', 'replace')}"
        )
    return p.stdout


def adb(*args, check=True):
    return run("adb", *args, check=check)


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
    raise RuntimeError("Unable to read Android UI hierarchy")


def find_node(desc=None, text=None, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        root = dump_ui()
        for node in root.iter("node"):
            if desc is not None and node.attrib.get("content-desc") == desc:
                return node
            if text is not None and node.attrib.get("text") == text:
                return node
        time.sleep(.25)
    raise AssertionError(f"UI node not found: desc={desc!r}, text={text!r}")


def find_any_desc(*descs, timeout=10):
    deadline = time.time() + timeout
    while time.time() < deadline:
        root = dump_ui()
        for node in root.iter("node"):
            if node.attrib.get("content-desc") in descs:
                return node
        time.sleep(.25)
    raise AssertionError(f"UI node not found: {descs!r}")


def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError(f"Invalid bounds: {node.attrib.get('bounds')!r}")
    return tuple(map(int, m.groups()))


def center(box):
    x1, y1, x2, y2 = box
    return (x1 + x2) / 2.0, (y1 + y2) / 2.0


def screen_size():
    out = adb("shell", "wm", "size").decode("utf-8", "replace")
    matches = re.findall(r"(\d+)x(\d+)", out)
    return tuple(map(int, matches[-1]))


def set_profile(size, density):
    adb("shell", "wm", "size", size)
    adb("shell", "wm", "density", str(density))
    time.sleep(.8)


def launch():
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    find_node(text="心动", timeout=18)
    find_node(desc="黑胶外框", timeout=18)


def tap(node):
    x, y = center(bounds(node))
    adb("shell", "input", "tap", str(int(x)), str(int(y)))


def shot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, name), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def check_profile(name, size, density):
    set_profile(size, density)
    launch()
    width, _ = screen_size()

    halo = bounds(find_node(desc="黑胶外框"))
    arm = bounds(find_node(desc="唱针:唱片外"))
    queue = bounds(find_node(desc="播放列表"))
    mode = bounds(find_any_desc("顺序播放", "单曲循环"))

    hx1, hy1, hx2, hy2 = halo
    ax1, ay1, ax2, ay2 = arm
    halo_w = hx2 - hx1
    arm_w = ax2 - ax1
    arm_h = ay2 - ay1
    halo_cx = (hx1 + hx2) / 2.0

    # v101 contour pivot is (14/258, 14/188) inside the paused tone-arm layer.
    pivot_x = ax1 + arm_w * (14.0 / 258.0)
    pivot_y = ay1 + arm_h * (14.0 / 188.0)
    if abs(pivot_x - halo_cx) > max(4.0, width * .006):
        raise AssertionError(
            f"{name} pivot not on record centerline: pivot_x={pivot_x:.1f}, halo_cx={halo_cx:.1f}"
        )
    gap_ratio = (hy1 - pivot_y) / halo_w
    if not (.18 <= gap_ratio <= .28):
        raise AssertionError(f"{name} pivot/record gap wrong: {gap_ratio:.3f}")

    # Left mode and right queue keep the same 44dp interaction footprint.
    mw = mode[2] - mode[0]
    mh = mode[3] - mode[1]
    qw = queue[2] - queue[0]
    qh = queue[3] - queue[1]
    if abs(mw - qw) > 3 or abs(mh - qh) > 3:
        raise AssertionError(
            f"{name} left/right control footprints differ: mode={mode}, queue={queue}"
        )

    shot(f"v101-{name}-paused.png")

    play = find_node(desc="播放", timeout=8)
    tap(play)
    find_node(desc="唱针:唱片上", timeout=8)
    time.sleep(.7)
    shot(f"v101-{name}-playing.png")


def main():
    try:
        for name, size, density in PROFILES:
            check_profile(name, size, density)
        print("V101_NETEASE_VISUAL=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

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
    ("common-360dp", "720x1600", 320),
    ("standard-411dp", "1080x2400", 420),
    ("breakpoint-432dp", "1080x2100", 400),
    ("wide-480dp", "1080x2160", 360),
    ("wide-549dp", "1440x2560", 420),
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
    last = None
    for _ in range(18):
        adb("shell", "uiautomator", "dump", "/sdcard/window.xml", check=False)
        raw = adb("exec-out", "cat", "/sdcard/window.xml", check=False)
        try:
            if raw.strip():
                return ET.fromstring(raw.decode("utf-8", "replace"))
        except Exception as exc:
            last = exc
        time.sleep(0.3)
    raise RuntimeError(f"Unable to read Android UI hierarchy: {last}")


def find_node(text=None, desc=None, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        root = dump_ui()
        for node in root.iter("node"):
            if text is not None and node.attrib.get("text") == text:
                return node
            if desc is not None and node.attrib.get("content-desc") == desc:
                return node
        time.sleep(0.3)
    raise AssertionError(f"UI node not found: text={text!r}, desc={desc!r}")


def find_tonearm():
    for desc in ("唱针:唱片外", "唱针:唱片上"):
        try:
            return find_node(desc=desc, timeout=2)
        except AssertionError:
            pass
    raise AssertionError("Tone arm semantics node not found")


def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError(f"Invalid node bounds: {node.attrib.get('bounds')!r}")
    return tuple(map(int, m.groups()))


def center_x(node):
    x1, _, x2, _ = bounds(node)
    return (x1 + x2) / 2.0


def screen_size():
    out = adb("shell", "wm", "size").decode("utf-8", "replace")
    matches = re.findall(r"(\d+)x(\d+)", out)
    if not matches:
        raise AssertionError(f"Unable to parse wm size: {out}")
    w, h = map(int, matches[-1])
    return w, h


def set_profile(size, density):
    adb("shell", "wm", "size", size)
    adb("shell", "wm", "density", str(density))
    time.sleep(1.0)


def launch_home():
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    find_node(text="心动", timeout=18)
    find_node(desc="黑胶外框", timeout=18)


def screenshot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, f"v95-{name}-home.png"), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def assert_home_geometry(name, density):
    width, _ = screen_size()
    title = find_node(text="心动")
    halo = find_node(desc="黑胶外框")
    arm = find_tonearm()

    # The page title and vinyl remain mathematically centered, independent of device width.
    for node, label in ((title, "title"), (halo, "vinyl")):
        delta = abs(center_x(node) - width / 2.0)
        if delta > max(3.0, width * 0.008):
            raise AssertionError(f"{name} {label} drifted: delta={delta:.1f}, width={width}")

    hx1, hy1, hx2, hy2 = bounds(halo)
    ax1, ay1, ax2, ay2 = bounds(arm)
    halo_w = hx2 - hx1
    ratio = halo_w / width

    # Phones from compact through 600dp must keep a deliberate, substantial vinyl scale.
    if not (0.58 <= ratio <= 0.84):
        raise AssertionError(f"{name} vinyl scale looks device-dependent: ratio={ratio:.3f}")

    dp_width = width * 160.0 / density
    if dp_width > 430 and ratio < 0.61:
        raise AssertionError(f"{name} wide-phone vinyl is too small: dp={dp_width:.1f}, ratio={ratio:.3f}")

    # Tone arm must stay attached to the vinyl visual object, not the phone's right edge.
    horizontal_offset = abs(center_x(arm) - center_x(halo))
    if horizontal_offset > halo_w * 0.48:
        raise AssertionError(
            f"{name} tone arm pulled away horizontally: offset={horizontal_offset:.1f}, halo={halo_w}"
        )

    vertical_gap = hy1 - ay2
    if vertical_gap > halo_w * 0.08:
        raise AssertionError(
            f"{name} tone arm pulled away vertically: gap={vertical_gap:.1f}, halo={halo_w}"
        )

    if ax1 < 0 or ax2 > width:
        raise AssertionError(f"{name} tone arm leaves screen: arm={bounds(arm)}, width={width}")

    screenshot(name)


def main():
    os.makedirs(DELIVERABLE, exist_ok=True)
    try:
        for name, size, density in PROFILES:
            set_profile(size, density)
            launch_home()
            assert_home_geometry(name, density)
        print("V95_RESPONSIVE_PLAYER=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

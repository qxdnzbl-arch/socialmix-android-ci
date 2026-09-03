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
    ("userlike-393dp", "1080x2400", 440),
    ("standard-411dp", "1080x2400", 420),
    ("continuity-432dp", "1080x2100", 400),
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
    for _ in range(18):
        adb("shell", "uiautomator", "dump", "/sdcard/window.xml", check=False)
        raw = adb("exec-out", "cat", "/sdcard/window.xml", check=False)
        try:
            if raw.strip():
                return ET.fromstring(raw.decode("utf-8", "replace"))
        except Exception:
            pass
        time.sleep(0.3)
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
        time.sleep(0.3)
    raise AssertionError(f"UI node not found: desc={desc!r}, text={text!r}")


def find_arm():
    for desc in ("唱针:唱片外", "唱针:唱片上"):
        try:
            return find_node(desc=desc, timeout=2)
        except AssertionError:
            pass
    raise AssertionError("tone arm node missing")


def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError(f"Invalid bounds: {node.attrib.get('bounds')!r}")
    return tuple(map(int, m.groups()))


def center_x(node):
    x1, _, x2, _ = bounds(node)
    return (x1 + x2) / 2.0


def screen_size():
    out = adb("shell", "wm", "size").decode("utf-8", "replace")
    matches = re.findall(r"(\d+)x(\d+)", out)
    return tuple(map(int, matches[-1]))


def set_profile(size, density):
    adb("shell", "wm", "size", size)
    adb("shell", "wm", "density", str(density))
    time.sleep(1.0)


def launch():
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    find_node(text="心动", timeout=18)
    find_node(desc="黑胶外框", timeout=18)


def shot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, f"v99-{name}-home.png"), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def check(name, density):
    width, _ = screen_size()
    halo = find_node(desc="黑胶外框")
    arm = find_arm()
    hx1, hy1, hx2, hy2 = bounds(halo)
    ax1, ay1, ax2, ay2 = bounds(arm)
    halo_w = hx2 - hx1

    # Vinyl keeps the responsive sizing introduced earlier.
    if abs(center_x(halo) - width / 2.0) > max(3.0, width * 0.008):
        raise AssertionError(f"{name} vinyl no longer centered")
    ratio = halo_w / width
    if not (0.58 <= ratio <= 0.84):
        raise AssertionError(f"{name} vinyl ratio invalid: {ratio:.3f}")

    # The approved pre-v95 arm lived clearly ABOVE the record. v95 pulled it down
    # onto the upper rim. This guard prevents that regression from returning.
    top_separation = hy1 - ay1
    sep_ratio = top_separation / halo_w
    if not (0.20 <= sep_ratio <= 0.62):
        raise AssertionError(
            f"{name} tonearm vertical placement changed: sep_ratio={sep_ratio:.3f}, "
            f"arm={bounds(arm)}, vinyl={bounds(halo)}"
        )

    # It remains a top-right control object and must stay fully on-screen.
    if center_x(arm) <= center_x(halo):
        raise AssertionError(f"{name} tonearm is no longer on the right side")
    if ax1 < 0 or ax2 > width or ay1 < 0:
        raise AssertionError(f"{name} tonearm leaves screen: {bounds(arm)}")

    shot(name)
    return width * 160.0 / density, sep_ratio


def main():
    continuity = []
    try:
        for name, size, density in PROFILES:
            set_profile(size, density)
            launch()
            dpw, sep = check(name, density)
            if 385 <= dpw <= 440:
                continuity.append((dpw, name, sep))

        continuity.sort()
        for prev, cur in zip(continuity, continuity[1:]):
            if abs(cur[2] - prev[2]) > 0.10:
                raise AssertionError(
                    f"tonearm placement jumps between {prev[1]} and {cur[1]}: {prev[2]:.3f}->{cur[2]:.3f}"
                )
        print("V99_TONEARM_REGRESSION=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

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


def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError(f"Invalid bounds: {node.attrib.get('bounds')!r}")
    return tuple(map(int, m.groups()))


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


def shot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, f"v102-{name}.png"), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def check(name, size, density):
    set_profile(size, density)
    launch()
    width, height = screen_size()
    halo = bounds(find_node(desc="黑胶外框"))
    arm = bounds(find_node(desc="唱针:唱片外"))
    queue = bounds(find_node(desc="播放列表"))

    hx1, hy1, hx2, hy2 = halo
    ax1, ay1, ax2, ay2 = arm
    halo_w = hx2 - hx1
    halo_cx = (hx1 + hx2) / 2.0
    halo_cy = (hy1 + hy2) / 2.0
    dp_width = width * 160.0 / density
    aspect = height / width

    # Disc remains horizontally centered.
    if abs(halo_cx - width / 2.0) > max(4.0, width * .008):
        raise AssertionError(f"{name} vinyl horizontal center changed: {halo}")

    # On the user's tall-phone class, the supplied NetEase reference has the disc
    # center at ~0.415 of screen height; v101 was ~0.385 and visibly too high.
    # Keep a modest responsive tolerance while preventing the old regression.
    if 385 <= dp_width <= 420 and aspect >= 2.0:
        ratio = halo_cy / height
        if not (.400 <= ratio <= .440):
            raise AssertionError(
                f"{name} player stage vertical ratio wrong: {ratio:.3f}, halo={halo}, screen={width}x{height}"
            )

    # Tone arm is still one object above/right of the disc and stays on-screen.
    if ax1 < 0 or ay1 < 0 or ax2 > width or ay2 > height:
        raise AssertionError(f"{name} tone arm leaves screen: {arm}")
    if (ax1 + ax2) / 2.0 <= halo_cx:
        raise AssertionError(f"{name} tone arm no longer occupies the top-right relation: {arm}")

    # Queue control keeps the app's standard 44dp interaction footprint.
    qw = queue[2] - queue[0]
    qh = queue[3] - queue[1]
    expected = density * 44.0 / 160.0
    if abs(qw - expected) > expected * .16 or abs(qh - expected) > expected * .16:
        raise AssertionError(f"{name} queue hit target changed: {queue}, expected~{expected:.1f}px")

    shot(name)


def main():
    try:
        for profile in PROFILES:
            check(*profile)
        print("V102_VISUAL_ALIGNMENT=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

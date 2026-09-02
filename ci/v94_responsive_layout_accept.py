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
    ("wide-549dp", "1440x2560", 420),
]


def run(*args, check=True, text=False):
    p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if check and p.returncode != 0:
        raise RuntimeError(
            f"command failed ({p.returncode}): {' '.join(args)}\n"
            f"stdout={p.stdout.decode('utf-8', 'replace')}\n"
            f"stderr={p.stderr.decode('utf-8', 'replace')}"
        )
    return p.stdout.decode("utf-8", "replace") if text else p.stdout


def adb(*args, check=True, text=False):
    return run("adb", *args, check=check, text=text)


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
        time.sleep(0.35)
    raise RuntimeError(f"Unable to read Android UI hierarchy: {last}")


def find_node(text=None, desc=None, timeout=15):
    deadline = time.time() + timeout
    last = ""
    while time.time() < deadline:
        root = dump_ui()
        last = ET.tostring(root, encoding="unicode")
        for node in root.iter("node"):
            if text is not None and node.attrib.get("text") == text:
                return node
            if desc is not None and node.attrib.get("content-desc") == desc:
                return node
        time.sleep(0.35)
    raise AssertionError(
        f"UI node not found: text={text!r}, desc={desc!r}\nLAST_UI={last[-12000:]}"
    )


def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError(f"Invalid node bounds: {node.attrib.get('bounds')!r}")
    return tuple(map(int, m.groups()))


def center_x(node):
    x1, _, x2, _ = bounds(node)
    return (x1 + x2) / 2.0


def center_y(node):
    _, y1, _, y2 = bounds(node)
    return (y1 + y2) / 2.0


def root_size(root):
    x1, y1, x2, y2 = bounds(root)
    return x2 - x1, y2 - y1


def tap_node(node):
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.8)


def screenshot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, f"{name}.png"), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def launch_home():
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    find_node(text="心动", timeout=18)


def assert_centered(node, screen_width, label, ratio=0.004):
    delta = abs(center_x(node) - screen_width / 2.0)
    tolerance = max(2.0, screen_width * ratio)
    if delta > tolerance:
        raise AssertionError(
            f"{label} is not centered: center={center_x(node):.1f}, "
            f"screen={screen_width}, delta={delta:.1f}, tolerance={tolerance:.1f}"
        )


def assert_nav_symmetry(screen_width):
    home = find_node(text="首页")
    library = find_node(text="音乐库")
    midpoint = (center_x(home) + center_x(library)) / 2.0
    tolerance = max(3.0, screen_width * 0.008)
    if abs(midpoint - screen_width / 2.0) > tolerance:
        raise AssertionError(
            f"Bottom navigation drifted: midpoint={midpoint:.1f}, screen={screen_width}"
        )


def assert_search_geometry(screen_width):
    back = find_node(desc="返回")
    pill = find_node(desc="搜索栏")
    field = find_node(desc="搜索输入框")
    assert_centered(pill, screen_width, "Search pill")

    bx1, by1, bx2, by2 = bounds(back)
    px1, py1, px2, py2 = bounds(pill)
    fx1, fy1, fx2, fy2 = bounds(field)
    if bx2 > px1 + 2:
        raise AssertionError(f"Back button overlaps search pill: back={bounds(back)}, pill={bounds(pill)}")
    if px1 < 0 or px2 > screen_width:
        raise AssertionError(f"Search pill leaves screen: {bounds(pill)} / width={screen_width}")
    if (px2 - px1) < screen_width * 0.48:
        raise AssertionError(f"Search pill became implausibly narrow: {bounds(pill)} / width={screen_width}")
    if not (px1 <= fx1 <= fx2 <= px2):
        raise AssertionError(f"Search text input escaped pill: pill={bounds(pill)}, field={bounds(field)}")
    if abs(center_y(back) - center_y(pill)) > max(5.0, (py2 - py1) * 0.18):
        raise AssertionError(f"Back/search vertical alignment drifted: back={bounds(back)}, pill={bounds(pill)}")

    left_margin = px1
    right_margin = screen_width - px2
    if abs(left_margin - right_margin) > max(3.0, screen_width * 0.006):
        raise AssertionError(
            f"Search pill margins are asymmetric: left={left_margin}, right={right_margin}, width={screen_width}"
        )


def set_profile(size, density):
    adb("shell", "wm", "size", size)
    adb("shell", "wm", "density", str(density))
    time.sleep(1.2)


def main():
    os.makedirs(DELIVERABLE, exist_ok=True)
    try:
        for name, size, density in PROFILES:
            set_profile(size, density)
            launch_home()
            root = dump_ui()
            width, height = root_size(root)
            if width <= 0 or height <= 0:
                raise AssertionError(f"Invalid root size for {name}: {width}x{height}")

            title = find_node(text="心动")
            assert_centered(title, width, f"Home title ({name})")
            assert_nav_symmetry(width)
            screenshot(f"{name}-home")

            tap_node(find_node(desc="搜索"))
            find_node(desc="搜索栏", timeout=15)
            find_node(desc="搜索输入框", timeout=15)
            root = dump_ui()
            width, _ = root_size(root)
            assert_search_geometry(width)
            screenshot(f"{name}-search")

            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.5)

        print("V94_RESPONSIVE_LAYOUT=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

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


def screen_size():
    out = adb("shell", "wm", "size").decode("utf-8", "replace")
    matches = re.findall(r"(\d+)x(\d+)", out)
    if not matches:
        raise AssertionError(f"Unable to parse wm size: {out}")
    return tuple(map(int, matches[-1]))


def tap_node(node):
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.8)


def set_profile(size, density):
    adb("shell", "wm", "size", size)
    adb("shell", "wm", "density", str(density))
    time.sleep(1.0)


def launch_home():
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    find_node(text="心动", timeout=18)
    find_node(desc="歌曲信息中心", timeout=18)


def screenshot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, f"v96-{name}.png"), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def assert_player_metadata(width, name):
    loop = find_node(desc="顺序播放")
    queue = find_node(desc="播放列表")
    info = find_node(desc="歌曲信息中心")

    tolerance = max(3.0, width * 0.008)
    delta = abs(center_x(info) - width / 2.0)
    if delta > tolerance:
        raise AssertionError(f"{name} song-info center drifted: delta={delta:.1f}, width={width}")

    side_midpoint = (center_x(loop) + center_x(queue)) / 2.0
    if abs(side_midpoint - width / 2.0) > tolerance:
        raise AssertionError(
            f"{name} metadata side controls are not symmetric: midpoint={side_midpoint:.1f}, width={width}"
        )

    if not (center_x(loop) < center_x(info) < center_x(queue)):
        raise AssertionError(
            f"{name} metadata order wrong: loop={center_x(loop):.1f}, info={center_x(info):.1f}, queue={center_x(queue):.1f}"
        )

    side_y_delta = abs(center_y(loop) - center_y(queue))
    if side_y_delta > 5.0:
        raise AssertionError(f"{name} side controls are vertically misaligned: delta={side_y_delta:.1f}")

    ix1, _, ix2, _ = bounds(info)
    if (ix2 - ix1) < width * 0.52:
        raise AssertionError(f"{name} center metadata became too narrow: info={bounds(info)}, width={width}")


def assert_search_header(width, name):
    back = find_node(desc="返回")
    pill = find_node(desc="搜索栏")
    field = find_node(desc="搜索输入框")

    bx1, by1, bx2, by2 = bounds(back)
    px1, py1, px2, py2 = bounds(pill)
    fx1, fy1, fx2, fy2 = bounds(field)

    if bx2 > px1 + 2:
        raise AssertionError(f"{name} back overlaps search pill: back={bounds(back)}, pill={bounds(pill)}")
    if not (px1 <= fx1 <= fx2 <= px2):
        raise AssertionError(f"{name} search input escaped pill: pill={bounds(pill)}, field={bounds(field)}")
    if abs(center_y(back) - center_y(pill)) > max(5.0, (py2 - py1) * 0.18):
        raise AssertionError(f"{name} search header vertical alignment drifted")

    # Back + search field is one component. The outer component margins must match.
    left_margin = bx1
    right_margin = width - px2
    tolerance = max(4.0, width * 0.012)
    if abs(left_margin - right_margin) > tolerance:
        raise AssertionError(
            f"{name} unified search header margins differ: left={left_margin}, right={right_margin}, width={width}"
        )

    group_center = (bx1 + px2) / 2.0
    if abs(group_center - width / 2.0) > tolerance:
        raise AssertionError(
            f"{name} unified search header drifted: center={group_center:.1f}, width={width}"
        )

    # This specifically guards against the v94 synthetic-right-spacer regression.
    pill_ratio = (px2 - px1) / width
    if pill_ratio < 0.66:
        raise AssertionError(f"{name} search field is still too short: ratio={pill_ratio:.3f}")

    gap = px1 - bx2
    back_w = bx2 - bx1
    if gap < 0 or gap > back_w * 0.35:
        raise AssertionError(f"{name} back/search relationship broke: gap={gap}, back_w={back_w}")


def main():
    os.makedirs(DELIVERABLE, exist_ok=True)
    try:
        for name, size, density in PROFILES:
            set_profile(size, density)
            launch_home()
            width, _ = screen_size()
            assert_player_metadata(width, name)
            screenshot(f"{name}-home-metadata")

            tap_node(find_node(desc="搜索"))
            find_node(desc="搜索栏", timeout=18)
            width, _ = screen_size()
            assert_search_header(width, name)
            screenshot(f"{name}-search-header")

        print("V96_VISUAL_COHESION=PASS")
    finally:
        adb("shell", "wm", "size", "reset", check=False)
        adb("shell", "wm", "density", "reset", check=False)


if __name__ == "__main__":
    main()

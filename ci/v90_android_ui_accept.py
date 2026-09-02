#!/usr/bin/env python3
import os
import re
import subprocess
import time
import wave
import xml.etree.ElementTree as ET

TITLE = "Queue Isolation Test"
PKG = "com.immersive.music"
ACTIVITY = f"{PKG}/{PKG}.MainActivity"
DELIVERABLE = "deliverable"


def run(*args, check=True, text=False):
    p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if check and p.returncode != 0:
        raise RuntimeError(
            f"command failed ({p.returncode}): {' '.join(args)}\n"
            f"stdout={p.stdout.decode('utf-8', 'replace')}\n"
            f"stderr={p.stderr.decode('utf-8', 'replace')}"
        )
    if text:
        return p.stdout.decode("utf-8", "replace")
    return p.stdout


def adb(*args, check=True, text=False):
    return run("adb", *args, check=check, text=text)


def wait_boot():
    deadline = time.time() + 60
    while time.time() < deadline:
        if adb("shell", "getprop", "sys.boot_completed", check=False, text=True).strip() == "1":
            return
        time.sleep(1)
    raise AssertionError("Android emulator did not finish booting")


def dump_ui_raw():
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml", check=False)
    return adb("exec-out", "cat", "/sdcard/window.xml", check=False)


def dump_ui():
    last = None
    for _ in range(15):
        raw = dump_ui_raw()
        try:
            if raw.strip():
                return ET.fromstring(raw.decode("utf-8", "replace"))
        except Exception as exc:
            last = exc
        time.sleep(0.4)
    raise RuntimeError(f"Unable to read Android UI hierarchy: {last}")


def node_matches(node, text=None, desc=None):
    if text is not None and node.attrib.get("text") == text:
        return True
    if desc is not None and node.attrib.get("content-desc") == desc:
        return True
    return False


def find_node(text=None, desc=None, timeout=12):
    deadline = time.time() + timeout
    last_xml = ""
    while time.time() < deadline:
        root = dump_ui()
        last_xml = ET.tostring(root, encoding="unicode")
        for node in root.iter("node"):
            if node_matches(node, text=text, desc=desc):
                return node
        time.sleep(0.35)
    focus = adb("shell", "dumpsys", "window", "windows", check=False, text=True)
    logcat = adb("logcat", "-d", "-t", "250", check=False, text=True)
    raise AssertionError(
        f"UI node not found: text={text!r}, desc={desc!r}\n"
        f"LAST_UI={last_xml[-12000:]}\n"
        f"WINDOWS={focus[-6000:]}\n"
        f"LOGCAT={logcat[-12000:]}"
    )


def exists(text=None, desc=None):
    root = dump_ui()
    return any(node_matches(n, text=text, desc=desc) for n in root.iter("node"))


def tap(text=None, desc=None):
    node = find_node(text=text, desc=desc)
    bounds = node.attrib.get("bounds", "")
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        raise AssertionError(f"Invalid node bounds: {bounds!r}")
    x1, y1, x2, y2 = map(int, m.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.8)


def back():
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.8)


def screenshot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, f"{name}.png"), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def dismiss_emulator_overlays():
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    adb("shell", "wm", "dismiss-keyguard", check=False)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS", check=False)
    time.sleep(0.5)
    raw = dump_ui_raw().decode("utf-8", "replace")
    if "Pixel Launcher" in raw and "responding" in raw:
        adb("shell", "input", "tap", "410", "1235", check=False)
        time.sleep(1)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS", check=False)


def media_rows():
    return adb(
        "shell",
        "content",
        "query",
        "--uri",
        "content://media/external/audio/media",
        "--projection",
        "_id:title:artist:is_music:_data",
        check=False,
        text=True,
    )


def create_and_scan_audio():
    local = f"/tmp/{TITLE}.wav"
    remote = f"/sdcard/Music/{TITLE}.wav"
    with wave.open(local, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(8000)
        w.writeframes(b"\x00\x00" * 16000)
    adb("shell", "mkdir", "-p", "/sdcard/Music")
    adb("push", local, remote)
    adb(
        "shell",
        "am",
        "broadcast",
        "-a",
        "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d",
        "file:///sdcard/Music/Queue%20Isolation%20Test.wav",
    )

    deadline = time.time() + 15
    media_id = None
    while time.time() < deadline:
        out = media_rows()
        for line in out.splitlines():
            if TITLE in line or remote in line:
                match = re.search(r"_id=(\d+)", line)
                if match:
                    media_id = match.group(1)
                    break
        if media_id:
            break
        time.sleep(1)
    if not media_id:
        raise AssertionError(f"Test audio did not enter MediaStore. rows={media_rows()}")

    # MediaScanner may classify a short synthetic WAV as is_music=0. The app
    # intentionally queries only MediaStore rows where IS_MUSIC != 0, so make the
    # acceptance fixture explicitly represent a normal music track.
    item_uri = f"content://media/external/audio/media/{media_id}"
    update = adb(
        "shell",
        "content",
        "update",
        "--uri",
        item_uri,
        "--bind",
        "is_music:i:1",
        "--bind",
        f"title:s:{TITLE}",
        "--bind",
        "artist:s:QA",
        check=False,
        text=True,
    )
    if "Updated 0 rows" in update:
        adb(
            "shell",
            "content",
            "update",
            "--uri",
            "content://media/external/audio/media",
            "--bind",
            "is_music:i:1",
            "--bind",
            f"title:s:{TITLE}",
            "--bind",
            "artist:s:QA",
            "--where",
            f"_id={media_id}",
            check=False,
        )

    deadline = time.time() + 10
    while time.time() < deadline:
        out = media_rows()
        matching = [line for line in out.splitlines() if f"_id={media_id}" in line]
        if matching and TITLE in matching[0] and "is_music=1" in matching[0]:
            return
        time.sleep(0.5)
    raise AssertionError(f"MediaStore fixture is not visible as music. rows={media_rows()}")


def launch_clean():
    dismiss_emulator_overlays()
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    time.sleep(2)
    dismiss_emulator_overlays()
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    time.sleep(1)
    find_node(text="心动", timeout=15)
    if not adb("shell", "pidof", PKG, check=False, text=True).strip():
        raise AssertionError("App process is not alive")


def assert_queue_excludes_title(label):
    tap(desc="播放列表")
    find_node(text="播放列表")
    if exists(text=TITLE):
        raise AssertionError(f"{label}: imported library song leaked into playback queue")
    screenshot(label)
    back()


def main():
    wait_boot()
    create_and_scan_audio()
    launch_clean()
    screenshot("home-before-import")

    tap(text="音乐库")
    tap(desc="添加喜欢的音乐")
    find_node(text="选择手机音乐")
    tap(text=TITLE)
    find_node(desc=f"已添加:{TITLE}", timeout=15)
    back()
    find_node(text=TITLE, timeout=15)
    screenshot("library-after-import")

    tap(text="首页")
    assert_queue_excludes_title("queue-after-import-empty")

    launch_clean()
    assert_queue_excludes_title("queue-after-relaunch-empty")

    tap(text="音乐库")
    find_node(text=TITLE, timeout=15)
    tap(text=TITLE)
    find_node(text="心动")
    find_node(text=TITLE, timeout=10)
    tap(desc="播放列表")
    find_node(text="播放列表")
    find_node(text=TITLE, timeout=10)
    screenshot("queue-after-explicit-play")
    back()
    screenshot("home-after-explicit-play")

    if not adb("shell", "pidof", PKG, check=False, text=True).strip():
        raise AssertionError("App process died during acceptance flow")

    print("V90_QUEUE_ISOLATION_ACCEPTANCE=PASS")


if __name__ == "__main__":
    main()

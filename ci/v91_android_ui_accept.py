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
    return p.stdout.decode("utf-8", "replace") if text else p.stdout


def adb(*args, check=True, text=False):
    return run("adb", *args, check=check, text=text)


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


def matches(node, text=None, desc=None):
    return ((text is not None and node.attrib.get("text") == text) or
            (desc is not None and node.attrib.get("content-desc") == desc))


def find_node(text=None, desc=None, timeout=15):
    deadline = time.time() + timeout
    last_xml = ""
    while time.time() < deadline:
        root = dump_ui()
        last_xml = ET.tostring(root, encoding="unicode")
        for node in root.iter("node"):
            if matches(node, text=text, desc=desc):
                return node
        time.sleep(0.35)
    raise AssertionError(
        f"UI node not found: text={text!r}, desc={desc!r}\n"
        f"LAST_UI={last_xml[-14000:]}\n"
        f"LOGCAT={adb('logcat', '-d', '-t', '180', check=False, text=True)[-10000:]}"
    )


def exists(text=None, desc=None):
    root = dump_ui()
    return any(matches(n, text=text, desc=desc) for n in root.iter("node"))


def tap(text=None, desc=None):
    node = find_node(text=text, desc=desc)
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        raise AssertionError(f"Invalid bounds: {node.attrib.get('bounds')!r}")
    x1, y1, x2, y2 = map(int, m.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.9)


def back():
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.9)


def screenshot(name):
    os.makedirs(DELIVERABLE, exist_ok=True)
    with open(os.path.join(DELIVERABLE, f"{name}.png"), "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)


def dismiss_overlays():
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    adb("shell", "wm", "dismiss-keyguard", check=False)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS", check=False)
    time.sleep(0.4)
    raw = dump_ui_raw().decode("utf-8", "replace")
    if "Pixel Launcher" in raw and "responding" in raw:
        adb("shell", "input", "tap", "410", "1235", check=False)
        time.sleep(1)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS", check=False)


def create_audio_fixture():
    local = f"/tmp/{TITLE}.wav"
    remote = f"/sdcard/Music/{TITLE}.wav"
    with wave.open(local, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(8000)
        w.writeframes(b"\x00\x00" * 8000 * 8)
    adb("shell", "mkdir", "-p", "/sdcard/Music")
    adb("push", local, remote)
    adb(
        "shell", "am", "broadcast",
        "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d", "file:///sdcard/Music/Queue%20Isolation%20Test.wav",
    )
    deadline = time.time() + 20
    while time.time() < deadline:
        rows = adb(
            "shell", "content", "query",
            "--uri", "content://media/external/audio/media",
            "--projection", "_id:title:_data",
            check=False, text=True,
        )
        if TITLE in rows:
            return
        time.sleep(1)
    raise AssertionError(f"Audio fixture did not enter MediaStore: {rows}")


def launch_app():
    dismiss_overlays()
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    time.sleep(2)
    dismiss_overlays()
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    find_node(text="心动")
    if not adb("shell", "pidof", PKG, check=False, text=True).strip():
        raise AssertionError("App process is not alive")


def assert_queue_does_not_contain(title, label):
    tap(desc="播放列表")
    find_node(text="播放列表")
    if exists(text=title):
        raise AssertionError(f"{label}: library song leaked into playback queue")
    screenshot(label)
    back()


def main():
    create_audio_fixture()
    launch_app()
    screenshot("home-before-import")

    # Import: the song must become visible in the app library.
    tap(text="音乐库")
    tap(desc="添加喜欢的音乐")
    find_node(text="选择手机音乐")
    tap(text=TITLE)
    find_node(desc=f"已添加:{TITLE}")
    back()
    find_node(text=TITLE)
    screenshot("library-after-import")

    # Import alone must not mutate the playback queue.
    tap(text="首页")
    assert_queue_does_not_contain(TITLE, "queue-after-import-empty")

    # Relaunch restores the library, but still must not rebuild/fill the queue.
    launch_app()
    assert_queue_does_not_contain(TITLE, "queue-after-relaunch-empty")

    # Only explicit playback may put the song into the active queue.
    tap(text="音乐库")
    find_node(text=TITLE)
    tap(text=TITLE)
    find_node(text=TITLE)
    tap(desc="播放列表")
    find_node(text="播放列表")
    find_node(text=TITLE)
    screenshot("queue-after-explicit-play")
    back()
    screenshot("home-after-explicit-play")

    if not adb("shell", "pidof", PKG, check=False, text=True).strip():
        raise AssertionError("App process died during acceptance flow")
    print("V91_ANDROID_QUEUE_ISOLATION=PASS")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "com.socialmix.experiment.b"
REMOTE_XML = "/sdcard/socialmix-ui.xml"
LOCAL_XML = "/tmp/socialmix-ui.xml"
OUT_DIR = "ui-acceptance"
os.makedirs(OUT_DIR, exist_ok=True)


def adb(*args, capture=True):
    cmd = ["adb", *args]
    if capture:
        return subprocess.check_output(cmd, text=True, stderr=subprocess.STDOUT).strip()
    subprocess.check_call(cmd)
    return ""


def dump():
    adb("shell", "uiautomator", "dump", REMOTE_XML)
    with open(LOCAL_XML, "wb") as f:
        subprocess.check_call(["adb", "exec-out", "cat", REMOTE_XML], stdout=f)
    return ET.parse(LOCAL_XML).getroot()


def screenshot(name):
    with open(os.path.join(OUT_DIR, f"{name}.png"), "wb") as f:
        subprocess.check_call(["adb", "exec-out", "screencap", "-p"], stdout=f)


def bounds_center(bounds):
    nums = list(map(int, re.findall(r"\d+", bounds)))
    if len(nums) != 4:
        raise RuntimeError(f"bad bounds: {bounds}")
    return (nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2


def nodes(root):
    return list(root.iter("node"))


def find_text(text, timeout=15):
    end = time.time() + timeout
    last = []
    while time.time() < end:
        root = dump()
        last = [n.attrib.get("text", "") for n in nodes(root) if n.attrib.get("text")]
        for n in nodes(root):
            if n.attrib.get("text") == text:
                return n
        time.sleep(0.4)
    raise AssertionError(f"text not found: {text}; visible={last[-30:]}")


def tap_text(text, timeout=15):
    n = find_text(text, timeout)
    x, y = bounds_center(n.attrib["bounds"])
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.6)


def editable(timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        root = dump()
        candidates = [
            n for n in nodes(root)
            if n.attrib.get("class", "").endswith("EditText")
            or n.attrib.get("editable") == "true"
        ]
        if candidates:
            return candidates[0]
        time.sleep(0.4)
    raise AssertionError("editable field not found")


def tap_node(n):
    x, y = bounds_center(n.attrib["bounds"])
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.4)


def input_text(value):
    adb("shell", "input", "text", value)
    time.sleep(0.5)


# The API acceptance test leaves a valid A session in this isolated prefs file.
# Copy it to the production prefs name so the device-level test exercises the real UI
# without bypassing production authentication logic or modifying app source.
adb("shell", "run-as", PKG, "sh", "-c", "cp shared_prefs/socialmix_live_session_production_core_a.xml shared_prefs/socialmix_live_session.xml")
adb("shell", "am", "force-stop", PKG)
adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
find_text("消息", 15)
screenshot("01-messages")

tap_text("我")
find_text("联系人", 10)
screenshot("02-me")

tap_text("联系人")
find_text("＋", 15)
screenshot("03-contacts")

tap_text("＋")
find_text("添加朋友", 10)
field = editable(10)
tap_node(field)
input_text("skillci_b_3d9cbd")
tap_text("搜索")
find_text("账号：skillci_b_3d9cbd", 15)
screenshot("04-search-result")

tap_text("‹")
find_text("Skill CI B", 15)
screenshot("05-friend-list")

tap_text("Skill CI B")
find_text("发送", 15)
find_text("发消息", 15)
screenshot("06-chat")

marker = f"ui{int(time.time())}"
tap_text("发消息")
input_text(marker)
tap_text("发送")
find_text(marker, 15)
screenshot("07-message-sent")

tap_text("‹")
find_text("联系人", 10)
screenshot("08-back-to-contacts")

print("SOCIALMIX_PRODUCTION_UI_ACCEPTANCE_OK")

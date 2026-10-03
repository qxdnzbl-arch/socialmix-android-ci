import json, os, re, subprocess, sys, time, urllib.request, xml.etree.ElementTree as ET
import websocket

APP = "com.qxdnzbl.zhiyintest"
ART = os.environ.get("GITHUB_WORKSPACE", ".") + "/ci/zhiyin-keyboard/artifacts"
os.makedirs(ART, exist_ok=True)

def sh(cmd, check=True):
    p = subprocess.run(cmd, shell=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if check and p.returncode:
        print(p.stdout)
        raise SystemExit(f"command failed: {cmd}")
    return p.stdout.strip()

def adb(args, check=True):
    return sh("adb " + args, check)

def shot(name):
    with open(os.path.join(ART, name), "wb") as f:
        p = subprocess.run("adb exec-out screencap -p", shell=True, stdout=f)
        if p.returncode: raise SystemExit("screenshot failed")

def tap_bounds(bounds):
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m: return False
    x=(int(m.group(1))+int(m.group(3)))//2
    y=(int(m.group(2))+int(m.group(4)))//2
    adb(f"shell input tap {x} {y}")
    return True

def start_app():
    print("IMES", adb("shell ime list -s", False))
    print("DEFAULT_IME", adb("shell settings get secure default_input_method", False))
    adb("shell settings put secure show_ime_with_hard_keyboard 1", False)
    adb("shell settings put global hide_error_dialogs 1", False)
    adb("shell input keyevent 4", False)
    adb("shell am force-stop com.google.android.apps.nexuslauncher", False)
    adb(f"shell am force-stop {APP}", False)
    adb(f"shell am start -W -n {APP}/.MainActivity")
    time.sleep(3)
    pid=wait_for(lambda: adb(f"shell pidof {APP}", False), timeout=15, name="test app pid")
    print("RESUMED", adb("shell dumpsys activity activities | grep mResumedActivity | head -1", False))
    adb("forward --remove tcp:9222", False)
    adb(f"forward tcp:9222 localabstract:webview_devtools_remote_{pid}")
    return pid

def pages():
    for _ in range(30):
        try:
            return json.load(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=2))
        except Exception: time.sleep(1)
    raise SystemExit("Android WebView DevTools endpoint unavailable")

class CDP:
    def __init__(self):
        ps=pages()
        p=next((x for x in ps if "android_asset/index.html" in x.get("url","")), ps[0])
        self.ws=websocket.create_connection(p["webSocketDebuggerUrl"], timeout=10, suppress_origin=True)
        self.i=0
    def call(self,method,params=None):
        self.i+=1
        self.ws.send(json.dumps({"id":self.i,"method":method,"params":params or {}}))
        while True:
            r=json.loads(self.ws.recv())
            if r.get("id")==self.i:
                if "error" in r: raise RuntimeError(r["error"])
                return r.get("result",{})
    def js(self,expr):
        r=self.call("Runtime.evaluate",{"expression":expr,"returnByValue":True,"awaitPromise":True})
        return r.get("result",{}).get("value")
    def touch(self,selector):
        r=self.js(f"""(()=>{{let e=document.querySelector({json.dumps(selector)});if(!e)return null;let r=e.getBoundingClientRect();return {{x:r.left+r.width/2,y:r.top+r.height/2,w:r.width,h:r.height}}}})()""")
        if not r: raise AssertionError("missing selector "+selector)
        x,y=r["x"],r["y"]
        self.call("Input.dispatchTouchEvent",{"type":"touchStart","touchPoints":[{"x":x,"y":y}]})
        self.call("Input.dispatchTouchEvent",{"type":"touchEnd","touchPoints":[]})
        return r

def wait_for(fn, timeout=10, name="condition"):
    end=time.time()+timeout
    last=None
    while time.time()<end:
        try:
            last=fn()
            if last: return last
        except Exception as e: last=e
        time.sleep(.25)
    raise AssertionError(f"timeout {name}; last={last}")

def metrics(cdp):
    return cdp.js("""(()=>{const R=s=>{const e=document.querySelector(s);if(!e)return null;const r=e.getBoundingClientRect(),cs=getComputedStyle(e);return{x:r.x,y:r.y,w:r.width,h:r.height,b:r.bottom,display:cs.display,vis:cs.visibility}};return {
      vv:visualViewport?visualViewport.height:innerHeight, ih:innerHeight, vh:document.documentElement.clientHeight,
      wrap:document.querySelector('.write-wrap')?.className||'', active:document.activeElement?.id||'',
      date:R('.wdate'), card:R('.wcardbox'), bar:R('.wbar'), cancel:R('.wcancel'), nav:R('.nav'),
      text:document.querySelector('#w-text')?.value||'', href:location.href
    }})()""")

def assert_inside(m, key):
    r=m[key]
    assert r and r["display"]!="none" and r["vis"]!="hidden", f"{key} hidden: {r}"
    assert r["y"] >= -1 and r["b"] <= m["vv"]+2, f"{key} outside visual viewport: {r}, vv={m['vv']}"

def ime_visible():
    out=adb("shell dumpsys input_method", False)
    return ("mInputShown=true" in out or "mInputViewShown=true" in out or "showRequested=true" in out)

def test_write(cdp):
    wait_for(lambda: cdp.js("!!document.querySelector('#w-text')"), name="write page")
    before=metrics(cdp); shot("01_write_before.png")
    cdp.touch("#w-text")
    wait_for(lambda: ime_visible() or metrics(cdp)["vv"] < before["vv"]-100, timeout=12, name="IME open on write")
    time.sleep(1)
    m=metrics(cdp); shot("02_write_keyboard.png")
    assert "focused" in m["wrap"], m
    assert m["vv"] < before["vv"]-100, (before,m)
    assert_inside(m,"bar"); assert_inside(m,"cancel")
    assert m["card"]["h"] >= max(220, m["vv"]*0.45), f"write card too short: {m}"
    assert not m["nav"] or m["nav"]["display"]=="none", f"nav should hide while typing: {m['nav']}"
    adb("shell input text zhiyin123")
    wait_for(lambda: "zhiyin123" in metrics(cdp)["text"], name="text input")
    cdp.touch(".wcancel")
    wait_for(lambda: metrics(cdp)["active"]!="w-text" and "focused" not in metrics(cdp)["wrap"], name="cancel blur")
    wait_for(lambda: not ime_visible() or metrics(cdp)["vv"] >= before["vv"]-20, timeout=12, name="IME close after cancel")
    after=metrics(cdp); shot("03_write_cancelled.png")
    assert "zhiyin123" in after["text"], after
    assert after["nav"] and after["nav"]["display"]!="none", after
    print("WRITE_PASS",json.dumps({"before_vv":before["vv"],"keyboard_vv":m["vv"],"card_h":m["card"]["h"],"bar_bottom":m["bar"]["b"],"cancel_bottom":m["cancel"]["b"]}))

def test_restore():
    adb(f"shell am force-stop {APP}", False)
    time.sleep(1)
    start_app()
    c=CDP()
    wait_for(lambda: c.js("!!document.querySelector('#w-text')"), name="restore page")
    m=metrics(c); shot("04_write_restored.png")
    assert "zhiyin123" in m["text"], m
    print("RESTORE_PASS",m["text"])
    return c

def test_chat(cdp):
    # Open chat tab with a real touch.
    cdp.touch('.nav button[data-t="chat"]')
    wait_for(lambda: cdp.js("!!document.querySelector('.crow .cm')"), name="chat list")
    cdp.touch(".crow .cm")
    wait_for(lambda: cdp.js("!!document.querySelector('#c-in')"), name="conversation composer")
    b=cdp.js("visualViewport.height")
    cdp.touch("#c-in")
    wait_for(lambda: ime_visible() or cdp.js("visualViewport.height") < b-100, timeout=12, name="IME open chat")
    time.sleep(.8)
    m=cdp.js("""(()=>{let e=document.querySelector('.cbar'),i=document.querySelector('#c-in'),s=document.querySelector('.csend');let q=x=>{let r=x.getBoundingClientRect();return{y:r.y,b:r.bottom,h:r.height,display:getComputedStyle(x).display}};return{vv:visualViewport.height,bar:q(e),input:q(i),send:q(s),active:document.activeElement.id}})()""")
    shot("05_chat_keyboard.png")
    assert m["active"]=="c-in", m
    for k in ["bar","input","send"]:
        assert m[k]["display"]!="none" and m[k]["y"]>=-1 and m[k]["b"]<=m["vv"]+2, (k,m)
    adb("shell input text hello123")
    wait_for(lambda: "hello123" in (cdp.js("document.querySelector('#c-in').value") or ""), name="chat input")
    # System back closes real keyboard, input stays and composer remains.
    adb("shell input keyevent 4")
    wait_for(lambda: not ime_visible() or cdp.js("visualViewport.height") >= b-20, timeout=12, name="chat IME close")
    shot("06_chat_keyboard_closed.png")
    print("CHAT_PASS",json.dumps(m))

def main():
    print("DEVICE",adb("shell getprop ro.product.model",False),adb("shell getprop ro.build.version.release",False))
    print("WEBVIEW",adb("shell dumpsys package com.google.android.webview | grep versionName | head -1",False))
    start_app()
    c=CDP()
    print("UA",c.js("navigator.userAgent"))
    test_write(c)
    c=test_restore()
    test_chat(c)
    print("ALL_ANDROID_KEYBOARD_TESTS_PASS")

if __name__=="__main__": main()

from pathlib import Path

path = Path("/tmp/zhiyin-site/index.html")
p = path.read_text()

# Keep v3.8 viewport behavior.
p = p.replace(", interactive-widget=resizes-content", "")

# Make Cancel a real Android touch target: it must not be swallowed by the no-focus pointerdown rule.
old_cancel_btn = '<button class="wcancel" data-a="w-cancel" data-nofocus aria-label="取消编辑并保存草稿">取消</button>'
new_cancel_btn = '<button class="wcancel" data-a="w-cancel" aria-label="取消编辑并保存草稿">取消</button>'
assert old_cancel_btn in p
p = p.replace(old_cancel_btn, new_cancel_btn, 1)

needle = ".write-wrap.hasdraft:not(.typing) .wcardbox #w-text{pointer-events:none}\n\n"
css = """/* ===== Android 写页键盘：只处理写页，不改变其他页面 =====
   键盘打开时保留原来的完整编辑页，只把写页底部工具栏抬到键盘上方。 */
@media (max-width:639px){
  html.write-kbd #stage{height:var(--vvmax,100dvh);min-height:var(--vvmax,100dvh)}
  html.write-kbd #phone{height:var(--vvmax,100dvh)!important}
  html.write-kbd .write-wrap.focused .wbar{
    z-index:12;
    background:var(--bg);
    transform:translateY(calc(-1 * var(--kbd-shift,0px)));
  }
}

"""
assert needle in p
p = p.replace(needle, needle + css, 1)

old = "T.wFocus = false; T.visOpen = false; T.keyboardOpen = false;\n    if (t) t.blur();"
new = "T.wFocus = false; T.visOpen = false; T.keyboardOpen = false;\n    document.documentElement.classList.remove('write-kbd');\n    if (t) t.blur();"
assert old in p
p = p.replace(old, new, 1)

oldvv = """  const vv = () => {
    const h = window.visualViewport ? window.visualViewport.height : window.innerHeight;
    document.documentElement.style.setProperty('--vvh', h + 'px');
    T.vvMax = Math.max(T.vvMax || 0, h);
    const open = (T.vvMax - h) > 120;
    const wasOpen = !!T.keyboardOpen;
    T.keyboardOpen = open;
    // 安卓用键盘收起键时 focusout 有时不会可靠触发；视口恢复后兜底恢复写页导航。
    if (wasOpen && !open && T.wFocus && topR().r === 'write') {
      T.wFocus = false;
      const t = $('#w-text'); if (t) t.blur();
      if (!T.sheet) render();
    }
  };"""

newvv = """  const vv = () => {
    const h = window.visualViewport ? window.visualViewport.height : window.innerHeight;
    const root = document.documentElement;
    root.style.setProperty('--vvh', h + 'px');
    T.vvMax = Math.max(T.vvMax || 0, h);
    root.style.setProperty('--vvmax', (T.vvMax || h) + 'px');
    const shift = Math.max(0, (T.vvMax || h) - h);
    root.style.setProperty('--kbd-shift', shift + 'px');
    const open = shift > 120;
    const wasOpen = !!T.keyboardOpen;
    T.keyboardOpen = open;
    root.classList.toggle('write-kbd', !!(open && T.wFocus && topR().r === 'write'));
    // 安卓用键盘收起键时 focusout 有时不会可靠触发；视口恢复后兜底恢复写页导航。
    if (wasOpen && !open && T.wFocus && topR().r === 'write') {
      root.classList.remove('write-kbd');
      T.wFocus = false;
      const t = $('#w-text'); if (t) t.blur();
      if (!T.sheet) render();
    }
  };"""

assert oldvv in p
p = p.replace(oldvv, newvv, 1)
path.write_text(p)

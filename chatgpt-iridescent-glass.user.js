// ==UserScript==
// @name         ChatGPT 彩光玻璃聊天美化包
// @namespace    local.chatgpt.iridescent.theme
// @version      1.0.0
// @description  只改变 ChatGPT 网页视觉：轻亮彩光背景、玻璃气泡、输入框、侧栏与代码块。不会读取、上传或修改聊天内容。
// @match        https://chatgpt.com/*
// @match        https://www.chatgpt.com/*
// @match        https://chat.openai.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(() => {
  'use strict';

  const STYLE_ID = 'cgpt-iridescent-glass-theme-v1';
  const ROOT_CLASS = 'cgpt-iridescent-glass-theme';

  const css = String.raw`
/* ---------- Theme tokens ---------- */
html.${ROOT_CLASS} {
  --cg-bg-0: #fffafb;
  --cg-bg-1: #f9f5ff;
  --cg-bg-2: #f3fbff;
  --cg-ink: #302c34;
  --cg-ink-soft: #716a77;
  --cg-line: rgba(88, 68, 95, .11);
  --cg-glass: rgba(255, 255, 255, .66);
  --cg-glass-strong: rgba(255, 255, 255, .82);
  --cg-user-a: rgba(255, 226, 239, .92);
  --cg-user-b: rgba(236, 230, 255, .92);
  --cg-shadow: 0 14px 34px rgba(91, 67, 91, .10);
  --cg-shadow-soft: 0 8px 22px rgba(91, 67, 91, .075);
  --cg-radius: 20px;
  --cg-composer-radius: 24px;

  --bg-primary: transparent !important;
  --bg-secondary: rgba(255,255,255,.52) !important;
  --main-surface-primary: transparent !important;
  --main-surface-secondary: rgba(255,255,255,.52) !important;
  --main-surface-tertiary: rgba(255,255,255,.58) !important;
  --bg-elevated-primary: rgba(255,255,255,.86) !important;
  --bg-elevated-secondary: rgba(255,255,255,.9) !important;
  --composer-surface-primary: rgba(255,255,255,.76) !important;
  --sidebar-surface-primary: rgba(255,255,255,.58) !important;
  --sidebar-surface-secondary: rgba(255,255,255,.7) !important;
  --text-primary: var(--cg-ink) !important;
  --text-secondary: var(--cg-ink-soft) !important;
}

html.${ROOT_CLASS},
html.${ROOT_CLASS} body {
  background:
    radial-gradient(circle at 12% 13%, rgba(255, 194, 224, .34), transparent 30%),
    radial-gradient(circle at 88% 18%, rgba(188, 224, 255, .30), transparent 28%),
    radial-gradient(circle at 70% 72%, rgba(223, 202, 255, .26), transparent 31%),
    radial-gradient(circle at 18% 78%, rgba(190, 245, 228, .22), transparent 28%),
    linear-gradient(145deg, var(--cg-bg-0) 0%, var(--cg-bg-1) 47%, var(--cg-bg-2) 100%) !important;
  color: var(--cg-ink) !important;
  background-attachment: fixed !important;
}

html.${ROOT_CLASS} body::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  opacity: .72;
  background:
    radial-gradient(circle at 23% 24%, rgba(255,255,255,.95) 0 1px, transparent 1.7px),
    radial-gradient(circle at 77% 34%, rgba(255,255,255,.9) 0 1px, transparent 1.8px),
    radial-gradient(circle at 58% 82%, rgba(255,255,255,.88) 0 1px, transparent 1.8px),
    radial-gradient(circle at 14% 66%, rgba(255,255,255,.82) 0 1px, transparent 1.8px);
  background-size: 96px 96px, 132px 132px, 118px 118px, 144px 144px;
}

/* ---------- Main shell ---------- */
html.${ROOT_CLASS} #__next,
html.${ROOT_CLASS} main,
html.${ROOT_CLASS} main#main,
html.${ROOT_CLASS} #thread,
html.${ROOT_CLASS} [data-testid='conversation-turn'] {
  background: transparent !important;
}

html.${ROOT_CLASS} #page-header,
html.${ROOT_CLASS} [data-testid='chat-header'] {
  background: rgba(255,255,255,.52) !important;
  border-color: rgba(255,255,255,.7) !important;
  box-shadow: 0 1px 0 rgba(91,67,91,.07) !important;
  backdrop-filter: blur(18px) saturate(145%) !important;
  -webkit-backdrop-filter: blur(18px) saturate(145%) !important;
}

/* ---------- Sidebar ---------- */
html.${ROOT_CLASS} aside,
html.${ROOT_CLASS} .dframe-sidebar,
html.${ROOT_CLASS} [data-testid*='sidebar'] {
  background: rgba(255,255,255,.56) !important;
  border-color: rgba(255,255,255,.72) !important;
  backdrop-filter: blur(22px) saturate(145%) !important;
  -webkit-backdrop-filter: blur(22px) saturate(145%) !important;
}

html.${ROOT_CLASS} aside a,
html.${ROOT_CLASS} aside button,
html.${ROOT_CLASS} .dframe-sidebar a,
html.${ROOT_CLASS} .dframe-sidebar button {
  border-radius: 14px !important;
}

html.${ROOT_CLASS} aside a:hover,
html.${ROOT_CLASS} aside button:hover,
html.${ROOT_CLASS} .dframe-sidebar a:hover,
html.${ROOT_CLASS} .dframe-sidebar button:hover {
  background: rgba(255,255,255,.72) !important;
}

/* ---------- Conversation width / rhythm ---------- */
html.${ROOT_CLASS} [data-testid^='conversation-turn'],
html.${ROOT_CLASS} article[data-turn] {
  padding-top: 8px !important;
  padding-bottom: 8px !important;
}

html.${ROOT_CLASS} [data-message-author-role] {
  color: var(--cg-ink) !important;
}

/* ---------- Assistant bubbles ---------- */
html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > p,
html.${ROOT_CLASS} [data-message-author-role='assistant'] .prose > p,
html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > ul,
html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > ol,
html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > blockquote,
html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > table {
  width: fit-content !important;
  max-width: min(100%, 760px) !important;
  margin: 0 0 10px 0 !important;
  padding: 12px 15px !important;
  border: 1px solid rgba(255,255,255,.86) !important;
  border-radius: 18px 18px 18px 7px !important;
  background:
    linear-gradient(135deg, rgba(255,255,255,.82), rgba(255,255,255,.58)) !important;
  box-shadow: var(--cg-shadow-soft) !important;
  backdrop-filter: blur(16px) saturate(135%) !important;
  -webkit-backdrop-filter: blur(16px) saturate(135%) !important;
}

html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > p::selection,
html.${ROOT_CLASS} [data-message-author-role='assistant'] .prose > p::selection {
  background: rgba(235, 205, 255, .55) !important;
}

/* ---------- User bubble ---------- */
html.${ROOT_CLASS} [data-message-author-role='user'] [data-user-message-bubble='true'],
html.${ROOT_CLASS} [data-message-author-role='user'] .user-message-bubble-color,
html.${ROOT_CLASS} [data-message-author-role='user'] .whitespace-pre-wrap {
  color: #443744 !important;
  background:
    linear-gradient(135deg, var(--cg-user-a), var(--cg-user-b)) !important;
  border: 1px solid rgba(255,255,255,.9) !important;
  border-radius: 19px 19px 7px 19px !important;
  box-shadow: var(--cg-shadow-soft) !important;
  backdrop-filter: blur(15px) saturate(130%) !important;
  -webkit-backdrop-filter: blur(15px) saturate(130%) !important;
}

/* ---------- Typography ---------- */
html.${ROOT_CLASS} [data-message-author-role] .markdown,
html.${ROOT_CLASS} [data-message-author-role] .prose,
html.${ROOT_CLASS} [data-message-author-role] p,
html.${ROOT_CLASS} [data-message-author-role] li {
  color: var(--cg-ink) !important;
  line-height: 1.67 !important;
  letter-spacing: .01em !important;
}

html.${ROOT_CLASS} [data-message-author-role] h1,
html.${ROOT_CLASS} [data-message-author-role] h2,
html.${ROOT_CLASS} [data-message-author-role] h3 {
  color: #342f38 !important;
  letter-spacing: -.01em !important;
}

html.${ROOT_CLASS} [data-message-author-role] a {
  color: #8a62a0 !important;
  text-decoration-color: rgba(138,98,160,.34) !important;
  text-underline-offset: 3px !important;
}

/* ---------- Code / tables ---------- */
html.${ROOT_CLASS} [data-message-author-role] pre {
  background: rgba(47, 43, 53, .92) !important;
  border: 1px solid rgba(255,255,255,.14) !important;
  border-radius: 16px !important;
  box-shadow: 0 12px 30px rgba(45,40,50,.12) !important;
}

html.${ROOT_CLASS} [data-message-author-role] :not(pre) > code {
  background: rgba(255,255,255,.72) !important;
  border: 1px solid var(--cg-line) !important;
  border-radius: 7px !important;
  color: #6c4e78 !important;
}

html.${ROOT_CLASS} [data-message-author-role] table {
  border-collapse: separate !important;
  border-spacing: 0 !important;
  overflow: hidden !important;
}

html.${ROOT_CLASS} [data-message-author-role] th,
html.${ROOT_CLASS} [data-message-author-role] td {
  border-color: rgba(88,68,95,.10) !important;
}

/* ---------- Composer ---------- */
html.${ROOT_CLASS} [data-composer-surface='true'],
html.${ROOT_CLASS} form[data-type='unified-composer'],
html.${ROOT_CLASS} form:has(#prompt-textarea) {
  background: rgba(255,255,255,.72) !important;
  border: 1px solid rgba(255,255,255,.9) !important;
  border-radius: var(--cg-composer-radius) !important;
  box-shadow: 0 16px 44px rgba(86,67,92,.13), inset 0 1px 0 rgba(255,255,255,.95) !important;
  backdrop-filter: blur(22px) saturate(150%) !important;
  -webkit-backdrop-filter: blur(22px) saturate(150%) !important;
}

html.${ROOT_CLASS} #thread-bottom,
html.${ROOT_CLASS} [data-testid='thread-disclaimer'] {
  background: transparent !important;
}

html.${ROOT_CLASS} #prompt-textarea,
html.${ROOT_CLASS} [data-testid='prompt-textarea'],
html.${ROOT_CLASS} [contenteditable='true'][data-lexical-editor='true'] {
  color: var(--cg-ink) !important;
  caret-color: #b46f98 !important;
}

html.${ROOT_CLASS} #prompt-textarea p[data-placeholder]::before,
html.${ROOT_CLASS} [data-testid='prompt-textarea'] p[data-placeholder]::before {
  color: rgba(71,63,74,.43) !important;
}

html.${ROOT_CLASS} button[data-testid='send-button'],
html.${ROOT_CLASS} button.composer-submit-btn,
html.${ROOT_CLASS} button[aria-label='Send prompt'],
html.${ROOT_CLASS} button[aria-label='Send dictated message'] {
  background: linear-gradient(135deg, #e5a9c8, #bca7e9) !important;
  color: white !important;
  border: 1px solid rgba(255,255,255,.8) !important;
  box-shadow: 0 6px 16px rgba(164,111,151,.22) !important;
}

html.${ROOT_CLASS} button[data-testid='composer-plus-btn'],
html.${ROOT_CLASS} button[aria-label='Start Voice'],
html.${ROOT_CLASS} button[aria-label='Start dictation'],
html.${ROOT_CLASS} button[aria-label='Dictate button'] {
  background: rgba(255,255,255,.6) !important;
  border-radius: 999px !important;
}

/* ---------- Menus / cards / tooltips ---------- */
html.${ROOT_CLASS} [role='dialog'],
html.${ROOT_CLASS} [role='menu'],
html.${ROOT_CLASS} [role='listbox'],
html.${ROOT_CLASS} [data-radix-popper-content-wrapper] > * {
  background: rgba(255,255,255,.91) !important;
  border-color: rgba(255,255,255,.92) !important;
  box-shadow: var(--cg-shadow) !important;
  backdrop-filter: blur(24px) saturate(145%) !important;
  -webkit-backdrop-filter: blur(24px) saturate(145%) !important;
}

html.${ROOT_CLASS} [role='tooltip'] {
  border-radius: 10px !important;
}

/* ---------- Scrollbars ---------- */
html.${ROOT_CLASS} * {
  scrollbar-color: rgba(153,120,156,.25) transparent;
}

html.${ROOT_CLASS} ::-webkit-scrollbar {
  width: 7px;
  height: 7px;
}

html.${ROOT_CLASS} ::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, rgba(225,166,197,.42), rgba(183,169,226,.42));
  border-radius: 999px;
}

html.${ROOT_CLASS} ::-webkit-scrollbar-track {
  background: transparent;
}

/* ---------- Mobile tuning ---------- */
@media (max-width: 767px) {
  html.${ROOT_CLASS} [data-testid^='conversation-turn'],
  html.${ROOT_CLASS} article[data-turn] {
    padding-left: 7px !important;
    padding-right: 7px !important;
  }

  html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > p,
  html.${ROOT_CLASS} [data-message-author-role='assistant'] .prose > p,
  html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > ul,
  html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > ol,
  html.${ROOT_CLASS} [data-message-author-role='assistant'] .markdown > blockquote {
    padding: 11px 13px !important;
    border-radius: 17px 17px 17px 6px !important;
  }

  html.${ROOT_CLASS} [data-composer-surface='true'],
  html.${ROOT_CLASS} form[data-type='unified-composer'],
  html.${ROOT_CLASS} form:has(#prompt-textarea) {
    border-radius: 22px !important;
  }
}

/* ---------- Accessibility / reduced motion ---------- */
@media (prefers-reduced-motion: reduce) {
  html.${ROOT_CLASS} *,
  html.${ROOT_CLASS} *::before,
  html.${ROOT_CLASS} *::after {
    scroll-behavior: auto !important;
    transition-duration: .01ms !important;
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
  }
}
`;

  function mount() {
    document.documentElement.classList.add(ROOT_CLASS);
    if (!document.getElementById(STYLE_ID)) {
      const style = document.createElement('style');
      style.id = STYLE_ID;
      style.textContent = css;
      (document.head || document.documentElement).appendChild(style);
    }
  }

  function healthCheck() {
    const selectors = {
      userMessage: '[data-message-author-role="user"]',
      assistantMessage: '[data-message-author-role="assistant"]',
      composer: '[data-composer-surface="true"], form[data-type="unified-composer"], form:has(#prompt-textarea)',
      textarea: '#prompt-textarea, [data-testid="prompt-textarea"]'
    };
    const result = Object.fromEntries(
      Object.entries(selectors).map(([name, selector]) => [name, document.querySelectorAll(selector).length])
    );
    console.info('[ChatGPT 彩光玻璃主题] selector health:', result);
  }

  mount();
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      mount();
      setTimeout(healthCheck, 1200);
    }, { once: true });
  } else {
    setTimeout(healthCheck, 400);
  }

  const observer = new MutationObserver(() => {
    if (!document.documentElement.classList.contains(ROOT_CLASS) || !document.getElementById(STYLE_ID)) {
      mount();
    }
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });
})();

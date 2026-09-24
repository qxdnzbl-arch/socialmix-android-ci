# Kehua Phase 1 Authority

## Final deliverable
Phase 1 has exactly one user-facing deliverable: **an installable Android APK**.

## What Phase 1 is
Rebuild the reference screenshots as a real native Android app UI and interaction shell.

## Hard boundaries
1. Reference screenshots are visual evidence only.
2. No screenshot, screenshot crop, base64 screenshot, image background, or transparent hit-area overlay may be used as runtime UI.
3. No HTML, PWA, WebView, iframe, PPT, or slideshow implementation is acceptable.
4. UI must be implemented with native Android/Jetpack Compose components.
5. Phase 1 is local UI + interaction only. No Supabase, Firebase, network backend, cloud auth, realtime, or multi-device sync.
6. Source code, screenshots, build logs, or a ZIP are intermediate artifacts, not the Phase 1 deliverable.
7. Phase 2 may not start until the user explicitly approves the Phase 1 APK.

## Completion gate
Phase 1 is complete only when all are true:
- static hard gate passes;
- Gradle build succeeds;
- APK exists;
- APK installs on an Android emulator;
- launcher starts without crash;
- core UI interactions are exercised by Android instrumentation tests;
- final APK artifact is downloadable;
- user explicitly approves the APK.

If any condition fails, Phase 1 remains unfinished.

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main"
ACTIVITY = MAIN / "java" / "com" / "suisuinian" / "app" / "KehuaActivity.kt"
MANIFEST = MAIN / "AndroidManifest.xml"
GRADLE = ROOT / "app" / "build.gradle"

errors = []

def err(msg):
    errors.append(msg)

for p in (ACTIVITY, MANIFEST, GRADLE):
    if not p.exists():
        err(f"missing required Android file: {p.relative_to(ROOT)}")

activity = ACTIVITY.read_text(encoding="utf-8") if ACTIVITY.exists() else ""
manifest = MANIFEST.read_text(encoding="utf-8") if MANIFEST.exists() else ""
gradle = GRADLE.read_text(encoding="utf-8") if GRADLE.exists() else ""
all_text = "\n".join([activity, manifest, gradle]).lower()

# Wrong implementation classes are blocked.
for token in (
    "android.webkit.webview",
    "webview(",
    "loadurl(",
    "file:///android_asset",
    "<html",
    "<iframe",
):
    if token in all_text:
        err(f"forbidden web/screenshot-wrapper implementation: {token}")

# Phase 1 must not include backend/network functionality.
if "android.permission.internet" in manifest.lower():
    err("Phase 1 must not request INTERNET permission")

for token in ("supabase", "firebase", "okhttp", "ktor-client", "coil-compose"):
    if token in gradle.lower():
        err(f"Phase 1 must not include backend/network/image-loader dependency: {token}")

# Reference screenshots must never enter runtime resources.
raster_exts = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"}
if MAIN.exists():
    for p in MAIN.rglob("*"):
        if not p.is_file():
            continue
        rel = p.relative_to(ROOT).as_posix()
        low = rel.lower()
        if p.suffix.lower() in raster_exts:
            # Launcher icons are infrastructure, not screenshot UI.
            if "/res/mipmap" in low and "ic_launcher" in p.name.lower():
                continue
            err(f"runtime raster asset forbidden in Phase 1: {rel}")
        if any(x in p.name.lower() for x in ("screenshot", "screen-shot", "reference", "master-shot", "capture")):
            err(f"reference image leaked into runtime source: {rel}")

# Block image-backed UI even when images are loaded dynamically or encoded.
for token in ("asyncimage", "bitmapfactory", "imagebitmap", "painterresource(", "base64,"):
    if token in activity.lower():
        err(f"image-backed UI forbidden in Phase 1: {token}")

# Require actual native interactive primitives.
required = {
    "@Composable": "Compose UI",
    "setContent": "native Activity content",
    "Text(": "real text",
    "BasicTextField": "real text input",
    "LazyColumn": "real scrollable list",
    "NavigationBar": "real bottom navigation",
    "clickable": "real click interaction",
}
for token, label in required.items():
    if token not in activity:
        err(f"missing {label}: {token}")

# Keep the real app identity and launcher.
if "com.qxdnzbl.kehua" not in gradle:
    err("applicationId com.qxdnzbl.kehua is missing")
if "android.intent.action.MAIN" not in manifest or "android.intent.category.LAUNCHER" not in manifest:
    err("launcher Activity is missing")

# PPT is never an acceptable software artifact.
for p in ROOT.rglob("*"):
    if not p.is_file():
        continue
    rel = p.relative_to(ROOT).as_posix().lower()
    if rel.startswith(".git/") or "/build/" in rel:
        continue
    if rel.endswith((".ppt", ".pptx")):
        err(f"PPT file is forbidden in Android project: {rel}")

if errors:
    print("KEHUA PHASE1 HARD GATE: BLOCKED")
    for i, message in enumerate(errors, 1):
        print(f"{i}. {message}")
    sys.exit(1)

print("KEHUA PHASE1 HARD GATE: PASSED")
print("- native Android Compose")
print("- no HTML/WebView/PPT")
print("- no screenshot/raster UI")
print("- no backend/network in Phase 1")
print("- real input/list/navigation/click primitives present")

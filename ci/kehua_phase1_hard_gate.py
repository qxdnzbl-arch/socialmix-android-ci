from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main"
ACTIVITY = MAIN / "java" / "com" / "suisuinian" / "app" / "KehuaNativeActivity.kt"
MANIFEST = MAIN / "AndroidManifest.xml"
GRADLE = ROOT / "app" / "build.gradle"

errors = []
def err(msg): errors.append(msg)

for p in (ACTIVITY, MANIFEST, GRADLE):
    if not p.exists(): err(f"missing required Android file: {p.relative_to(ROOT)}")

activity = ACTIVITY.read_text(encoding="utf-8") if ACTIVITY.exists() else ""
manifest = MANIFEST.read_text(encoding="utf-8") if MANIFEST.exists() else ""
gradle = GRADLE.read_text(encoding="utf-8") if GRADLE.exists() else ""
all_text = "\n".join([activity, manifest, gradle]).lower()

# Product UI must remain native Android; web shells are forbidden.
for token in ("android.webkit.webview", "webview(", "loadurl(", "file:///android_asset", "<html", "<iframe"):
    if token in all_text: err(f"forbidden web/screenshot-wrapper implementation: {token}")

# Production app is networked and must declare Internet access.
if "android.permission.internet" not in manifest.lower():
    err("production app must request INTERNET permission")

# Require the actual native production entry point and Compose primitives.
required = {
    "class KehuaNativeActivity": "KehuaNativeActivity entry point",
    "@Composable": "Compose UI",
    "setContent": "native Activity content",
    "Text(": "real text",
    "OutlinedTextField": "real text input",
    "LazyColumn": "real scrollable list",
    "NavigationBar": "real bottom navigation",
    "clickable": "real click interaction",
    "KehuaProdApi": "production API integration",
}
for token, label in required.items():
    if token not in activity: err(f"missing {label}: {token}")

if "com.qxdnzbl.kehua" not in gradle:
    err("applicationId com.qxdnzbl.kehua is missing")
if "android.intent.action.MAIN" not in manifest or "android.intent.category.LAUNCHER" not in manifest:
    err("launcher Activity is missing")
if "KehuaNativeActivity" not in manifest:
    err("launcher must use KehuaNativeActivity")

# PPT is never an acceptable software artifact.
for p in ROOT.rglob("*"):
    if not p.is_file(): continue
    rel = p.relative_to(ROOT).as_posix().lower()
    if rel.startswith(".git/") or "/build/" in rel: continue
    if rel.endswith((".ppt", ".pptx")): err(f"PPT file is forbidden in Android project: {rel}")

if errors:
    print("KEHUA NATIVE PRODUCTION HARD GATE: BLOCKED")
    for i, message in enumerate(errors, 1): print(f"{i}. {message}")
    sys.exit(1)

print("KEHUA NATIVE PRODUCTION HARD GATE: PASSED")
print("- KehuaNativeActivity + Jetpack Compose")
print("- no HTML/WebView/PPT product UI")
print("- networked production architecture")
print("- real input/list/navigation/click primitives present")

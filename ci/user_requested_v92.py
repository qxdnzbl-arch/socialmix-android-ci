from pathlib import Path

main = Path('app/src/main/java/com/immersive/music/MainActivity.kt')
m = main.read_text()

# Android 10+ exposes volume-specific MediaStore collections. The phone music
# picker should read the primary shared-storage audio collection directly; using
# the synthetic VOLUME_EXTERNAL collection can return no rows on Android 15 even
# when the same audio is visible in external_primary.
old = '''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {'''
new = '''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {'''
if old not in m:
    raise SystemExit('MediaStore volume anchor missing')
m = m.replace(old, new, 1)
main.write_text(m)

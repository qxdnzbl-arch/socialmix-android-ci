from pathlib import Path

main = Path('app/src/main/java/com/immersive/music/MainActivity.kt')
m = main.read_text()

# Some real Android devices (and Android 15 MediaStore) can expose a valid audio
# row while IS_MUSIC is null/incorrect. The local picker is explicitly an audio
# picker, so do not hide valid audio solely because that metadata flag is absent.
old = '    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"\n'
new = '    val selection: String? = null\n'
if old not in m:
    raise SystemExit('device-audio MediaStore selection anchor missing')
m = m.replace(old, new, 1)
main.write_text(m)

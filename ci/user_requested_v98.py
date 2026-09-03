from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

start_marker = '@Composable\nfun SearchScreen('
end_marker = '\n@Composable\nprivate fun TrackRow'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('SearchScreen block not found; refusing to patch guessed layout')

block = text[start:end]

# v97 corrected the 44dp hit-target vs 22dp Icon-box mismatch, but the Material
# ArrowBack path itself also has optical inset inside its 24x24 vector viewport:
# visible path starts at x=4, i.e. 4/24 of the 22dp icon box. Include that too.
# Result: actual visible arrow left edge and actual visible search-pill right edge
# both land on AppPageSide, so the thing the user sees is centered, not just boxes.
old = '''                        start = AppPageSide - (AppTopIconHit - AppTopIconSize) / 2f,
                        end = AppPageSide,'''
new = '''                        start = AppPageSide
                            - (AppTopIconHit - AppTopIconSize) / 2f
                            - AppTopIconSize * (4f / 24f),
                        end = AppPageSide,'''
if old not in block:
    raise SystemExit('v97 search optical padding not found')
block = block.replace(old, new, 1)

text = text[:start] + block + text[end:]
path.write_text(text)

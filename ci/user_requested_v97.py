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

# Optical centering, not hit-box centering.
# The back arrow is a 22dp visible glyph centered inside a 44dp touch target, so
# its visible left edge sits 11dp inside the touch target. v96 balanced the 44dp
# target against the search pill, which made the visible whole header shift right.
# Keep the 44dp touch target, but pull only the outer component start inward by the
# derived 11dp optical inset. The visible arrow edge and search-pill right edge then
# share the same AppPageSide visual margin on every phone width.
old = '''                    .padding(horizontal = AppPageSide)
                    .padding(top = AppTopGap)
                    .height(AppTopBarHeight)
                    .semantics { contentDescription = "页面顶部:搜索" },'''
new = '''                    .padding(
                        start = AppPageSide - (AppTopIconHit - AppTopIconSize) / 2f,
                        end = AppPageSide,
                    )
                    .padding(top = AppTopGap)
                    .height(AppTopBarHeight)
                    .semantics { contentDescription = "页面顶部:搜索" },'''
if old not in block:
    raise SystemExit('Final search-header padding not found')
block = block.replace(old, new, 1)

text = text[:start] + block + text[end:]
path.write_text(text)

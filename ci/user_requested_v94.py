from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

start_marker = '@Composable\nfun SearchScreen('
end_marker = '\n@Composable\nprivate fun TrackRow'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('SearchScreen block not found; refusing to patch a guessed layout')

block = text[start:end]

# The existing final layout has a 44dp back target + 6dp gap on the left.
# Give the right side the exact same 50dp reserve, so the visible search pill is
# mathematically centered at every portrait width instead of drifting with screen size.
needle = '''                        .clip(RoundedCornerShape(21.dp))
                        .background(Color.White.copy(alpha = .58f))
                        .padding(horizontal = 13.dp),'''
replacement = '''                        .clip(RoundedCornerShape(21.dp))
                        .background(Color.White.copy(alpha = .58f))
                        .semantics { contentDescription = "搜索栏" }
                        .padding(horizontal = 13.dp),'''
if needle not in block:
    raise SystemExit('Final search pill modifier not found')
block = block.replace(needle, replacement, 1)

needle = '''                    )
                }
            }

            if (query.isNotBlank() && results.isEmpty()) {'''
replacement = '''                    )
                }
                Spacer(Modifier.width(AppTopIconHit + 6.dp))
            }

            if (query.isNotBlank() && results.isEmpty()) {'''
if needle not in block:
    raise SystemExit('Final search header closing block not found')
block = block.replace(needle, replacement, 1)

text = text[:start] + block + text[end:]
path.write_text(text)

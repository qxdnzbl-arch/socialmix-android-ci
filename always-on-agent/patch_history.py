from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()
old="hist=[e for e in s['events'] if e.get('goal_id')==goal['id']]"
block="""        # A system_context_updated event supersedes obsolete connection/rate-limit history.\n        for i in range(len(hist)-1,-1,-1):\n            if hist[i].get('type')=='system_context_updated':\n                hist=hist[i:]\n                break\n"""

# Collapse any duplicates left by older versions of this patch.
while block + block in s:
    s=s.replace(block + block, block)

if block not in s:
    if old not in s:
        raise SystemExit('history pattern not found')
    s=s.replace(old, old + '\n' + block.rstrip('\n'), 1)
    print('superseded-history filter installed')
else:
    print('superseded-history filter present')

p.write_text(s)

from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()
old="hist=[e for e in s['events'] if e.get('goal_id')==goal['id']]"
new="""hist=[e for e in s['events'] if e.get('goal_id')==goal['id']]\n        # A system_context_updated event supersedes obsolete connection/rate-limit history.\n        for i in range(len(hist)-1,-1,-1):\n            if hist[i].get('type')=='system_context_updated':\n                hist=hist[i:]\n                break"""
if old in s:
    s=s.replace(old,new,1)
    p.write_text(s)
    print('superseded-history filter installed')
elif 'A system_context_updated event supersedes obsolete connection/rate-limit history.' in s:
    print('superseded-history filter already installed')
else:
    raise SystemExit('history pattern not found')

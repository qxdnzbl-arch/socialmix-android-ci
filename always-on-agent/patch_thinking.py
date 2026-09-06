from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()
old="'reasoning_effort':'low','max_tokens':1200"
new="'thinking':{'type':'disabled'},'max_tokens':1200"
if old in s:
    s=s.replace(old,new,1)
    p.write_text(s)
    print('DeepSeek thinking disabled for low-cost JSON planning')
elif new in s:
    print('DeepSeek non-thinking planner already configured')
else:
    raise SystemExit('planner request body pattern not found')

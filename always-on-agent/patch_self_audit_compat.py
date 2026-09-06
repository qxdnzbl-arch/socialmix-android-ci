from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()
old="""            elif task['kind']=='executor_request':
                task['status']='waiting_executor'
"""
new="""            elif task['kind']=='executor_request': task['status']='waiting_executor'
"""
if old in s:
    s=s.replace(old,new,1)
    p.write_text(s)
    print('normalized executor approval routing for self-audit patch')
else:
    print('self-audit compatibility already satisfied')

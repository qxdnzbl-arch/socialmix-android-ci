from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()

# Advertise delegation to the connected executor bridge.
s=s.replace(
    "Allowed action types: web_search, web_get, note, clarify, complete.\n",
    "Allowed action types: web_search, web_get, note, executor_request, clarify, complete.\n"
    "Use executor_request when the next useful step requires a connected service or capability that this runtime cannot directly execute (for example GitHub code changes, Render configuration/deploys, Supabase maintenance, browser/plugin work, files/design/media workflows). Payload must include capability, objective, and params. Do not use note when an actual executable change is the next step.\n",
    1
)
s=s.replace(
    "Payload contracts: web_search requires payload.query or payload.queries; web_get requires payload.url and must only be used when a concrete URL is already known; note uses payload.text; clarify uses payload.question.",
    "Payload contracts: web_search requires payload.query or payload.queries; web_get requires payload.url and must only be used when a concrete URL is already known; note uses payload.text; executor_request requires payload.capability, payload.objective, and optional payload.params; clarify uses payload.question.",
    1
)

# Normalize executor requests without inventing targets.
needle="""    elif kind=='note':\n        text=str(p.get('text') or p.get('message') or p.get('status') or instruction or title or '').strip()\n        p={'text':text}\n    elif kind=='clarify':\n"""
replacement="""    elif kind=='note':\n        text=str(p.get('text') or p.get('message') or p.get('status') or instruction or title or '').strip()\n        p={'text':text}\n    elif kind=='executor_request':\n        cap=str(p.get('capability') or '').strip()\n        objective=str(p.get('objective') or instruction or title or '').strip()\n        params=p.get('params') if isinstance(p.get('params'),dict) else {}\n        if not cap or not objective:\n            kind='clarify'; p={'question':'Executor request is missing a confirmed capability or objective.'}\n        else:\n            p={'capability':cap,'objective':objective,'params':params}\n    elif kind=='clarify':\n"""
if "elif kind=='executor_request':" not in s:
    if needle not in s: raise SystemExit('normalizer note marker not found')
    s=s.replace(needle,replacement,1)

# Executor requests are queued for the hourly connected-tool executor, not executed locally.
old="""        task={'id':tid,'goal_id':goal['id'],'title':str(action.get('title') or 'Next action'),'instruction':str(action.get('instruction') or ''),'kind':action.get('action_type','note'),'status':'pending' if allowed else 'waiting_approval','payload':action.get('payload') or {},'result':{},'requires_approval':not allowed,'attempts':0}\n"""
new="""        task_kind=action.get('action_type','note')\n        task_status=('waiting_executor' if allowed and task_kind=='executor_request' else ('pending' if allowed else 'waiting_approval'))\n        task={'id':tid,'goal_id':goal['id'],'title':str(action.get('title') or 'Next action'),'instruction':str(action.get('instruction') or ''),'kind':task_kind,'status':task_status,'payload':action.get('payload') or {},'result':{},'requires_approval':not allowed,'attempts':0}\n"""
if "task_status=('waiting_executor'" not in s:
    if old not in s: raise SystemExit('task creation marker not found')
    s=s.replace(old,new,1)

# Waiting executor is a real blocker until the connected executor records evidence of completion.
old2="""        blocked=next((t for t in s['tasks'] if t['goal_id']==goal['id'] and t['status'] in ('pending','waiting_approval','running')),None)\n"""
new2="""        blocked=next((t for t in s['tasks'] if t['goal_id']==goal['id'] and t['status'] in ('pending','waiting_approval','waiting_executor','running')),None)\n"""
if "'waiting_executor','running'" not in s:
    if old2 not in s: raise SystemExit('blocked status marker not found')
    s=s.replace(old2,new2,1)

p.write_text(s)
print('executor bridge installed')

from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()

marker="def policy(action):\n"
helper=r'''def _dedup_text(value):
    return re.sub(r'[^a-z0-9]+',' ',str(value or '').lower()).strip()


def action_dedup_key(action):
    """Stable semantic-enough key for exact repeated work, ignoring punctuation/spacing noise."""
    a=action or {}; kind=str(a.get('action_type') or '')
    payload=a.get('payload') if isinstance(a.get('payload'),dict) else {}
    if kind=='executor_request':
        core=[payload.get('capability'),payload.get('objective')]
    elif kind=='web_search':
        q=payload.get('queries') if isinstance(payload.get('queries'),list) else payload.get('query')
        core=[q]
    elif kind=='web_get': core=[payload.get('url')]
    elif kind=='note': core=[payload.get('text') or payload.get('message') or a.get('instruction')]
    elif kind=='clarify': core=[payload.get('question') or a.get('instruction')]
    else: core=[a.get('title'),a.get('instruction')]
    return kind+'|'+_dedup_text(json.dumps(core,ensure_ascii=False,sort_keys=True))


def task_dedup_key(task):
    return action_dedup_key({
        'action_type':task.get('kind'),
        'title':task.get('title'),
        'instruction':task.get('instruction'),
        'payload':task.get('payload') or {}
    })


def repair_duplicate_action(action, state):
    """Do not spend another cycle on an equivalent action already completed or queued."""
    a=dict(action or {})
    if list(a.get('risk_flags') or []): return a, False, None
    key=action_dedup_key(a)
    if not key.split('|',1)[1]: return a, False, None
    duplicate=None
    for t in reversed((state.get('tasks') or [])[-20:]):
        if t.get('status') not in {'completed','pending','running','waiting_executor'}: continue
        if task_dedup_key(t)==key:
            duplicate=t; break
    if not duplicate: return a, False, None
    active=any(g.get('status')=='active' for g in state.get('goals',[]) if isinstance(g,dict))
    if not active: return a, False, None
    repaired={
        'action_type':'executor_request',
        'title':'Skip duplicate work and advance active goal',
        'instruction':'An equivalent action is already completed or queued. Do not repeat it. Read the newest persistent state and execute a different highest-value safe step toward the parent deliverable.',
        'payload':{
            'capability':'continue_active_goal',
            'objective':'Advance the active parent goal without repeating an equivalent completed or queued action. Inspect the latest work contract, evidence, and connected tools, then execute a different highest-value safe step and update the checkpoint.',
            'params':{'duplicate_task_id':duplicate.get('id'),'duplicate_key':key[:180]}
        },
        'risk_flags':[],
        'why':'Deterministic duplicate-action guard prevented repeated work and unnecessary model/tool spend.'
    }
    return repaired, True, duplicate.get('id')

'''
if 'def repair_duplicate_action(' not in s:
    if marker not in s: raise SystemExit('policy marker not found')
    s=s.replace(marker,helper+marker,1)

old="""            action=await planner_decide(goal,hist,s)\n            action,continuity_repaired=repair_continuity_action(action,s)\n            if continuity_repaired:\n                add_event(s,'continuity_action_repaired',goal.get('id'),data={'action_type':action.get('action_type'),'title':action.get('title'),'reason':'passive_wait_or_premature_complete'})\n            print('PLANNER_ACTION', action.get('action_type'), action.get('title'), flush=True)\n"""
new="""            action=await planner_decide(goal,hist,s)\n            action,continuity_repaired=repair_continuity_action(action,s)\n            if continuity_repaired:\n                add_event(s,'continuity_action_repaired',goal.get('id'),data={'action_type':action.get('action_type'),'title':action.get('title'),'reason':'passive_wait_or_premature_complete'})\n            action,duplicate_repaired,duplicate_task_id=repair_duplicate_action(action,s)\n            if duplicate_repaired:\n                add_event(s,'duplicate_action_repaired',goal.get('id'),data={'duplicate_task_id':duplicate_task_id,'action_type':action.get('action_type'),'title':action.get('title')})\n            print('PLANNER_ACTION', action.get('action_type'), action.get('title'), flush=True)\n"""
if old in s:
    s=s.replace(old,new,1)
elif 'action,duplicate_repaired,duplicate_task_id=repair_duplicate_action(action,s)' not in s:
    raise SystemExit('planner dedup insertion marker not found')

p.write_text(s)
print('deterministic duplicate-action suppression installed')

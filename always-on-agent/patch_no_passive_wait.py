from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()

helper=r'''
def repair_continuity_action(action, state):
    """Prevent an active, unblocked goal from turning into passive waiting or premature completion."""
    a=dict(action or {})
    active=any(g.get('status')=='active' for g in state.get('goals',[]) if isinstance(g,dict))
    wc=state.get('work_contract') or {}
    contract_running=wc.get('status')=='running'
    if not active:
        return a, False

    flags=list(a.get('risk_flags') or [])
    if flags:
        # Real owner-only risks must still flow through the normal approval policy.
        return a, False

    typ=str(a.get('action_type') or '')
    payload=a.get('payload') if isinstance(a.get('payload'),dict) else {}
    text=' '.join([
        str(a.get('title') or ''), str(a.get('instruction') or ''),
        str(payload.get('text') or ''), str(payload.get('message') or ''),
        str(payload.get('status') or ''), str(payload.get('question') or '')
    ]).lower()
    passive_markers=(
        'await further owner','awaiting further','wait for owner','waiting for owner',
        'stand by','standby','further owner directives','further instructions from the owner',
        'no further executable','no remaining executable','wait for the next objective',
        'waiting for next objective','await owner directives'
    )
    passive_note=(typ=='note' and any(m in text for m in passive_markers))
    premature_complete=(typ=='complete' and contract_running)
    if not (passive_note or premature_complete):
        return a, False

    repaired={
        'action_type':'executor_request',
        'title':'Continue active goal without passive waiting',
        'instruction':'Continue the active goal from persistent state. Do not stop at a progress note; execute the highest-value safe unblocked next step and verify its result.',
        'payload':{
            'capability':'continue_active_goal',
            'objective':'Inspect the active goal, persistent work contract, recent evidence and connected tools; choose and execute the highest-value safe unblocked next step toward a verified deliverable, then update the checkpoint.',
            'params':{
                'original_action_type':typ,
                'original_title':str(a.get('title') or ''),
                'original_instruction':str(a.get('instruction') or ''),
                'work_contract_status':wc.get('status')
            }
        },
        'risk_flags':[],
        'why':'Hard continuity guard converted passive waiting/premature completion into an executable handoff while the parent goal remains active.'
    }
    return repaired, True
'''

if 'def repair_continuity_action(action, state):' not in s:
    anchor='def policy(action):\n'
    if anchor not in s: raise SystemExit('policy anchor missing')
    s=s.replace(anchor,helper+'\n'+anchor,1)

old="""            action=await planner_decide(goal,hist,s)
            print('PLANNER_ACTION', action.get('action_type'), action.get('title'), flush=True)
"""
new="""            action=await planner_decide(goal,hist,s)
            action,continuity_repaired=repair_continuity_action(action,s)
            if continuity_repaired:
                add_event(s,'continuity_action_repaired',goal.get('id'),data={'action_type':action.get('action_type'),'title':action.get('title'),'reason':'passive_wait_or_premature_complete'})
            print('PLANNER_ACTION', action.get('action_type'), action.get('title'), flush=True)
"""
if old in s:
    s=s.replace(old,new,1)
elif 'continuity_action_repaired' not in s:
    raise SystemExit('planner action anchor missing')

p.write_text(s)
print('hard no-passive-wait continuity guard installed')

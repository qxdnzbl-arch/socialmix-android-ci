from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()

# Make tool payload contracts explicit for the planner.
needle="Allowed action types: web_search, web_get, note, clarify, complete.\nSchema: {\"action_type\":\"...\",\"title\":\"...\",\"instruction\":\"...\",\"payload\":{},\"risk_flags\":[],\"why\":\"...\"}\n"
extra=(
    "Allowed action types: web_search, web_get, note, clarify, complete.\n"
    "Payload contracts: web_search requires payload.query or payload.queries; web_get requires payload.url and must only be used when a concrete URL is already known; note uses payload.text; clarify uses payload.question. If you need a source but do not yet have a concrete URL, use web_search first.\n"
    "Schema: {\"action_type\":\"...\",\"title\":\"...\",\"instruction\":\"...\",\"payload\":{},\"risk_flags\":[],\"why\":\"...\"}\n"
)
if 'Payload contracts: web_search requires' not in s:
    if needle not in s:
        raise SystemExit('system rule schema marker not found')
    s=s.replace(needle,extra,1)

# Add a deterministic repair layer so malformed planner payloads do not waste retries or require the owner.
marker="async def run_tool(kind,payload):\n"
helper=r'''def normalize_tool_request(kind,payload,title='',instruction=''):
    kind=str(kind or '').strip()
    aliases={'search':'web_search','websearch':'web_search','web-search':'web_search','fetch':'web_get','browse':'web_get','read_url':'web_get','get_url':'web_get'}
    kind=aliases.get(kind,kind)
    p=dict(payload or {}) if isinstance(payload,dict) else {}
    fallback=(str(title or '').strip()+' '+str(instruction or '').strip()).strip()
    if kind=='web_get':
        url=str(p.get('url','')).strip()
        if url.startswith(('http://','https://')):
            p={'url':url}
        else:
            q=str(p.get('query','')).strip() or fallback
            if not q:
                q='persistent autonomous cloud agent architecture state scheduling self-healing'
            kind='web_search'; p={'query':q,'count':6}
    elif kind=='web_search':
        one=str(p.get('query','')).strip()
        many=p.get('queries') if isinstance(p.get('queries'),list) else []
        many=[str(x).strip() for x in many if str(x).strip()]
        if one:
            p={'query':one,'count':max(1,min(int(p.get('count',8)),12))}
        elif many:
            p={'queries':many[:4],'count':max(1,min(int(p.get('count',8)),12))}
        else:
            p={'query':fallback or 'persistent autonomous cloud agent architecture state scheduling self-healing','count':6}
    elif kind=='note':
        text=str(p.get('text') or p.get('message') or p.get('status') or instruction or title or '').strip()
        p={'text':text}
    elif kind=='clarify':
        q=str(p.get('question') or instruction or title or 'Owner clarification required.').strip()
        p={'question':q}
    elif kind not in {'complete'}:
        kind='note'; p={'text':fallback or f'Unsupported action normalized from {kind}.'}
    return kind,p

'''
if 'def normalize_tool_request(' not in s:
    if marker not in s:
        raise SystemExit('run_tool marker not found')
    s=s.replace(marker,helper+marker,1)

# Repair already-created pending tasks before executing them.
old="""            try:\n                result=await run_tool(pending['kind'],pending.get('payload') or {})\n"""
new="""            try:\n                repaired_kind,repaired_payload=normalize_tool_request(pending.get('kind'),pending.get('payload') or {},pending.get('title',''),pending.get('instruction',''))\n                if repaired_kind != pending.get('kind') or repaired_payload != (pending.get('payload') or {}):\n                    pending['kind']=repaired_kind; pending['payload']=repaired_payload\n                    add_event(s,'task_repaired',pending.get('goal_id'),pending.get('id'),{'kind':repaired_kind,'payload':repaired_payload})\n                    await store_set(s)\n                result=await run_tool(pending['kind'],pending.get('payload') or {})\n"""
if 'task_repaired' not in s:
    if old not in s:
        raise SystemExit('pending execution marker not found')
    s=s.replace(old,new,1)

# Normalize new planner actions before policy and task creation.
old2="""        if action.get('action_type')=='complete':\n            goal['status']='completed'; add_event(s,'goal_completed',goal['id'],data=action); await store_set(s); return {'status':'completed','goal_id':goal['id']}\n        allowed,cat=policy(action); tid=s['next_ids']['task']; s['next_ids']['task']+=1\n"""
new2="""        if action.get('action_type')=='complete':\n            goal['status']='completed'; add_event(s,'goal_completed',goal['id'],data=action); await store_set(s); return {'status':'completed','goal_id':goal['id']}\n        normalized_kind,normalized_payload=normalize_tool_request(action.get('action_type'),action.get('payload') or {},action.get('title',''),action.get('instruction',''))\n        if normalized_kind != action.get('action_type') or normalized_payload != (action.get('payload') or {}):\n            action=dict(action); action['action_type']=normalized_kind; action['payload']=normalized_payload\n            add_event(s,'planner_action_repaired',goal['id'],data={'action_type':normalized_kind,'payload':normalized_payload})\n        allowed,cat=policy(action); tid=s['next_ids']['task']; s['next_ids']['task']+=1\n"""
if 'planner_action_repaired' not in s:
    if old2 not in s:
        raise SystemExit('planner action marker not found')
    s=s.replace(old2,new2,1)

p.write_text(s)
print('action normalization guard installed')

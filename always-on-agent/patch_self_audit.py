from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()

# Import hashing for compact checkpoint fingerprints.
if 'import asyncio, hashlib, json, os, re, time, traceback' not in s:
    s=s.replace('import asyncio, json, os, re, time, traceback','import asyncio, hashlib, json, os, re, time, traceback',1)

# Persistent audit/checkpoint control state.
old_default="DEFAULT_STATE = {'goals': [], 'tasks': [], 'approvals': [], 'events': [], 'memories': {}, 'budget': {'day':'','planner_calls':0,'last_call_at':0.0,'day_start_balance_cny':None,'last_balance_cny':None,'last_usage':{},'input_tokens_today':0,'output_tokens_today':0,'paused':False,'pause_reason':''}, 'next_ids': {'goal': 1, 'task': 1, 'approval': 1, 'event': 1}}"
new_default="DEFAULT_STATE = {'goals': [], 'tasks': [], 'approvals': [], 'events': [], 'memories': {}, 'audit': {'last_run_at':'','last_daily':'','last_event_id':0,'last_result':{},'runs':0}, 'checkpoints': [], 'system_guard': {'paused':False,'reason':'','issues':[],'updated_at':''}, 'budget': {'day':'','planner_calls':0,'last_call_at':0.0,'day_start_balance_cny':None,'last_balance_cny':None,'last_usage':{},'input_tokens_today':0,'output_tokens_today':0,'paused':False,'pause_reason':''}, 'next_ids': {'goal': 1, 'task': 1, 'approval': 1, 'event': 1}}"
if "'system_guard':" not in s:
    if old_default not in s: raise SystemExit('DEFAULT_STATE marker not found')
    s=s.replace(old_default,new_default,1)

# Install audit/checkpoint functions after add_event.
marker="""def policy(action):\n"""
helper=r'''RISK_FLAG_TO_CATEGORY={'uncertain_fact':'uncertainty','spend_money':'spending','use_owner_identity':'identity','make_formal_commitment':'formal_commitment','upload_private_data':'private_upload','destructive_action':'destructive','change_credentials':'credential_change'}

def _checkpoint_core(s):
    return {
        'goals':[{'id':g.get('id'),'status':g.get('status'),'priority':g.get('priority')} for g in s.get('goals',[])],
        'tasks':[{'id':t.get('id'),'goal_id':t.get('goal_id'),'kind':t.get('kind'),'status':t.get('status'),'requires_approval':bool(t.get('requires_approval'))} for t in s.get('tasks',[])],
        'approvals':[{'id':a.get('id'),'task_id':a.get('task_id'),'status':a.get('status'),'category':a.get('category')} for a in s.get('approvals',[])],
        'next_ids':dict(s.get('next_ids') or {}),
        'guard':dict(s.get('system_guard') or {}),
    }

def record_checkpoint(s, reason, goal_id=None, task_id=None):
    core=_checkpoint_core(s)
    digest=hashlib.sha256(json.dumps(core,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode('utf-8')).hexdigest()
    cp={'at':now(),'reason':reason,'goal_id':goal_id,'task_id':task_id,'digest':digest,'counts':{'goals':len(s.get('goals',[])),'tasks':len(s.get('tasks',[])),'approvals':len(s.get('approvals',[])),'events':len(s.get('events',[]))},'next_ids':dict(s.get('next_ids') or {})}
    arr=s.setdefault('checkpoints',[]); arr.append(cp)
    if len(arr)>50: s['checkpoints']=arr[-50:]
    return cp

def self_audit(s, trigger='periodic', goal_id=None, task_id=None):
    issues=[]; repairs=[]

    # 1) IDs must be unique. next_ids can be repaired deterministically.
    for plural,singular in [('goals','goal'),('tasks','task'),('approvals','approval'),('events','event')]:
        vals=[x.get('id') for x in s.get(plural,[]) if isinstance(x,dict) and isinstance(x.get('id'),int)]
        if len(vals)!=len(set(vals)):
            issues.append({'severity':'critical','code':'duplicate_ids','resource':plural})
        max_id=max(vals,default=0)
        nxt=int((s.get('next_ids') or {}).get(singular,1) or 1)
        if nxt<=max_id:
            s.setdefault('next_ids',{})[singular]=max_id+1
            repairs.append({'code':'next_id_advanced','resource':plural,'from':nxt,'to':max_id+1})

    approvals_by_task={}
    for a in s.get('approvals',[]):
        approvals_by_task.setdefault(a.get('task_id'),[]).append(a)

    # 2) Executor requests must never fall into the local tool runner.
    for t in s.get('tasks',[]):
        if t.get('kind')=='executor_request' and t.get('status')=='pending' and not t.get('requires_approval'):
            t['status']='waiting_executor'
            repairs.append({'code':'executor_rerouted','task_id':t.get('id')})
        if t.get('status')=='waiting_executor' and t.get('requires_approval'):
            t['requires_approval']=False
            repairs.append({'code':'executor_approval_flag_cleared','task_id':t.get('id')})

        # 3) A task waiting for approval must actually have a pending approval record.
        if t.get('status')=='waiting_approval':
            aps=approvals_by_task.get(t.get('id'),[])
            if not any(a.get('status')=='pending' for a in aps):
                issues.append({'severity':'critical','code':'missing_pending_approval','task_id':t.get('id')})

        # 4) Risky actions may only complete if there is explicit approval evidence.
        flags=[f for f in (t.get('risk_flags') or []) if f in RISK_FLAG_TO_CATEGORY]
        if t.get('status')=='completed' and flags:
            aps=approvals_by_task.get(t.get('id'),[])
            if not any(a.get('status')=='approved' for a in aps):
                issues.append({'severity':'critical','code':'risky_task_completed_without_approval','task_id':t.get('id'),'flags':flags})

    # 5) Running tasks are allowed, but impossible status/kind combinations are not.
    valid_status={'pending','running','completed','waiting_approval','waiting_executor','rejected','failed'}
    for t in s.get('tasks',[]):
        if t.get('status') not in valid_status:
            issues.append({'severity':'critical','code':'invalid_task_status','task_id':t.get('id'),'status':t.get('status')})
        if t.get('kind')=='executor_request' and t.get('status')=='running':
            issues.append({'severity':'critical','code':'executor_running_locally','task_id':t.get('id')})

    critical=[x for x in issues if x.get('severity')=='critical']
    guard=s.setdefault('system_guard',{})
    guard.update({'paused':bool(critical),'reason':critical[0]['code'] if critical else '', 'issues':critical[:20], 'updated_at':now()})

    audit=s.setdefault('audit',{})
    audit['last_run_at']=now(); audit['runs']=int(audit.get('runs') or 0)+1
    audit['last_result']={'ok':not critical,'trigger':trigger,'issues':issues[:30],'repairs':repairs[:30]}

    add_event(s,'self_audit',goal_id,task_id,{'ok':not critical,'trigger':trigger,'issues':issues[:20],'repairs':repairs[:20]})
    audit['last_event_id']=int((s.get('events') or [{}])[-1].get('id') or 0)
    cp=record_checkpoint(s,'self_audit:'+trigger,goal_id,task_id)
    audit['last_checkpoint_digest']=cp['digest']
    return audit['last_result']

'''
if 'def self_audit(' not in s:
    if marker not in s: raise SystemExit('policy marker not found')
    s=s.replace(marker,helper+marker,1)

# Reuse the common risk map in policy.
old_policy="""def policy(action):\n    m={'uncertain_fact':'uncertainty','spend_money':'spending','use_owner_identity':'identity','make_formal_commitment':'formal_commitment','upload_private_data':'private_upload','destructive_action':'destructive','change_credentials':'credential_change'}\n    for f in action.get('risk_flags') or []:\n        if f in m: return False, m[f]\n    return True, None\n"""
new_policy="""def policy(action):\n    for f in action.get('risk_flags') or []:\n        if f in RISK_FLAG_TO_CATEGORY: return False, RISK_FLAG_TO_CATEGORY[f]\n    return True, None\n"""
if old_policy in s:
    s=s.replace(old_policy,new_policy,1)

# Persist risk evidence on tasks.
old_task="""task={'id':tid,'goal_id':goal['id'],'title':str(action.get('title') or 'Next action'),'instruction':str(action.get('instruction') or ''),'kind':task_kind,'status':task_status,'payload':action.get('payload') or {},'result':{},'requires_approval':not allowed,'attempts':0}\n"""
new_task="""task={'id':tid,'goal_id':goal['id'],'title':str(action.get('title') or 'Next action'),'instruction':str(action.get('instruction') or ''),'kind':task_kind,'status':task_status,'payload':action.get('payload') or {},'result':{},'risk_flags':action.get('risk_flags') or [],'requires_approval':not allowed,'attempts':0}\n"""
if "'risk_flags':action.get('risk_flags')" not in s:
    if old_task not in s: raise SystemExit('task creation marker not found')
    s=s.replace(old_task,new_task,1)

# At the start of each cycle, audit any externally changed state and once per UTC day.
needle="""        s=await store_get()\n        # One-time recovery: the owner has now configured the real reasoning model.\n"""
replacement="""        s=await store_get()\n        audit=s.setdefault('audit',{})\n        latest_event_id=int((s.get('events') or [{}])[-1].get('id') or 0)\n        today=datetime.now(timezone.utc).strftime('%Y-%m-%d')\n        need_daily=audit.get('last_daily') != today\n        need_change=latest_event_id > int(audit.get('last_event_id') or 0)\n        if need_daily or need_change:\n            self_audit(s,'daily' if need_daily else 'state_change')\n            if need_daily: s['audit']['last_daily']=today\n            await store_set(s)\n        if (s.get('system_guard') or {}).get('paused'):\n            return {'status':'guard_paused','reason':(s.get('system_guard') or {}).get('reason'),'issues':(s.get('system_guard') or {}).get('issues',[])[:5]}\n        # One-time recovery: the owner has now configured the real reasoning model.\n"""
if 'need_change=latest_event_id >' not in s:
    if needle not in s: raise SystemExit('tick start marker not found')
    s=s.replace(needle,replacement,1)

# Audit after every local execution attempt before persisting final task state.
old_store="""            await store_set(s); return {'status':pending['status'],'task_id':pending['id']}\n"""
new_store="""            self_audit(s,'task_execution',pending.get('goal_id'),pending.get('id'))\n            await store_set(s); return {'status':pending['status'],'task_id':pending['id']}\n"""
if "self_audit(s,'task_execution'" not in s:
    if old_store not in s: raise SystemExit('task execution store marker not found')
    s=s.replace(old_store,new_store,1)

# Audit every new plan/approval request before state is committed.
old_plan_store="""        await store_set(s); return {'status':task['status'],'goal_id':goal['id'],'task_id':tid}\n"""
new_plan_store="""        self_audit(s,'task_planning',goal.get('id'),tid)\n        await store_set(s); return {'status':task['status'],'goal_id':goal['id'],'task_id':tid}\n"""
if "self_audit(s,'task_planning'" not in s:
    if old_plan_store not in s: raise SystemExit('task planning store marker not found')
    s=s.replace(old_plan_store,new_plan_store,1)

# Approval of an executor request routes it to the connected executor, never the local runner.
old_approve="""            if task['kind']=='clarify': task['status']='completed'; task['result']={'owner_answer':body.note}; add_event(s,'task_completed',task['goal_id'],task['id'],task['result'])\n            else: task['status']='pending'\n"""
new_approve="""            if task['kind']=='clarify': task['status']='completed'; task['result']={'owner_answer':body.note}; add_event(s,'task_completed',task['goal_id'],task['id'],task['result'])\n            elif task['kind']=='executor_request': task['status']='waiting_executor'\n            else: task['status']='pending'\n"""
if "elif task['kind']=='executor_request': task['status']='waiting_executor'" not in s:
    if old_approve not in s: raise SystemExit('approval routing marker not found')
    s=s.replace(old_approve,new_approve,1)

old_decide_store="""        add_event(s,'approval_decided',task['goal_id'],task['id'],{'approved':body.approve,'note':body.note}); await store_set(s); return {'ok':True,'task_status':task['status']}\n"""
new_decide_store="""        add_event(s,'approval_decided',task['goal_id'],task['id'],{'approved':body.approve,'note':body.note}); self_audit(s,'approval_decision',task.get('goal_id'),task.get('id')); await store_set(s); return {'ok':True,'task_status':task['status']}\n"""
if "self_audit(s,'approval_decision'" not in s:
    if old_decide_store not in s: raise SystemExit('approval decision marker not found')
    s=s.replace(old_decide_store,new_decide_store,1)

# Surface audit/guard status on health endpoint.
old_health="""    return {'ok':ok,'version':'0.4.0','store':detail,'llm_mode':LLM_MODE,'llm_provider':LLM_PROVIDER,'budget_guard':True}\n"""
new_health="""    guard=(s.get('system_guard') or {}) if ok else {}\n    audit=(s.get('audit') or {}) if ok else {}\n    return {'ok':ok and not bool(guard.get('paused')),'version':'0.5.0','store':detail,'llm_mode':LLM_MODE,'llm_provider':LLM_PROVIDER,'budget_guard':True,'system_guard':guard,'audit':{'last_run_at':audit.get('last_run_at'),'last_result':audit.get('last_result'),'runs':audit.get('runs',0)}}\n"""
if "'version':'0.5.0'" not in s:
    if old_health not in s: raise SystemExit('health marker not found')
    s=s.replace(old_health,new_health,1)

s=s.replace("app=FastAPI(title='Always-On Owner Agent',version='0.4.0')","app=FastAPI(title='Always-On Owner Agent',version='0.5.0')",1)
s=s.replace('<h1>Always-On Agent v0.2</h1>','<h1>Always-On Agent v0.5</h1>',1)

p.write_text(s)
print('persistent self-audit/checkpoint guard installed')

from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()

# 1) Planner guidance: internal reversible owner-project work is not a formal external commitment.
needle="2. Spending, owner identity use, formal external commitments, private uploads, destructive actions, or credential changes must wait for approval.\n"
base_rules=(
"2a. Internal, reversible code/config/deploy/database maintenance on the owner's already-confirmed projects is NOT a formal external commitment and must not be flagged make_formal_commitment. Only commitments to external parties (for example sending an application, quote, bid, contract, purchase, payment, or signed promise) use that flag.\n"
"2b. Internal technical failures are not owner blockers. Repair, change path, or emit executor_request for connected-tool repair; do not ask the owner to approve ordinary debugging, CI fixes, deployment fixes, search fixes, or reversible project maintenance.\n"
"2c. If the owner has asked for a result, keep the goal active until a verified deliverable exists or a genuinely owner-only action is required. Do not treat progress notes as completion.\n"
"2d. Passive waiting is prohibited while an active goal exists and there is no genuine owner-only blocker. Never emit a note whose purpose is to wait for further owner directives, wait for the next objective, or stop after recording progress. Choose the next executable action instead. Use note only when recording evidence is itself the useful action.\n"
"2e. A delivered subtask or work contract does not end an active parent goal. If the parent goal remains active, immediately choose the next highest-value verified subtask instead of waiting for instructions.\n"
)
if '2a. Internal, reversible code/config/deploy/database maintenance' not in s:
    if needle not in s: raise SystemExit('SYSTEM_RULES anchor missing')
    s=s.replace(needle,needle+base_rules,1)
else:
    anchor="2c. If the owner has asked for a result, keep the goal active until a verified deliverable exists or a genuinely owner-only action is required. Do not treat progress notes as completion.\n"
    extra=(
    "2d. Passive waiting is prohibited while an active goal exists and there is no genuine owner-only blocker. Never emit a note whose purpose is to wait for further owner directives, wait for the next objective, or stop after recording progress. Choose the next executable action instead. Use note only when recording evidence is itself the useful action.\n"
    "2e. A delivered subtask or work contract does not end an active parent goal. If the parent goal remains active, immediately choose the next highest-value verified subtask instead of waiting for instructions.\n"
    )
    if '2d. Passive waiting is prohibited' not in s:
        if anchor not in s: raise SystemExit('continuity rule anchor missing')
        s=s.replace(anchor,anchor+extra,1)

# 1b) Give the planner the durable work contract and current verification state.
old_ctx="json.dumps({'goal':goal,'recent_history':history[-30:]},ensure_ascii=False)"
new_ctx="json.dumps({'goal':goal,'work_contract':state.get('work_contract') or {},'system_guard':state.get('system_guard') or {},'audit_last_result':(state.get('audit') or {}).get('last_result') or {},'recent_history':history[-30:]},ensure_ascii=False)"
if old_ctx in s:
    s=s.replace(old_ctx,new_ctx,1)
elif "'work_contract':state.get('work_contract')" not in s:
    raise SystemExit('planner context anchor missing')

# 2) Policy guard: strip false formal-commitment flags from known internal executor capabilities.
old="""def policy(action):
    m={'uncertain_fact':'uncertainty','spend_money':'spending','use_owner_identity':'identity','make_formal_commitment':'formal_commitment','upload_private_data':'private_upload','destructive_action':'destructive','change_credentials':'credential_change'}
    for f in action.get('risk_flags') or []:
        if f in m: return False, m[f]
    return True, None
"""
new="""def policy(action):
    flags=list(action.get('risk_flags') or [])
    if action.get('action_type')=='executor_request':
        p=action.get('payload') or {}
        cap=str(p.get('capability') or '').strip().lower()
        internal_caps={
            'deployment_and_code_change','runtime_debug_and_repair','github_code_change',
            'render_deploy','supabase_maintenance','internal_project_change',
            'code_change','deployment','database_maintenance'
        }
        if cap in internal_caps and 'make_formal_commitment' in flags:
            flags=[f for f in flags if f!='make_formal_commitment']
            action['risk_flags']=flags
    m={'uncertain_fact':'uncertainty','spend_money':'spending','use_owner_identity':'identity','make_formal_commitment':'formal_commitment','upload_private_data':'private_upload','destructive_action':'destructive','change_credentials':'credential_change'}
    for f in flags:
        if f in m: return False, m[f]
    return True, None
"""
if old in s:
    s=s.replace(old,new,1)
elif 'internal_caps={' not in s:
    raise SystemExit('policy function anchor missing')

# 3) Internal repeated failures escalate to the connected executor instead of asking the owner.
old2="""                if pending['attempts']>=MAX_ATTEMPTS:
                    pending['status']='waiting_approval'; pending['requires_approval']=True
                    aid=s['next_ids']['approval']; s['next_ids']['approval']+=1
                    s['approvals'].append({'id':aid,'task_id':pending['id'],'category':'failure','summary':f\"Task failed {pending['attempts']} times: {e}\",'status':'pending','decision_note':''})
                else: pending['status']='pending'
"""
new2="""                if pending['attempts']>=MAX_ATTEMPTS:
                    original_kind=pending.get('kind')
                    original_payload=pending.get('payload') or {}
                    pending['kind']='executor_request'
                    pending['status']='waiting_executor'; pending['requires_approval']=False
                    pending['payload']={
                        'capability':'runtime_debug_and_repair',
                        'objective':f\"Repair the internal execution failure and continue the original task: {pending.get('title','task')}\",
                        'params':{
                            'original_kind':original_kind,
                            'original_payload':original_payload,
                            'last_error':str(e),
                            'attempts':pending['attempts']
                        }
                    }
                    add_event(s,'task_escalated_to_executor',pending['goal_id'],pending['id'],{'error':str(e),'capability':'runtime_debug_and_repair'})
                else: pending['status']='pending'
"""
if old2 in s:
    s=s.replace(old2,new2,1)
elif "task_escalated_to_executor" not in s:
    raise SystemExit('failure escalation anchor missing')

# 4) Approval endpoint must return executor requests to the executor queue, never local pending execution.
old3="""            if task['kind']=='clarify': task['status']='completed'; task['result']={'owner_answer':body.note}; add_event(s,'task_completed',task['goal_id'],task['id'],task['result'])
            else: task['status']='pending'
"""
new3="""            if task['kind']=='clarify':
                task['status']='completed'; task['result']={'owner_answer':body.note}; add_event(s,'task_completed',task['goal_id'],task['id'],task['result'])
            elif task['kind']=='executor_request':
                task['status']='waiting_executor'
            else:
                task['status']='pending'
"""
if old3 in s:
    s=s.replace(old3,new3,1)
elif "elif task['kind']=='executor_request'" not in s:
    raise SystemExit('approval routing anchor missing')

p.write_text(s)
print('execution continuity hardening installed')

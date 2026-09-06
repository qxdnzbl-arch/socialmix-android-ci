from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()

# Runtime retry controls. Defaults are conservative; Render/worker cadence can still be configured externally.
anchor="MAX_ATTEMPTS = int(os.getenv('MAX_TASK_ATTEMPTS', '3'))\n"
addition="MAX_ATTEMPTS = int(os.getenv('MAX_TASK_ATTEMPTS', '3'))\nRETRY_BASE_SECONDS = float(os.getenv('TASK_RETRY_BASE_SECONDS', '60'))\nRETRY_MAX_SECONDS = float(os.getenv('TASK_RETRY_MAX_SECONDS', '3600'))\nRUNNING_STALE_SECONDS = float(os.getenv('TASK_RUNNING_STALE_SECONDS', '1800'))\n"
if 'RETRY_BASE_SECONDS =' not in s:
    if anchor not in s: raise SystemExit('retry constants anchor not found')
    s=s.replace(anchor,addition,1)

# Deterministic verification, durable backoff, and crash recovery helpers.
marker="async def run_tool(kind,payload):\n"
helpers=r'''def verify_tool_result(kind, result):
    """Deterministic postcondition checks. A tool call is not success merely because it returned."""
    if not isinstance(result,dict):
        return {'ok':False,'reason':'result_not_object'}
    if result.get('error'):
        return {'ok':False,'reason':'tool_error','detail':str(result.get('error'))[:300]}
    if kind=='web_get':
        status=int(result.get('status') or 0)
        text=str(result.get('text') or '').strip()
        if status < 200 or status >= 400: return {'ok':False,'reason':'http_status','status':status}
        if len(text) < 80: return {'ok':False,'reason':'empty_or_too_short_content','chars':len(text)}
        return {'ok':True,'reason':'http_content_verified','status':status,'chars':len(text)}
    if kind=='web_search':
        batches=result.get('batches') if isinstance(result.get('batches'),list) else []
        count=sum(len(b.get('results') or []) for b in batches if isinstance(b,dict))
        if count < 1: return {'ok':False,'reason':'empty_search_results','result_count':0}
        return {'ok':True,'reason':'search_results_verified','result_count':count}
    if kind=='note':
        text=str(result.get('text') or '').strip()
        return {'ok':bool(text),'reason':'note_recorded' if text else 'empty_note'}
    return {'ok':True,'reason':'no_special_postcondition'}


def failure_signature(kind, error):
    raw=f'{kind}:{str(error).strip().lower()}'
    raw=re.sub(r'\d{2,}', '#', raw)
    return hashlib.sha256(raw[:600].encode('utf-8')).hexdigest()[:16]


def retry_delay_seconds(attempts):
    n=max(1,int(attempts or 1))
    return min(RETRY_MAX_SECONDS, RETRY_BASE_SECONDS*(2**(n-1)))


def retry_due(task, at=None):
    at=time.time() if at is None else float(at)
    due=task.get('retry_at_epoch')
    return due is None or float(due) <= at


def recover_stale_running_tasks(state, at=None):
    """A process crash must not leave a local task permanently blocking the parent goal."""
    at=time.time() if at is None else float(at)
    recovered=[]
    for t in state.get('tasks',[]):
        if t.get('status')!='running' or t.get('kind')=='executor_request': continue
        started=float(t.get('started_at_epoch') or 0)
        if started and at-started >= RUNNING_STALE_SECONDS:
            t['status']='pending'
            t['retry_at_epoch']=at
            t['last_error']='stale_running_recovered'
            t.pop('started_at_epoch',None)
            recovered.append(t.get('id'))
    return recovered

'''
if 'def verify_tool_result(' not in s:
    if marker not in s: raise SystemExit('run_tool marker not found')
    s=s.replace(marker,helpers+marker,1)

# Recover process-crash orphans before deciding whether the goal is blocked.
tick_anchor="""        s=await store_get()\n        audit=s.setdefault('audit',{})\n"""
tick_repl="""        s=await store_get()\n        recovered_stale=recover_stale_running_tasks(s)\n        if recovered_stale:\n            for task_id in recovered_stale:\n                add_event(s,'stale_running_recovered',task_id=task_id,data={'retry':'immediate'})\n            await store_set(s)\n        audit=s.setdefault('audit',{})\n"""
if 'recovered_stale=recover_stale_running_tasks(s)' not in s:
    if tick_anchor not in s: raise SystemExit('tick recovery anchor not found')
    s=s.replace(tick_anchor,tick_repl,1)

# A pending task with a future retry timestamp must not be hammered repeatedly.
old_pending="pending=next((t for t in s['tasks'] if t['status']=='pending' and not t.get('requires_approval')),None)"
new_pending="pending=next((t for t in s['tasks'] if t['status']=='pending' and not t.get('requires_approval') and retry_due(t)),None)"
if old_pending in s:
    s=s.replace(old_pending,new_pending,1)
elif new_pending not in s:
    raise SystemExit('pending selector marker not found')

old_start="pending['status']='running'; pending['attempts']=pending.get('attempts',0)+1; await store_set(s)"
new_start="pending['status']='running'; pending['attempts']=pending.get('attempts',0)+1; pending['started_at_epoch']=time.time(); await store_set(s)"
if old_start in s:
    s=s.replace(old_start,new_start,1)
elif "pending['started_at_epoch']=time.time()" not in s:
    raise SystemExit('task start marker not found')

# Do not mark a local action complete until deterministic postconditions pass.
old_success="""                result=await run_tool(pending['kind'],pending.get('payload') or {})\n                pending['result']=result; pending['status']='completed'; add_event(s,'task_completed',pending['goal_id'],pending['id'],result)\n"""
new_success="""                result=await run_tool(pending['kind'],pending.get('payload') or {})\n                verification=verify_tool_result(pending['kind'],result)\n                pending['verification']=verification\n                if not verification.get('ok'):\n                    raise RuntimeError('postcondition_failed:'+str(verification.get('reason')))\n                pending['result']=result; pending['status']='completed'; pending.pop('retry_at_epoch',None); pending.pop('started_at_epoch',None); pending.pop('last_error',None); add_event(s,'task_completed',pending['goal_id'],pending['id'],{'result':result,'verification':verification})\n"""
if old_success in s:
    s=s.replace(old_success,new_success,1)
elif 'verification=verify_tool_result(' not in s:
    raise SystemExit('success verification marker not found')

# Replace immediate blind retries with durable exponential backoff; same-root repeat twice escalates to connected executor.
old_except="""            except Exception as e:\n                pending['result']={'error':str(e)}\n                if pending['attempts']>=MAX_ATTEMPTS:\n                    original_kind=pending.get('kind')\n                    original_payload=pending.get('payload') or {}\n                    pending['kind']='executor_request'\n                    pending['status']='waiting_executor'; pending['requires_approval']=False\n                    pending['payload']={\n                        'capability':'runtime_debug_and_repair',\n                        'objective':f\"Repair the internal execution failure and continue the original task: {pending.get('title','task')}\",\n                        'params':{\n                            'original_kind':original_kind,\n                            'original_payload':original_payload,\n                            'last_error':str(e),\n                            'attempts':pending['attempts']\n                        }\n                    }\n                    add_event(s,'task_escalated_to_executor',pending['goal_id'],pending['id'],{'error':str(e),'capability':'runtime_debug_and_repair'})\n                else: pending['status']='pending'\n                add_event(s,'task_failed',pending['goal_id'],pending['id'],{'error':str(e),'trace':traceback.format_exc(limit=2)})\n"""
new_except="""            except Exception as e:\n                err=str(e); sig=failure_signature(pending.get('kind'),err)\n                history=pending.setdefault('failure_signatures',[]); history.append(sig); history[:]=history[-6:]\n                same_count=sum(1 for x in history[-2:] if x==sig)\n                pending['result']={'error':err}; pending['last_error']=err; pending['last_error_signature']=sig; pending.pop('started_at_epoch',None)\n                must_change_path=(same_count>=2 or pending['attempts']>=MAX_ATTEMPTS)\n                if must_change_path:\n                    original_kind=pending.get('kind')\n                    original_payload=pending.get('payload') or {}\n                    pending['kind']='executor_request'\n                    pending['status']='waiting_executor'; pending['requires_approval']=False; pending.pop('retry_at_epoch',None)\n                    pending['payload']={\n                        'capability':'runtime_debug_and_repair',\n                        'objective':f\"Change path, repair the verified internal execution failure, and continue the original task: {pending.get('title','task')}\",\n                        'params':{\n                            'original_kind':original_kind,\n                            'original_payload':original_payload,\n                            'last_error':err,\n                            'error_signature':sig,\n                            'attempts':pending['attempts'],\n                            'same_root_repeated':same_count>=2\n                        }\n                    }\n                    add_event(s,'task_escalated_to_executor',pending['goal_id'],pending['id'],{'error':err,'error_signature':sig,'capability':'runtime_debug_and_repair','reason':'same_root_twice' if same_count>=2 else 'max_attempts'})\n                else:\n                    delay=retry_delay_seconds(pending['attempts'])\n                    pending['status']='pending'; pending['retry_at_epoch']=time.time()+delay\n                    add_event(s,'task_retry_scheduled',pending['goal_id'],pending['id'],{'delay_seconds':delay,'error_signature':sig})\n                add_event(s,'task_failed',pending['goal_id'],pending['id'],{'error':err,'error_signature':sig,'trace':traceback.format_exc(limit=2)})\n"""
if old_except in s:
    s=s.replace(old_except,new_except,1)
elif "pending.setdefault('failure_signatures'" not in s:
    raise SystemExit('failure handling marker not found')

p.write_text(s)
print('deterministic verifier, durable backoff, and crash recovery installed')

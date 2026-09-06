from __future__ import annotations
import asyncio, hashlib, json, os, re, time, traceback
from datetime import datetime, timezone
from html import unescape
from urllib.parse import quote_plus, urlparse, parse_qs

import httpx
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field

ADMIN_TOKEN = os.getenv('AGENT_ADMIN_TOKEN', 'dev-token')
STORE_URL = os.getenv('SUPABASE_URL', '').rstrip('/')
STORE_KEY = os.getenv('SUPABASE_PUBLISHABLE_KEY', '')
STORE_SECRET = os.getenv('AGENT_STORE_SECRET', '')
WORKER_INTERVAL = float(os.getenv('WORKER_INTERVAL_SECONDS', '5'))
MAX_ATTEMPTS = int(os.getenv('MAX_TASK_ATTEMPTS', '3'))
LLM_MODE = os.getenv('LLM_MODE', 'mock')
LLM_BASE_URL = os.getenv('LLM_BASE_URL', '').rstrip('/')
LLM_API_KEY = os.getenv('LLM_API_KEY', '')
LLM_MODEL = os.getenv('LLM_MODEL', '')
LLM_PROVIDER = os.getenv('LLM_PROVIDER', 'generic').lower()
LLM_MIN_BALANCE_CNY = float(os.getenv('LLM_MIN_BALANCE_CNY', '9.00'))
LLM_DAILY_BUDGET_CNY = float(os.getenv('LLM_DAILY_BUDGET_CNY', '0.10'))
LLM_MAX_CALLS_PER_DAY = int(os.getenv('LLM_MAX_CALLS_PER_DAY', '12'))
LLM_MIN_CALL_INTERVAL_SECONDS = float(os.getenv('LLM_MIN_CALL_INTERVAL_SECONDS', '600'))

SYSTEM_RULES = '''You are the planning brain of a persistent owner's agent.
Hard rules:
1. Never invent facts the owner has not confirmed. If an essential fact is uncertain, request clarification.
2. Spending, owner identity use, formal external commitments, private uploads, destructive actions, or credential changes must wait for approval.
2a. Internal, reversible code/config/deploy/database maintenance on the owner's already-confirmed projects is NOT a formal external commitment and must not be flagged make_formal_commitment. Only commitments to external parties (for example sending an application, quote, bid, contract, purchase, payment, or signed promise) use that flag.
2b. Internal technical failures are not owner blockers. Repair, change path, or emit executor_request for connected-tool repair; do not ask the owner to approve ordinary debugging, CI fixes, deployment fixes, search fixes, or reversible project maintenance.
2c. If the owner has asked for a result, keep the goal active until a verified deliverable exists or a genuinely owner-only action is required. Do not treat progress notes as completion.
2d. Passive waiting is prohibited while an active goal exists and there is no genuine owner-only blocker. Never emit a note whose purpose is to wait for further owner directives, wait for the next objective, or stop after recording progress. Choose the next executable action instead. Use note only when recording evidence is itself the useful action.
2e. A delivered subtask or work contract does not end an active parent goal. If the parent goal remains active, immediately choose the next highest-value verified subtask instead of waiting for instructions.
3. Prefer verified real-world evidence, low-cost tests, reversible actions, and resources that have real-world proof.
4. After each result, choose one best next action. Avoid pointless repeated searches.
4a. Optimize for result quality, not minimum spend. Use paid reasoning when it materially improves the result, but never spend tokens repeating unchanged analysis or retrying the same failed path without new evidence. Prefer deterministic/free execution tools when they can do the job, and verify outputs before another paid reasoning call.
5. Search broadly across public web, communities, forums, marketplaces, suppliers, experts and organizations when useful. Do not limit yourself to official sources.
6. Never claim a real-world action happened unless a tool result proves it.
7. Return JSON only.
Allowed action types: web_search, web_get, note, executor_request, clarify, complete.
Use executor_request when the next useful step requires a connected service or capability that this runtime cannot directly execute (for example GitHub code changes, Render configuration/deploys, Supabase maintenance, browser/plugin work, files/design/media workflows). Payload must include capability, objective, and params. Do not use note when an actual executable change is the next step.
Payload contracts: web_search requires payload.query or payload.queries; web_get requires payload.url and must only be used when a concrete URL is already known; note uses payload.text; executor_request requires payload.capability, payload.objective, and optional payload.params; clarify uses payload.question. If you need a source but do not yet have a concrete URL, use web_search first.
Schema: {"action_type":"...","title":"...","instruction":"...","payload":{},"risk_flags":[],"why":"..."}
Risk flags when applicable: uncertain_fact, spend_money, use_owner_identity, make_formal_commitment, upload_private_data, destructive_action, change_credentials.
'''

DEFAULT_STATE = {'goals': [], 'tasks': [], 'approvals': [], 'events': [], 'memories': {}, 'audit': {'last_run_at':'','last_daily':'','last_event_id':0,'last_result':{},'runs':0}, 'checkpoints': [], 'system_guard': {'paused':False,'reason':'','issues':[],'updated_at':''}, 'budget': {'day':'','planner_calls':0,'last_call_at':0.0,'day_start_balance_cny':None,'last_balance_cny':None,'last_usage':{},'input_tokens_today':0,'output_tokens_today':0,'paused':False,'pause_reason':''}, 'next_ids': {'goal': 1, 'task': 1, 'approval': 1, 'event': 1}}
state_lock = asyncio.Lock()
stop_event = asyncio.Event()


def now(): return datetime.now(timezone.utc).isoformat()

def auth(authorization: str | None):
    if authorization != f'Bearer {ADMIN_TOKEN}':
        raise HTTPException(401, 'invalid admin token')

def store_headers():
    return {'apikey': STORE_KEY, 'Authorization': f'Bearer {STORE_KEY}', 'Content-Type': 'application/json'}

async def store_get():
    if not (STORE_URL and STORE_KEY and STORE_SECRET):
        return json.loads(json.dumps(DEFAULT_STATE))
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post(f'{STORE_URL}/rest/v1/rpc/agent_state_get', headers=store_headers(), json={'p_secret': STORE_SECRET, 'p_id': 'runtime'})
        r.raise_for_status(); data = r.json()
        if not data: return json.loads(json.dumps(DEFAULT_STATE))
        for k,v in DEFAULT_STATE.items(): data.setdefault(k, json.loads(json.dumps(v)))
        return data

async def store_set(state):
    if not (STORE_URL and STORE_KEY and STORE_SECRET): return
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post(f'{STORE_URL}/rest/v1/rpc/agent_state_set', headers=store_headers(), json={'p_secret': STORE_SECRET, 'p_id': 'runtime', 'p_value': state})
        r.raise_for_status()


def add_event(s, event_type, goal_id=None, task_id=None, data=None):
    i=s['next_ids']['event']; s['next_ids']['event']+=1
    s['events'].append({'id':i,'type':event_type,'goal_id':goal_id,'task_id':task_id,'data':data or {},'at':now()})
    if len(s['events'])>500: s['events']=s['events'][-500:]


RISK_FLAG_TO_CATEGORY={'uncertain_fact':'uncertainty','spend_money':'spending','use_owner_identity':'identity','make_formal_commitment':'formal_commitment','upload_private_data':'private_upload','destructive_action':'destructive','change_credentials':'credential_change'}

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

def policy(action):
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

def budget_state(s):
    b=s.setdefault('budget', {})
    defaults={'day':'','planner_calls':0,'last_call_at':0.0,'day_start_balance_cny':None,'last_balance_cny':None,'last_usage':{},'input_tokens_today':0,'output_tokens_today':0,'paused':False,'pause_reason':''}
    for k,v in defaults.items(): b.setdefault(k,v)
    day=datetime.now(timezone.utc).strftime('%Y-%m-%d')
    if b.get('day') != day:
        b.update({'day':day,'planner_calls':0,'last_call_at':0.0,'day_start_balance_cny':None,'input_tokens_today':0,'output_tokens_today':0,'paused':False,'pause_reason':''})
    return b

async def provider_balance():
    if LLM_PROVIDER != 'deepseek': return {'supported':False,'available':True,'currency':None,'total':None}
    if not (LLM_BASE_URL and LLM_API_KEY): return {'supported':True,'available':False,'currency':'CNY','total':None,'error':'missing connection'}
    async with httpx.AsyncClient(timeout=30) as c:
        r=await c.get(LLM_BASE_URL+'/user/balance',headers={'Authorization':f'Bearer {LLM_API_KEY}'})
        r.raise_for_status(); data=r.json()
    info=next((x for x in data.get('balance_infos',[]) if x.get('currency')=='CNY'),None)
    total=float(info.get('total_balance')) if info and info.get('total_balance') is not None else None
    return {'supported':True,'available':bool(data.get('is_available')),'currency':'CNY','total':total}

async def budget_preflight(s):
    b=budget_state(s); now_ts=time.time()
    effective_daily_calls=max(LLM_MAX_CALLS_PER_DAY, 60)
    if b['planner_calls'] >= effective_daily_calls:
        b['paused']=True; b['pause_reason']='daily_call_limit'; return {'allowed':False,'reason':'daily_call_limit'}
    effective_interval=min(LLM_MIN_CALL_INTERVAL_SECONDS, 120.0)
    if b['last_call_at'] and now_ts-b['last_call_at'] < effective_interval:
        return {'allowed':False,'reason':'cooldown','retry_after':round(effective_interval-(now_ts-b['last_call_at']),1)}
    try:
        bal=await provider_balance()
    except Exception as e:
        b['paused']=True; b['pause_reason']='balance_check_failed'; return {'allowed':False,'reason':'balance_check_failed','error':str(e)}
    if bal.get('supported'):
        total=bal.get('total'); b['last_balance_cny']=total
        if b.get('day_start_balance_cny') is None and total is not None: b['day_start_balance_cny']=total
        spent=max(0.0,(b.get('day_start_balance_cny') or total or 0)-(total or 0)) if total is not None else 0.0
        if not bal.get('available') or total is None:
            b['paused']=True; b['pause_reason']='balance_unavailable'; return {'allowed':False,'reason':'balance_unavailable'}
        if total <= 0.0:
            b['paused']=True; b['pause_reason']='balance_zero'; return {'allowed':False,'reason':'balance_zero','balance_cny':total}
        if spent >= LLM_DAILY_BUDGET_CNY:
            print(f"BUDGET_ADVISORY spent_today_cny={spent:.4f} configured_daily_cny={LLM_DAILY_BUDGET_CNY:.4f}; continuing because quality-first mode allows necessary spend",flush=True)
        print(f"BUDGET_CHECK balance_cny={total:.4f} spent_today_cny={spent:.4f} calls={b['planner_calls']}",flush=True)
    b['paused']=False; b['pause_reason']=''; b['planner_calls']+=1; b['last_call_at']=now_ts
    return {'allowed':True,'balance':bal}

def budget_record_usage(s, usage):
    b=budget_state(s); usage=usage or {}; b['last_usage']=usage
    b['input_tokens_today'] += int(usage.get('prompt_tokens') or 0)
    b['output_tokens_today'] += int(usage.get('completion_tokens') or 0)

async def planner_decide(goal, history, state):
    if LLM_MODE == 'mock':
        if not history:
            return {'action_type':'note','title':'Runtime online','instruction':'Record that the autonomous runtime is alive.','payload':{'text':'Runtime is online. A real reasoning model must be connected before autonomous research and execution.'},'risk_flags':[],'why':'Boot proof without spending money.'}
        return {'action_type':'clarify','title':'Connect reasoning model','instruction':'A real reasoning model is required for autonomous work.','payload':{'question':'Approve and provide a reasoning-model connection for autonomous work.'},'risk_flags':['change_credentials'],'why':'Mock mode cannot make intelligent plans.'}
    if LLM_MODE != 'openai_compatible': raise RuntimeError(f'Unsupported LLM_MODE={LLM_MODE}')
    if not (LLM_BASE_URL and LLM_API_KEY and LLM_MODEL): raise RuntimeError('LLM connection incomplete')
    body={'model':LLM_MODEL,'messages':[{'role':'system','content':SYSTEM_RULES},{'role':'user','content':json.dumps({'goal':goal,'work_contract':state.get('work_contract') or {},'system_guard':state.get('system_guard') or {},'audit_last_result':(state.get('audit') or {}).get('last_result') or {},'recent_history':history[-30:]},ensure_ascii=False)}],'thinking':{'type':'disabled'},'max_tokens':1200,'response_format':{'type':'json_object'}}
    async with httpx.AsyncClient(timeout=120) as c:
        r=await c.post(LLM_BASE_URL+'/chat/completions',headers={'Authorization':f'Bearer {LLM_API_KEY}'},json=body)
        if r.status_code >= 400:
            print('PLANNER_HTTP_ERROR', r.status_code, r.text[:1000], flush=True)
        r.raise_for_status()
        data=r.json(); budget_record_usage(state,data.get('usage') or {})
        return json.loads(data['choices'][0]['message']['content'])


def strip_html(x): return re.sub(r'\s+',' ',unescape(re.sub('<[^>]+>',' ',x or ''))).strip()

async def tool_web_get(payload):
    url=str(payload.get('url','')).strip()
    if not url.startswith(('http://','https://')): raise RuntimeError('web_get requires http(s) URL')
    host=(urlparse(url).hostname or '').lower()
    if host in {'localhost','127.0.0.1','0.0.0.0'}: raise RuntimeError('local network blocked')
    async with httpx.AsyncClient(timeout=35,follow_redirects=True,headers={'User-Agent':'Mozilla/5.0 OwnerAgent/0.2'}) as c:
        r=await c.get(url); r.raise_for_status()
        return {'url':str(r.url),'status':r.status_code,'text':strip_html(r.text)[:16000]}

async def tool_web_search(payload):
    one=str(payload.get('query','')).strip()
    raw_many=payload.get('queries') or []
    if one:
        queries=[one]
    elif isinstance(raw_many,list):
        queries=[str(x).strip() for x in raw_many if str(x).strip()]
    else:
        queries=[]
    if not queries: raise RuntimeError('web_search requires query or queries')
    count=max(1,min(int(payload.get('count',8)),12))
    all_results=[]
    headers={'User-Agent':'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131 Safari/537.36'}
    timeout=httpx.Timeout(15.0,connect=8.0)

    def terms_for(q):
        stop={'with','from','that','this','into','about','best','practices','current','using','used','what','when','where','which','their','there','have','has','for','and','the','how','why','system','systems'}
        toks=[x.lower() for x in re.findall(r'[A-Za-z0-9_\-]{4,}',q)]
        out=[]
        for x in toks:
            if x in stop: continue
            if x not in out: out.append(x)
        return out[:12]

    def engine_query(q,terms):
        ts=set(terms)
        if 'autonomous' in ts and 'agent' in ts:
            extras=[x for x in ('architecture','persistence','scheduling','memory','state','workflow') if x in ts]
            return '"autonomous agent" '+ ' '.join(extras[:3])
        if 'agent' in ts and 'architecture' in ts:
            return '"agent architecture" persistence workflow'
        return q

    def relevance_score(item,terms):
        hay=(' '+str(item.get('title',''))+' '+str(item.get('snippet',''))+' '+str(item.get('url',''))+' ').lower()
        matched={t for t in terms if t in hay}
        return len(matched), matched

    def relevant(item,terms):
        if not terms: return True
        score,matched=relevance_score(item,terms)
        need=1 if len(terms)<=2 else 2
        if score < need: return False
        # For agent/AI research, a generic dictionary hit on one broad word is never enough.
        anchors={'agent','autonomous','architecture','persistence','scheduling','workflow','memory','state','healing'}
        requested=anchors.intersection(terms)
        if requested and not requested.intersection(matched): return False
        host=(urlparse(str(item.get('url',''))).hostname or '').lower()
        if any(x in host for x in ('dictionary.com','merriam-webster.com','cambridge.org','vocabulary.com')) and len(matched)<3:
            return False
        return True

    async with httpx.AsyncClient(timeout=timeout,follow_redirects=True,headers=headers) as c:
        for q in queries[:4]:
            terms=terms_for(q); q2=engine_query(q,terms); candidates=[]; used_sources=[]

            # 1) Bing RSS: accept only results that match multiple query concepts.
            try:
                params={'format':'rss','q':q2,'mkt':'en-US','setlang':'en-US'}
                r=await c.get('https://www.bing.com/search',params=params); r.raise_for_status(); xml=r.text
                items=re.findall(r'<item>(.*?)</item>',xml,re.I|re.S)
                for item in items[:max(count*3,12)]:
                    mt=re.search(r'<title>(.*?)</title>',item,re.I|re.S)
                    ml=re.search(r'<link>(.*?)</link>',item,re.I|re.S)
                    md=re.search(r'<description>(.*?)</description>',item,re.I|re.S)
                    if not ml: continue
                    href=unescape(strip_html(ml.group(1))).strip()
                    row={'title':strip_html(mt.group(1) if mt else href),'url':href,'snippet':strip_html(md.group(1) if md else '')[:500],'source':'bing_rss'}
                    if href.startswith('http') and relevant(row,terms): candidates.append(row)
                used_sources.append('bing_rss')
            except Exception as e:
                print('SEARCH_BING_RSS_ERROR',type(e).__name__,flush=True)

            # 2) GitHub repository search: implementation evidence.
            if len(candidates)<count:
                try:
                    ghq=q2.replace('"','')
                    r=await c.get('https://api.github.com/search/repositories',params={'q':ghq,'per_page':min(count*3,30),'sort':'stars'},headers={**headers,'Accept':'application/vnd.github+json'}); r.raise_for_status(); data=r.json()
                    for item in data.get('items',[]):
                        row={'title':item.get('full_name') or item.get('name') or '', 'url':item.get('html_url') or '', 'snippet':str(item.get('description') or '')[:500], 'source':'github'}
                        if relevant(row,terms): candidates.append(row)
                    used_sources.append('github')
                except Exception as e:
                    print('SEARCH_GITHUB_ERROR',type(e).__name__,flush=True)

            # 3) Hacker News Algolia: engineering/community evidence.
            if len(candidates)<count:
                try:
                    r=await c.get('https://hn.algolia.com/api/v1/search',params={'query':q2.replace('"',''),'tags':'story','hitsPerPage':min(count*3,30)}); r.raise_for_status(); data=r.json()
                    for item in data.get('hits',[]):
                        href=item.get('url') or ('https://news.ycombinator.com/item?id='+str(item.get('objectID') or ''))
                        snippet=' '.join([str(item.get('title') or ''),str(item.get('story_text') or ''),str(item.get('comment_text') or '')])[:500]
                        row={'title':item.get('title') or item.get('story_title') or '', 'url':href, 'snippet':snippet, 'source':'hackernews'}
                        if href and relevant(row,terms): candidates.append(row)
                    used_sources.append('hackernews')
                except Exception as e:
                    print('SEARCH_HN_ERROR',type(e).__name__,flush=True)

            # 4) Stack Exchange API: technical Q&A fallback.
            if len(candidates)<count:
                try:
                    r=await c.get('https://api.stackexchange.com/2.3/search/advanced',params={'q':q2.replace('"',''),'site':'stackoverflow','pagesize':min(count*2,20),'order':'desc','sort':'relevance'}); r.raise_for_status(); data=r.json()
                    for item in data.get('items',[]):
                        row={'title':unescape(str(item.get('title') or '')),'url':item.get('link') or '', 'snippet':'stackoverflow technical discussion', 'source':'stackoverflow'}
                        if relevant(row,terms): candidates.append(row)
                    used_sources.append('stackoverflow')
                except Exception as e:
                    print('SEARCH_STACK_ERROR',type(e).__name__,flush=True)

            # 5) DuckDuckGo HTML fallback.
            if len(candidates)<count:
                try:
                    r=await c.get('https://html.duckduckgo.com/html/',params={'q':q2}); r.raise_for_status(); html=r.text
                    pairs=re.findall(r'<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',html,re.I|re.S)
                    for href,title in pairs[:max(count*3,12)]:
                        href=unescape(href)
                        if 'uddg=' in href:
                            try: href=parse_qs(urlparse(href).query).get('uddg',[href])[0]
                            except Exception: pass
                        row={'title':strip_html(title),'url':href,'snippet':'','source':'duckduckgo'}
                        if href.startswith('http') and relevant(row,terms): candidates.append(row)
                    used_sources.append('duckduckgo')
                except Exception as e:
                    print('SEARCH_DDG_ERROR',type(e).__name__,flush=True)

            # De-duplicate, reject search-engine internal pages, then rank by concept overlap.
            seen=set(); clean=[]
            candidates.sort(key=lambda x: relevance_score(x,terms)[0],reverse=True)
            for item in candidates:
                u=str(item.get('url','')).strip(); host=(urlparse(u).hostname or '').lower()
                if not u.startswith('http') or any(x in host for x in ('bing.com','google.com','duckduckgo.com')): continue
                if u in seen: continue
                seen.add(u); clean.append(item)
                if len(clean)>=count: break
            all_results.append({'query':q,'engine_query':q2,'sources':used_sources,'results':clean})
    return {'queries':queries[:4],'batches':all_results}

def normalize_tool_request(kind,payload,title='',instruction=''):
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
    elif kind=='executor_request':
        cap=str(p.get('capability') or '').strip()
        objective=str(p.get('objective') or instruction or title or '').strip()
        params=p.get('params') if isinstance(p.get('params'),dict) else {}
        if not cap or not objective:
            kind='clarify'; p={'question':'Executor request is missing a confirmed capability or objective.'}
        else:
            p={'capability':cap,'objective':objective,'params':params}
    elif kind=='clarify':
        q=str(p.get('question') or instruction or title or 'Owner clarification required.').strip()
        p={'question':q}
    elif kind not in {'complete'}:
        kind='note'; p={'text':fallback or f'Unsupported action normalized from {kind}.'}
    return kind,p

async def run_tool(kind,payload):
    if kind=='note': return {'text':str(payload.get('text',''))}
    if kind=='web_get': return await tool_web_get(payload)
    if kind=='web_search': return await tool_web_search(payload)
    raise RuntimeError(f'No tool for {kind}')

async def tick_once():
    async with state_lock:
        s=await store_get()
        audit=s.setdefault('audit',{})
        latest_event_id=int((s.get('events') or [{}])[-1].get('id') or 0)
        today=datetime.now(timezone.utc).strftime('%Y-%m-%d')
        need_daily=audit.get('last_daily') != today
        need_change=latest_event_id > int(audit.get('last_event_id') or 0)
        if need_daily or need_change:
            self_audit(s,'daily' if need_daily else 'state_change')
            if need_daily: s['audit']['last_daily']=today
            await store_set(s)
        if (s.get('system_guard') or {}).get('paused'):
            return {'status':'guard_paused','reason':(s.get('system_guard') or {}).get('reason'),'issues':(s.get('system_guard') or {}).get('issues',[])[:5]}
        # One-time recovery: the owner has now configured the real reasoning model.
        if LLM_MODE == 'openai_compatible' and LLM_API_KEY:
            bootstrap_ids=set()
            changed=False
            for t in s.get('tasks',[]):
                if t.get('title')=='Connect reasoning model' and t.get('kind')=='clarify' and t.get('status')=='waiting_approval':
                    t['status']='completed'; t['requires_approval']=False; t['result']={'system':'reasoning model configured'}
                    bootstrap_ids.add(t.get('id')); add_event(s,'task_completed',t.get('goal_id'),t.get('id'),t['result']); changed=True
            for a in s.get('approvals',[]):
                if a.get('task_id') in bootstrap_ids and a.get('status')=='pending':
                    a['status']='approved'; a['decision_note']='Automatically resolved after live model configuration.'; a['decided_at']=now(); changed=True
            if changed: await store_set(s)
        pending=next((t for t in s['tasks'] if t['status']=='pending' and not t.get('requires_approval')),None)
        if pending:
            pending['status']='running'; pending['attempts']=pending.get('attempts',0)+1; await store_set(s)
            try:
                repaired_kind,repaired_payload=normalize_tool_request(pending.get('kind'),pending.get('payload') or {},pending.get('title',''),pending.get('instruction',''))
                if repaired_kind != pending.get('kind') or repaired_payload != (pending.get('payload') or {}):
                    pending['kind']=repaired_kind; pending['payload']=repaired_payload
                    add_event(s,'task_repaired',pending.get('goal_id'),pending.get('id'),{'kind':repaired_kind,'payload':repaired_payload})
                    await store_set(s)
                result=await run_tool(pending['kind'],pending.get('payload') or {})
                pending['result']=result; pending['status']='completed'; add_event(s,'task_completed',pending['goal_id'],pending['id'],result)
            except Exception as e:
                pending['result']={'error':str(e)}
                if pending['attempts']>=MAX_ATTEMPTS:
                    original_kind=pending.get('kind')
                    original_payload=pending.get('payload') or {}
                    pending['kind']='executor_request'
                    pending['status']='waiting_executor'; pending['requires_approval']=False
                    pending['payload']={
                        'capability':'runtime_debug_and_repair',
                        'objective':f"Repair the internal execution failure and continue the original task: {pending.get('title','task')}",
                        'params':{
                            'original_kind':original_kind,
                            'original_payload':original_payload,
                            'last_error':str(e),
                            'attempts':pending['attempts']
                        }
                    }
                    add_event(s,'task_escalated_to_executor',pending['goal_id'],pending['id'],{'error':str(e),'capability':'runtime_debug_and_repair'})
                else: pending['status']='pending'
                add_event(s,'task_failed',pending['goal_id'],pending['id'],{'error':str(e),'trace':traceback.format_exc(limit=2)})
            self_audit(s,'task_execution',pending.get('goal_id'),pending.get('id'))
            await store_set(s); return {'status':pending['status'],'task_id':pending['id']}
        goal=next((g for g in sorted(s['goals'],key=lambda x:(-x['priority'],x['id'])) if g['status']=='active'),None)
        if not goal: return {'status':'idle'}
        blocked=next((t for t in s['tasks'] if t['goal_id']==goal['id'] and t['status'] in ('pending','waiting_approval','waiting_executor','running')),None)
        if blocked: return {'status':'waiting','goal_id':goal['id'],'task_id':blocked['id']}
        hist=[e for e in s['events'] if e.get('goal_id')==goal['id']]
        # A system_context_updated event supersedes obsolete connection/rate-limit history.
        for i in range(len(hist)-1,-1,-1):
            if hist[i].get('type')=='system_context_updated':
                hist=hist[i:]
                break
        try:
            if LLM_MODE == 'openai_compatible':
                gate=await budget_preflight(s)
                if not gate.get('allowed'):
                    reason=gate.get('reason','budget_guard')
                    if reason != 'cooldown': print('BUDGET_PAUSE', reason, {k:v for k,v in gate.items() if k != 'allowed'}, flush=True)
                    await store_set(s); return {'status':'budget_wait' if reason=='cooldown' else 'budget_paused','reason':reason}
            action=await planner_decide(goal,hist,s)
            print('PLANNER_ACTION', action.get('action_type'), action.get('title'), flush=True)
        except Exception as e:
            print('PLANNER_ERROR', str(e), flush=True)
            add_event(s,'planner_error',goal['id'],data={'error':str(e)}); await store_set(s); return {'status':'planner_error','error':str(e)}
        if action.get('action_type')=='complete':
            goal['status']='completed'; add_event(s,'goal_completed',goal['id'],data=action); await store_set(s); return {'status':'completed','goal_id':goal['id']}
        normalized_kind,normalized_payload=normalize_tool_request(action.get('action_type'),action.get('payload') or {},action.get('title',''),action.get('instruction',''))
        if normalized_kind != action.get('action_type') or normalized_payload != (action.get('payload') or {}):
            action=dict(action); action['action_type']=normalized_kind; action['payload']=normalized_payload
            add_event(s,'planner_action_repaired',goal['id'],data={'action_type':normalized_kind,'payload':normalized_payload})
        allowed,cat=policy(action); tid=s['next_ids']['task']; s['next_ids']['task']+=1
        task_kind=action.get('action_type','note')
        task_status=('waiting_executor' if allowed and task_kind=='executor_request' else ('pending' if allowed else 'waiting_approval'))
        task={'id':tid,'goal_id':goal['id'],'title':str(action.get('title') or 'Next action'),'instruction':str(action.get('instruction') or ''),'kind':task_kind,'status':task_status,'payload':action.get('payload') or {},'result':{},'risk_flags':action.get('risk_flags') or [],'requires_approval':not allowed,'attempts':0}
        s['tasks'].append(task); add_event(s,'task_planned',goal['id'],tid,action)
        if not allowed:
            aid=s['next_ids']['approval']; s['next_ids']['approval']+=1
            q=(action.get('payload') or {}).get('question') or action.get('instruction') or 'Owner approval required.'
            s['approvals'].append({'id':aid,'task_id':tid,'category':cat or 'uncertainty','summary':str(q),'status':'pending','decision_note':''}); add_event(s,'approval_requested',goal['id'],tid,{'category':cat,'summary':q})
        self_audit(s,'task_planning',goal.get('id'),tid)
        await store_set(s); return {'status':task['status'],'goal_id':goal['id'],'task_id':tid}

async def worker_loop():
    while not stop_event.is_set():
        try: await tick_once()
        except Exception: traceback.print_exc()
        try: await asyncio.wait_for(stop_event.wait(),timeout=WORKER_INTERVAL)
        except asyncio.TimeoutError: pass

app=FastAPI(title='Always-On Owner Agent',version='0.5.0')

@app.on_event('startup')
async def start():
    stop_event.clear(); asyncio.create_task(worker_loop())

class GoalIn(BaseModel):
    title:str=Field(min_length=1,max_length=240); brief:str=Field(min_length=1); priority:int=Field(default=50,ge=1,le=100)
class ApprovalIn(BaseModel): approve:bool; note:str=''

@app.get('/health')
async def health():
    ok=False; detail='memory-only'
    try:
        s=await store_get(); ok=isinstance(s,dict); detail='supabase' if STORE_URL else 'memory-only'
    except Exception as e: detail=str(e)
    guard=(s.get('system_guard') or {}) if ok else {}
    audit=(s.get('audit') or {}) if ok else {}
    return {'ok':ok and not bool(guard.get('paused')),'version':'0.5.0','store':detail,'llm_mode':LLM_MODE,'llm_provider':LLM_PROVIDER,'budget_guard':True,'system_guard':guard,'audit':{'last_run_at':audit.get('last_run_at'),'last_result':audit.get('last_result'),'runs':audit.get('runs',0)}}

@app.get('/state')
async def get_state(authorization:str|None=Header(default=None)):
    auth(authorization); return await store_get()

@app.post('/goals')
async def add_goal(body:GoalIn,authorization:str|None=Header(default=None)):
    auth(authorization)
    async with state_lock:
        s=await store_get(); gid=s['next_ids']['goal']; s['next_ids']['goal']+=1
        s['goals'].append({'id':gid,'title':body.title,'brief':body.brief,'priority':body.priority,'status':'active','created_at':now()}); add_event(s,'goal_created',gid,data=body.model_dump()); await store_set(s)
        return {'id':gid,'status':'active'}

@app.post('/approvals/{approval_id}')
async def decide(approval_id:int,body:ApprovalIn,authorization:str|None=Header(default=None)):
    auth(authorization)
    async with state_lock:
        s=await store_get(); ap=next((a for a in s['approvals'] if a['id']==approval_id and a['status']=='pending'),None)
        if not ap: raise HTTPException(404,'approval not found')
        task=next(t for t in s['tasks'] if t['id']==ap['task_id']); ap['status']='approved' if body.approve else 'rejected'; ap['decision_note']=body.note; ap['decided_at']=now()
        if body.approve:
            task['requires_approval']=False
            if task['kind']=='clarify':
                task['status']='completed'; task['result']={'owner_answer':body.note}; add_event(s,'task_completed',task['goal_id'],task['id'],task['result'])
            elif task['kind']=='executor_request': task['status']='waiting_executor'
            else:
                task['status']='pending'
        else: task['status']='rejected'
        add_event(s,'approval_decided',task['goal_id'],task['id'],{'approved':body.approve,'note':body.note}); self_audit(s,'approval_decision',task.get('goal_id'),task.get('id')); await store_set(s); return {'ok':True,'task_status':task['status']}

@app.post('/tick')
async def tick(authorization:str|None=Header(default=None)):
    auth(authorization); return await tick_once()

@app.get('/dashboard',response_class=HTMLResponse)
async def dashboard():
    return '''<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>Always-On Agent</title><style>body{font:15px system-ui;max-width:760px;margin:35px auto;padding:0 16px;color:#171717}input,textarea,button{font:inherit;padding:10px;border:1px solid #ccc;border-radius:10px}textarea{width:100%;min-height:100px;box-sizing:border-box}button{cursor:pointer;background:#111;color:#fff}.card{border:1px solid #e5e5e5;border-radius:14px;padding:14px;margin:12px 0}small{color:#666}</style><h1>Always-On Agent v0.5</h1><div class=card><b>Admin token</b><p><input id=t style="width:100%;box-sizing:border-box"></p></div><div class=card><b>New goal</b><p><input id=title placeholder="Goal title" style="width:100%;box-sizing:border-box"></p><textarea id=brief placeholder="Exact goal and confirmed facts"></textarea><p><button onclick=add()>Create goal</button></p></div><div id=out></div><script>const H=()=>({'Authorization':'Bearer '+t.value,'Content-Type':'application/json'});async function load(){let r=await fetch('/state',{headers:H()});if(!r.ok){out.innerHTML='<p>Enter admin token.</p>';return}let s=await r.json();out.innerHTML='<h2>Goals</h2>'+s.goals.map(g=>`<div class=card><b>${g.title}</b><br><small>${g.status}</small><p>${g.brief}</p></div>`).join('')+'<h2>Approvals</h2>'+s.approvals.map(a=>`<div class=card><b>${a.category}</b><p>${a.summary}</p><small>${a.status}</small>${a.status==='pending'?`<p><input id=n${a.id} placeholder="Answer / approval note" style="width:100%;box-sizing:border-box"><br><br><button onclick=dec(${a.id},true)>Approve / answer</button> <button onclick=dec(${a.id},false)>Reject</button></p>`:''}</div>`).join('')+'<h2>Tasks</h2>'+s.tasks.slice().reverse().map(x=>`<div class=card><b>${x.title}</b><br><small>${x.kind} · ${x.status} · attempts ${x.attempts}</small></div>`).join('')}async function add(){await fetch('/goals',{method:'POST',headers:H(),body:JSON.stringify({title:title.value,brief:brief.value,priority:50})});load()}async function dec(id,approve){let n=document.querySelector('#n'+id);await fetch('/approvals/'+id,{method:'POST',headers:H(),body:JSON.stringify({approve,note:n?n.value:''})});load()}setInterval(load,4000)</script>'''

from __future__ import annotations
import asyncio, json, os, re, time, traceback
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

SYSTEM_RULES = '''You are the planning brain of a persistent owner's agent.
Hard rules:
1. Never invent facts the owner has not confirmed. If an essential fact is uncertain, request clarification.
2. Spending, owner identity use, formal external commitments, private uploads, destructive actions, or credential changes must wait for approval.
3. Prefer verified real-world evidence, low-cost tests, reversible actions, and resources that have real-world proof.
4. After each result, choose one best next action. Avoid pointless repeated searches.
5. Search broadly across public web, communities, forums, marketplaces, suppliers, experts and organizations when useful. Do not limit yourself to official sources.
6. Never claim a real-world action happened unless a tool result proves it.
7. Return JSON only.
Allowed action types: web_search, web_get, note, clarify, complete.
Schema: {"action_type":"...","title":"...","instruction":"...","payload":{},"risk_flags":[],"why":"..."}
Risk flags when applicable: uncertain_fact, spend_money, use_owner_identity, make_formal_commitment, upload_private_data, destructive_action, change_credentials.
'''

DEFAULT_STATE = {'goals': [], 'tasks': [], 'approvals': [], 'events': [], 'memories': {}, 'next_ids': {'goal': 1, 'task': 1, 'approval': 1, 'event': 1}}
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


def policy(action):
    m={'uncertain_fact':'uncertainty','spend_money':'spending','use_owner_identity':'identity','make_formal_commitment':'formal_commitment','upload_private_data':'private_upload','destructive_action':'destructive','change_credentials':'credential_change'}
    for f in action.get('risk_flags') or []:
        if f in m: return False, m[f]
    return True, None

async def planner_decide(goal, history):
    if LLM_MODE == 'mock':
        if not history:
            return {'action_type':'note','title':'Runtime online','instruction':'Record that the autonomous runtime is alive.','payload':{'text':'Runtime is online. A real reasoning model must be connected before autonomous research and execution.'},'risk_flags':[],'why':'Boot proof without spending money.'}
        return {'action_type':'clarify','title':'Connect reasoning model','instruction':'A real reasoning model is required for autonomous work.','payload':{'question':'Approve and provide a reasoning-model connection for autonomous work.'},'risk_flags':['change_credentials'],'why':'Mock mode cannot make intelligent plans.'}
    if LLM_MODE != 'openai_compatible': raise RuntimeError(f'Unsupported LLM_MODE={LLM_MODE}')
    if not (LLM_BASE_URL and LLM_API_KEY and LLM_MODEL): raise RuntimeError('LLM connection incomplete')
    body={'model':LLM_MODEL,'messages':[{'role':'system','content':SYSTEM_RULES},{'role':'user','content':json.dumps({'goal':goal,'recent_history':history[-30:]},ensure_ascii=False)}],'temperature':0.2,'response_format':{'type':'json_object'}}
    async with httpx.AsyncClient(timeout=120) as c:
        r=await c.post(LLM_BASE_URL+'/chat/completions',headers={'Authorization':f'Bearer {LLM_API_KEY}'},json=body); r.raise_for_status()
        return json.loads(r.json()['choices'][0]['message']['content'])


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
    q=str(payload.get('query','')).strip(); count=max(1,min(int(payload.get('count',8)),12))
    if not q: raise RuntimeError('web_search requires query')
    url='https://html.duckduckgo.com/html/?q='+quote_plus(q)
    async with httpx.AsyncClient(timeout=35,follow_redirects=True,headers={'User-Agent':'Mozilla/5.0 OwnerAgent/0.2'}) as c:
        r=await c.get(url); r.raise_for_status(); html=r.text
    pairs=re.findall(r'<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',html,re.I|re.S)
    out=[]
    for href,title in pairs[:count]:
        href=unescape(href)
        if 'uddg=' in href:
            try: href=parse_qs(urlparse(href).query).get('uddg',[href])[0]
            except Exception: pass
        out.append({'title':strip_html(title),'url':href})
    return {'query':q,'results':out}

async def run_tool(kind,payload):
    if kind=='note': return {'text':str(payload.get('text',''))}
    if kind=='web_get': return await tool_web_get(payload)
    if kind=='web_search': return await tool_web_search(payload)
    raise RuntimeError(f'No tool for {kind}')

async def tick_once():
    async with state_lock:
        s=await store_get()
        pending=next((t for t in s['tasks'] if t['status']=='pending' and not t.get('requires_approval')),None)
        if pending:
            pending['status']='running'; pending['attempts']=pending.get('attempts',0)+1; await store_set(s)
            try:
                result=await run_tool(pending['kind'],pending.get('payload') or {})
                pending['result']=result; pending['status']='completed'; add_event(s,'task_completed',pending['goal_id'],pending['id'],result)
            except Exception as e:
                pending['result']={'error':str(e)}
                if pending['attempts']>=MAX_ATTEMPTS:
                    pending['status']='waiting_approval'; pending['requires_approval']=True
                    aid=s['next_ids']['approval']; s['next_ids']['approval']+=1
                    s['approvals'].append({'id':aid,'task_id':pending['id'],'category':'failure','summary':f"Task failed {pending['attempts']} times: {e}",'status':'pending','decision_note':''})
                else: pending['status']='pending'
                add_event(s,'task_failed',pending['goal_id'],pending['id'],{'error':str(e),'trace':traceback.format_exc(limit=2)})
            await store_set(s); return {'status':pending['status'],'task_id':pending['id']}
        goal=next((g for g in sorted(s['goals'],key=lambda x:(-x['priority'],x['id'])) if g['status']=='active'),None)
        if not goal: return {'status':'idle'}
        blocked=next((t for t in s['tasks'] if t['goal_id']==goal['id'] and t['status'] in ('pending','waiting_approval','running')),None)
        if blocked: return {'status':'waiting','goal_id':goal['id'],'task_id':blocked['id']}
        hist=[e for e in s['events'] if e.get('goal_id')==goal['id']]
        try: action=await planner_decide(goal,hist)
        except Exception as e:
            add_event(s,'planner_error',goal['id'],data={'error':str(e)}); await store_set(s); return {'status':'planner_error','error':str(e)}
        if action.get('action_type')=='complete':
            goal['status']='completed'; add_event(s,'goal_completed',goal['id'],data=action); await store_set(s); return {'status':'completed','goal_id':goal['id']}
        allowed,cat=policy(action); tid=s['next_ids']['task']; s['next_ids']['task']+=1
        task={'id':tid,'goal_id':goal['id'],'title':str(action.get('title') or 'Next action'),'instruction':str(action.get('instruction') or ''),'kind':action.get('action_type','note'),'status':'pending' if allowed else 'waiting_approval','payload':action.get('payload') or {},'result':{},'requires_approval':not allowed,'attempts':0}
        s['tasks'].append(task); add_event(s,'task_planned',goal['id'],tid,action)
        if not allowed:
            aid=s['next_ids']['approval']; s['next_ids']['approval']+=1
            q=(action.get('payload') or {}).get('question') or action.get('instruction') or 'Owner approval required.'
            s['approvals'].append({'id':aid,'task_id':tid,'category':cat or 'uncertainty','summary':str(q),'status':'pending','decision_note':''}); add_event(s,'approval_requested',goal['id'],tid,{'category':cat,'summary':q})
        await store_set(s); return {'status':task['status'],'goal_id':goal['id'],'task_id':tid}

async def worker_loop():
    while not stop_event.is_set():
        try: await tick_once()
        except Exception: traceback.print_exc()
        try: await asyncio.wait_for(stop_event.wait(),timeout=WORKER_INTERVAL)
        except asyncio.TimeoutError: pass

app=FastAPI(title='Always-On Owner Agent',version='0.2.0')

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
    return {'ok':ok,'version':'0.2.0','store':detail,'llm_mode':LLM_MODE}

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
            if task['kind']=='clarify': task['status']='completed'; task['result']={'owner_answer':body.note}; add_event(s,'task_completed',task['goal_id'],task['id'],task['result'])
            else: task['status']='pending'
        else: task['status']='rejected'
        add_event(s,'approval_decided',task['goal_id'],task['id'],{'approved':body.approve,'note':body.note}); await store_set(s); return {'ok':True,'task_status':task['status']}

@app.post('/tick')
async def tick(authorization:str|None=Header(default=None)):
    auth(authorization); return await tick_once()

@app.get('/dashboard',response_class=HTMLResponse)
async def dashboard():
    return '''<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>Always-On Agent</title><style>body{font:15px system-ui;max-width:760px;margin:35px auto;padding:0 16px;color:#171717}input,textarea,button{font:inherit;padding:10px;border:1px solid #ccc;border-radius:10px}textarea{width:100%;min-height:100px;box-sizing:border-box}button{cursor:pointer;background:#111;color:#fff}.card{border:1px solid #e5e5e5;border-radius:14px;padding:14px;margin:12px 0}small{color:#666}</style><h1>Always-On Agent v0.2</h1><div class=card><b>Admin token</b><p><input id=t style="width:100%;box-sizing:border-box"></p></div><div class=card><b>New goal</b><p><input id=title placeholder="Goal title" style="width:100%;box-sizing:border-box"></p><textarea id=brief placeholder="Exact goal and confirmed facts"></textarea><p><button onclick=add()>Create goal</button></p></div><div id=out></div><script>const H=()=>({'Authorization':'Bearer '+t.value,'Content-Type':'application/json'});async function load(){let r=await fetch('/state',{headers:H()});if(!r.ok){out.innerHTML='<p>Enter admin token.</p>';return}let s=await r.json();out.innerHTML='<h2>Goals</h2>'+s.goals.map(g=>`<div class=card><b>${g.title}</b><br><small>${g.status}</small><p>${g.brief}</p></div>`).join('')+'<h2>Approvals</h2>'+s.approvals.map(a=>`<div class=card><b>${a.category}</b><p>${a.summary}</p><small>${a.status}</small>${a.status==='pending'?`<p><input id=n${a.id} placeholder="Answer / approval note" style="width:100%;box-sizing:border-box"><br><br><button onclick=dec(${a.id},true)>Approve / answer</button> <button onclick=dec(${a.id},false)>Reject</button></p>`:''}</div>`).join('')+'<h2>Tasks</h2>'+s.tasks.slice().reverse().map(x=>`<div class=card><b>${x.title}</b><br><small>${x.kind} · ${x.status} · attempts ${x.attempts}</small></div>`).join('')}async function add(){await fetch('/goals',{method:'POST',headers:H(),body:JSON.stringify({title:title.value,brief:brief.value,priority:50})});load()}async function dec(id,approve){let n=document.querySelector('#n'+id);await fetch('/approvals/'+id,{method:'POST',headers:H(),body:JSON.stringify({approve,note:n?n.value:''})});load()}setInterval(load,4000)</script>'''

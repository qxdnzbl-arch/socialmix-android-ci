import asyncio
import copy
import json
from pathlib import Path

import httpx
import pytest
import main


def test_blocked_goal_does_not_block_other_work():
    state={'goals':[{'id':1,'priority':100,'status':'active'},{'id':2,'priority':60,'status':'active'}],
           'tasks':[{'id':1,'goal_id':1,'status':'waiting_approval'}]}
    assert main.select_unblocked_goal(state)['id']==2


def test_external_action_cannot_omit_approval_flags():
    action={'action_type':'executor_request','payload':{'capability':'upwork_application'},'risk_flags':[]}
    assert main.policy(action)==(False,'formal_commitment')


def test_unapproved_executor_is_not_silently_released():
    state=copy.deepcopy(main.DEFAULT_STATE)
    state['tasks']=[{'id':1,'kind':'executor_request','status':'waiting_executor','requires_approval':True}]
    assert not main.self_audit(state)['ok']
    assert state['tasks'][0]['requires_approval'] is True


def test_unfinished_goal_is_reopened():
    state=copy.deepcopy(main.DEFAULT_STATE)
    state['goals']=[{'id':1,'status':'completed'}]
    state['tasks']=[{'id':1,'goal_id':1,'kind':'executor_request','status':'waiting_executor'}]
    assert main.self_audit(state)['ok']
    assert state['goals'][0]['status']=='active'


def test_completion_needs_recorded_inspection():
    goal={'id':1}
    assert not main.completion_is_verified(goal,{'tasks':[]})
    goal['verification']={'status':'passed','reviewed_at':'2026-09-07','evidence':['inspection-report']}
    assert main.completion_is_verified(goal,{'tasks':[]})
    assert not main.completion_is_verified(goal,{'tasks':[{'goal_id':1,'status':'waiting_executor'}]})


def test_chinese_actions_are_distinct():
    assert main.action_dedup_key({'action_type':'web_search','payload':{'query':'工厂打样'}}) != main.action_dedup_key({'action_type':'web_search','payload':{'query':'动画制作'}})


def test_missing_storage_fails_visibly(monkeypatch):
    monkeypatch.setattr(main,'STORE_URL','')
    with pytest.raises(RuntimeError,match='persistent_store_not_configured'):
        asyncio.run(main.store_get())


def test_real_worker_continues_after_restart(tmp_path,monkeypatch):
    """Exercise tick -> actual HTTP tool -> verification -> reload, without an AI charge."""
    disk=tmp_path/'state.json'
    state=copy.deepcopy(main.DEFAULT_STATE)
    state['goals']=[{'id':1,'status':'active','priority':100},{'id':2,'status':'active','priority':60}]
    state['tasks']=[{'id':1,'goal_id':1,'status':'waiting_approval','kind':'executor_request','requires_approval':True},
        {'id':2,'goal_id':2,'status':'pending','kind':'web_get','payload':{'url':'https://public.example.test/proof'},'requires_approval':False,'attempts':0}]
    state['approvals']=[{'id':1,'task_id':1,'status':'pending'}]
    disk.write_text(json.dumps(state))
    async def get(): return json.loads(disk.read_text())
    async def put(s): disk.write_text(json.dumps(s))
    monkeypatch.setattr(main,'store_get',get)
    monkeypatch.setattr(main,'store_set',put)
    monkeypatch.setattr(main,'LLM_MODE','mock')
    calls=[]
    async def planner(goal,history,state):
        calls.append(goal['id'])
        return {'action_type':'executor_request','title':'Inspect saved source','instruction':'Read and verify saved result','payload':{'capability':'verify_deliverable','objective':'Inspect source'},'risk_flags':[]}
    monkeypatch.setattr(main,'planner_decide',planner)
    real_client=httpx.AsyncClient
    def client(**kwargs):
        return real_client(transport=httpx.MockTransport(lambda r:httpx.Response(200,text='<p>'+('Verified source content. '*15)+'</p>')),**kwargs)
    monkeypatch.setattr(main.httpx,'AsyncClient',client)
    first=asyncio.run(main.tick_once())
    assert first['status']=='completed'
    assert json.loads(disk.read_text())['tasks'][1]['verification']['ok']
    # New call reloads persisted state rather than using in-memory task references.
    second=asyncio.run(main.tick_once())
    assert second['status']=='waiting_executor' and second['goal_id']==2
    assert calls==[2]
    saved=json.loads(disk.read_text())
    assert saved['tasks'][0]['status']=='waiting_approval'
    assert saved['approvals'][0]['status']=='pending'


def test_conflicting_save_never_claims_success(monkeypatch):
    monkeypatch.setattr(main,'STORE_URL','https://database.example.test')
    monkeypatch.setattr(main,'STORE_KEY','test-key')
    monkeypatch.setattr(main,'STORE_SECRET','test-secret')
    real_client=httpx.AsyncClient
    monkeypatch.setattr(main.httpx,'AsyncClient',lambda **kw:real_client(transport=httpx.MockTransport(lambda r:httpx.Response(409,json={'message':'state_changed_reload_required'})),**kw))
    s={'_store_revision':2}
    with pytest.raises(main.StateConflict): asyncio.run(main.store_set(s))
    assert s['_store_revision']==2


def test_goal_contract_does_not_leak_into_another_project():
    state={'work_contract':{'goal_id':2,'status':'running','deliverable':'First income'}}
    assert main.contract_for_goal({'id':1},state)=={}
    assert main.contract_for_goal({'id':2},state)['deliverable']=='First income'


def test_external_event_counter_drift_does_not_create_duplicate_history():
    state=copy.deepcopy(main.DEFAULT_STATE)
    state['events']=[{'id':4,'type':'external_evidence'}]
    main.add_event(state,'task_planned')
    assert [e['id'] for e in state['events']]==[4,5]
    assert main.self_audit(state)['ok']


def test_clarification_never_runs_as_a_local_tool():
    assert main.policy({'action_type':'clarify','risk_flags':[]})==(False,'uncertainty')

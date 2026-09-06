import copy
import main


def fresh():
    return copy.deepcopy(main.DEFAULT_STATE)


def test_healthy_state_passes():
    s=fresh()
    s['goals']=[{'id':1,'status':'active','priority':50}]
    s['next_ids']['goal']=2
    r=main.self_audit(s,'test')
    assert r['ok'] is True
    assert s['system_guard']['paused'] is False
    assert s['checkpoints']


def test_duplicate_ids_pause_guard():
    s=fresh()
    s['tasks']=[{'id':1,'status':'completed','kind':'note','requires_approval':False},{'id':1,'status':'completed','kind':'note','requires_approval':False}]
    s['next_ids']['task']=2
    r=main.self_audit(s,'test')
    assert r['ok'] is False
    assert s['system_guard']['paused'] is True
    assert any(x['code']=='duplicate_ids' for x in r['issues'])


def test_executor_pending_is_repaired():
    s=fresh()
    s['tasks']=[{'id':1,'status':'pending','kind':'executor_request','requires_approval':False,'risk_flags':[]}]
    s['next_ids']['task']=2
    r=main.self_audit(s,'test')
    assert r['ok'] is True
    assert s['tasks'][0]['status']=='waiting_executor'
    assert any(x['code']=='executor_rerouted' for x in r['repairs'])


def test_risky_completion_requires_approval_evidence():
    s=fresh()
    s['tasks']=[{'id':1,'status':'completed','kind':'executor_request','requires_approval':False,'risk_flags':['spend_money']}]
    s['next_ids']['task']=2
    r=main.self_audit(s,'test')
    assert r['ok'] is False
    assert any(x['code']=='risky_task_completed_without_approval' for x in r['issues'])


def test_risky_completion_with_approval_passes():
    s=fresh()
    s['tasks']=[{'id':1,'status':'completed','kind':'executor_request','requires_approval':False,'risk_flags':['spend_money']}]
    s['approvals']=[{'id':1,'task_id':1,'status':'approved','category':'spending'}]
    s['next_ids']['task']=2
    s['next_ids']['approval']=2
    r=main.self_audit(s,'test')
    assert r['ok'] is True


def test_next_id_is_self_healed():
    s=fresh()
    s['tasks']=[{'id':7,'status':'completed','kind':'note','requires_approval':False}]
    s['next_ids']['task']=1
    r=main.self_audit(s,'test')
    assert r['ok'] is True
    assert s['next_ids']['task']==8
    assert any(x['code']=='next_id_advanced' for x in r['repairs'])


if __name__ == '__main__':
    test_healthy_state_passes()
    test_duplicate_ids_pause_guard()
    test_executor_pending_is_repaired()
    test_risky_completion_requires_approval_evidence()
    test_risky_completion_with_approval_passes()
    test_next_id_is_self_healed()
    print('self-audit acceptance tests passed')

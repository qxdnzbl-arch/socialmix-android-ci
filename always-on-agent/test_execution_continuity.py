from main import repair_continuity_action

base_state={
    'goals':[{'id':1,'status':'active'}],
    'work_contract':{'status':'running'}
}

# Passive waiting must be converted into executable connected-tool work.
a,changed=repair_continuity_action({
    'action_type':'note',
    'title':'Awaiting further owner directives',
    'instruction':'Stand by for further instructions from the owner.',
    'payload':{'text':'No remaining executable steps are identified.'},
    'risk_flags':[]
},base_state)
assert changed is True
assert a['action_type']=='executor_request'
assert a['payload']['capability']=='continue_active_goal'

# Premature completion while the persistent contract is still running is blocked.
a,changed=repair_continuity_action({
    'action_type':'complete','title':'Done','instruction':'','payload':{},'risk_flags':[]
},base_state)
assert changed is True
assert a['action_type']=='executor_request'

# Real executable work is untouched.
a,changed=repair_continuity_action({
    'action_type':'web_search','title':'Research','instruction':'','payload':{'query':'x'},'risk_flags':[]
},base_state)
assert changed is False
assert a['action_type']=='web_search'

# Risk-bearing actions stay in the normal approval path rather than being silently rewritten.
a,changed=repair_continuity_action({
    'action_type':'note','title':'Wait for owner','instruction':'','payload':{},'risk_flags':['spend_money']
},base_state)
assert changed is False
assert a['risk_flags']==['spend_money']

# No active parent goal means there is nothing to continue.
a,changed=repair_continuity_action({
    'action_type':'note','title':'Awaiting further owner directives','instruction':'','payload':{},'risk_flags':[]
},{'goals':[{'id':1,'status':'completed'}],'work_contract':{'status':'running'}})
assert changed is False

print('execution-continuity acceptance tests passed')

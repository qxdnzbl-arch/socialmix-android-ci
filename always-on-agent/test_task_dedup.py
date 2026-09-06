import main

state={
    'goals':[{'id':1,'status':'active'}],
    'tasks':[
        {
            'id':27,'kind':'executor_request','status':'completed',
            'title':'Verify live planner creates a real next task',
            'instruction':'Run the deployed planner once.',
            'payload':{
                'capability':'deployment_and_code_change',
                'objective':'Prove the agent resumes after task 26 clearance: true and rule 2d no passive waiting.'
            }
        }
    ]
}

# Punctuation/spacing noise must not defeat duplicate detection.
a={
    'action_type':'executor_request',
    'title':'Verify live planner emits a real next task',
    'instruction':'Run planner again.',
    'payload':{
        'capability':'deployment_and_code_change',
        'objective':'Prove the agent resumes after task 26 clearance:true and rule 2d no passive waiting.'
    },
    'risk_flags':[]
}
r,changed,dup=main.repair_duplicate_action(a,state)
assert changed is True
assert dup==27
assert r['action_type']=='executor_request'
assert r['payload']['capability']=='continue_active_goal'
assert r['payload']['params']['duplicate_task_id']==27

# A genuinely different objective is not rewritten.
a2=dict(a)
a2['payload']={'capability':'deployment_and_code_change','objective':'Implement a new deterministic verifier stage.'}
r,changed,dup=main.repair_duplicate_action(a2,state)
assert changed is False and dup is None

# Risk-bearing work remains in approval policy rather than being silently deduplicated.
a3=dict(a); a3['risk_flags']=['spend_money']
r,changed,dup=main.repair_duplicate_action(a3,state)
assert changed is False

# Completed parent goals do not need continuation repair.
state_done={'goals':[{'id':1,'status':'completed'}],'tasks':state['tasks']}
r,changed,dup=main.repair_duplicate_action(a,state_done)
assert changed is False

print('task-dedup acceptance tests passed')

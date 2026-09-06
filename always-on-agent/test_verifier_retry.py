import time
import main

# Deterministic tool verification: a returned object is not automatically success.
assert main.verify_tool_result('web_get', {'status':200,'text':'x'*120})['ok'] is True
assert main.verify_tool_result('web_get', {'status':200,'text':''})['ok'] is False
assert main.verify_tool_result('web_get', {'status':500,'text':'x'*120})['ok'] is False
assert main.verify_tool_result('web_search', {'batches':[{'results':[{'url':'https://example.com'}]}]})['ok'] is True
assert main.verify_tool_result('web_search', {'batches':[{'results':[]}]})['ok'] is False
assert main.verify_tool_result('note', {'text':'evidence recorded'})['ok'] is True
assert main.verify_tool_result('note', {'text':''})['ok'] is False

# Backoff is exponential, bounded, and persisted as a due timestamp.
old_base, old_max = main.RETRY_BASE_SECONDS, main.RETRY_MAX_SECONDS
main.RETRY_BASE_SECONDS, main.RETRY_MAX_SECONDS = 10, 25
assert main.retry_delay_seconds(1) == 10
assert main.retry_delay_seconds(2) == 20
assert main.retry_delay_seconds(3) == 25
now=time.time()
assert main.retry_due({'retry_at_epoch':now-1}, now) is True
assert main.retry_due({'retry_at_epoch':now+30}, now) is False
main.RETRY_BASE_SECONDS, main.RETRY_MAX_SECONDS = old_base, old_max

# Failure signatures collapse changing numeric noise so the same root failure is recognized.
a=main.failure_signature('web_get','HTTP 500 after 12345 ms')
b=main.failure_signature('web_get','HTTP 500 after 67890 ms')
assert a == b

# A process crash must not strand a local running task forever.
old_stale=main.RUNNING_STALE_SECONDS
main.RUNNING_STALE_SECONDS=100
state={'tasks':[{'id':1,'kind':'web_get','status':'running','started_at_epoch':1000.0},{'id':2,'kind':'executor_request','status':'waiting_executor'}]}
recovered=main.recover_stale_running_tasks(state, at=1200.0)
assert recovered == [1]
assert state['tasks'][0]['status']=='pending'
assert state['tasks'][0]['retry_at_epoch']==1200.0
main.RUNNING_STALE_SECONDS=old_stale

print('verifier/retry acceptance tests passed')

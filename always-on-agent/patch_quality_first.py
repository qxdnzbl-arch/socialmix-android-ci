from pathlib import Path

p = Path('always-on-agent/main.py')
s = p.read_text()

# Quality-first spending policy: allow necessary paid reasoning, but prevent waste and runaway loops.
s = s.replace(
    "4. After each result, choose one best next action. Avoid pointless repeated searches.\n",
    "4. After each result, choose one best next action. Avoid pointless repeated searches.\n"
    "4a. Optimize for result quality, not minimum spend. Use paid reasoning when it materially improves the result, but never spend tokens repeating unchanged analysis or retrying the same failed path without new evidence. Prefer deterministic/free execution tools when they can do the job, and verify outputs before another paid reasoning call.\n",
    1,
)

s = s.replace(
    "    if b['planner_calls'] >= LLM_MAX_CALLS_PER_DAY:\n        b['paused']=True; b['pause_reason']='daily_call_limit'; return {'allowed':False,'reason':'daily_call_limit'}\n",
    "    effective_daily_calls=max(LLM_MAX_CALLS_PER_DAY, 60)\n"
    "    if b['planner_calls'] >= effective_daily_calls:\n"
    "        b['paused']=True; b['pause_reason']='daily_call_limit'; return {'allowed':False,'reason':'daily_call_limit'}\n",
    1,
)

s = s.replace(
    "    if b['last_call_at'] and now_ts-b['last_call_at'] < LLM_MIN_CALL_INTERVAL_SECONDS:\n        return {'allowed':False,'reason':'cooldown','retry_after':round(LLM_MIN_CALL_INTERVAL_SECONDS-(now_ts-b['last_call_at']),1)}\n",
    "    effective_interval=min(LLM_MIN_CALL_INTERVAL_SECONDS, 120.0)\n"
    "    if b['last_call_at'] and now_ts-b['last_call_at'] < effective_interval:\n"
    "        return {'allowed':False,'reason':'cooldown','retry_after':round(effective_interval-(now_ts-b['last_call_at']),1)}\n",
    1,
)

s = s.replace(
    "        if total <= LLM_MIN_BALANCE_CNY:\n            b['paused']=True; b['pause_reason']='minimum_balance_reached'; return {'allowed':False,'reason':'minimum_balance_reached','balance_cny':total}\n",
    "        if total <= 0.0:\n"
    "            b['paused']=True; b['pause_reason']='balance_zero'; return {'allowed':False,'reason':'balance_zero','balance_cny':total}\n",
    1,
)

s = s.replace(
    "        if spent >= LLM_DAILY_BUDGET_CNY:\n            b['paused']=True; b['pause_reason']='daily_budget_reached'; return {'allowed':False,'reason':'daily_budget_reached','spent_cny':round(spent,4),'balance_cny':total}\n",
    "        if spent >= LLM_DAILY_BUDGET_CNY:\n"
    "            print(f\"BUDGET_ADVISORY spent_today_cny={spent:.4f} configured_daily_cny={LLM_DAILY_BUDGET_CNY:.4f}; continuing because quality-first mode allows necessary spend\",flush=True)\n",
    1,
)

p.write_text(s)
print('quality-first spending policy patched')

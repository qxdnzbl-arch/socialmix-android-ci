#!/usr/bin/env bash
set -euo pipefail

fail() { echo "KEHUA_REVIVAL_BOUNDARY_FAIL: $*" >&2; exit 1; }

AUTH="KEHUA-REVIVAL-AUTHORITY.md"
[[ -f "$AUTH" ]] || fail "missing $AUTH"

grep -q '状态：ACTIVE' "$AUTH" || fail "revival authority is not ACTIVE"
grep -q '暂停全部 AI 接入' "$AUTH" || fail "AI freeze missing"
grep -q '用户聊天中亲自确认的原可话需求是最高依据' "$AUTH" || fail "user-confirmed authority missing"
grep -q '当前视觉复刻尚未经过用户实际看过并确认通过' "$AUTH" || fail "visual confirmation gate missing"
grep -q '注册 / 找回密码流程尚未最终定案' "$AUTH" || fail "auth-flow confirmation gate missing"
grep -q 'Android、iPhone/iPad、Windows、macOS' "$AUTH" || fail "multi-platform target missing"

# Historical specs remain allowed as evidence, but may not claim current product authority.
for f in SPEC-socialmix-core-stable.md SPEC-socialmix-live.md; do
  [[ -f "$f" ]] || continue
  if grep -Eqi '(^|[[:space:]])(current|authoritative|source of truth|当前权威|产品定义权威)[[:space:]:：]' "$f"; then
    fail "$f contains an authority claim; historical specs cannot define revived Kehua"
  fi
done

echo "KEHUA_REVIVAL_BOUNDARY_PASS"

from pathlib import Path

p=Path('always-on-agent/main.py')
s=p.read_text()
start=s.find('async def tool_web_search(payload):')
end=s.find('\nasync def run_tool(kind,payload):', start)
if start < 0 or end < 0:
    raise SystemExit('web_search function markers not found')
new=r'''async def tool_web_search(payload):
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
'''
current=s[start:end]
if current.strip()==new.strip():
    print('strict relevance search already installed')
else:
    s=s[:start]+new+s[end:]
    p.write_text(s)
    print('strict relevance search installed')

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
        stop={'with','from','that','this','into','about','best','practices','current','using','used','what','when','where','which','their','there','have','has','for','and','the','how','why'}
        toks=[x.lower() for x in re.findall(r'[A-Za-z0-9_\-]{4,}',q)]
        return [x for x in toks if x not in stop][:10]

    def relevant(item,terms):
        if not terms: return True
        hay=(' '+str(item.get('title',''))+' '+str(item.get('snippet',''))+' '+str(item.get('url',''))+' ').lower()
        return any(t in hay for t in terms)

    async with httpx.AsyncClient(timeout=timeout,follow_redirects=True,headers=headers) as c:
        for q in queries[:4]:
            terms=terms_for(q); candidates=[]; used_sources=[]

            # 1) Bing RSS: fast and stable when it returns relevant results.
            try:
                params={'format':'rss','q':q,'mkt':'en-US','setlang':'en-US'}
                r=await c.get('https://www.bing.com/search',params=params); r.raise_for_status(); xml=r.text
                items=re.findall(r'<item>(.*?)</item>',xml,re.I|re.S)
                for item in items[:max(count*2,10)]:
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

            # 2) GitHub repository search: useful for software, tooling and implementation evidence.
            if len(candidates)<count:
                try:
                    r=await c.get('https://api.github.com/search/repositories',params={'q':q,'per_page':min(count*2,20)},headers={**headers,'Accept':'application/vnd.github+json'}); r.raise_for_status(); data=r.json()
                    for item in data.get('items',[]):
                        row={'title':item.get('full_name') or item.get('name') or '', 'url':item.get('html_url') or '', 'snippet':str(item.get('description') or '')[:500], 'source':'github'}
                        if relevant(row,terms): candidates.append(row)
                    used_sources.append('github')
                except Exception as e:
                    print('SEARCH_GITHUB_ERROR',type(e).__name__,flush=True)

            # 3) Hacker News Algolia: community/engineering discussions and real-world references.
            if len(candidates)<count:
                try:
                    r=await c.get('https://hn.algolia.com/api/v1/search',params={'query':q,'tags':'story','hitsPerPage':min(count*2,20)}); r.raise_for_status(); data=r.json()
                    for item in data.get('hits',[]):
                        href=item.get('url') or ('https://news.ycombinator.com/item?id='+str(item.get('objectID') or ''))
                        row={'title':item.get('title') or item.get('story_title') or '', 'url':href, 'snippet':str(item.get('_highlightResult',{}))[:500], 'source':'hackernews'}
                        if href and relevant(row,terms): candidates.append(row)
                    used_sources.append('hackernews')
                except Exception as e:
                    print('SEARCH_HN_ERROR',type(e).__name__,flush=True)

            # 4) DuckDuckGo HTML fallback.
            if len(candidates)<count:
                try:
                    r=await c.get('https://html.duckduckgo.com/html/',params={'q':q}); r.raise_for_status(); html=r.text
                    pairs=re.findall(r'<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',html,re.I|re.S)
                    for href,title in pairs[:max(count*2,10)]:
                        href=unescape(href)
                        if 'uddg=' in href:
                            try: href=parse_qs(urlparse(href).query).get('uddg',[href])[0]
                            except Exception: pass
                        row={'title':strip_html(title),'url':href,'snippet':'','source':'duckduckgo'}
                        if href.startswith('http') and relevant(row,terms): candidates.append(row)
                    used_sources.append('duckduckgo')
                except Exception as e:
                    print('SEARCH_DDG_ERROR',type(e).__name__,flush=True)

            # 5) Wikipedia API: background concepts when web/community sources are sparse.
            if len(candidates)<count:
                try:
                    r=await c.get('https://en.wikipedia.org/w/api.php',params={'action':'query','list':'search','srsearch':q,'format':'json','utf8':1,'srlimit':min(count*2,20)}); r.raise_for_status(); data=r.json()
                    for item in (data.get('query') or {}).get('search',[]):
                        title=str(item.get('title') or '').strip()
                        if not title: continue
                        row={'title':title,'url':'https://en.wikipedia.org/wiki/'+quote_plus(title.replace(' ','_')),'snippet':strip_html(str(item.get('snippet') or ''))[:500],'source':'wikipedia'}
                        if relevant(row,terms): candidates.append(row)
                    used_sources.append('wikipedia')
                except Exception as e:
                    print('SEARCH_WIKIPEDIA_ERROR',type(e).__name__,flush=True)

            # De-duplicate and strip search-engine internal pages.
            seen=set(); clean=[]
            for item in candidates:
                u=str(item.get('url','')).strip(); host=(urlparse(u).hostname or '').lower()
                if not u.startswith('http') or any(x in host for x in ('bing.com','google.com','duckduckgo.com')): continue
                if u in seen: continue
                seen.add(u); clean.append(item)
                if len(clean)>=count: break
            all_results.append({'query':q,'sources':used_sources,'results':clean})
    return {'queries':queries[:4],'batches':all_results}
'''
current=s[start:end]
if current.strip()==new.strip():
    print('relevance-filtered search already installed')
else:
    s=s[:start]+new+s[end:]
    p.write_text(s)
    print('relevance-filtered search installed')

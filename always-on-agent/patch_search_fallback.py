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
    async with httpx.AsyncClient(timeout=timeout,follow_redirects=True,headers=headers) as c:
        for q in queries[:4]:
            out=[]; source='bing_rss'

            # 1) Bing RSS is substantially more stable than scraping search-result HTML.
            try:
                r=await c.get('https://www.bing.com/search?format=rss&q='+quote_plus(q)); r.raise_for_status(); xml=r.text
                items=re.findall(r'<item>(.*?)</item>',xml,re.I|re.S)
                for item in items[:count]:
                    mt=re.search(r'<title>(.*?)</title>',item,re.I|re.S)
                    ml=re.search(r'<link>(.*?)</link>',item,re.I|re.S)
                    md=re.search(r'<description>(.*?)</description>',item,re.I|re.S)
                    if not ml: continue
                    href=unescape(strip_html(ml.group(1))).strip()
                    if href.startswith('http'):
                        out.append({'title':strip_html(mt.group(1) if mt else href),'url':href,'snippet':strip_html(md.group(1) if md else '')[:500]})
            except Exception as e:
                print('SEARCH_BING_RSS_ERROR',type(e).__name__,flush=True)

            # 2) DuckDuckGo HTML fallback.
            if not out:
                source='duckduckgo'
                try:
                    r=await c.get('https://html.duckduckgo.com/html/?q='+quote_plus(q)); r.raise_for_status(); html=r.text
                    pairs=re.findall(r'<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',html,re.I|re.S)
                    for href,title in pairs[:count]:
                        href=unescape(href)
                        if 'uddg=' in href:
                            try: href=parse_qs(urlparse(href).query).get('uddg',[href])[0]
                            except Exception: pass
                        if href.startswith('http'): out.append({'title':strip_html(title),'url':href})
                except Exception as e:
                    print('SEARCH_DDG_ERROR',type(e).__name__,flush=True)

            # 3) Bing HTML fallback.
            if not out:
                source='bing_html'
                try:
                    r=await c.get('https://www.bing.com/search?q='+quote_plus(q)); r.raise_for_status(); html=r.text
                    pairs=re.findall(r'<li[^>]+class="[^"]*b_algo[^"]*"[^>]*>.*?<h2[^>]*>\s*<a[^>]+href="(https?://[^"]+)"[^>]*>(.*?)</a>',html,re.I|re.S)
                    for href,title in pairs[:count]: out.append({'title':strip_html(title),'url':unescape(href)})
                except Exception as e:
                    print('SEARCH_BING_HTML_ERROR',type(e).__name__,flush=True)

            # 4) Public GitHub repository search gives a useful real-world fallback for technical research.
            if not out:
                source='github'
                try:
                    r=await c.get('https://api.github.com/search/repositories',params={'q':q,'per_page':count},headers={**headers,'Accept':'application/vnd.github+json'}); r.raise_for_status(); data=r.json()
                    for item in data.get('items',[])[:count]:
                        out.append({'title':item.get('full_name') or item.get('name') or '', 'url':item.get('html_url') or '', 'snippet':str(item.get('description') or '')[:500]})
                except Exception as e:
                    print('SEARCH_GITHUB_ERROR',type(e).__name__,flush=True)

            # 5) Wikipedia API is a low-risk final fallback for background concepts.
            if not out:
                source='wikipedia'
                try:
                    r=await c.get('https://en.wikipedia.org/w/api.php',params={'action':'query','list':'search','srsearch':q,'format':'json','utf8':1,'srlimit':count}); r.raise_for_status(); data=r.json()
                    for item in (data.get('query') or {}).get('search',[])[:count]:
                        title=str(item.get('title') or '').strip()
                        if title:
                            out.append({'title':title,'url':'https://en.wikipedia.org/wiki/'+quote_plus(title.replace(' ','_')),'snippet':strip_html(str(item.get('snippet') or ''))[:500]})
                except Exception as e:
                    print('SEARCH_WIKIPEDIA_ERROR',type(e).__name__,flush=True)

            seen=set(); clean=[]
            for item in out:
                u=str(item.get('url','')).strip(); host=(urlparse(u).hostname or '').lower()
                if not u.startswith('http') or any(x in host for x in ('bing.com','google.com','duckduckgo.com')): continue
                if u in seen: continue
                seen.add(u); clean.append(item)
                if len(clean)>=count: break
            all_results.append({'query':q,'source':source,'results':clean})
    return {'queries':queries[:4],'batches':all_results}
'''
current=s[start:end]
if current.strip()==new.strip():
    print('RSS-first search already installed')
else:
    s=s[:start]+new+s[end:]
    p.write_text(s)
    print('RSS-first search installed')

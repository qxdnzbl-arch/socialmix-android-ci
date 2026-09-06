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
    async with httpx.AsyncClient(timeout=35,follow_redirects=True,headers=headers) as c:
        for q in queries[:4]:
            out=[]
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
            if not out:
                source='bing'
                try:
                    r=await c.get('https://www.bing.com/search?q='+quote_plus(q)); r.raise_for_status(); html=r.text
                    pairs=re.findall(r'<li[^>]+class="[^"]*b_algo[^"]*"[^>]*>.*?<h2[^>]*>\s*<a[^>]+href="(https?://[^"]+)"[^>]*>(.*?)</a>',html,re.I|re.S)
                    for href,title in pairs[:count]:
                        out.append({'title':strip_html(title),'url':unescape(href)})
                except Exception as e:
                    print('SEARCH_BING_ERROR',type(e).__name__,flush=True)
            if not out:
                source='google'
                try:
                    r=await c.get('https://www.google.com/search?q='+quote_plus(q)+'&num='+str(count)); r.raise_for_status(); html=r.text
                    pairs=re.findall(r'<a[^>]+href="/url\?q=(https?%3A%2F%2F[^&\"]+)[^\"]*"[^>]*>(.*?)</a>',html,re.I|re.S)
                    from urllib.parse import unquote
                    for href,title in pairs[:count]:
                        out.append({'title':strip_html(title),'url':unquote(unescape(href))})
                except Exception as e:
                    print('SEARCH_GOOGLE_ERROR',type(e).__name__,flush=True)
            # de-duplicate and remove search-engine internal links
            seen=set(); clean=[]
            for item in out:
                u=item.get('url','')
                host=(urlparse(u).hostname or '').lower()
                if not u.startswith('http') or any(x in host for x in ('bing.com','google.com','duckduckgo.com')): continue
                if u in seen: continue
                seen.add(u); clean.append(item)
                if len(clean)>=count: break
            all_results.append({'query':q,'source':source,'results':clean})
    return {'queries':queries[:4],'batches':all_results}
'''
if 'SEARCH_BING_ERROR' in s[start:end]:
    print('search fallbacks already installed')
else:
    s=s[:start]+new+s[end:]
    p.write_text(s)
    print('search fallbacks installed')

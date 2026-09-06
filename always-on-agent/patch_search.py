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
    async with httpx.AsyncClient(timeout=35,follow_redirects=True,headers={'User-Agent':'Mozilla/5.0 OwnerAgent/0.2'}) as c:
        for q in queries[:4]:
            url='https://html.duckduckgo.com/html/?q='+quote_plus(q)
            r=await c.get(url); r.raise_for_status(); html=r.text
            pairs=re.findall(r'<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',html,re.I|re.S)
            out=[]
            for href,title in pairs[:count]:
                href=unescape(href)
                if 'uddg=' in href:
                    try: href=parse_qs(urlparse(href).query).get('uddg',[href])[0]
                    except Exception: pass
                out.append({'title':strip_html(title),'url':href})
            all_results.append({'query':q,'results':out})
    return {'queries':queries[:4],'batches':all_results}
'''
if 'web_search requires query or queries' in s[start:end]:
    print('batch web_search already installed')
else:
    s=s[:start]+new+s[end:]
    p.write_text(s)
    print('batch web_search installed')

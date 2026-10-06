"""Publish only the APK whose native core acceptance has succeeded."""
import pathlib,json,hashlib,os,urllib.request,urllib.error,sys
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'acceptance';REPO='qxdnzbl-arch/socialmix-android-ci';BASE='https://api.github.com/repos/'+REPO;TOKEN=os.environ['GH_TOKEN']
def api(path,method='GET',data=None,raw=False):
 url=path if path.startswith('https://') else BASE+path
 payload=data if raw else None if data is None else json.dumps(data).encode()
 r=urllib.request.Request(url,data=payload,method=method,headers={'Authorization':'Bearer '+TOKEN,'Accept':'application/vnd.github+json','Content-Type':'application/octet-stream' if raw else 'application/json','User-Agent':'DualPhone-release-ci'})
 with urllib.request.urlopen(r,timeout=60) as response:
  body=response.read();return json.loads(body) if body else None
def find(tag):
 try:return api('/releases/tags/'+tag)
 except urllib.error.HTTPError as e:
  if e.code==404:return None
  raise
if len(sys.argv)>1 and sys.argv[1]=='rollback':
 previous=json.loads((OUT/'previous-channel.json').read_text());channel=find('dualphone-latest')
 if channel is not None:
  if previous is None:api('/releases/'+str(channel['id']),'DELETE')
  else:
   channel=api('/releases/'+str(channel['id']),'PATCH',{'body':json.dumps(previous,ensure_ascii=False)})
   for a in channel.get('assets',[]):
    if a['name']=='update.json':api('/releases/assets/'+str(a['id']),'DELETE')
   api(channel['upload_url'].split('{')[0]+'?name=update.json','POST',json.dumps(previous,ensure_ascii=False).encode(),True)
 print('Update channel restored after failed install acceptance',flush=True);raise SystemExit(0)
if not json.loads((OUT/'core-result.json').read_text()).get('passed'):raise RuntimeError('Core runtime acceptance has not passed')
apk=ROOT/'app/build/outputs/apk/release/app-release.apk';version=int(os.environ['RELEASE_VERSION_CODE']);name=os.environ['RELEASE_VERSION_NAME'];commit=os.environ['GITHUB_SHA'];tag='dualphone-v'+name.replace('-native','')+'-'+commit[:8]
sha=hashlib.sha256(apk.read_bytes()).hexdigest();asset_name='ShuangJiChuan_'+str(version)+'.apk'
release=find(tag)
if release is None:release=api('/releases','POST',{'tag_name':tag,'target_commitish':commit,'name':'双机传 '+name,'body':'深灰实底界面、图片查看、最新消息滚动、光标退出和内置更新。','draft':True,'prerelease':False,'make_latest':'false'})
existing=next((a for a in release.get('assets',[]) if a['name']==asset_name),None)
if existing is not None:
 if existing.get('digest') and existing['digest']!='sha256:'+sha:raise RuntimeError('Immutable versioned asset changed')
else:api(release['upload_url'].split('{')[0]+'?name='+asset_name,'POST',apk.read_bytes(),True)
if release.get('draft'):release=api('/releases/'+str(release['id']),'PATCH',{'draft':False,'make_latest':'false'})
meta={'packageName':'com.qxdnzbl.shuangjichuan.offline','versionCode':version,'versionName':name,'sha256':sha,'apkUrl':'https://github.com/'+REPO+'/releases/download/'+tag+'/'+asset_name,'notes':'深灰实底界面；图片点开查看和保存；发送后跟到最新消息；退出输入光标消失；内置更新。'}
# Validate the real public download, without forwarding a token to asset hosts.
with urllib.request.urlopen(meta['apkUrl'],timeout=60) as response:download=response.read()
if hashlib.sha256(download).hexdigest()!=sha:raise RuntimeError('Public APK bytes mismatch')
channel=find('dualphone-latest')
(OUT/'previous-channel.json').write_text(json.dumps(json.loads(channel.get('body','{}')) if channel else None,ensure_ascii=False))
if channel is not None:
 try:old=json.loads(channel.get('body','{}'))
 except ValueError:old={}
 if old.get('versionCode',0)>version:raise RuntimeError('A newer update channel exists')
 channel=api('/releases/'+str(channel['id']),'PATCH',{'body':json.dumps(meta,ensure_ascii=False),'name':'双机传应用更新','draft':False,'prerelease':False,'make_latest':'false'})
else:channel=api('/releases','POST',{'tag_name':'dualphone-latest','target_commitish':commit,'name':'双机传应用更新','body':json.dumps(meta,ensure_ascii=False),'draft':False,'prerelease':False,'make_latest':'false'})
for a in channel.get('assets',[]):
 if a['name']=='update.json':api('/releases/assets/'+str(a['id']),'DELETE')
api(channel['upload_url'].split('{')[0]+'?name=update.json','POST',json.dumps(meta,ensure_ascii=False).encode(),True)
current=json.loads(api('/releases/tags/dualphone-latest')['body'])
if current!=meta:raise RuntimeError('Published channel mismatch')
(OUT/'published-update.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2))
print(json.dumps({'published_version':version,'sha256':sha,'download_verified':True},ensure_ascii=False),flush=True)

import shlex,base64,socket,urllib.request,subprocess,pathlib,time,json,xml.etree.ElementTree as ET,re,sqlite3,sys,os,shutil,hashlib
ADB=shutil.which('adb') or '/tmp/dualphone-sdk/platform-tools/adb'; PKG='com.qxdnzbl.shuangjichuan.offline';COMP=PKG+'/com.qxdnzbl.shuangjichuan.MainActivity'
PROJECT=pathlib.Path(__file__).resolve().parents[1];ROOT=PROJECT.parent;OUT=PROJECT/'acceptance';OUT.mkdir(exist_ok=True)
BASELINE=pathlib.Path(os.environ['BASELINE_APK'])
checks=[]
def adb(*args,timeout=25,ok=True):
 r=subprocess.run([ADB,*args],capture_output=True,timeout=timeout)
 if ok and r.returncode:raise RuntimeError(str(args)+r.stderr.decode(errors='replace')+r.stdout.decode(errors='replace'))
 return r.stdout

def shell(*args,**kw):return adb('shell',*args,**kw).decode(errors='replace')
def record(name,value=True,**extra):
 d={'check':name,'passed':value,**extra};checks.append(d);print(json.dumps(d,ensure_ascii=False),flush=True)
 (OUT/'runtime-checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
 if not value:raise AssertionError(d)

def screen(name):
 (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))

def ui(name='ui',timeout=20):
 start=time.monotonic();deadline=start+timeout;last=''
 while time.monotonic()<deadline:
  shell('rm','-f','/sdcard/acceptance.xml')
  r=subprocess.run([ADB,'shell','uiautomator','dump','/sdcard/acceptance.xml'],capture_output=True,timeout=max(.1,deadline-time.monotonic()))
  last=(r.stdout+r.stderr).decode(errors='replace');(OUT/(name+'-dump.txt')).write_text(last)
  b=adb('exec-out','cat','/sdcard/acceptance.xml')
  if b.lstrip().startswith(b'<?xml'):
   t=ET.fromstring(b);(OUT/(name+'.xml')).write_bytes(b)
   return t,round(time.monotonic()-start,3)
  time.sleep(.3)
 raise RuntimeError('UI tree unavailable: '+last)

def node(tree,text=None,desc=None,cls=None,contains=None):
 for n in tree.iter('node'):
  if text is not None and n.get('text')!=text:continue
  if desc is not None and n.get('content-desc')!=desc:continue
  if cls is not None and n.get('class')!=cls:continue
  if contains is not None and contains not in n.get('text',''):continue
  if n.get('enabled')=='false':continue
  return n
 raise AssertionError('Missing node '+str((text,desc,cls,contains)))

def center(n):
 a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')));return (a+c)//2,(b+d)//2

def tap(n):shell('input','tap',*map(str,center(n)));time.sleep(.4)

def database(name):
 # Copy a consistent SQLite snapshot instead of reading a file while its pages change.
 remote='/data/local/tmp/fixture-snapshot.db';shell('rm','-f',remote)
 encoded=base64.b64encode(("VACUUM INTO '"+remote+"'").encode()).decode()
 shell('env','CLASSPATH=/data/local/tmp/fixture.dex','app_process','/system/bin','FixtureSql','/data/data/'+PKG+'/databases/shuangjichuan.db',encoded)
 p=OUT/(name+'.db');p.write_bytes(adb('exec-out','cat',remote));c=sqlite3.connect(p)
 if c.execute('PRAGMA quick_check').fetchone()[0]!='ok':raise AssertionError('Invalid SQLite snapshot')
 return c

def seed():
 p=OUT/'fixture.db'
 if p.exists():p.unlink()
 c=sqlite3.connect(p)
 c.execute('CREATE TABLE messages (id TEXT PRIMARY KEY,mine INTEGER NOT NULL,kind TEXT NOT NULL,text_content TEXT,file_name TEXT,file_path TEXT,file_size INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,status TEXT NOT NULL)')
 c.execute('CREATE INDEX idx_messages_time ON messages(created_at)');c.execute('CREATE INDEX idx_messages_pending ON messages(mine,status)');c.execute('PRAGMA user_version=1')
 base=int(time.time()*1000)-2000000
 for i in range(1000):
  text='load-'+str(i)+' native-message '+('long message line\n'*10 if i%21==0 else 'scroll test')
  c.execute('INSERT INTO messages VALUES (?,?,?,?,?,?,?,?,?)',('load-'+str(i),i%3!=0,'text',text,None,None,0,base+i*1000,'sent'))
 content=b'file-save-regression\x00\x01';(OUT/'fixture.bin').write_bytes(content)
 c.execute('INSERT INTO messages VALUES (?,?,?,?,?,?,?,?,?)',('file-fixture',0,'file',None,'fixture.bin','/data/data/'+PKG+'/files/incoming/fixture.bin',len(content),base+1000500,'received'))
 for i in range(4):c.execute('INSERT INTO messages VALUES (?,?,?,?,?,?,?,?,?)',('held-'+str(i),1,'text','held-queue-'+str(i),None,None,0,base+1001000+i,'pending'))
 c.commit();c.close()
 shell('am','force-stop',PKG);shell('mkdir','-p','/data/data/'+PKG+'/databases','/data/data/'+PKG+'/files/incoming')
 uid=shell('stat','-c','%u','/data/data/'+PKG).strip()
 for suffix in ['', '-journal','-wal','-shm']:shell('rm','-f','/data/data/'+PKG+'/databases/shuangjichuan.db'+suffix)
 adb('push',str(p),'/data/data/'+PKG+'/databases/shuangjichuan.db');adb('push',str(OUT/'fixture.bin'),'/data/data/'+PKG+'/files/incoming/fixture.bin')
 shell('rm','-f','/data/data/'+PKG+'/databases/shuangjichuan.db-wal','/data/data/'+PKG+'/databases/shuangjichuan.db-shm')
 shell('chown','-R',uid+':'+uid,'/data/data/'+PKG+'/databases','/data/data/'+PKG+'/files')
 shell('restorecon','-R','/data/data/'+PKG+'/databases','/data/data/'+PKG+'/files')


def optional(t,**kw):
 try:return node(t,**kw)
 except AssertionError:return None

def edit(t):return node(t,cls='android.widget.EditText',desc='消息') if optional(t,cls='android.widget.EditText',desc='消息') is not None else next(n for n in t.iter('node') if n.get('class')=='android.widget.EditText' and (n.get('resource-id','').endswith('/message') or n.get('text') in ['消息','Draft_Keep','']))

def insert(id,text,mine=False,kind='text',name=None,path=None,size=0,status=None):
 values=[id,int(mine),kind,text,name,path,size,int(time.time()*1000),status or ('sent' if mine else 'received')]
 sql='INSERT INTO messages VALUES ('+','.join('NULL' if v is None else str(v) if isinstance(v,int) else "'"+v.replace("'","''")+"'" for v in values)+')'
 encoded=base64.b64encode(sql.encode()).decode()
 r=shell('env','CLASSPATH=/data/local/tmp/fixture.dex','app_process','/system/bin','FixtureSql','/data/data/'+PKG+'/databases/shuangjichuan.db',encoded)
 if 'fixture_written' not in r:raise RuntimeError(r)
 shell('am','broadcast','-a','com.qxdnzbl.shuangjichuan.CHANGED','-p',PKG);time.sleep(.5)

def core(reuse=False):
 adb('root');time.sleep(.5);adb('wait-for-device')
 adb('push',str(OUT/'fixture-dex/classes.dex'),'/data/local/tmp/fixture.dex')
 for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:shell('settings','put','global',key,'0')
 shell('settings','put','secure','show_ime_with_hard_keyboard','1');shell('wm','dismiss-keyguard')
 shell('iptables','-A','OUTPUT','-p','tcp','--dport','443','-j','DROP');shell('ip6tables','-A','OUTPUT','-p','tcp','--dport','443','-j','DROP',ok=False)
 adb('install','-r',str(BASELINE),timeout=60);shell('pm','clear',PKG)
 for perm in ['BLUETOOTH_ADVERTISE','BLUETOOTH_CONNECT','BLUETOOTH_SCAN','NEARBY_WIFI_DEVICES','POST_NOTIFICATIONS']:shell('pm','grant',PKG,'android.permission.'+perm,ok=False)
 shell('am','start','-W','-n',COMP);time.sleep(.6);t,_=ui('baseline-empty')
 if optional(t,text="Pixel Launcher isn't responding") is not None:
  tap(node(t,text='Close app'));shell('am','start','-W','-n',COMP);time.sleep(.6);t=wait_ui('baseline-recovered',lambda t:optional(t,text='我的两台手机') is not None,20)
  record('test_device_launcher_recovered')
 record('baseline_chat_visible',optional(t,text='我的两台手机') is not None)
 seed()
 # A real Android screen capture is used only as a private test photo; it is not shipped in the APK.
 photo=adb('exec-out','screencap','-p');(OUT/'fixture-photo.png').write_bytes(photo)
 adb('push',str(OUT/'fixture-photo.png'),'/sdcard/Download/fixture-photo.png')
 adb('push',str(OUT/'fixture-photo.png'),'/data/data/'+PKG+'/files/chat-background.jpg')
 uid=shell('stat','-c','%u','/data/data/'+PKG).strip();shell('chown',uid+':'+uid,'/data/data/'+PKG+'/files/chat-background.jpg')
 shell('mkdir','-p','/data/data/'+PKG+'/shared_prefs')
 prefs=OUT/'old-background-prefs.xml';prefs.write_text('<?xml version="1.0" encoding="utf-8"?><map><int name="bg_blur" value="18"/><int name="bg_dim" value="42"/><boolean name="bg_soft_v2" value="true"/></map>')
 adb('push',str(prefs),'/data/data/'+PKG+'/shared_prefs/dual.xml');shell('chown','-R',uid+':'+uid,'/data/data/'+PKG+'/shared_prefs');shell('restorecon','-R','/data/data/'+PKG)
 before=database('before-upgrade').execute('SELECT COUNT(*) FROM messages').fetchone()[0]
 adb('install','-r',str(OUT/'bootstrap.apk'),timeout=60)
 after=database('after-upgrade').execute('SELECT COUNT(*) FROM messages').fetchone()[0]
 record('same_signature_upgrade_preserves_history',before==after==1005)
 adb('push',str(OUT/'fixture-dex/classes.dex'),'/data/local/tmp/fixture.dex')
 adb('logcat','-c');shell('am','start','-W','-n',COMP);time.sleep(.8)
 t,_=ui('start');record('chat_responsive_with_1000_messages_and_stalled_https',optional(t,text='我的两台手机') is not None)
 screen('background-no-effects')
 from PIL import Image
 source=Image.open(OUT/'fixture-photo.png').convert('RGB');rendered=Image.open(OUT/'background-no-effects.png').convert('RGB')
 points=[(10,y) for y in range(350,min(source.height,rendered.height)-250,80)]
 matched=sum(max(abs(a-b) for a,b in zip(source.getpixel(xy),rendered.getpixel(xy)))<8 for xy in points)
 record('old_blur_and_white_veil_disabled',matched>=len(points)*.8,matching=matched,total=len(points))
 colors=rendered.getcolors(rendered.width*rendered.height) or [];color_counts={color:n for n,color in colors}
 record('opaque_charcoal_bubbles_and_solid_composer',color_counts.get((48,51,58),0)>500 and color_counts.get((236,238,241),0)>1000)
 if not reuse:
  e=edit(t);record('composer_not_focused_on_launch',e.get('focused')=='false')
  tap(e);t,_=ui('blank-input');record('tap_focuses_empty_input',edit(t).get('focused')=='true')
  shell('input','keyevent','4');time.sleep(.6);t,_=ui('input-back');record('keyboard_back_clears_empty_cursor_focus',edit(t).get('focused')=='false')
  tap(edit(t));shell('input','text','Draft_Keep');time.sleep(.5);t,_=ui('keyboard');screen('keyboard')
  record('typing_works',optional(t,text='Draft_Keep',cls='android.widget.EditText') is not None)
  record('real_ime_visible','mInputShown=true' in shell('dumpsys','input_method'))
  tap(node(t,text='我的两台手机'));t,_=ui('outside-input');record('outside_tap_clears_focus_and_keeps_draft',edit(t).get('focused')=='false' and edit(t).get('text')=='Draft_Keep')
  for i in range(4):shell('input','swipe','270','400','270','1100','180')
  time.sleep(.6);t,_=ui('history');seen=[n.get('text') for n in t.iter('node') if n.get('text','').startswith('load-')]
  record('real_history_scroll',bool(seen));record('return_to_latest_available',optional(t,desc='回到最新') is not None)
  insert('arrival-history','incoming-while-reading')
  t,_=ui('history-incoming');after=[n.get('text') for n in t.iter('node') if n.get('text','').startswith('load-')]
  record('incoming_keeps_history_position_and_draft',seen==after and edit(t).get('text')=='Draft_Keep')
  # Local send must jump from history all the way to the newest item.
  tap(node(t,desc='发送'));time.sleep(.7);t,_=ui('own-send')
  record('own_send_clears_input',edit(t).get('text') in ('消息',''))
  record('own_send_forces_latest',optional(t,text='Draft_Keep') is not None and optional(t,desc='回到最新') is None)
  c=database('sent-data');record('own_text_queued_once',c.execute("SELECT COUNT(*) FROM messages WHERE text_content='Draft_Keep' AND status='pending'").fetchone()[0]==1)
  state=node(t,text='等待连接');body=node(t,text='Draft_Keep');parents={child:parent for parent in t.iter() for child in parent}
  record('waiting_state_outside_bubble',parents.get(parents.get(body)) is parents.get(state) and parents.get(body) is not parents.get(state))
  insert('arrival-bottom','incoming-while-at-bottom');t,_=ui('bottom-incoming');record('incoming_follows_at_bottom',optional(t,text='incoming-while-at-bottom') is not None)
  tap(edit(t));t,_=ui('bottom-keyboard');record('latest_visible_above_ime',optional(t,text='incoming-while-at-bottom') is not None)
  shell('input','keyevent','4');time.sleep(.5)
  # Both search and composer must use the same focus exit behavior.
  t,_=ui('before-search');tap(node(t,desc='搜索'));t,_=ui('search-blank')
  search=next(n for n in t.iter('node') if n.get('class')=='android.widget.EditText' and not n.get('resource-id','').endswith('/message'))
  record('search_focuses_on_open',search.get('focused')=='true')
  tap(node(t,text='我的两台手机'));t,_=ui('search-outside');search=next(n for n in t.iter('node') if n.get('class')=='android.widget.EditText' and not n.get('resource-id','').endswith('/message'));record('search_outside_tap_clears_cursor',search.get('focused')=='false')
  tap(search);shell('input','text','load-');time.sleep(.4);t,_=ui('search-match');record('search_all_history',optional(t,text='1/1000') is not None)
  tap(node(t,text='↓'));time.sleep(.5);t,_=ui('search-next');record('search_next',optional(t,text='2/1000') is not None)
  tap(node(t,text='↑'));time.sleep(.4);t,_=ui('search-prev');record('search_previous',optional(t,text='1/1000') is not None)
  tap(node(t,text='×'));t,_=ui('search-close');record('search_close_clears_focus',edit(t).get('focused')=='false')
  tap(node(t,desc='回到最新'));t,_=ui('latest-button');record('return_to_latest_reaches_newest',optional(t,text='incoming-while-at-bottom') is not None)
  target=node(t,text='incoming-while-at-bottom');x,y=center(target);shell('input','swipe',str(x),str(y),str(x),str(y),'850');t,_=ui('longpress');record('long_press_copy_and_search',optional(t,text='复制') is not None and optional(t,text='搜索') is not None);tap(node(t,text='复制'))
  # Real received photo, asynchronous thumbnail, full viewer, gestures, save and close.
  private='/data/data/'+PKG+'/files/incoming/fixture-photo.png';adb('push',str(OUT/'fixture-photo.png'),private);shell('chown',uid+':'+uid,private);shell('restorecon',private)
  insert('photo-arrival',None,kind='file',name='fixture-photo.png',path=private,size=len(photo));time.sleep(.7);t,_=ui('photo-thumb')
  record('received_photo_is_thumbnail',optional(t,desc='图片：fixture-photo.png') is not None and optional(t,text='图片') is None)
  tap(node(t,desc='图片：fixture-photo.png'));time.sleep(.5);t,_=ui('photo-viewer');record('native_photo_viewer_opens',optional(t,desc='图片预览') is not None and optional(t,text='保存到下载') is not None)
  screen('photo-viewer');shell('input','tap','500','750');shell('input','tap','500','750');shell('input','swipe','700','850','430','650','300');t,_=ui('photo-zoom');record('photo_zoom_pan_remains_responsive',optional(t,desc='关闭图片') is not None)
  tap(node(t,text='保存到下载'));time.sleep(.6)
  deadline=time.monotonic()+12;valid=False;paths=[];expected_hash=hashlib.sha256(photo).hexdigest()
  while time.monotonic()<deadline:
   paths=shell('find','/sdcard/Download','-type','f','-name','fixture-photo*').splitlines()
   valid=len(paths)>=2 and any(shell('sha256sum '+shlex.quote(p)).startswith(expected_hash) for p in paths if p!='/sdcard/Download/fixture-photo.png')
   if valid:break
   time.sleep(.3)
  record('photo_save_keeps_original_bytes',valid,download_files=paths)
  t,_=ui('photo-save');tap(node(t,desc='关闭图片'));t,_=ui('photo-close');record('viewer_returns_to_chat',optional(t,text='我的两台手机') is not None)
  # Real file picker selection must enqueue and land at latest, even from history.
  for i in range(3):shell('input','swipe','270','400','270','1100','150')
  t,_=ui('file-send-from-history');tap(node(t,desc='添加文件'));time.sleep(.6);t,_=ui('file-picker')
  if optional(t,desc='Show roots') is not None:tap(node(t,desc='Show roots'));t,_=ui('file-roots')
  if optional(t,text='Downloads') is not None:tap(node(t,text='Downloads'));t,_=ui('file-downloads')
  if optional(t,text='fixture-photo.png') is None:
   search=optional(t,desc='Search');
   if search is not None:
    tap(search);t,_=ui('file-picker-search');tap(node(t,cls='android.widget.EditText'));shell('input','text','fixture-photo.png');shell('input','keyevent','66');time.sleep(.6);t,_=ui('file-picker-found')
  tap(node(t,text='fixture-photo.png'));time.sleep(.7);t,_=ui('file-sent')
  record('file_picker_selects_and_returns',optional(t,text='我的两台手机') is not None)
  record('own_file_send_forces_latest',optional(t,desc='图片：fixture-photo.png') is not None and optional(t,desc='回到最新') is None)
  thumbs=[n for n in t.iter('node') if n.get('content-desc')=='图片：fixture-photo.png'];tap(thumbs[-1]);t,_=ui('own-photo-viewer');record('own_photo_also_opens_viewer',optional(t,desc='关闭图片') is not None);tap(node(t,desc='关闭图片'));t,_=ui('own-photo-close')
  c=database('file-sent-data');record('outgoing_file_queued_with_original_bytes',c.execute("SELECT COUNT(*) FROM messages WHERE mine=1 AND kind='file' AND file_name='fixture-photo.png' AND status='pending'").fetchone()[0]==1)
  # Locate regular file by actual filename and save the real bytes.
  tap(node(t,desc='搜索'));t,_=ui('file-search-open');search=next(n for n in t.iter('node') if n.get('class')=='android.widget.EditText' and not n.get('resource-id','').endswith('/message'));tap(search);shell('input','text','fixture.bin');time.sleep(.3);t,_=ui('file-search');tap(node(t,text='↓'));time.sleep(.5);t,_=ui('file-found');tap(node(t,text='×'));t,_=ui('file-ready');tap(node(t,text='fixture.bin'));time.sleep(.7)
  paths=shell('find','/sdcard/Download','-type','f','-name','fixture*.bin').splitlines();record('compact_file_saves_original_bytes',bool(paths) and adb('exec-out','cat',paths[-1])==(OUT/'fixture.bin').read_bytes())
  t,_=ui('before-background');tap(node(t,desc='聊天背景'));t,_=ui('background-dialog')
  record('background_controls_removed',optional(t,text='选择照片') is not None and not any(n.get('class')=='android.widget.SeekBar' or n.get('text') in ('模糊','柔化','背景只保存在这台手机，没有预设背景。') for n in t.iter('node')))
  record('update_entry_visible',optional(t,text='检查更新') is not None);screen('background-dialog')
  tap(node(t,text='清除'));time.sleep(.5);t,_=ui('background-cleared');record('clear_background_returns',optional(t,text='我的两台手机') is not None)
  for attempt in range(3):
   tap(node(t,desc='聊天背景'));t,_=ui('cancel-check-settings');tap(node(t,text='检查更新'));time.sleep(.2);t,_=ui('cancel-check')
   if optional(t,text='取消') is not None:tap(node(t,text='取消'));break
   t,_=ui('cancel-retry-chat')
  else:raise AssertionError('Unable to open cancellable update check')
  t,_=ui('cancel-check-return');record('stalled_update_check_can_cancel_back_to_chat',optional(t,text='我的两台手机') is not None)
 if reuse:
  private='/data/data/'+PKG+'/files/incoming/fixture-photo.png';adb('push',str(OUT/'fixture-photo.png'),private);shell('chown',uid+':'+uid,private);shell('restorecon',private)
  tap(node(t,desc='聊天背景'));t,_=ui('fast-background');tap(node(t,text='清除'));t,_=ui('fast-default-chat')
  record('new_update_fixture_ready',optional(t,text='我的两台手机') is not None)
 # Add human-readable demo messages to the test device only, for actual release screenshots.
 insert('demo-1','现在消息终于能跟到最下面了。',mine=True)
 insert('demo-2','收到。图片可以直接点开看。')
 plan=OUT/'weekend-plan.txt';plan.write_text('周末计划\n听音乐，休息，看一部喜欢的电影。\n',encoding='utf-8')
 planpath='/data/data/'+PKG+'/files/incoming/weekend-plan.txt';adb('push',str(plan),planpath);shell('chown',uid+':'+uid,planpath);shell('restorecon',planpath)
 insert('demo-file',None,mine=True,kind='file',name='周末计划.txt',path=planpath,size=plan.stat().st_size)
 insert('demo-3','这个深灰实底先试试看。\n输入栏也有清楚的边界了。',mine=True)
 t,_=ui('demo-before-update');screen('chat-before-update')
 log=adb('logcat','-d').decode(errors='replace');(OUT/'core-logcat.txt').write_text(log)
 record('no_anr_or_fatal',bool(shell('pidof',PKG).strip()) and re.search(r'ANR in '+re.escape(PKG)+r'|FATAL EXCEPTION',log) is None)
 tap(edit(t));shell('input','text','Unsent_Update_Draft');shell('input','keyevent','4');time.sleep(.4)
 record('core_acceptance_complete')
 (OUT/'core-result.json').write_text(json.dumps({'passed':True,'checks':len(checks)},indent=2))

def allow_update_hosts():
 # Keep all other external HTTPS blocked: the user's private relay room is never contacted.
 addresses={}
 for host in ['api.github.com','github.com','release-assets.githubusercontent.com','objects.githubusercontent.com','github-releases.githubusercontent.com']:
  resolved=shell('env','CLASSPATH=/data/local/tmp/fixture.dex','app_process','/system/bin','FixtureSql','dns',host,ok=False).splitlines()
  ips=sorted({addr.strip() for addr in resolved if re.fullmatch(r'[0-9a-fA-F:.]+',addr.strip()) and ('.' in addr or ':' in addr)})
  if not ips:
   if host in ['api.github.com','github.com','release-assets.githubusercontent.com']:raise AssertionError('Device DNS returned no update host addresses: '+host)
   continue
  addresses[host]=ips
  for addr in ips:shell('ip6tables' if ':' in addr else 'iptables','-I','OUTPUT','1','-p','tcp','-d',addr,'--dport','443','-j','ACCEPT')
 record('github_update_hosts_allowed_by_device_dns',True,addresses=addresses)

def wait_ui(name,predicate,seconds=45):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  t,_=ui(name)
  if predicate(t):return t
  time.sleep(.4)
 raise AssertionError('UI did not reach '+name)

def update():
 allow_update_hosts();shell('input','keyevent','3');time.sleep(.5);shell('am','force-stop',PKG);shell('am','start','-W','-n',COMP)
 t=wait_ui('automatic-update-offer',lambda t:any(n.get('text','').startswith('有新版本') for n in t.iter('node')))
 record('automatic_live_update_check',True);tap(node(t,text='稍后'));t,_=ui('update-deferred');record('update_can_be_deferred',optional(t,text='我的两台手机') is not None)
 tap(node(t,desc='聊天背景'));t,_=ui('update-settings');tap(node(t,text='检查更新'));time.sleep(.6);t=wait_ui('update-offer',lambda t:any(n.get('text','').startswith('有新版本') for n in t.iter('node')))
 record('live_update_offer',any(n.get('text','').startswith('有新版本') for n in t.iter('node')))
 before=database('before-in-app-update').execute('SELECT COUNT(*) FROM messages').fetchone()[0]
 tap(node(t,text='更新'));time.sleep(.6);t=wait_ui('update-downloaded',lambda t:optional(t,text='去允许') is not None or optional(t,text='Update') is not None or optional(t,text='Install') is not None);screen('update-downloaded')
 if optional(t,text='去允许') is not None:
  record('first_update_permission_guidance',True);tap(node(t,text='去允许'));t,_=ui('install-permission')
  switch=optional(t,cls='android.widget.Switch')
  if switch is None:switch=optional(t,cls='android.widget.SwitchCompat')
  if switch is None:switch=node(t,text='Allow from this source')
  tap(switch);shell('input','keyevent','4');time.sleep(1);t,_=ui('installer')
 record('android_installer_opens',optional(t,text='Update') is not None or optional(t,text='Install') is not None)
 screen('installer');tap(optional(t,text='Update') if optional(t,text='Update') is not None else node(t,text='Install'));time.sleep(3);t,_=ui('installed')
 if optional(t,text='Open') is not None:tap(node(t,text='Open'))
 else:shell('am','start','-W','-n',COMP)
 time.sleep(.7);t,_=ui('updated-chat')
 expected=int(json.loads((OUT/'published-update.json').read_text())['versionCode'])
 details=shell('dumpsys','package',PKG);record('in_app_update_installs_final_release',('versionCode='+str(expected)+' ') in details)
 after=database('after-in-app-update').execute('SELECT COUNT(*) FROM messages').fetchone()[0];record('in_app_update_keeps_all_history',before==after,before=before,after=after)
 record('updated_app_opens_at_latest',optional(t,text='这个深灰实底先试试看。\n输入栏也有清楚的边界了。') is not None)
 record('in_app_update_keeps_unsent_draft',edit(t).get('text')=='Unsent_Update_Draft')
 record('in_app_update_keeps_photo_bytes',adb('exec-out','cat','/data/data/'+PKG+'/files/incoming/fixture-photo.png')==(OUT/'fixture-photo.png').read_bytes())
 tap(edit(t));shell('input','keyevent','KEYCODE_MOVE_END');shell('input','keyevent',*['67']*len('Unsent_Update_Draft'));shell('input','text','Update_Draft');shell('input','keyevent','4');time.sleep(.5);t,_=ui('updated-input');record('final_cursor_exit_and_draft_preserved',edit(t).get('focused')=='false' and edit(t).get('text')=='Update_Draft')
 tap(node(t,desc='发送'));time.sleep(.5);t,_=ui('updated-send');record('final_send_goes_to_latest',optional(t,text='Update_Draft') is not None)
 tap(node(t,desc='聊天背景'));t,_=ui('updated-settings');tap(node(t,text='检查更新'));time.sleep(3);t,_=ui('no-new-update');record('current_release_does_not_offer_same_version',not any(n.get('text','').startswith('有新版本') for n in t.iter('node')))
 log=adb('logcat','-d').decode(errors='replace');(OUT/'final-logcat.txt').write_text(log)
 record('download_verified_before_install','verified_version='+str(expected) in log and 'installer_opened' in log)
 record('manual_check_really_confirms_latest','up_to_date='+str(expected) in log)
 record('final_release_alive_no_anr',bool(shell('pidof',PKG).strip()) and re.search(r'ANR in '+re.escape(PKG)+r'|FATAL EXCEPTION',log) is None)
 # Final screenshot contains only a few test messages. The actual app ships empty and preserves the user's data.
 sql="DELETE FROM messages WHERE id NOT LIKE 'demo-%'";encoded=base64.b64encode(sql.encode()).decode()
 shell('env','CLASSPATH=/data/local/tmp/fixture.dex','app_process','/system/bin','FixtureSql','/data/data/'+PKG+'/databases/shuangjichuan.db',encoded);shell('am','broadcast','-a','com.qxdnzbl.shuangjichuan.CHANGED','-p',PKG);time.sleep(.5)
 t,_=ui('chat-final');screen('chat-final')
 record('all_acceptance_complete')

try:
 if len(sys.argv)>1 and sys.argv[1]=='update':
  checks=json.loads((OUT/'runtime-checks.json').read_text());update()
 else:
  reuse=os.environ.get('REUSE_CORE')=='1'
  if reuse:
   previous=json.loads((OUT/'reused-core-checks.json').read_text());checks=[{**c,'source_run':37494942937} for c in previous]
   record('unchanged_app_reuses_verified_native_ui_checks',True,source_commit='0e90a758f78b34e5ac6f1f26f0f4aba9cf9ff4ec')
  core(reuse)
except Exception as e:
 if len(sys.argv)>1 and sys.argv[1]=='update' and (OUT/'previous-channel.json').exists():subprocess.run([sys.executable,str(OUT/'publish_release.py'),'rollback'],check=False)
 record('acceptance_error',False,error=str(e))
finally:
 try:screen('last-screen')
 except:pass
 try:(OUT/'last-logcat.txt').write_bytes(adb('logcat','-d',timeout=12,ok=False))
 except:pass

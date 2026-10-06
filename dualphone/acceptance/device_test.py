import subprocess,pathlib,time,json,xml.etree.ElementTree as ET,re,sqlite3,sys,os,shutil,hashlib
ADB=shutil.which('adb') or '/tmp/dualphone-sdk/platform-tools/adb'; PKG='com.qxdnzbl.shuangjichuan.offline';COMP=PKG+'/com.qxdnzbl.shuangjichuan.MainActivity'
PROJECT=pathlib.Path(__file__).resolve().parents[1];ROOT=PROJECT.parent;OUT=PROJECT/'acceptance';OUT.mkdir(exist_ok=True)
BASELINE=pathlib.Path(os.environ.get('BASELINE_APK','/workspace/scratch/d7d42ee5626d/upload/双机传_v5.4_全原生_iMessage风_防卡顿.apk'))
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
 start=time.monotonic();s=shell('uiautomator','dump','/sdcard/acceptance.xml',timeout=timeout)
 if 'ERROR' in s:raise RuntimeError(s)
 b=adb('exec-out','cat','/sdcard/acceptance.xml');(OUT/(name+'.xml')).write_bytes(b)
 return ET.fromstring(b),round(time.monotonic()-start,3)

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
 p=OUT/(name+'.db');p.write_bytes(adb('exec-out','cat','/data/data/'+PKG+'/databases/shuangjichuan.db'))
 return sqlite3.connect(p)

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
 adb('push',str(p),'/data/data/'+PKG+'/databases/shuangjichuan.db');adb('push',str(OUT/'fixture.bin'),'/data/data/'+PKG+'/files/incoming/fixture.bin')
 shell('rm','-f','/data/data/'+PKG+'/databases/shuangjichuan.db-wal','/data/data/'+PKG+'/databases/shuangjichuan.db-shm')
 shell('chown','-R',uid+':'+uid,'/data/data/'+PKG+'/databases','/data/data/'+PKG+'/files')
 shell('restorecon','-R','/data/data/'+PKG+'/databases','/data/data/'+PKG+'/files')

def run():
 adb('root');time.sleep(1);adb('wait-for-device')
 shell('settings','put','global','window_animation_scale','0');shell('settings','put','global','transition_animation_scale','0');shell('settings','put','global','animator_duration_scale','0');shell('settings','put','secure','show_ime_with_hard_keyboard','1');shell('input','keyevent','82')
 # Block external HTTPS on this test device so it cannot receive the user's messages.
 shell('iptables','-A','OUTPUT','-p','tcp','--dport','443','-j','DROP')
 record('exact_uploaded_baseline',hashlib.sha256(BASELINE.read_bytes()).hexdigest()=='499bccd9c9e04ee0b838827fe5e3887a6259b2ab0970fdbb3d811b68b5b89c51')
 adb('install','-r',str(BASELINE),timeout=60)
 shell('pm','clear',PKG)
 for perm in ['BLUETOOTH_ADVERTISE','BLUETOOTH_CONNECT','BLUETOOTH_SCAN','NEARBY_WIFI_DEVICES','POST_NOTIFICATIONS']:shell('pm','grant',PKG,'android.permission.'+perm,ok=False)
 shell('am','start','-n',COMP);time.sleep(3)
 t,_=ui('baseline-empty');record('baseline_empty_chat_visible',node(t,text='我的两台手机') is not None);screen('baseline-empty')
 seed();adb('logcat','-c');shell('am','start','-n',COMP);time.sleep(3)
 blocked=False
 try:
  t,d=ui('baseline-under-stall',timeout=7)
  tap(node(t,desc='搜索'));t,d=ui('baseline-search',timeout=7)
  blocked=not any(n.get('text')=='搜索聊天' for n in t.iter('node'))
 except (subprocess.TimeoutExpired,RuntimeError):blocked=True
 pid=shell('pidof',PKG).strip()
 if pid:shell('kill','-3',pid,ok=False)
 time.sleep(1)
 log=adb('logcat','-d').decode(errors='replace');(OUT/'baseline-logcat.txt').write_text(log)
 stack_block=('TransferService.tryStartNearby' in log and ('waiting to lock' in log or 'Blocked' in log or 'BLOCKED' in log))
 record('original_stalls_with_pending_and_slow_network',blocked or stack_block,ui_blocked=blocked,service_lock_trace=stack_block)
 screen('baseline-under-stall')
 shell('am','force-stop',PKG)
 apk=PROJECT/'app/build/outputs/apk/release/app-release.apk'
 deadline=time.monotonic()+240
 while not apk.exists() and time.monotonic()<deadline:time.sleep(2)
 if not apk.exists():raise RuntimeError('Release build missing')
 old=database('before-upgrade').execute('SELECT COUNT(*) FROM messages').fetchone()[0]
 adb('install','-r',str(apk),timeout=60)
 new=database('after-upgrade').execute('SELECT COUNT(*) FROM messages').fetchone()[0]
 record('upgrade_preserves_history',old==new==1005,before=old,after=new)
 adb('logcat','-c');shell('am','start','-n',COMP);time.sleep(3)
 t,d=ui('fixed-start');record('release_start_under_slow_network',node(t,text='我的两台手机') is not None,tree_seconds=d);screen('fixed-start')
 edit=node(t,cls='android.widget.EditText');tap(edit);shell('input','text','Draft_Keep');time.sleep(.8)
 t,d=ui('fixed-keyboard');record('real_tap_and_typing',node(t,text='Draft_Keep',cls='android.widget.EditText') is not None,tree_seconds=d)
 ime=shell('dumpsys','input_method');(OUT/'input-method.txt').write_text(ime)
 record('real_keyboard_visible','mInputShown=true' in ime or 'mShowRequested=true' in ime)
 screen('fixed-keyboard');shell('input','keyevent','4');time.sleep(.5)
 # Broadcast refresh while reading earlier messages, retaining draft and scroll.
 for i in range(6):shell('input','swipe','270','320','270','900','150')
 time.sleep(.5);t,d=ui('fixed-scrolled');screen('fixed-scrolled');seen=[n.get('text') for n in t.iter('node') if n.get('text','').startswith('load-')]
 record('actual_scroll_with_1000_messages',bool(seen),visible_messages=seen[:3],tree_seconds=d)
 for i in range(3):shell('am','broadcast','-a','com.qxdnzbl.shuangjichuan.CHANGED','-p',PKG,ok=False)
 time.sleep(.7);t,d=ui('fixed-refresh')
 record('refresh_preserves_draft',node(t,text='Draft_Keep',cls='android.widget.EditText') is not None)
 after=[n.get('text') for n in t.iter('node') if n.get('text','').startswith('load-')];record('refresh_preserves_scroll',seen==after)
 tap(node(t,desc='搜索'));t,_=ui('search-open');ed=node(t,cls='android.widget.EditText');tap(ed);shell('input','text','load-');time.sleep(.6);t,d=ui('search-results');record('search_all_1000_messages',any('/1000' in n.get('text','') for n in t.iter('node')),tree_seconds=d);screen('fixed-search')
 tap(node(t,text='↓'));time.sleep(.7);t,_=ui('search-next');record('search_next_result',any(n.get('text')=='2/1000' for n in t.iter('node')))
 tap(node(t,text='↑'));t,_=ui('search-prev');record('search_previous_result',any(n.get('text')=='1/1000' for n in t.iter('node')))
 tap(node(t,text='×'));t,_=ui('search-closed');record('search_closes',not any(n.get('text')=='load-' and n.get('class')=='android.widget.EditText' for n in t.iter('node')))
 # Send via the actual button while an earlier send is stalled.
 tap(node(t,desc='发送'));time.sleep(.8);t,_=ui('sent-under-stall');record('send_remains_responsive',not any(n.get('text')=='Draft_Keep' and n.get('class')=='android.widget.EditText' for n in t.iter('node')))
 c=database('sent-data');record('message_locally_queued',c.execute("SELECT COUNT(*) FROM messages WHERE text_content='Draft_Keep' AND status='pending'").fetchone()[0]==1)
 # Return to the bottom and long press a real message.
 tap(node(t,desc='搜索'));t,_=ui('longpress-search-open');tap(node(t,cls='android.widget.EditText'));shell('input','text','held-queue-');time.sleep(.4);t,_=ui('longpress-search-match');tap(node(t,text='↓'));time.sleep(2);t,_=ui('near-bottom');tap(node(t,text='×'));t,_=ui('near-bottom-closed')
 target=node(t,contains='held-queue-');x,y=center(target);shell('input','swipe',str(x),str(y),str(x),str(y),'850');t,_=ui('longpress');record('custom_long_press_menu',node(t,text='复制') is not None and node(t,text='搜索') is not None);screen('fixed-longpress')
 tap(node(t,text='复制'));t,_=ui('copied')
 # File click saves the original bytes in Downloads.
 tap(node(t,desc='搜索'));t,_=ui('file-search-open');tap(node(t,cls='android.widget.EditText'));shell('input','text','fixture.bin');time.sleep(.4);t,_=ui('file-search-match');tap(node(t,text='↓'));time.sleep(1);t,_=ui('file-search-scroll');tap(node(t,text='×'));t,_=ui('file-ready');tap(node(t,text='fixture.bin'));time.sleep(.8)
 files=shell('find','/sdcard/Download','-type','f','-name','fixture*');paths=[p for p in files.splitlines() if p.endswith('.bin')]
 record('received_file_saves_identical_bytes',bool(paths) and adb('exec-out','cat',paths[-1])==(OUT/'fixture.bin').read_bytes())
 t,_=ui('file-saved');tap(node(t,desc='聊天背景'));t,_=ui('background-dialog');record('background_dialog_opens',node(t,text='聊天背景') is not None and node(t,text='选择照片') is not None);screen('fixed-background');shell('input','keyevent','4');t,_=ui('background-return');record('background_returns',not any(n.get('text')=='选择照片' for n in t.iter('node')))
 tap(node(t,desc='添加文件'));time.sleep(1);screen('file-picker');shell('input','keyevent','4');time.sleep(.5);t,_=ui('file-picker-return');record('file_picker_returns_to_chat',node(t,text='我的两台手机') is not None)
 log=adb('logcat','-d').decode(errors='replace');(OUT/'fixed-logcat.txt').write_text(log)
 fatal=re.search(r'(ANR in '+re.escape(PKG)+r'|Input dispatching timed out.*shuangjichuan|FATAL EXCEPTION)',log)
 record('release_alive_without_anr_or_fatal',bool(shell('pidof',PKG).strip()) and fatal is None)
 screen('fixed-final');record('acceptance_complete')

try:run()
except Exception as e:
 record('acceptance_error',False,error=str(e))
finally:
 try:(OUT/'last-logcat.txt').write_bytes(adb('logcat','-d',timeout=12,ok=False))
 except:pass

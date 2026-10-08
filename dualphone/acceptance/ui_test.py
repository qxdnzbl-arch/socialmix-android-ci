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

def long_press(n):
 x,y=center(n);shell('input','swipe',str(x),str(y),str(x),str(y),'850');time.sleep(.3)

def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))

def assert_save_option(t,pressed,label):
 from PIL import Image
 options=[n for n in t.iter('node') if n.get('package')==PKG and n.get('text')=='保存']
 allowed=all(n.get('text') not in ('保存图片？','取消','复制文件名','搜索') for n in t.iter('node') if n.get('package')==PKG)
 b=bounds(options[0]) if len(options)==1 else [0,0,10000,10000]
 capture=label+'-save-option';screen(capture)
 after=Image.open(OUT/(capture+'.png')).convert('RGB');before=Image.open(OUT/(label+'-before-menu.png')).convert('RGB')
 point=center(pressed);near=abs((b[0]+b[2])//2-point[0])<after.width*.15 and abs((b[1]+b[3])//2-point[1])<after.height*.15
 record(label+'_only_compact_save_option_near_press',len(options)==1 and allowed and b[2]-b[0]<after.width*.3 and b[3]-b[1]<after.height*.1 and near,bounds=b,press=list(point))
 matched=total=0
 for y in range(250,min(after.height,before.height)-250,130):
  for x in range(30,min(after.width,before.width)-30,140):
   if b[0]-25<=x<=b[2]+25 and b[1]-25<=y<=b[3]+25:continue
   total+=1;matched+=max(a-z for a,z in zip(before.getpixel((x,y)),after.getpixel((x,y))))<=8
 record(label+'_save_option_does_not_dim_background',total>0 and matched>=total*.85,matching=matched,total=total)
 return options[0]

def photo_downloads():return set(shell('find','/sdcard/Download','-type','f','-name','fixture-photo*').splitlines())

def tap_save_once(t,previous,label,viewer=False,expected_path=None):
 tap(node(t,text='保存'));t,_=ui(label+'-saved-once')
 record(label+'_one_tap_without_confirmation',optional(t,text='保存图片？') is None and optional(t,text='取消') is None and optional(t,text='保存') is None and (optional(t,desc='关闭图片') is not None if viewer else optional(t,text='我的两台手机') is not None))
 deadline=time.monotonic()+12;fresh=set();valid=False
 expected=hashlib.sha256((expected_path or OUT/'fixture-photo.png').read_bytes()).hexdigest()
 while time.monotonic()<deadline:
  fresh=photo_downloads()-previous
  valid=bool(fresh) and all(shell('sha256sum '+shlex.quote(p)).startswith(expected) for p in fresh)
  if valid:break
  time.sleep(.3)
 record(label+'_saves_one_original_photo',valid and len(fresh)==1,files=sorted(fresh))
 return t

def preview(t):
 return next(n for n in t.iter('node') if n.get('content-desc','').startswith('图片预览'))

def double_tap(x,y):
 shell('input','tap',str(x),str(y));time.sleep(.09);shell('input','tap',str(x),str(y))
 time.sleep(.2)

def gallery_checks(t,label):
 from PIL import Image
 tap(node(t,desc='图片：fixture-photo-gallery3.png'));t,_=ui(label+'-open')
 def position(expected,step,color=None):
  tree=ui(label+'-'+step)[0];screen(label+'-'+step)
  correct=preview(tree).get('content-desc')=='图片预览，'+expected
  if color is not None:
   img=Image.open(OUT/(label+'-'+step+'.png')).convert('RGB');pixel=img.getpixel((img.width//2,img.height//2));correct=correct and max(abs(a-b) for a,b in zip(pixel,color))<8
  record(label+'_'+step,correct);return tree
 def swipe(direction):shell('input','swipe','800' if direction<0 else '250','1200','250' if direction<0 else '800','1200','400');time.sleep(.4)
 position('4/4','opens_tapped_latest_photo',(170,100,90))
 swipe(1);position('3/4','right_swipe_goes_to_previous_photo',(80,120,180))
 swipe(1);position('2/4','skips_text_and_regular_files')
 swipe(1);position('1/4','reaches_received_first_photo')
 swipe(1);position('1/4','first_photo_does_not_wrap')
 swipe(-1);position('2/4','left_swipe_returns_to_own_photo')
 swipe(-1);t=position('3/4','left_swipe_returns_to_next_photo',(80,120,180))
 pressed=preview(t);before_files=photo_downloads();screen(label+'-before-menu');long_press(pressed);t,_=ui(label+'-save-option');assert_save_option(t,pressed,label)
 t=tap_save_once(t,before_files,label,viewer=True,expected_path=OUT/'fixture-photo-gallery2.png')
 double_tap(500,750);screen(label+'-zoomed')
 zoomed=Image.open(OUT/(label+'-zoomed.png')).convert('RGB');record(label+'_double_tap_zoom_is_visible',zoomed.getpixel((zoomed.width//2,100))==(80,120,180))
 swipe(-1);position('3/4','zoomed_drag_does_not_change_photo',(80,120,180))
 double_tap(500,750);swipe(-1);position('4/4','swipe_after_zoom_reset_works',(170,100,90))
 swipe(-1);t=position('4/4','last_photo_does_not_wrap',(170,100,90))
 tap(node(t,desc='关闭图片'));t,_=ui(label+'-closed');record(label+'_close_returns_to_chat',optional(t,text='我的两台手机') is not None)
 return t

def set_search(t,value):
 e=node(t,desc='搜索聊天',cls='android.widget.EditText');tap(e);shell('input','keyevent','KEYCODE_MOVE_END')
 shell('input','keyevent',*['67']*max(1,len(e.get('text',''))));shell('input','text',value);time.sleep(.4)
 return ui('search-'+value)[0]

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

def edit(t):
 for n in t.iter('node'):
  if n.get('class')=='android.widget.EditText' and n.get('resource-id','').endswith('/message'):return n
 raise AssertionError('Composer not in foreground; packages='+str(sorted({n.get('package') for n in t.iter('node')})))

def insert(id,text,mine=False,kind='text',name=None,path=None,size=0,status=None):
 values=[id,int(mine),kind,text,name,path,size,int(time.time()*1000),status or ('sent' if mine else 'received')]
 sql='INSERT INTO messages VALUES ('+','.join('NULL' if v is None else str(v) if isinstance(v,int) else "'"+v.replace("'","''")+"'" for v in values)+')'
 encoded=base64.b64encode(sql.encode()).decode()
 r=shell('env','CLASSPATH=/data/local/tmp/fixture.dex','app_process','/system/bin','FixtureSql','/data/data/'+PKG+'/databases/shuangjichuan.db',encoded)
 if 'fixture_written' not in r:raise RuntimeError(r)
 shell('am','broadcast','-a','com.qxdnzbl.shuangjichuan.CHANGED','-p',PKG);time.sleep(.5)

def core(reuse=False):
 adb('root');time.sleep(.5);adb('wait-for-device')
 manifest={'commit':os.environ.get('GITHUB_SHA'),'runner_image':os.environ.get('ImageVersion'),'runner_os':os.environ.get('RUNNER_OS'),'api':shell('getprop','ro.build.version.sdk').strip(),'build_fingerprint':shell('getprop','ro.build.fingerprint').strip(),'adb':adb('version').decode(),'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'python':sys.version,'inputs':{}}
 for path in subprocess.check_output(['git','ls-files','dualphone/app','dualphone/build.gradle','dualphone/settings.gradle','dualphone/gradle.properties','dualphone/keystore.b64','dualphone/acceptance/ui_test.py','dualphone/acceptance/FixtureSql.java','dualphone/acceptance/publish_release.py','dualphone/acceptance/verify_native_checkpoint.py','.github/workflows/dual-phone-ui-update.yml'],cwd=ROOT,text=True).splitlines():
  f=ROOT/path
  if f.is_file():manifest['inputs'][path]=hashlib.sha256(f.read_bytes()).hexdigest()
 manifest['gradle']=subprocess.run(['gradle','--version'],capture_output=True,text=True,timeout=30).stdout
 manifest['apks']={name:hashlib.sha256((OUT/name).read_bytes()).hexdigest() for name in ['final.apk','bootstrap.apk']}
 manifest['baseline_apk_sha256']=hashlib.sha256(BASELINE.read_bytes()).hexdigest()
 sdk=pathlib.Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT','/usr/local/lib/android/sdk'));manifest['android_sdk_sources']={}
 for name in ['platforms/android-35/source.properties','build-tools/35.0.0/source.properties']:
  f=sdk/name
  if f.is_file():manifest['android_sdk_sources'][name]=f.read_text()
 (OUT/'native-input-manifest.json').write_text(json.dumps(manifest,indent=2))
 if reuse:
  original=json.loads((OUT/'reused-input-manifest.json').read_text())
  same=all(manifest[key]==original[key] for key in ['runner_image','runner_os','api','build_fingerprint','adb','java','python','gradle','apks','baseline_apk_sha256','android_sdk_sources'])
  if not same:checks.clear();record('runtime_changed_reruns_all_native_ui_checks');reuse=False
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
 shell('am','start','-W','-n',COMP);time.sleep(.7);fixture_tree,_=ui('fixture-photo-content')
 record('fixture_photo_contains_real_visible_app',optional(fixture_tree,text='我的两台手机') is not None)
 # A real Android screen capture is used only as a private test photo; it is not shipped in the APK.
 photo=adb('exec-out','screencap','-p');(OUT/'fixture-photo.png').write_bytes(photo);shell('am','force-stop',PKG)
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
  t,_=ui('restored-search-entry');record('top_chat_search_entry_restored',optional(t,desc='搜索') is not None)
  tap(node(t,desc='搜索'));t,_=ui('search-open');record('search_opens_and_focuses',node(t,desc='搜索聊天').get('focused')=='true')
  t=set_search(t,'load-99');record('search_matches_existing_history',optional(t,text='1/11') is not None)
  screen('search-keyboard');record('search_real_ime_visible','mInputShown=true' in shell('dumpsys','input_method'))
  from PIL import Image
  screen_height=Image.open(OUT/'search-keyboard.png').height
  layouts=re.findall(r'screen=\d+ ime=(\d+) inputBottom=(\d+)',adb('logcat','-d','-s','DualPhoneNative:I').decode(errors='replace'))
  visible_layout=next(((int(ime),int(bottom)) for ime,bottom in reversed(layouts) if int(ime)>0),None)
  record('search_ime_keeps_search_and_composer_visible',visible_layout is not None and bounds(edit(t))[3]<=screen_height-visible_layout[0]+4 and bounds(node(t,desc='搜索聊天'))[3]<=screen_height-visible_layout[0],layout=visible_layout,composer=bounds(edit(t)))
  tap(node(t,desc='下一个结果'));t,_=ui('search-next');record('search_next_navigates_to_matching_message',optional(t,text='2/11') is not None and any(n.get('text','').startswith('load-990 ') for n in t.iter('node')))
  tap(node(t,desc='上一个结果'));t,_=ui('search-previous');record('search_previous_navigates_to_matching_message',optional(t,text='1/11') is not None and any(n.get('text','').startswith('load-99 ') for n in t.iter('node')))
  t=set_search(t,'fixture.bin');record('search_also_finds_file_names',optional(t,text='1/1') is not None)
  t=set_search(t,'NoHit_32');record('empty_search_result_is_clear',optional(t,text='0/0') is not None)
  shell('input','keyevent','4');time.sleep(.4);t,_=ui('search-ime-back');record('search_back_hides_keyboard_without_losing_query',node(t,desc='搜索聊天').get('focused')=='false' and node(t,desc='搜索聊天').get('text')=='NoHit_32')
  tap(node(t,desc='关闭搜索'));t,_=ui('search-closed');record('search_close_returns_to_chat_and_preserves_composer',optional(t,desc='搜索聊天') is None and optional(t,desc='搜索') is not None and edit(t).get('focused')=='false')
  if optional(t,desc='回到最新') is not None:tap(node(t,desc='回到最新'));t,_=ui('search-return-latest')
  for i in range(2):shell('input','swipe','270','400','270','1100','180')
  t,_=ui('latest-button-before');tap(node(t,desc='回到最新'));t,_=ui('latest-button');record('return_to_latest_reaches_newest',optional(t,text='incoming-while-at-bottom') is not None)
  # Copy is immediate and preserves the exact Unicode/multiline text. Paste uses Android's clipboard.
  for mine,value,label in [(False,'复制原文第一行\n第二行😊','received'),(True,'我的复制原文\n第二行','own')]:
   insert('copy-'+label,value,mine=mine);t,_=ui('copy-'+label+'-before');long_press(node(t,text=value));t,_=ui('copy-'+label+'-after')
   record(label+'_text_longpress_has_no_action_menu',not any(n.get('package')==PKG and n.get('text') in ('复制','复制文件名','搜索','保存') for n in t.iter('node')))
   screen('copy-'+label+'-direct');tap(node(t,text='我的两台手机'));t,_=ui('copy-'+label+'-overlay-dismissed')
   tap(edit(t));shell('input','keyevent','279');time.sleep(.3);t,_=ui('copy-'+label+'-paste')
   record(label+'_text_longpress_copies_exact_content',edit(t).get('text')==value)
   shell('input','keyevent','KEYCODE_MOVE_END');shell('input','keyevent',*['67']*(len(value)*2));shell('input','keyevent','4');time.sleep(.4)
  # Real received photo, asynchronous thumbnail, full viewer, gestures, save and close.
  private='/data/data/'+PKG+'/files/incoming/fixture-photo.png';adb('push',str(OUT/'fixture-photo.png'),private);shell('chown',uid+':'+uid,private);shell('restorecon',private)
  insert('photo-arrival',None,kind='file',name='fixture-photo.png',path=private,size=len(photo));time.sleep(.7);t,_=ui('photo-thumb')
  record('received_photo_is_thumbnail',optional(t,desc='图片：fixture-photo.png') is not None and optional(t,text='图片') is None)
  before_files=photo_downloads();pressed=node(t,desc='图片：fixture-photo.png');screen('received-thumb-before-menu')
  long_press(pressed);t,_=ui('photo-thumb-save-option');assert_save_option(t,pressed,'received-thumb')
  shell('input','keyevent','4');t,_=ui('photo-option-dismissed');record('dismissing_save_option_writes_nothing',before_files==photo_downloads() and optional(t,text='保存') is None)
  pressed=node(t,desc='图片：fixture-photo.png');screen('received-thumb-before-menu');long_press(pressed);t,_=ui('photo-thumb-save-option-again');assert_save_option(t,pressed,'received-thumb')
  t=tap_save_once(t,before_files,'received-thumb')
  tap(node(t,desc='图片：fixture-photo.png'));time.sleep(.5);t,_=ui('photo-viewer')
  record('native_photo_viewer_opens_directly_without_download_toolbar',preview(t) is not None and optional(t,text='保存到下载') is None and optional(t,text='复制文件名') is None and optional(t,text='fixture-photo.png') is None)
  screen('photo-viewer');double_tap(500,750);shell('input','swipe','700','850','430','650','300');t,_=ui('photo-zoom');record('photo_zoom_pan_remains_responsive',optional(t,desc='关闭图片') is not None)
  before_files=photo_downloads();pressed=preview(t);screen('received-viewer-before-menu');long_press(pressed);t,_=ui('photo-viewer-save-option');assert_save_option(t,pressed,'received-viewer')
  t=tap_save_once(t,before_files,'received-viewer',viewer=True)
  tap(node(t,desc='关闭图片'));t,_=ui('photo-close');record('viewer_returns_to_chat',optional(t,text='我的两台手机') is not None)
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
  thumbs=[n for n in t.iter('node') if n.get('content-desc')=='图片：fixture-photo.png'];pressed=thumbs[-1];before_files=photo_downloads();screen('own-thumb-before-menu')
  long_press(pressed);t,_=ui('own-photo-save-option');assert_save_option(t,pressed,'own-thumb');t=tap_save_once(t,before_files,'own-thumb')
  thumbs=[n for n in t.iter('node') if n.get('content-desc')=='图片：fixture-photo.png'];tap(thumbs[-1]);t,_=ui('own-photo-viewer');record('own_photo_also_opens_viewer',optional(t,desc='关闭图片') is not None)
  pressed=preview(t);before_files=photo_downloads();screen('own-viewer-before-menu');long_press(pressed);t,_=ui('own-viewer-save-option');assert_save_option(t,pressed,'own-viewer');t=tap_save_once(t,before_files,'own-viewer',viewer=True)
  tap(node(t,desc='关闭图片'));t,_=ui('own-photo-close')
  c=database('file-sent-data');record('outgoing_file_queued_with_original_bytes',c.execute("SELECT COUNT(*) FROM messages WHERE mine=1 AND kind='file' AND file_name='fixture-photo.png' AND status='pending'").fetchone()[0]==1)
  # A new received regular file remains usable without a search or long-press menu.
  insert('file-save-arrival',None,kind='file',name='fixture.bin',path='/data/data/'+PKG+'/files/incoming/fixture.bin',size=(OUT/'fixture.bin').stat().st_size)
  t,_=ui('file-ready');before_files=shell('find','/sdcard/Download','-type','f','-name','fixture*.bin').splitlines();long_press(node(t,text='fixture.bin'));t,_=ui('file-longpress')
  record('regular_file_has_no_longpress_menu',optional(t,text='复制文件名') is None and optional(t,text='搜索') is None and optional(t,text='保存') is None)
  record('regular_file_longpress_writes_nothing',before_files==shell('find','/sdcard/Download','-type','f','-name','fixture*.bin').splitlines())
  tap(node(t,text='fixture.bin'));time.sleep(.7)
  paths=shell('find','/sdcard/Download','-type','f','-name','fixture*.bin').splitlines();record('compact_file_saves_original_bytes',bool(paths) and adb('exec-out','cat',paths[-1])==(OUT/'fixture.bin').read_bytes())
  # Distinct private fixtures prove chronological paging, boundaries, zoom and saving the current image.
  for number,mine,color in [(2,False,(80,120,180)),(3,True,(170,100,90))]:
   filename='fixture-photo-gallery'+str(number)+'.png';image_path=OUT/filename;Image.new('RGB',(1080,1800),color).save(image_path)
   private='/data/data/'+PKG+'/files/incoming/'+filename;adb('push',str(image_path),private);shell('chown',uid+':'+uid,private);shell('restorecon',private)
   if number==3:insert('gallery-between-text','Not an image');insert('gallery-between-file',None,kind='file',name='gallery-marker.bin',path='/data/data/'+PKG+'/files/incoming/fixture.bin',size=(OUT/'fixture.bin').stat().st_size)
   insert('gallery-'+str(number),None,mine=mine,kind='file',name=filename,path=private,size=image_path.stat().st_size)
  t,_=ui('gallery-ready');t=gallery_checks(t,'gallery')
 if reuse:
  private='/data/data/'+PKG+'/files/incoming/fixture-photo.png';adb('push',str(OUT/'fixture-photo.png'),private);shell('chown',uid+':'+uid,private);shell('restorecon',private)
  insert('photo-arrival',None,kind='file',name='fixture-photo.png',path=private,size=len(photo))
  insert('photo-own',None,mine=True,kind='file',name='fixture-photo.png',path=private,size=len(photo))
  for number,mine,color in [(2,False,(80,120,180)),(3,True,(170,100,90))]:
   filename='fixture-photo-gallery'+str(number)+'.png';image_path=OUT/filename;Image.new('RGB',(1080,1800),color).save(image_path)
   private='/data/data/'+PKG+'/files/incoming/'+filename;adb('push',str(image_path),private);shell('chown',uid+':'+uid,private);shell('restorecon',private)
   if number==3:insert('gallery-between-text','Not an image');insert('gallery-between-file',None,kind='file',name='gallery-marker.bin',path='/data/data/'+PKG+'/files/incoming/fixture.bin',size=(OUT/'fixture.bin').stat().st_size)
   insert('gallery-'+str(number),None,mine=mine,kind='file',name=filename,path=private,size=image_path.stat().st_size)
  t,_=ui('gallery-ready');record('new_gallery_fixture_ready');t=gallery_checks(t,'gallery')
 t,_=ui('before-background');tap(node(t,desc='聊天背景'));t,_=ui('background-dialog')
 record('background_controls_removed',optional(t,text='选择照片') is not None and not any(n.get('class')=='android.widget.SeekBar' or n.get('text') in ('模糊','柔化','背景只保存在这台手机，没有预设背景。') for n in t.iter('node')))
 record('update_entry_visible',optional(t,text='检查更新') is not None)
 clear_bounds=list(map(int,re.findall(r'\d+',node(t,text='清除').get('bounds'))));title_bounds=list(map(int,re.findall(r'\d+',node(t,text='清除背景').get('bounds'))));sub_bounds=list(map(int,re.findall(r'\d+',node(t,text='恢复默认界面').get('bounds'))))
 photo_bounds=bounds(node(t,text='选择照片'));update_bounds=bounds(node(t,text='检查更新'))
 record('clear_and_photo_buttons_align_in_right_column',abs(clear_bounds[0]-photo_bounds[0])<=3 and abs(clear_bounds[2]-photo_bounds[2])<=3 and clear_bounds[0]>=title_bounds[2] and clear_bounds[1]<sub_bounds[3] and clear_bounds[3]>title_bounds[1] and abs(update_bounds[0]-photo_bounds[0])<=3,clear=clear_bounds,photo=photo_bounds,title=title_bounds,update=update_bounds)
 screen('background-dialog')
 tap(node(t,text='清除'));time.sleep(.5);t,_=ui('background-cleared');record('clear_background_returns',optional(t,text='我的两台手机') is not None)
 screen('cancel-check-before');tap(node(t,desc='聊天背景'));t,_=ui('cancel-check-settings')
 # A UI hierarchy dump can outlast the network timeout. Capture the visible modal immediately, then press Back.
 shell('input','tap',*map(str,center(node(t,text='检查更新'))));screen('cancel-check-visible')
 modal=Image.open(OUT/'cancel-check-visible.png').convert('RGB');plain=Image.open(OUT/'cancel-check-before.png').convert('RGB')
 def dialog_pixels(img):
  return sum(img.getpixel((x,y))==(244,245,247) for y in range(int(img.height*.35),int(img.height*.65),8) for x in range(int(img.width*.1),int(img.width*.9),8))
 record('stalled_update_check_shows_progress_dialog',dialog_pixels(modal)>dialog_pixels(plain)+1000)
 shell('input','keyevent','4');t,_=ui('cancel-check-return');record('stalled_update_check_back_cancels_to_chat',optional(t,text='我的两台手机') is not None and optional(t,text='正在检查') is None)
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
 request=urllib.request.Request('https://api.github.com/meta',headers={'User-Agent':'DualPhone-native-acceptance','Authorization':'Bearer '+os.environ['GH_TOKEN']})
 with urllib.request.urlopen(request,timeout=20) as response:ranges=json.load(response)
 cidrs=sorted({cidr for group in ['web','api','git'] for cidr in ranges[group]})
 for cidr in cidrs:shell('ip6tables' if ':' in cidr else 'iptables','-I','OUTPUT','1','-p','tcp','-d',cidr,'--dport','443','-j','ACCEPT')
 record('official_github_frontend_ranges_allowed',True,cidrs=cidrs)
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
 tap(node(t,text='更新'));time.sleep(.6)
 diagnostic=[]
 for path in ['/proc/net/tcp','/proc/net/tcp6']:diagnostic.append(path+'\n'+shell('cat',path,ok=False))
 (OUT/'update-network.txt').write_text('\n'.join(diagnostic))
 (OUT/'update-firewall.txt').write_text(shell('iptables','-L','OUTPUT','-n','-v',ok=False)+'\n'+shell('ip6tables','-L','OUTPUT','-n','-v',ok=False))
 t=wait_ui('update-downloaded',lambda t:optional(t,text='去允许') is not None or optional(t,text='Update') is not None or optional(t,text='Install') is not None);screen('update-downloaded')
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
 record('final_search_entry_restored',optional(t,desc='搜索') is not None)
 record('updated_app_opens_at_latest',optional(t,text='这个深灰实底先试试看。\n输入栏也有清楚的边界了。') is not None)
 record('in_app_update_keeps_unsent_draft',edit(t).get('text')=='Unsent_Update_Draft')
 record('in_app_update_keeps_photo_bytes',adb('exec-out','cat','/data/data/'+PKG+'/files/incoming/fixture-photo.png')==(OUT/'fixture-photo.png').read_bytes())
 tap(edit(t));shell('input','keyevent','KEYCODE_MOVE_END');shell('input','keyevent',*['67']*len('Unsent_Update_Draft'));shell('input','text','Update_Draft');shell('input','keyevent','4');time.sleep(.5);t,_=ui('updated-input');record('final_cursor_exit_and_draft_preserved',edit(t).get('focused')=='false' and edit(t).get('text')=='Update_Draft')
 tap(node(t,desc='发送'));time.sleep(.5);t,_=ui('updated-send');record('final_send_goes_to_latest',optional(t,text='Update_Draft') is not None)
 tap(node(t,desc='搜索'));t,_=ui('updated-search');t=set_search(t,'Update_Draft');record('final_search_really_finds_new_message',optional(t,text='1/1') is not None);tap(node(t,desc='下一个结果'));t,_=ui('updated-search-match');record('final_search_really_navigates',optional(t,text='Update_Draft') is not None);tap(node(t,desc='关闭搜索'));t,_=ui('updated-search-closed')
 if optional(t,desc='回到最新') is not None:tap(node(t,desc='回到最新'));t,_=ui('updated-latest')
 t=gallery_checks(t,'final-gallery')
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
   previous=json.loads((OUT/'reused-core-checks.json').read_text());checks=[{**c,'source_run':37746361502} for c in previous]
   record('identical_apk_reuses_completed_native_ui_segment',True,source_commit='cc26445c816cf9d134da550e9f011707342c047f',scope='through regular file saving; gallery, settings, cancellation and live upgrade are fresh')
  core(reuse)
except Exception as e:
 if len(sys.argv)>1 and sys.argv[1]=='update' and (OUT/'previous-channel.json').exists():subprocess.run([sys.executable,str(OUT/'publish_release.py'),'rollback'],check=False)
 record('acceptance_error',False,error=str(e))
finally:
 try:screen('last-screen')
 except:pass
 try:(OUT/'last-logcat.txt').write_bytes(adb('logcat','-d',timeout=12,ok=False))
 except:pass

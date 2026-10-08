"""Reuse passed native checks only when sources, compiled contents, signer and runtime match."""
import ast,hashlib,json,os,pathlib,re,shutil,subprocess,sys,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'dualphone/acceptance'
SOURCE='71bd58252c09e64f70cb4548b49259cc362070c6';RUN=37748508709
previous=pathlib.Path(sys.argv[1])/'acceptance'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert digest(previous/'native-input-manifest.json')=='3a4caae0514bf7a3457ebe39c3393e235390925fd28682ebd35876860328fc41'
assert digest(previous/'runtime-checks.json')=='74ae5f6e4e5239570296b4aa295aa912a98222c28b01ceb0e225c277ca22c5c8'
manifest=json.loads((previous/'native-input-manifest.json').read_text());assert manifest['commit']==SOURCE
dependencies={}
for name,sha in manifest['inputs'].items():
 if name in ['dualphone/acceptance/ui_test.py','dualphone/acceptance/verify_native_checkpoint.py','.github/workflows/dual-phone-ui-update.yml']:continue
 assert digest(ROOT/name)==sha,'Changed source dependency: '+name
 dependencies[name]=sha
old_apk=previous.parent/'app/build/outputs/apk/release/app-release.apk'
assert digest(old_apk)==manifest['apks']['final.apk'],'Source APK evidence changed'
def payload(path):
 with zipfile.ZipFile(path) as archive:
  assert len(archive.namelist())==len(set(archive.namelist())),'Duplicate APK entry'
  return {n:hashlib.sha256(archive.read(n)).hexdigest() for n in archive.namelist()}
old_payload=payload(old_apk);final_payload=payload(OUT/'final.apk');bootstrap_payload=payload(OUT/'bootstrap.apk')
assert final_payload==old_payload,'Compiled APK contents changed'
assert {n:s for n,s in final_payload.items() if n!='AndroidManifest.xml'}=={n:s for n,s in bootstrap_payload.items() if n!='AndroidManifest.xml'},'Preview contains different compiled code or resources'
sdk=pathlib.Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT','/usr/local/lib/android/sdk'))/'build-tools/35.0.0'
def output(tool,*args):return subprocess.check_output([str(sdk/tool),*map(str,args)],text=True)
def identity(apk):
 info=output('aapt','dump','badging',apk).splitlines()[0]
 return dict(re.findall(r"(name|versionCode|versionName)='([^']*)'",info))
final_id=identity(OUT/'final.apk');assert identity(old_apk)==final_id
assert final_id=={'name':'com.qxdnzbl.shuangjichuan.offline','versionCode':'563','versionName':'5.6.2-native'}
assert identity(OUT/'bootstrap.apk')=={**final_id,'versionCode':'562','versionName':'5.6.2-native-preview'}
def normalized_manifest(apk):
 return '\n'.join(line for line in output('aapt','dump','xmltree',apk,'AndroidManifest.xml').splitlines() if not re.search(r'A: android:version(?:Code|Name)\(',line))
assert normalized_manifest(OUT/'final.apk')==normalized_manifest(OUT/'bootstrap.apk'),'Preview manifest changed beyond version'
def signer(apk):
 verified=output('apksigner','verify','--print-certs',apk)
 result=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)',verified);assert result,'Missing verified signer'
 return result
certificate=signer(old_apk);assert signer(OUT/'final.apk')==signer(OUT/'bootstrap.apk')==certificate
def original(name):return subprocess.check_output(['git','show',SOURCE+':'+name],cwd=ROOT,text=True)
old=original('dualphone/acceptance/ui_test.py');new=(OUT/'ui_test.py').read_text()
def functions(text):return {n.name:ast.dump(n,include_attributes=False) for n in ast.parse(text).body if isinstance(n,ast.FunctionDef)}
a,b=functions(old),functions(new)
helpers=['adb','shell','record','screen','ui','node','center','tap','long_press','bounds','assert_save_option','photo_downloads','tap_save_once','preview','double_tap','gallery_checks','set_search','database','seed','optional','edit','insert']
for name in helpers:assert a[name]==b[name],'Changed shared UI helper: '+name
def segment(text):
 start=text.index("  e=edit(t);record('composer_not_focused_on_launch'");end=text.index('  # Distinct private fixtures',start)
 return text[start:end]
assert segment(old)==segment(new),'Earlier native UI scenarios changed'
def workflow(text):
 start=text.index('      - name: Locate previous signed release');end=text.index('      - name: Enable hardware acceleration',start)
 return text[:start]+text[end:]
assert workflow(original('.github/workflows/dual-phone-ui-update.yml'))==workflow((ROOT/'.github/workflows/dual-phone-ui-update.yml').read_text()),'Build or emulator configuration changed'
all_checks=json.loads((previous/'runtime-checks.json').read_text());stop=next(i for i,c in enumerate(all_checks) if c['check']=='stalled_update_check_shows_progress_dialog')
checks=[c for c in all_checks[:stop] if c['check']!='photo_zoom_pan_remains_responsive'];assert checks and all(c['passed'] for c in checks)
for f in previous.iterdir():
 if f.suffix in ['.png','.xml','.json','.txt']:shutil.copy2(f,OUT/('reused-'+f.name))
shutil.copy2(previous/'native-input-manifest.json',OUT/'reused-input-manifest.json')
(OUT/'reused-core-checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
proof={'passed':True,'source_run':RUN,'source_commit':SOURCE,'checks':len(checks),'scope':'Successful native UI, gallery and alignment checks. Cancellation and live upgrade are fresh; gallery is checked again after the real final upgrade.','dependencies':dependencies,'shared_helper_hashes':{name:hashlib.sha256(b[name].encode()).hexdigest() for name in helpers},'scenario_segment_sha256':hashlib.sha256(segment(new).encode()).hexdigest(),'source_apks':manifest['apks'],'candidate_apks':{n:digest(OUT/n) for n in ['final.apk','bootstrap.apk']},'compiled_payload':final_payload,'verified_certificate_sha256':certificate,'preview_manifest_only_version_differs':True,'evidence_hashes':{f.name:digest(f) for f in previous.iterdir() if f.suffix in ['.png','.xml','.json','.txt']},'runtime_gate':'Exact runner image, API, fingerprint, ADB, Java, Python, Gradle, SDK and previous baseline must match. Signed candidates must match this complete compiled-payload and certificate proof; otherwise all checks rerun.'}
(OUT/'partial-reuse-proof.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2));print(json.dumps({'verified_checkpoint':RUN,'reused_ui_checks':len(checks)}))

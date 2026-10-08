"""Reuse only the successful, unchanged UI segment of an identical signed APK."""
import ast,hashlib,json,pathlib,shutil,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'dualphone/acceptance'
SOURCE='cc26445c816cf9d134da550e9f011707342c047f';RUN=37746361502
previous=pathlib.Path(sys.argv[1])/'acceptance'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert digest(previous/'native-input-manifest.json')=='e8659bf085415bf99c913d0c2abf80e955a0241379f3a207ddbc416e3513ca16'
assert digest(previous/'runtime-checks.json')=='f515fdc6d8c42a87cb7b7672ee97faad455db36e7aff3c1a35446136eaa17344'
manifest=json.loads((previous/'native-input-manifest.json').read_text());assert manifest['commit']==SOURCE
dependencies={}
for name,sha in manifest['inputs'].items():
 if name in ['dualphone/acceptance/ui_test.py','.github/workflows/dual-phone-ui-update.yml']:continue
 assert digest(ROOT/name)==sha,'Changed source dependency: '+name
 dependencies[name]=sha
assert digest(OUT/'final.apk')==manifest['apks']['final.apk'],'Signed APK is different'
def original(name):return subprocess.check_output(['git','show',SOURCE+':'+name],cwd=ROOT,text=True)
old=original('dualphone/acceptance/ui_test.py');new=(OUT/'ui_test.py').read_text()
def functions(text):return {n.name:ast.dump(n,include_attributes=False) for n in ast.parse(text).body if isinstance(n,ast.FunctionDef)}
a,b=functions(old),functions(new)
helpers=['adb','shell','record','screen','ui','node','center','tap','long_press','bounds','assert_save_option','photo_downloads','tap_save_once','preview','set_search','database','seed','optional','edit','insert']
for name in helpers:assert a[name]==b[name],'Changed shared UI helper: '+name
def segment(text):
 start=text.index("  e=edit(t);record('composer_not_focused_on_launch'");end=text.index('  # Distinct private fixtures',start)
 return text[start:end].replace("screen('photo-viewer');double_tap(500,750);","screen('photo-viewer');shell('input','tap','500','750');shell('input','tap','500','750');")
assert segment(old)==segment(new),'Earlier native UI scenarios changed'
def workflow(text):
 start=text.index('      - name: Locate previous signed release');end=text.index('      - name: Enable hardware acceleration',start)
 return text[:start]+text[end:]
assert workflow(original('.github/workflows/dual-phone-ui-update.yml'))==workflow((ROOT/'.github/workflows/dual-phone-ui-update.yml').read_text()),'Build or emulator configuration changed'
all_checks=json.loads((previous/'runtime-checks.json').read_text());stop=next(i for i,c in enumerate(all_checks) if c['check'].startswith('gallery_'))
checks=[c for c in all_checks[:stop] if c['check']!='photo_zoom_pan_remains_responsive'];assert checks and all(c['passed'] for c in checks)
for f in previous.iterdir():
 if f.suffix in ['.png','.xml','.json','.txt']:shutil.copy2(f,OUT/('reused-'+f.name))
shutil.copy2(previous/'native-input-manifest.json',OUT/'reused-input-manifest.json')
(OUT/'reused-core-checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
proof={'passed':True,'source_run':RUN,'source_commit':SOURCE,'checks':len(checks),'scope':'Successful UI segment through regular-file saving. Double-tap, gallery, settings, cancellation and live upgrade are excluded and rerun.','dependencies':dependencies,'shared_helper_hashes':{name:hashlib.sha256(b[name].encode()).hexdigest() for name in helpers},'scenario_segment_sha256':hashlib.sha256(segment(new).encode()).hexdigest(),'signed_apk_sha256':manifest['apks']['final.apk'],'evidence_hashes':{f.name:digest(f) for f in previous.iterdir() if f.suffix in ['.png','.xml','.json','.txt']},'runtime_gate':'The current real-device manifest must match API, fingerprint, runner image, ADB, Java, Python, Gradle, SDK and both signed APKs; otherwise rerun the full UI.'}
(OUT/'partial-reuse-proof.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2));print(json.dumps({'verified_checkpoint':RUN,'reused_ui_checks':len(checks)}))

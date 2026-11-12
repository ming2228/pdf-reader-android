import json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build'/'release-smoke';OUT.mkdir(parents=True,exist_ok=True)
ADB='C:/Users/lin/AppData/Local/Android/Sdk/platform-tools/adb.exe';checks=[]
def adb(*args):
    p=subprocess.run([ADB,'-s','emulator-5554',*args],capture_output=True,encoding='utf-8',errors='replace',timeout=35)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    return p.stdout
def snap():
    adb('shell','uiautomator','dump','/sdcard/pdf-release.xml');return ET.fromstring(adb('shell','cat','/sdcard/pdf-release.xml'))
def node(tree,text=None,rid=None):return next((n for n in tree.iter('node') if (text is not None and (n.get('text')==text or n.get('content-desc')==text))or(rid is not None and n.get('resource-id','').endswith(':id/'+rid))),None)
def box(n):return list(map(int,re.findall(r'\d+',n.get('bounds',''))))
def tap(text):
    n=node(snap(),text=text)
    if n is None:raise AssertionError('missing '+text)
    a,b,c,d=box(n);adb('shell','input','tap',str((a+c)//2),str((b+d)//2))
def viewport(tree=None):
    n=node(snap() if tree is None else tree,rid='pdf_view')
    if n is None:raise AssertionError('viewport missing')
    return n,list(map(int,re.findall(r'\d+',n.get('content-desc',''))))
def wait(name):
    for _ in range(30):
        t=snap()
        if node(t,text=name) is not None and node(t,rid='reader_status') is not None:return t
        time.sleep(.15)
    raise AssertionError('release PDF not open')
def open_pdf(name):adb('shell','am','start','-n','com.jy.pdf.read.io.test/com.jy.pdf.read.io.FixtureLauncher','--es','name',name);return wait(name)
def check(ok,name):
    if not ok:raise AssertionError(name)
    checks.append(name);print('PASS',name,flush=True)
def capture(name):adb('shell','screencap','-p','/sdcard/pdf-release.png');adb('pull','/sdcard/pdf-release.png',str(OUT/name))
tree=open_pdf('阅读演示.pdf');v,first=viewport(tree);check(first[0]==1 and first[1]==3,'signed release opens external PDF through temporary grant')
capture('pdf-reader-release-fit.png');tap('放大');tap('放大');v,zoom=viewport();check(zoom[2]>100 and zoom[4]>0 and zoom[6]>0,'signed release zoom creates two-axis scrollable content')
a,b,c,d=box(v);adb('shell','input','swipe',str(c*3//4),str(b+(d-b)*3//5),str(c//2),str(b+(d-b)*2//5),'650')
_,pan=viewport();adb('shell','input','keyevent','KEYCODE_VOLUME_DOWN');_,key=viewport();check(key[3]==pan[3] and key[5]>=pan[5],'signed release page key scrolls vertically and preserves horizontal position')
capture('pdf-reader-release-reading.png');adb('shell','input','tap',str((a+c)//2),str((b+d)//2));t=snap();check(node(t,text='返回') is None and node(t,rid='reader_status') is None,'signed release tap hides both floating layers');capture('pdf-reader-release-immersive.png')
_,expected=viewport();time.sleep(.7);adb('shell','am','force-stop','com.jy.pdf.read.io');open_pdf('改名后继续阅读.pdf');_,restored=viewport();check(expected==restored,'signed release process restart and renamed PDF restore reading position')
resolves=adb('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-d','content://example/book.pdf','-t','application/pdf');check('com.jy.pdf.read.io/.MainActivity' in resolves,'PDF reader is registered in Android Open With resolution')
other=adb('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-d','content://example/book.txt','-t','text/plain');check('com.jy.pdf.read.io' not in other,'reader is not offered for text/plain files')
(OUT/'results.json').write_text(json.dumps({'checks':checks},ensure_ascii=False,indent=2),encoding='utf-8')
(OUT/'memory.txt').write_text(adb('shell','dumpsys','meminfo','com.jy.pdf.read.io'),encoding='utf-8')
print('RELEASE_SMOKE_PASS',len(checks),flush=True)

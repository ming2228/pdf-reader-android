"""Operate only the explicitly named local emulator, never an attached physical reader."""
import json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ADB=Path('C:/Users/lin/AppData/Local/Android/Sdk/platform-tools/adb.exe')
OUT=ROOT/'build'/'ui-smoke';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def adb(*args):
    p=subprocess.run([str(ADB),'-s','emulator-5554',*args],capture_output=True,encoding='utf-8',errors='replace',timeout=35)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    return p.stdout
def snap():
    adb('shell','uiautomator','dump','/sdcard/pdf-smoke.xml')
    content=adb('shell','cat','/sdcard/pdf-smoke.xml');(OUT/'latest.xml').write_text(content,encoding='utf-8');return ET.fromstring(content)
def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds',''))))
def find(tree,text=None,rid=None,desc=None):
    return next((n for n in tree.iter('node') if (text is not None and (n.get('text')==text or n.get('content-desc')==text)) or (rid is not None and n.get('resource-id','').endswith(':id/'+rid)) or (desc is not None and desc in n.get('content-desc',''))),None)
def tap(text=None,rid=None):
    n=find(snap(),text=text,rid=rid)
    if n is None:raise AssertionError('Missing UI '+str(text or rid))
    x1,y1,x2,y2=bounds(n);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def check(ok,label):
    if not ok:raise AssertionError(label)
    checks.append(label);print('PASS',label,flush=True)
def view(tree=None):
    n=find(snap() if tree is None else tree,rid='pdf_view')
    if n is None:raise AssertionError('PDF viewport missing')
    nums=list(map(int,re.findall(r'\d+',n.get('content-desc',''))));return n,nums
def wait_pdf():
    for _ in range(40):
        tree=snap()
        if find(tree,rid='pdf_view') is not None and find(tree,rid='reader_status') is not None:return tree
        time.sleep(.15)
    raise AssertionError('PDF did not open')
def saved():
    root=json.loads(adb('shell','run-as','com.jy.pdf.read.io','cat','files/reading-progress.json'))
    return next(b for b in root['books'] if b['name']=='ui-smoke.pdf')
def screenshot(name):
    adb('shell','screencap','-p','/sdcard/pdf-preview.png');adb('pull','/sdcard/pdf-preview.png',str(OUT/name))

adb('shell','am','start','-n','com.jy.pdf.read.io.test/com.jy.pdf.read.io.FixtureLauncher','--es','name','ui-smoke.pdf')
wait_pdf();tap(rid='reader_status');tree=snap();edit=next(n for n in tree.iter('node') if n.get('class')=='android.widget.EditText')
x1,y1,x2,y2=bounds(edit);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));adb('shell','input','keyevent','123');adb('shell','input','keyevent','67');adb('shell','input','text','1');tap(text='跳转')
check(view()[1][0]==1,'page jump opens first page')
tap(text='裁切');tap(text='重置');tree=snap();n=find(tree,desc='拖动裁切框四边中心点');check(n is not None,'crop selection is available to touch')
x1,y1,x2,y2=bounds(n);density=int(re.findall(r'\d+',adb('shell','wm','density'))[-1])/160
pad=16*density;scale=min((x2-x1-2*pad)/600,(y2-y1-2*pad)/1800);pw,ph=600*scale,1800*scale
left=(x1+x2-pw)/2;top=(y1+y2-ph)/2
adb('shell','input','swipe',str(round(left)),str(round(top+ph*.5)),str(round(left+pw*.12)),str(round(top+ph*.5)),'450')
adb('shell','input','swipe',str(round(left+pw*.56)),str(round(top)),str(round(left+pw*.56)),str(round(top+ph*.12)),'450')
screenshot('pdf-reader-crop.png');tap(text='应用到整份 PDF');time.sleep(.65);state=saved()
check(.05<state['crop']['left']<.2 and .05<state['crop']['top']<.2,'dragging individual midpoint handles applies crop to saved document state')
tap(text='放大');tap(text='放大');node,initial=view();check(initial[2]>100 and initial[4]>0 and initial[6]>0,'zoom enables both horizontal and vertical scroll ranges')
old_bounds=bounds(node);x1,y1,x2,y2=old_bounds
adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));tree=snap()
check(find(tree,text='返回') is None and find(tree,rid='reader_status') is None and bounds(view(tree)[0])==old_bounds,'single tap hides floating bars without changing viewport bounds')
adb('shell','input','swipe',str(round(x2*.72)),str(round(y1+(y2-y1)*.65)),str(round(x2*.42)),str(round(y1+(y2-y1)*.45)),'600')
_,pan=view();check(pan[3]>0 and pan[5]>0,'touch drag pans zoomed PDF horizontally and vertically')
before_x=pan[3];adb('shell','input','keyevent','KEYCODE_VOLUME_DOWN');_,after=view()
check(after[3]==before_x,'Android volume-down page-key fallback preserves horizontal scroll')
screenshot('pdf-reader-immersive.png')
adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));tree=snap();check(find(tree,text='返回') is not None and find(tree,rid='reader_status') is not None,'single tap restores top and bottom floating bars')
time.sleep(.65);expected=saved();_,position=view();screenshot('pdf-reader-reading.png')
adb('shell','am','force-stop','com.jy.pdf.read.io')
adb('shell','am','start','-n','com.jy.pdf.read.io.test/com.jy.pdf.read.io.FixtureLauncher','--es','name','ui-smoke.pdf');wait_pdf();_,restored=view()
check(restored==position,'process restart and external reopen restore exact page zoom and scroll offsets')
actual=saved();check(actual['md5']==expected['md5'] and actual['crop']==expected['crop'],'crop and MD5 identity survive process death')
adb('shell','am','start','-n','com.jy.pdf.read.io.test/com.jy.pdf.read.io.FixtureLauncher','--es','name','invalid.pdf');tree=snap()
check(any('不是 PDF' in n.get('text','') for n in tree.iter('node')),'invalid PDF is rejected with a readable error')
(OUT/'results.json').write_text(json.dumps({'checks':checks},ensure_ascii=False,indent=2),encoding='utf-8')
print('UI_SMOKE_PASS',len(checks),flush=True)

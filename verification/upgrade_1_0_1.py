"""Release-only black-box checks on local emulator-5554, including 1.0.0 upgrade."""
import json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build'/'upgrade-1.0.1';OUT.mkdir(parents=True,exist_ok=True)
ADB='C:/Users/lin/AppData/Local/Android/Sdk/platform-tools/adb.exe';checks=[]
def adb(*args):
    p=subprocess.run([ADB,'-s','emulator-5554',*args],capture_output=True,encoding='utf-8',errors='replace',timeout=40)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    return p.stdout
def snap():
    adb('shell','uiautomator','dump','/sdcard/pdf-upgrade.xml');xml=adb('shell','cat','/sdcard/pdf-upgrade.xml');(OUT/'latest.xml').write_text(xml,encoding='utf-8');return ET.fromstring(xml)
def node(tree,text=None,rid=None):return next((n for n in tree.iter('node') if (text is not None and (n.get('text')==text or n.get('content-desc')==text)) or (rid is not None and n.get('resource-id','').endswith(':id/'+rid))),None)
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds',''))))
def tap(text=None,rid=None):
    n=node(snap(),text,rid)
    if n is None:raise AssertionError('Missing control '+str(text or rid))
    a,b,c,d=bounds(n);adb('shell','input','tap',str((a+c)//2),str((b+d)//2))
def viewport(tree=None):
    n=node(snap() if tree is None else tree,rid='pdf_view')
    if n is None:raise AssertionError('PDF viewport missing')
    return n,list(map(int,re.findall(r'\d+',n.get('content-desc',''))))
def check(ok,name):
    if not ok:raise AssertionError(name)
    checks.append(name);print('PASS',name,flush=True)
def open_pdf(name):
    adb('shell','am','start','-n','com.jy.pdf.read.io.test/com.jy.pdf.read.io.FixtureLauncher','--es','name',name)
    for _ in range(35):
        tree=snap()
        if node(tree,text=name) is not None and node(tree,rid='reader_status') is not None:return tree
        time.sleep(.1)
    raise AssertionError('PDF did not open: '+name)
def restart(name):adb('shell','am','force-stop','com.jy.pdf.read.io');return open_pdf(name)
def jump(value):
    tap(rid='reader_status');tree=snap();field=next(n for n in tree.iter('node') if n.get('class')=='android.widget.EditText');a,b,c,d=bounds(field)
    adb('shell','input','tap',str((a+c)//2),str((b+d)//2));adb('shell','input','keyevent','KEYCODE_MOVE_END');adb('shell','input','keyevent','KEYCODE_DEL');adb('shell','input','text',str(value));tap(text='跳转')
def capture(name):adb('shell','screencap','-p','/sdcard/pdf101.png');adb('pull','/sdcard/pdf101.png',str(OUT/name))

# Caller installed the original, signed 1.0.0 release. Create actual legacy progress.
open_pdf('upgrade-book.pdf');jump(2);tap(text='放大');tap(text='放大');v,before=viewport();a,b,c,d=bounds(v)
adb('shell','input','swipe',str(c*3//4),str(b+(d-b)*3//5),str(c//2),str(b+(d-b)//2),'900');_,legacy=viewport();time.sleep(.6)
check(legacy[0]==2 and legacy[2]>100,'1.0.0 legacy release stores reading progress')
adb('shell','am','force-stop','com.jy.pdf.read.io');adb('install','-r',str(ROOT/'dist'/'pdf-reader-1.0.1-universal.apk'))
open_pdf('upgrade-renamed.pdf');_,upgraded=viewport();check(upgraded==legacy,'1.0.1 installs over 1.0.0 and preserves legacy page zoom and scroll offsets')
tree=snap();back=node(tree,rid='reader_back');mark=node(tree,rid='bookmark_toggle');check(back is not None and back.get('class')=='android.widget.ImageButton' and mark is not None,'header contains back arrow and bookmark image buttons')
tap(rid='bookmark_toggle');check(node(snap(),rid='bookmark_toggle').get('selected')=='true','release can set current page as bookmark')
tap(text='下一页');_,next_page=viewport();check(next_page[0]==3 and next_page[3]==0 and next_page[5]==0,'next page enters with both scrollbars at zero')
tap(text='上一页');_,previous=viewport();check(previous[0]==2 and previous[3]==0 and previous[5]==0,'previous page enters with both scrollbars at zero')
tap(text='下一页');restart('upgrade-bookmark.pdf');_,marked=viewport();check(marked[0]==2 and marked[3]==0 and marked[5]==0,'bookmark overrides later reading page after process restart')
jump(1);tap(rid='bookmark_toggle');tap(text='下一页');restart('upgrade-bookmark-replaced.pdf');_,replaced=viewport();check(replaced[0]==1,'new bookmark replaces old bookmark on another page')
tap(rid='bookmark_toggle');tap(text='下一页');v,_=viewport();a,b,c,d=bounds(v);adb('shell','input','swipe',str(c*3//4),str(b+(d-b)*3//5),str(c//2),str(b+(d-b)//2),'900');_,last=viewport();time.sleep(.6)
restart('upgrade-bookmark-cleared.pdf');_,fallback=viewport();check(fallback==last,'clearing bookmark restores last reading page and offsets')
jump(9999999999999999999999);check(viewport()[1][0]==3,'oversized page-jump input clamps to last page in release')
jump(0);check(viewport()[1][0]==1,'zero page-jump input clamps to first page in release')
tap(rid='bookmark_toggle');longname='这是用于验证文件名称很长时省略号和书签图标布局的PDF阅读文档'*6+'.pdf';open_pdf(longname);capture('pdf-reader-1.0.1-reading.png')
tree=snap();v,_=viewport(tree);a,b,c,d=bounds(v);adb('shell','input','tap',str((a+c)//2),str((b+d)//2));tree=snap()
check(node(tree,rid='reader_back') is None and node(tree,rid='bookmark_toggle') is None and node(tree,rid='reader_status') is None,'tap hides updated top and bottom floating bars together');capture('pdf-reader-1.0.1-immersive.png')
adb('shell','input','tap',str((a+c)//2),str((b+d)//2));check(node(snap(),rid='bookmark_toggle') is not None,'tap restores bookmark icon with floating layers')
tap(text='裁切');tree=snap();check(any('四边中心点' in n.get('content-desc','') for n in tree.iter('node')),'signed release exposes four single-edge midpoint crop controls');capture('pdf-reader-1.0.1-crop.png');tap(text='取消')
(OUT/'results.json').write_text(json.dumps({'checks':checks},ensure_ascii=False,indent=2),encoding='utf-8')
print('UPGRADE_ALL_PASS',len(checks),flush=True)

package com.jy.pdf.read.io;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;

public final class ReaderInstrumentation extends Instrumentation {
    private final List<String> checks=new ArrayList<>();
    private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private <T>T ui(Callable<T> action){AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{result.set(action.call());}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new AssertionError(failure.get());return result.get();}
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks.add(name);Bundle b=new Bundle();b.putString("stream","PASS "+name+"\n");sendStatus(0,b);}
    private void waitReady()throws Exception{for(int i=0;i<200;i++){if(ui(()->activity.current!=null&&activity.reader.isEnabled()&&activity.reader.preview()!=null&&activity.reader.positionReady()&&activity.current.pageCount>0))return;Thread.sleep(75);}throw new AssertionError("PDF did not become ready");}
    private void waitPage(int page)throws Exception{for(int i=0;i<200;i++){if(ui(()->activity.current!=null&&activity.current.page==page&&activity.reader.positionReady())){Thread.sleep(150);return;}Thread.sleep(75);}throw new AssertionError("Page not loaded: "+page);}
    private void launchPdf(String name){Intent intent=new Intent().setClassName("com.jy.pdf.read.io.test","com.jy.pdf.read.io.FixtureLauncher").putExtra("name",name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getContext().startActivity(intent);
        if(!name.startsWith("invalid")&&!name.startsWith("unique-cancel")){for(int i=0;i<200;i++){if(ui(()->activity.current!=null&&activity.current.name.equals(name)&&activity.reader.isEnabled()&&activity.reader.positionReady()))return;try{Thread.sleep(75);}catch(InterruptedException e){throw new AssertionError(e);}}throw new AssertionError("External PDF did not open: "+name);}}
    private void key(int direction,boolean repeat){ui(()->{int code=direction<0?24:25,scan=direction<0?115:114;long now=SystemClock.uptimeMillis();activity.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,repeat?1:0,0,2,scan,0,InputDevice.SOURCE_KEYBOARD));activity.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,code,0,0,2,scan,0,InputDevice.SOURCE_KEYBOARD));return null;});}
    private void touchDrag(float sx,float sy,float ex,float ey){ui(()->{long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,sx,sy,0);activity.reader.dispatchTouchEvent(down);down.recycle();for(int i=1;i<=5;i++){MotionEvent move=MotionEvent.obtain(now,now+i*50,MotionEvent.ACTION_MOVE,sx+(ex-sx)*i/5,sy+(ey-sy)*i/5,0);activity.reader.dispatchTouchEvent(move);move.recycle();}MotionEvent up=MotionEvent.obtain(now,now+500,MotionEvent.ACTION_UP,ex,ey,0);activity.reader.dispatchTouchEvent(up);up.recycle();return null;});}
    private void pinch(){ui(()->{
        long now=SystemClock.uptimeMillis();float cx=activity.reader.getWidth()/2f,cy=activity.reader.getHeight()/2f;
        MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
        for(int i=0;i<2;i++){properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;}
        MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
        for(MotionEvent.PointerCoords point:coords){point.y=cy;point.pressure=1;point.size=1;}
        float base=Math.max(100,ViewConfiguration.get(activity).getScaledMinimumScalingSpan()/2f+40);
        coords[0].x=cx-base;coords[1].x=cx+base;
        sendTouch(now,now,MotionEvent.ACTION_DOWN,1,properties,coords);
        sendTouch(now,now+20,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,properties,coords);
        for(int i=1;i<=12;i++){coords[0].x=cx-base-i*20;coords[1].x=cx+base+i*20;sendTouch(now,now+30+i*25,MotionEvent.ACTION_MOVE,2,properties,coords);}
        sendTouch(now,now+300,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,properties,coords);
        sendTouch(now,now+320,MotionEvent.ACTION_UP,1,properties,coords);return null;
    });}
    private void sendTouch(long down,long time,int action,int count,MotionEvent.PointerProperties[] properties,MotionEvent.PointerCoords[] coords){MotionEvent event=MotionEvent.obtain(down,time,action,count,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);activity.reader.dispatchTouchEvent(event);event.recycle();}
    private Dialog dialog()throws Exception{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("documentDialog");field.setAccessible(true);return ui(()->(Dialog)field.get(activity));}
    private View findText(View root,String text){if(root instanceof android.widget.TextView&&((android.widget.TextView)root).getText().toString().equals(text))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View result=findText(group.getChildAt(i),text);if(result!=null)return result;}}return null;}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Intent start=new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity=(MainActivity)startActivitySync(start);launchPdf("sample.pdf");waitReady();Thread.sleep(300);
            check(ui(()->activity.current.pageCount==3&&activity.current.name.equals("sample.pdf")),"external ACTION_VIEW accepts temporary read grant from a private pipe provider");
            String id=ui(()->activity.current.md5);check(id.matches("[0-9a-f]{32}"),"imported content identified by MD5");
            ui(()->{activity.current.bookmarkPage=-1;return null;});
            if(ui(()->activity.current.page)!=0){ui(()->{activity.changePage(0,1);return null;});waitPage(0);}
            ui(()->{activity.reader.applyCrop(ReaderGeometry.Crop.full());activity.reader.zoomTo(1);activity.reader.geometry.x=0;activity.reader.geometry.y=0;return null;});
            check(ui(()->Math.abs(activity.reader.geometry.width()-activity.reader.getWidth())<1),"minimum zoom fits PDF width");
            pinch();double pinchedZoom=ui(()->activity.reader.geometry.zoom);check(pinchedZoom>1.3&&pinchedZoom<=4,"two-finger gesture zooms the real PDF viewport ("+pinchedZoom+")");
            ui(()->{activity.reader.zoomTo(2);activity.reader.geometry.x=200;activity.reader.geometry.y=0;return null;});
            key(1,false);check(ui(()->activity.current.page==0&&activity.reader.geometry.y>0&&Math.abs(activity.reader.geometry.x-200)<1),"physical next scrolls vertically without changing horizontal offset");
            ui(()->{activity.reader.geometry.y=activity.reader.geometry.maxY()-20;return null;});key(1,false);
            check(ui(()->activity.current.page==0&&Math.abs(activity.reader.geometry.y-activity.reader.geometry.maxY())<1),"reaching bottom does not turn PDF page in same press");
            Thread.sleep(200);key(1,true);check(ui(()->activity.current.page==0),"held key cannot cross a page boundary");
            key(1,false);waitPage(1);check(ui(()->activity.reader.geometry.y<1&&activity.reader.geometry.x<1),"next page starts with both scroll offsets at zero");
            key(-1,false);waitPage(0);check(ui(()->activity.reader.geometry.y<1&&activity.reader.geometry.x<1),"previous page starts with both scroll offsets at zero");
            ui(()->{activity.reader.geometry.y=0;return null;});key(-1,false);check(ui(()->activity.current.page==0),"previous key at document start stays on first page");
            ui(()->{activity.reader.geometry.x=0;activity.reader.geometry.y=0;return null;});touchDrag(600,900,350,550);
            check(ui(()->activity.reader.geometry.x>100&&activity.reader.geometry.y>100),"single-finger drag pans both horizontal and vertical axes");
            ui(()->{activity.reader.preparePageChange();activity.reader.geometry.x=200;activity.reader.geometry.y=300;return null;});
            double beforeHeight=ui(()->(double)activity.reader.getHeight());ui(()->{activity.reader.performClick();return null;});
            check(ui(()->!activity.findViewById(R.id.reader_status).isShown()&&activity.reader.getHeight()==beforeHeight),"tap hides both floating bars without resizing PDF viewport");
            ui(()->{activity.reader.performClick();return null;});check(ui(()->activity.findViewById(R.id.reader_status).isShown()),"second tap restores floating bars");
            ReaderGeometry.Crop crop=new ReaderGeometry.Crop(.1,.1,.9,.9);ui(()->{activity.reader.applyCrop(crop);return null;});
            ui(()->{activity.changePage(1,1);return null;});waitPage(1);check(ui(()->Math.abs(activity.reader.geometry.crop.left-.1)<.0001&&Math.abs(activity.reader.geometry.crop.bottom-.9)<.0001),"document crop applies across PDF pages");
            ui(()->{activity.reader.zoomTo(2.5);activity.reader.geometry.x=250;activity.reader.geometry.y=500;return null;});
            double px=ui(()->activity.reader.geometry.pageX()),py=ui(()->activity.reader.geometry.pageY());
            ui(()->{activity.finish();return null;});Thread.sleep(250);
            activity=(MainActivity)startActivitySync(start);launchPdf("renamed-same-content.pdf");waitReady();waitPage(1);
            check(ui(()->activity.current.md5.equals(id)&&activity.current.name.equals("renamed-same-content.pdf")),"renamed PDF reuses same content identity");
            check(ui(()->activity.current.page==1&&Math.abs(activity.reader.geometry.zoom-2.5)<.001&&Math.abs(activity.reader.geometry.pageX()-px)<.002&&Math.abs(activity.reader.geometry.pageY()-py)<.002),"reopen restores page zoom and both PDF-space scroll positions");
            check(ui(()->Math.abs(activity.reader.geometry.crop.left-.1)<.001),"crop persists through closing and reopening");
            DocumentStore fresh=new DocumentStore(getTargetContext());ReadingState saved=fresh.get(id);
            check(saved!=null&&saved.page==1&&Math.abs(saved.zoom-2.5)<.001,"progress JSON survives a fresh store instance");
            ui(()->{activity.changePage(2,1);return null;});waitPage(2);ui(()->{activity.reader.geometry.y=activity.reader.geometry.maxY();return null;});key(1,false);
            check(ui(()->activity.current.page==2),"next at end stays within PDF bounds");
            ui(()->{activity.reader.zoomTo(.1);return null;});check(ui(()->Math.abs(activity.reader.geometry.zoom-1)<.001),"zoom out cannot make content narrower than viewport");
            Thread.sleep(1200);
            check(ui(()->{Bitmap image=Bitmap.createBitmap(activity.reader.getWidth(),activity.reader.getHeight(),Bitmap.Config.ARGB_8888);activity.reader.draw(new Canvas(image));int dark=0;for(int y=0;y<image.getHeight();y+=7)for(int x=0;x<image.getWidth();x+=7)if(Color.red(image.getPixel(x,y))<100)dark++;image.recycle();return dark>50;}),"real PDF preview and tile rendering produce visible page content");
            ui(()->{activity.changePage(1,1);activity.onBackPressed();return null;});
            check(ui(()->activity.current==null&&activity.reader.preview()==null),"back during page loading closes the obsolete render session safely");
            launchPdf("sample.pdf");waitReady();check(ui(()->activity.current.page==2),"cancelled page change retains last completed reading page");
            ui(()->{findText(activity.getWindow().getDecorView(),"裁切").performClick();return null;});Dialog oldCrop=dialog();
            check(ui(()->oldCrop!=null&&oldCrop.isShowing()),"crop dialog opens for the current PDF");
            launchPdf("unique-second.pdf");for(int i=0;i<100&&!ui(()->activity.current!=null&&activity.current.name.equals("unique-second.pdf"));i++)Thread.sleep(75);waitReady();
            check(ui(()->!oldCrop.isShowing()&&!activity.current.md5.equals(id)&&activity.reader.geometry.crop.left==0),"external document switch dismisses old crop dialog without applying it to new PDF");
            ui(()->{activity.findViewById(R.id.reader_status).performClick();return null;});Dialog oldJump=dialog();launchPdf("sample.pdf");
            for(int i=0;i<100&&!ui(()->activity.current!=null&&activity.current.md5.equals(id));i++)Thread.sleep(75);waitReady();
            check(ui(()->!oldJump.isShowing()&&activity.current.page==2),"external document switch dismisses old page-jump dialog");
            for(int i=0;i<4;i++){launchPdf("unique-cancel-"+i+".pdf");Thread.sleep(i*8+2);ui(()->{if(activity.current!=null||!activity.reader.isEnabled())activity.onBackPressed();return null;});}
            Thread.sleep(700);DocumentStore afterCancel=new DocumentStore(getTargetContext());Set<String> ids=new HashSet<>();for(ReadingState book:afterCancel.recent())ids.add(book.md5+".pdf");
            File[] local=new File(getTargetContext().getFilesDir(),"documents").listFiles((dir,name)->name.endsWith(".pdf"));boolean indexed=true;if(local!=null)for(File file:local)indexed&=ids.contains(file.getName());
            check(indexed,"cancelled and superseded imports leave no unindexed PDF copies");
            launchPdf("sample.pdf");waitReady();ui(()->{activity.changePage(0,1);return null;});launchPdf("invalid.pdf");
            for(int i=0;i<100&&!ui(()->activity.current==null);i++)Thread.sleep(75);
            check(ui(()->activity.current==null&&activity.reader.preview()==null),"invalid external PDF during page change cannot leave a stale rendering session");
            Thread.sleep(250);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);launchPdf("sample.pdf");waitReady();
            check(ui(()->activity.current.md5.equals(id)&&activity.reader.preview()!=null),"reader can reopen last valid PDF after a failed external open");
            result.putString("stream","\nALL_PASS "+checks.size()+" reader checks\n");result.putInt("checks",checks.size());finish(Activity.RESULT_OK,result);
        }catch(Throwable error){StringWriter text=new StringWriter();error.printStackTrace(new PrintWriter(text));result.putString("stream","\nFAILED after "+checks.size()+" checks\n"+text);finish(Activity.RESULT_CANCELED,result);}
    }
}

package com.jy.pdf.read.io;

import android.app.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.MotionEvent;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

public final class CropInteractionInstrumentation extends Instrumentation {
    private int checks;
    private float left,top,width,height;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private ReaderGeometry.Crop drag(ReaderGeometry.Crop initial,float sx,float sy,float dx,float dy){
        AtomicReference<ReaderGeometry.Crop> result=new AtomicReference<>();runOnMainSync(()->{
            Bitmap preview=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);CropSelectionView view=new CropSelectionView(getTargetContext(),preview,initial);view.layout(0,0,600,1000);
            long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,sx,sy,0);view.dispatchTouchEvent(down);down.recycle();
            MotionEvent move=MotionEvent.obtain(now,now+100,MotionEvent.ACTION_MOVE,sx+dx,sy+dy,0);view.dispatchTouchEvent(move);move.recycle();
            MotionEvent up=MotionEvent.obtain(now,now+150,MotionEvent.ACTION_UP,sx+dx,sy+dy,0);view.dispatchTouchEvent(up);up.recycle();result.set(view.crop());preview.recycle();
        });return result.get();
    }
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;Bundle b=new Bundle();b.putString("stream","PASS "+text+"\n");sendStatus(0,b);}
    private boolean same(double a,double b){return Math.abs(a-b)<.0001;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        float pad=16*getTargetContext().getResources().getDisplayMetrics().density,scale=Math.min((600-2*pad)/600,(1000-2*pad)/900);width=600*scale;height=900*scale;left=(600-width)/2;top=(1000-height)/2;
        ReaderGeometry.Crop full=ReaderGeometry.Crop.full(),c=drag(full,left,top,40,70);
        check(same(c.left,0)&&same(c.top,0)&&same(c.right,1)&&same(c.bottom,1),"old corner is not a two-edge control");
        ReaderGeometry.Crop initial=new ReaderGeometry.Crop(.1,.1,.9,.9);
        c=drag(initial,300,500,40,70);check(same(c.left,.1)&&same(c.top,.1)&&same(c.right,.9)&&same(c.bottom,.9),"dragging inside does not move the crop frame");
        c=drag(initial,left+.1f*width,500,40,70);check(c.left>.1&&same(c.top,.1)&&same(c.right,.9)&&same(c.bottom,.9),"left midpoint changes only left edge during diagonal drag");
        c=drag(initial,left+.9f*width,500,-40,70);check(c.right<.9&&same(c.top,.1)&&same(c.left,.1)&&same(c.bottom,.9),"right midpoint changes only right edge during diagonal drag");
        c=drag(initial,300,top+.1f*height,40,70);check(c.top>.1&&same(c.left,.1)&&same(c.right,.9)&&same(c.bottom,.9),"top midpoint changes only top edge during diagonal drag");
        c=drag(initial,300,top+.9f*height,40,-70);check(c.bottom<.9&&same(c.left,.1)&&same(c.right,.9)&&same(c.top,.1),"bottom midpoint changes only bottom edge during diagonal drag");
        c=drag(initial,left+.1f*width,500,10000,70);check(c.valid()&&c.right-c.left>=.05,"single-edge drag cannot invert or collapse the crop");
        result.putString("stream","\nCROP_ALL_PASS "+checks+" checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){StringWriter text=new StringWriter();e.printStackTrace(new PrintWriter(text));result.putString("stream","\nCROP_FAILED after "+checks+" checks\n"+text);finish(Activity.RESULT_CANCELED,result);}}
}

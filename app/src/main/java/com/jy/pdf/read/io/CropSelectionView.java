package com.jy.pdf.read.io;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

final class CropSelectionView extends View {
    private final Bitmap preview;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF page=new RectF(),selection=new RectF();
    private ReaderGeometry.Crop crop;
    private int edges;
    private float lastX,lastY;
    CropSelectionView(Context context,Bitmap preview,ReaderGeometry.Crop crop){super(context);this.preview=preview;this.crop=crop;setContentDescription("拖动裁切框四边中心点，每个点只调整一条边，框内拖动不移动裁切框，应用到整份 PDF");}
    ReaderGeometry.Crop crop(){return crop;}
    void reset(){crop=ReaderGeometry.Crop.full();invalidate();}
    private void geometry(){
        float pad=16*getResources().getDisplayMetrics().density;
        float scale=Math.min((getWidth()-2*pad)/preview.getWidth(),(getHeight()-2*pad)/preview.getHeight());
        float width=preview.getWidth()*scale,height=preview.getHeight()*scale;
        page.set((getWidth()-width)/2,(getHeight()-height)/2,(getWidth()+width)/2,(getHeight()+height)/2);
        selection.set(page.left+(float)crop.left*width,page.top+(float)crop.top*height,page.left+(float)crop.right*width,page.top+(float)crop.bottom*height);
    }
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(Color.LTGRAY);geometry();paint.setColor(Color.WHITE);canvas.drawRect(page,paint);canvas.drawBitmap(preview,null,page,paint);
        paint.setColor(0x88000000);
        canvas.drawRect(page.left,page.top,page.right,selection.top,paint);canvas.drawRect(page.left,selection.bottom,page.right,page.bottom,paint);
        canvas.drawRect(page.left,selection.top,selection.left,selection.bottom,paint);canvas.drawRect(selection.right,selection.top,page.right,selection.bottom,paint);
        paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);canvas.drawRect(selection,paint);paint.setStyle(Paint.Style.FILL);
        float handle=9*getResources().getDisplayMetrics().density;
        drawHandle(canvas,selection.left,selection.centerY(),handle);drawHandle(canvas,selection.right,selection.centerY(),handle);
        drawHandle(canvas,selection.centerX(),selection.top,handle);drawHandle(canvas,selection.centerX(),selection.bottom,handle);
    }
    private void drawHandle(Canvas canvas,float x,float y,float radius){paint.setColor(Color.WHITE);canvas.drawCircle(x,y,radius,paint);paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);canvas.drawCircle(x,y,radius,paint);paint.setStyle(Paint.Style.FILL);}
    private int edgeAt(float x,float y,float hit){
        int edge=0;float best=hit*hit;
        float distance=squared(x-selection.left,y-selection.centerY());if(distance<=best){best=distance;edge=1;}
        distance=squared(x-selection.right,y-selection.centerY());if(distance<best){best=distance;edge=2;}
        distance=squared(x-selection.centerX(),y-selection.top);if(distance<best){best=distance;edge=4;}
        distance=squared(x-selection.centerX(),y-selection.bottom);if(distance<best)edge=8;
        return edge;
    }
    private static float squared(float x,float y){return x*x+y*y;}
    @Override public boolean onTouchEvent(MotionEvent event){
        geometry();float x=event.getX(),y=event.getY(),hit=32*getResources().getDisplayMetrics().density;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            edges=edgeAt(x,y,hit);
            lastX=x;lastY=y;return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&edges!=0){
            double dx=(x-lastX)/page.width(),dy=(y-lastY)/page.height();double l=crop.left,t=crop.top,r=crop.right,b=crop.bottom;
            if(edges==1)l=ReaderGeometry.bound(l+dx,0,r-.05);else if(edges==2)r=ReaderGeometry.bound(r+dx,l+.05,1);
            else if(edges==4)t=ReaderGeometry.bound(t+dy,0,b-.05);else if(edges==8)b=ReaderGeometry.bound(b+dy,t+.05,1);
            ReaderGeometry.Crop next=new ReaderGeometry.Crop(l,t,r,b);if(next.valid())crop=next;lastX=x;lastY=y;invalidate();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){edges=0;if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

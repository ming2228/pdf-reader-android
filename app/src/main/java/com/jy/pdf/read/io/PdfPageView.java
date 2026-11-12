package com.jy.pdf.read.io;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.view.*;
import android.widget.OverScroller;
import java.util.HashSet;
import java.util.Set;

final class PdfPageView extends View {
    interface Listener {void changed();void tap();}
    final ReaderGeometry geometry=new ReaderGeometry();
    private static final int TILE=512;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final LruCache<String,Bitmap> tiles;
    private final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    private final ScaleGestureDetector pinch;
    private final GestureDetector touch;
    private final OverScroller scroller;
    private PdfEngine engine;
    private Bitmap preview;
    private int pageToken,epoch,pageIndex,pageCount;
    private boolean scaling;
    private Listener listener;
    private double pendingPX,pendingPY;
    private int pendingEdge;
    private boolean needsRestore;

    PdfPageView(Context context) {
        super(context);setId(R.id.pdf_view);setBackgroundColor(Color.WHITE);
        setFocusableInTouchMode(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        // Draw high-contrast bars ourselves: a programmatically enabled platform
        // scrollbar can have an uninitialized drawable on some framework builds.
        setHorizontalScrollBarEnabled(false);setVerticalScrollBarEnabled(false);
        int budget=(int)Math.max(8*1024*1024,Math.min(16*1024*1024,Runtime.getRuntime().maxMemory()/8));
        tiles=new LruCache<String,Bitmap>(budget){@Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
        scroller=new OverScroller(context);
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector detector){scroller.abortAnimation();scaling=true;return true;}
            @Override public boolean onScale(ScaleGestureDetector detector){geometry.zoomTo(geometry.zoom*detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());notifyChanged();return true;}
            @Override public void onScaleEnd(ScaleGestureDetector detector){scaling=false;resetTiles();notifyChanged();}
        });
        touch=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){scroller.abortAnimation();return true;}
            @Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){
                if(scaling)return true;geometry.x+=dx;geometry.y+=dy;geometry.clamp();notifyChanged();return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e){performClick();return true;}
            @Override public boolean onDoubleTap(MotionEvent e){zoomTo(geometry.zoom<1.5?2:1,e.getX(),e.getY());return true;}
            @Override public boolean onFling(MotionEvent a,MotionEvent b,float vx,float vy){
                if(scaling)return false;scroller.fling((int)geometry.x,(int)geometry.y,(int)-vx,(int)-vy,0,(int)Math.min(Integer.MAX_VALUE,geometry.maxX()),0,(int)Math.min(Integer.MAX_VALUE,geometry.maxY()));postInvalidateOnAnimation();return true;
            }
        });
    }
    void setListener(Listener listener){this.listener=listener;}
    Bitmap preview(){return preview;}
    boolean positionReady(){return !needsRestore&&getWidth()>0&&getHeight()>0;}
    void setPage(PdfEngine engine,int token,int index,int count,int width,int height,Bitmap image,ReadingState state,int edge){
        scroller.abortAnimation();this.engine=engine;pageToken=token;pageIndex=index;pageCount=count;preview=image;
        geometry.pageW=width;geometry.pageH=height;geometry.crop=state.crop;geometry.zoom=ReaderGeometry.bound(state.zoom,1,4);
        geometry.viewW=Math.max(1,getWidth());geometry.viewH=Math.max(1,getHeight());
        pendingEdge=edge;pendingPX=state.px;pendingPY=state.py;needsRestore=true;
        restorePosition();resetTiles();notifyChanged();
    }
    private void restorePosition(){
        if(!needsRestore||getWidth()==0||getHeight()==0)return;
        if(pendingEdge==0)geometry.restore(pendingPX,pendingPY);
        else{geometry.x=0;geometry.y=0;geometry.clamp();}
        needsRestore=false;
    }
    void clear(){scroller.abortAnimation();engine=null;preview=null;tiles.evictAll();pending.clear();failed.clear();invalidate();setContentDescription("尚未打开 PDF");}
    void preparePageChange(){scroller.abortAnimation();}
    void applyCrop(ReaderGeometry.Crop crop){geometry.cropTo(crop);resetTiles();notifyChanged();}
    void zoomTo(double zoom){zoomTo(zoom,getWidth()/2f,getHeight()/2f);}
    private void zoomTo(double zoom,float x,float y){if(preview==null)return;scroller.abortAnimation();geometry.zoomTo(zoom,x,y);resetTiles();notifyChanged();}
    int keyStep(int direction,boolean repeat){scroller.abortAnimation();int delta=geometry.step(direction,repeat,pageIndex,pageCount);notifyChanged();return delta;}
    void resetTiles(){if(engine!=null)epoch=engine.invalidateTiles();tiles.evictAll();pending.clear();failed.clear();invalidate();}
    void trim(){tiles.evictAll();invalidate();}

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        double px=geometry.pageX(),py=geometry.pageY();geometry.viewW=Math.max(1,w);geometry.viewH=Math.max(1,h);
        if(needsRestore)restorePosition();else geometry.restore(px,py);resetTiles();notifyChanged();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(preview==null||!isEnabled())return true;
        pinch.onTouchEvent(event);touch.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){if(!scaling)notifyChanged();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();if(listener!=null)listener.tap();return true;}
    @Override public void computeScroll(){if(scroller.computeScrollOffset()){geometry.x=scroller.getCurrX();geometry.y=scroller.getCurrY();geometry.clamp();notifyChanged();postInvalidateOnAnimation();}}
    private void notifyChanged(){
        invalidate();
        setContentDescription("第 "+(pageIndex+1)+" / "+pageCount+" 页；缩放 "+Math.round(geometry.zoom*100)+"%；横向 "+Math.round(geometry.x)+" / "+Math.round(geometry.maxX())+"；纵向 "+Math.round(geometry.y)+" / "+Math.round(geometry.maxY()));
        if(listener!=null&&preview!=null&&!needsRestore)listener.changed();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(preview==null||engine==null)return;
        double scale=geometry.scale();float x=(float)geometry.x,y=(float)geometry.y;
        canvas.save();canvas.clipRect(0,0,(float)Math.min(getWidth(),geometry.width()-x),(float)Math.min(getHeight(),geometry.height()-y));
        float left=(float)(-geometry.crop.left*geometry.pageW*scale)-x,top=(float)(-geometry.crop.top*geometry.pageH*scale)-y;
        canvas.drawBitmap(preview,null,new RectF(left,top,left+(float)(geometry.pageW*scale),top+(float)(geometry.pageH*scale)),paint);
        if(!scaling){
            int startX=Math.max(0,(int)(x/TILE)),startY=Math.max(0,(int)(y/TILE));
            int endX=(int)Math.ceil(Math.min(geometry.width(),x+getWidth())/TILE),endY=(int)Math.ceil(Math.min(geometry.height(),y+getHeight())/TILE);
            for(int row=startY;row<endY;row++)for(int col=startX;col<endX;col++){
                String key=col+":"+row;Bitmap tile=tiles.get(key);
                int tx=col*TILE,ty=row*TILE;
                if(tile!=null)canvas.drawBitmap(tile,tx-x,ty-y,paint);
                else if(pending.size()<32&&!pending.contains(key)&&!failed.contains(key))requestTile(key,tx,ty,scale);
            }
        }
        canvas.restore();drawPositionBars(canvas);
    }
    private void drawPositionBars(Canvas canvas){
        float unit=getResources().getDisplayMetrics().density,thickness=Math.max(4,3*unit),gap=2*unit;
        if(geometry.maxY()>0){
            float track=getHeight()-(geometry.maxX()>0?thickness+gap:0);
            float thumb=(float)Math.min(track,Math.max(24*unit,track*geometry.viewH/geometry.height()));
            float top=(float)((track-thumb)*geometry.y/geometry.maxY());
            paint.setColor(Color.LTGRAY);canvas.drawRect(getWidth()-thickness-gap,0,getWidth()-gap,track,paint);
            paint.setColor(Color.BLACK);canvas.drawRect(getWidth()-thickness-gap,top,getWidth()-gap,top+thumb,paint);
        }
        if(geometry.maxX()>0){
            float track=getWidth()-(geometry.maxY()>0?thickness+gap:0);
            float thumb=(float)Math.min(track,Math.max(24*unit,track*geometry.viewW/geometry.width()));
            float left=(float)((track-thumb)*geometry.x/geometry.maxX());
            paint.setColor(Color.LTGRAY);canvas.drawRect(0,getHeight()-thickness-gap,track,getHeight()-gap,paint);
            paint.setColor(Color.BLACK);canvas.drawRect(left,getHeight()-thickness-gap,left+thumb,getHeight()-gap,paint);
        }
    }
    private void requestTile(String key,int left,int top,double scale){
        pending.add(key);int expected=epoch;PdfEngine expectedEngine=engine;
        int width=Math.max(1,(int)Math.min(TILE,Math.ceil(geometry.width()-left))),height=Math.max(1,(int)Math.min(TILE,Math.ceil(geometry.height()-top)));
        engine.tile(pageToken,epoch,scale,geometry.crop.left*geometry.pageW,geometry.crop.top*geometry.pageH,left,top,width,height,new PdfEngine.TileCallback(){
            @Override public void ready(Bitmap bitmap){if(engine!=expectedEngine||epoch!=expected){bitmap.recycle();return;}pending.remove(key);tiles.put(key,bitmap);invalidate();}
            @Override public void failed(){if(engine!=expectedEngine||epoch!=expected)return;pending.remove(key);failed.add(key);}
        });
    }
    @Override protected int computeHorizontalScrollRange(){return (int)Math.min(Integer.MAX_VALUE,Math.ceil(geometry.width()));}
    @Override protected int computeHorizontalScrollOffset(){return (int)geometry.x;}
    @Override protected int computeHorizontalScrollExtent(){return getWidth();}
    @Override protected int computeVerticalScrollRange(){return (int)Math.min(Integer.MAX_VALUE,Math.ceil(geometry.height()));}
    @Override protected int computeVerticalScrollOffset(){return (int)geometry.y;}
    @Override protected int computeVerticalScrollExtent(){return getHeight();}
}

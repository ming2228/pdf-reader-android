package com.jy.pdf.read.io;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class PdfEngine {
    interface PageCallback {void ready(int token,int index,int count,int width,int height,Bitmap preview);void error(Exception error);}
    interface TileCallback {void ready(Bitmap bitmap);void failed();}
    final AtomicInteger tileEpoch=new AtomicInteger();
    private final AtomicInteger pageToken=new AtomicInteger();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"pdf-render"));
    private PdfRenderer renderer;
    private PdfRenderer.Page page;
    private volatile boolean closed;
    private File file;
    void open(File file,int requested,PageCallback callback) {this.file=file;show(requested,callback);}
    void show(int requested,PageCallback callback) {
        int token=pageToken.incrementAndGet();tileEpoch.incrementAndGet();
        worker.execute(()->{
            if(closed||token!=pageToken.get())return;
            Bitmap preview=null;
            try {
                if(renderer==null){ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);try{renderer=new PdfRenderer(fd);}catch(Exception e){fd.close();throw e;}}
                if(renderer.getPageCount()<1)throw new java.io.IOException("PDF 没有可阅读的页面");
                if(page!=null){page.close();page=null;}
                int index=Math.max(0,Math.min(renderer.getPageCount()-1,requested));page=renderer.openPage(index);
                int width=page.getWidth(),height=page.getHeight();
                double factor=Math.min(1000.0/width,Math.min(1600.0/height,Math.sqrt(1200000.0/width/height)));
                int w=Math.max(1,(int)Math.round(width*factor)),h=Math.max(1,(int)Math.round(height*factor));
                preview=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);preview.eraseColor(Color.WHITE);
                Matrix matrix=new Matrix();matrix.setScale((float)factor,(float)factor);page.render(preview,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap result=preview;int count=renderer.getPageCount();
                main.post(()->{if(closed||token!=pageToken.get()){result.recycle();return;}callback.ready(token,index,count,width,height,result);});
            }catch(Exception|OutOfMemoryError error){if(preview!=null)preview.recycle();Exception e=error instanceof Exception?(Exception)error:new java.io.IOException("内存不足，请关闭其他应用后重试",error);main.post(()->{if(!closed&&token==pageToken.get())callback.error(e);});}
        });
    }
    int invalidateTiles(){return tileEpoch.incrementAndGet();}
    void tile(int token,int epoch,double scale,double cropLeft,double cropTop,int left,int top,int width,int height,TileCallback callback){
        if(closed){callback.failed();return;}
        worker.execute(()->{
            if(closed||token!=pageToken.get()||epoch!=tileEpoch.get()){main.post(callback::failed);return;}
            Bitmap bitmap=null;
            try {
                bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);
                Matrix transform=new Matrix();transform.setValues(new float[]{(float)scale,0,(float)(-cropLeft*scale-left),0,(float)scale,(float)(-cropTop*scale-top),0,0,1});
                page.render(bitmap,null,transform,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap result=bitmap;
                main.post(()->{if(closed||token!=pageToken.get()||epoch!=tileEpoch.get()){result.recycle();callback.failed();}else callback.ready(result);});
            }catch(Exception|OutOfMemoryError error){if(bitmap!=null)bitmap.recycle();main.post(callback::failed);}
        });
    }
    void close(){
        if(closed)return;closed=true;pageToken.incrementAndGet();tileEpoch.incrementAndGet();
        worker.execute(()->{if(page!=null){page.close();page=null;}if(renderer!=null){renderer.close();renderer=null;}});worker.shutdown();
    }
}

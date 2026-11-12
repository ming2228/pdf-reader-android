package com.jy.pdf.read.io;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public final class FixtureProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private synchronized File fixture() throws IOException {
        File file=new File(getContext().getFilesDir(),"reader-fixture-v1.pdf");if(file.isFile())return file;
        PdfDocument document=new PdfDocument();Paint paint=new Paint();paint.setColor(Color.BLACK);paint.setAntiAlias(true);
        for(int i=0;i<3;i++){
            int width=i==2?1200:600,height=i==0?1800:i==1?1200:600;
            PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(width,height,i+1).create());Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);canvas.drawRect(30,30,width-30,height-30,paint);paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(28);canvas.drawText("PDF READER / PAGE "+(i+1),50,80,paint);
            paint.setTextSize(16);for(int y=140;y<height-60;y+=55){canvas.drawText("Reading line "+y+" - vertical scroll, crop and zoom",50,y,paint);canvas.drawLine(50,y+10,width-50,y+10,paint);}
            canvas.drawRect(60,100,120,120,paint);canvas.drawText("BOTTOM PAGE "+(i+1),50,height-45,paint);document.finishPage(page);
        }
        try(FileOutputStream out=new FileOutputStream(file)){document.writeTo(out);}finally{document.close();}return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        try{
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){
                if(uri.getLastPathSegment().startsWith("invalid"))out.write("this is not a pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                else {try(InputStream in=new FileInputStream(fixture())){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
                    if(uri.getLastPathSegment().startsWith("unique"))out.write(("\n% "+uri.getLastPathSegment()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            }catch(Exception ignored){}},"fixture-pipe").start();return pipe[0];
        }catch(IOException e){throw new FileNotFoundException(e.toString());}
    }
    @Override public String getType(Uri uri){return "application/pdf";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});cursor.addRow(new Object[]{uri.getLastPathSegment(),null});return cursor;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}

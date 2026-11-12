package com.jy.pdf.read.io;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class DocumentStore {
    private final Context context;
    private final File directory;
    private final AtomicFile progress;
    private final Map<String,ReadingState> books=new LinkedHashMap<>();
    String readWarning;
    DocumentStore(Context context) {
        this.context=context;directory=new File(context.getFilesDir(),"documents");
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("无法创建文档目录");
        progress=new AtomicFile(new File(context.getFilesDir(),"reading-progress.json"));
        try(InputStream input=progress.openRead()) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1) {if(bytes.size()>4*1024*1024)throw new IOException("阅读记录过大");bytes.write(buffer,0,n);}
            JSONArray array=new JSONObject(bytes.toString("UTF-8")).optJSONArray("books");
            if(array!=null)for(int i=0;i<array.length();i++){JSONObject row=array.optJSONObject(i);if(row==null)continue;ReadingState state=ReadingState.read(row);if(state!=null && file(state.md5).isFile())books.put(state.md5,state);}
        } catch(FileNotFoundException ignored) {} catch(Exception e) {
            try{java.nio.file.Files.copy(progress.getBaseFile().toPath(),new File(context.getFilesDir(),"reading-progress.corrupt-"+System.currentTimeMillis()+".json").toPath());readWarning="旧阅读记录无法读取，已备份损坏的记录；PDF 副本仍保留。";}
            catch(Exception backupError){readWarning="旧阅读记录无法读取；PDF 副本仍保留。";}
        }
    }
    File file(String md5) {if(!md5.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("无效文档 ID");return new File(directory,md5+".pdf");}
    synchronized ReadingState get(String id) {return books.get(id);}
    synchronized List<ReadingState> recent() {List<ReadingState> list=new ArrayList<>(books.values());list.sort((a,b)->Long.compare(b.opened,a.opened));return list;}
    synchronized void put(ReadingState state) throws IOException {books.put(state.md5,state);write();}
    synchronized void remove(String id) throws IOException {
        ReadingState previous=books.remove(id);
        try {write();} catch(IOException e){if(previous!=null)books.put(id,previous);throw e;}
        File local=file(id);if(local.exists()&&!local.delete())throw new IOException("记录已移除，但本地副本未能删除");
    }
    private void write() throws IOException {
        FileOutputStream stream=null;
        try {
            JSONObject root=new JSONObject();root.put("version",1);JSONArray array=new JSONArray();for(ReadingState book:books.values())array.put(book.json());root.put("books",array);
            stream=progress.startWrite();stream.write(root.toString().getBytes(StandardCharsets.UTF_8));progress.finishWrite(stream);
        } catch(Exception e){if(stream!=null)progress.failWrite(stream);throw new IOException("保存阅读记录失败",e);}
    }
    static final class Imported {
        final String md5,name;final File file;final boolean existed;
        Imported(String id,String name,File file,boolean existed){this.md5=id;this.name=name;this.file=file;this.existed=existed;}
    }
    Imported importPdf(Uri uri) throws Exception {
        String name=name(uri);File temporary=File.createTempFile("import-",".tmp",directory);
        try {
            MessageDigest hash=MessageDigest.getInstance("MD5");long copied=0;
            long budget=new StatFs(directory.getAbsolutePath()).getAvailableBytes()-32L*1024*1024;
            ByteArrayOutputStream header=new ByteArrayOutputStream();
            try(InputStream input=context.getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(temporary)) {
                if(input==null)throw new IOException("文件来源未提供可读取内容");
                byte[] buffer=new byte[65536];int count;
                while((count=input.read(buffer))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("导入已取消");
                    copied+=count;if(copied>budget)throw new IOException("设备可用空间不足，已预留 32MiB");
                    if(header.size()<1024)header.write(buffer,0,Math.min(count,1024-header.size()));
                    hash.update(buffer,0,count);output.write(buffer,0,count);
                }
            }
            if(!header.toString("ISO-8859-1").contains("%PDF-"))throw new IOException("选择的文件不是 PDF");
            StringBuilder id=new StringBuilder();for(byte b:hash.digest())id.append(String.format(Locale.ROOT,"%02x",b&255));
            File destination=file(id.toString());boolean existed=destination.isFile();
            if(!existed&&!temporary.renameTo(destination))throw new IOException("无法保存 PDF 本地副本");
            return new Imported(id.toString(),name,destination,existed);
        } finally {if(temporary.exists()&&!temporary.delete())temporary.deleteOnExit();}
    }
    private String name(Uri uri) {
        if("file".equals(uri.getScheme()))return new File(uri.getPath()).getName();
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){String value=cursor.getString(0);if(value!=null&&!value.trim().isEmpty())return value;}
        } catch(Exception ignored){}
        return "未命名.pdf";
    }
}

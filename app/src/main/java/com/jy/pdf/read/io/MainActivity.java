package com.jy.pdf.read.io;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity implements PdfPageView.Listener {
    private static final int PICK_PDF=10,READ_FILES=11;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService imports=Executors.newSingleThreadExecutor(r->new Thread(r,"pdf-import"));
    private Future<?> importTask;
    private Uri pendingFileUri;
    private DocumentStore store;
    private FrameLayout root,reading;
    private LinearLayout home,header,footer;
    private TextView title,status,loading;
    private Button previous,next,zoomLabel;
    private ImageButton bookmarkIcon;
    PdfPageView reader;
    ReadingState current;
    private PdfEngine engine,pendingEngine;
    private boolean busy,chrome=true,ready;
    private volatile boolean destroyed;
    private volatile int sequence;
    private final Object importLock=new Object();
    private DocumentStore.Imported uncommittedImport;
    private Dialog documentDialog;
    private long lastKeyAt;
    private final Runnable saveLater=()->saveNow();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new DocumentStore(this);buildUi();
        String id=state==null?null:state.getString("book");
        String pending=state==null?null:state.getString("pendingFileUri");
        if(pending!=null)openUri(Uri.parse(pending));
        else if(id!=null&&store.get(id)!=null)showBook(store.get(id),null,getLastNonConfigurationInstance()==null);
        else if(!handleIntent(getIntent()))showHome();
        if(store.readWarning!=null)toast(store.readWarning);
    }
    private void buildUi(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.WHITE);root.setFocusableInTouchMode(true);
        // Target 35 edge-to-edge on newer systems; consume insets into the app layout.
        root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        reading=new FrameLayout(this);reader=new PdfPageView(this);reader.setListener(this);reading.addView(reader,new FrameLayout.LayoutParams(-1,-1));root.addView(reading,new FrameLayout.LayoutParams(-1,-1));
        header=column();header.setPadding(dp(8),dp(4),dp(8),dp(5));header.setBackground(new ChromeBackground(getResources().getDisplayMetrics().density,true));
        LinearLayout heading=row();ImageButton back=icon(R.drawable.ic_arrow_back,"返回",this::showHome);back.setId(R.id.reader_back);heading.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        title=label("PDF 阅读器",18,true);title.setId(R.id.reader_title);title.setSingleLine(true);title.setMinWidth(0);title.setGravity(Gravity.CENTER_VERTICAL);title.setEllipsize(TextUtils.TruncateAt.END);heading.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        bookmarkIcon=icon(R.drawable.ic_bookmark_outline,"设置当前页书签",this::toggleBookmark);bookmarkIcon.setId(R.id.bookmark_toggle);heading.addView(bookmarkIcon,new LinearLayout.LayoutParams(dp(48),dp(48)));header.addView(heading);
        reading.addView(header,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        footer=column();footer.setBackground(new ChromeBackground(getResources().getDisplayMetrics().density,false));footer.setPadding(dp(8),dp(2),dp(8),dp(4));
        LinearLayout tools=row();add(tools,button("裁切",this::cropDialog));add(tools,button("缩小",()->reader.zoomTo(reader.geometry.zoom/1.25)));
        zoomLabel=button("100%",()->reader.zoomTo(1));add(tools,zoomLabel);add(tools,button("放大",()->reader.zoomTo(reader.geometry.zoom*1.25)));footer.addView(tools);
        LinearLayout pages=row();
        previous=button("上一页",()->changePage(current.page-1,-1));next=button("下一页",()->changePage(current.page+1,1));
        add(pages,previous);status=button("页码",this::jumpDialog);status.setId(R.id.reader_status);add(pages,status);add(pages,next);footer.addView(pages);
        reading.addView(footer,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        loading=label("正在打开 PDF…",17,true);loading.setPadding(dp(20),dp(14),dp(20),dp(14));loading.setBackgroundColor(Color.WHITE);loading.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);lp.leftMargin=dp(20);lp.rightMargin=dp(20);root.addView(loading,lp);loading.setVisibility(View.GONE);
    }
    private boolean handleIntent(Intent intent){
        if(intent!=null&&Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null){openUri(intent.getData());return true;}return false;
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent);}
    void openUri(Uri uri){
        dismissDocumentDialog();saveNow();
        if(needsFilePermission(uri)){pendingFileUri=uri;requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},READ_FILES);return;}
        pendingFileUri=null;int token=++sequence;cancelPending();setBusy(true,"正在导入 PDF 并读取阅读记录…");
        importTask=imports.submit(()->{
            try{DocumentStore.Imported imported=store.importPdf(uri);
                synchronized(importLock){if(destroyed||token!=sequence){deleteUnindexed(imported);return;}uncommittedImport=imported;}
                main.post(()->{if(destroyed||token!=sequence){releaseImported(imported,true);return;}ReadingState state=store.get(imported.md5);
                if(state==null)state=new ReadingState(imported.md5,imported.name);else state.name=imported.name;
                showBook(state,imported);
            });}catch(Exception e){main.post(()->{if(destroyed||token!=sequence)return;showHome();error("无法打开 PDF，原阅读位置已保留",e);});}
        });
    }
    private void showBook(ReadingState state,DocumentStore.Imported imported){
        showBook(state,imported,true);
    }
    private void showBook(ReadingState state,DocumentStore.Imported imported,boolean preferBookmark){
        dismissDocumentDialog();saveNow();int token=++sequence;cancelPending(imported);setBusy(true,"正在读取 PDF 页面…");PdfEngine candidate=new PdfEngine();pendingEngine=candidate;
        boolean fromBookmark=preferBookmark&&state.hasBookmark();
        candidate.open(store.file(state.md5),fromBookmark?state.bookmarkPage:state.page,new PdfEngine.PageCallback(){
            @Override public void ready(int renderToken,int index,int count,int width,int height,Bitmap preview){
                if(destroyed||token!=sequence){preview.recycle();candidate.close();releaseImported(imported,true);return;}
                PdfEngine old=engine;engine=candidate;pendingEngine=null;current=state;state.page=index;state.pageCount=count;state.opened=System.currentTimeMillis();
                ready=true;setBusy(false,null);showReader();reader.setPage(engine,renderToken,index,count,width,height,preview,state,fromBookmark?1:0);if(old!=null&&old!=engine)old.close();saveNow();releaseImported(imported,false);
            }
            @Override public void error(Exception e){if(destroyed||token!=sequence)return;candidate.close();pendingEngine=null;
                releaseImported(imported,true);
                showHome();MainActivity.this.error("无法读取 PDF（可能损坏或需要密码），原阅读位置已保留",e);
            }
        });
    }
    private void cancelPending(){cancelPending(null);}
    private void cancelPending(DocumentStore.Imported keep){
        if(importTask!=null){importTask.cancel(true);importTask=null;}if(pendingEngine!=null){pendingEngine.close();pendingEngine=null;}
        synchronized(importLock){if(uncommittedImport!=null&&uncommittedImport!=keep){deleteUnindexed(uncommittedImport);uncommittedImport=null;}}
    }
    private void releaseImported(DocumentStore.Imported expected,boolean delete){
        if(expected==null)return;synchronized(importLock){if(uncommittedImport==expected){if(delete)deleteUnindexed(expected);uncommittedImport=null;}}
    }
    private void deleteUnindexed(DocumentStore.Imported imported){if(!imported.existed&&store.get(imported.md5)==null&&imported.file.exists()&&!imported.file.delete())imported.file.deleteOnExit();}
    private void dismissDocumentDialog(){if(documentDialog!=null){documentDialog.dismiss();documentDialog=null;}}
    private void showReader(){if(home!=null)home.setVisibility(View.GONE);reading.setVisibility(View.VISIBLE);title.setText(current.name);updateBookmarkIcon();chrome=true;header.setVisibility(View.VISIBLE);footer.setVisibility(View.VISIBLE);reader.requestFocus();}
    private void toggleBookmark(){
        if(current==null||busy||!ready)return;saveNow();int old=current.bookmarkPage;
        current.bookmarkPage=old==current.page?-1:current.page;
        try{store.put(current);updateBookmarkIcon();toast(current.hasBookmark()?"已设置第 "+(current.bookmarkPage+1)+" 页为唯一书签":"已取消书签，将恢复最后阅读位置");}
        catch(Exception e){current.bookmarkPage=old;updateBookmarkIcon();error("书签未能保存",e);}
        reader.requestFocus();
    }
    private void updateBookmarkIcon(){
        if(current==null)return;boolean marked=current.hasBookmark()&&current.bookmarkPage==current.page;
        bookmarkIcon.setImageResource(marked?R.drawable.ic_bookmark_filled:R.drawable.ic_bookmark_outline);bookmarkIcon.setSelected(marked);
        bookmarkIcon.setContentDescription(marked?"取消当前页书签":"设置当前页书签"+(current.hasBookmark()?"，原书签在第 "+(current.bookmarkPage+1)+" 页":""));
    }
    private void setBusy(boolean value,String message){busy=value;reader.setEnabled(!value);loading.setVisibility(value?View.VISIBLE:View.GONE);if(message!=null)loading.setText(message);
        for(int i=0;i<header.getChildCount();i++)enableTree(header.getChildAt(i),!value);enableTree(footer,!value);
    }
    private void enableTree(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof android.view.ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)enableTree(group.getChildAt(i),enabled);}}
    private void showHome(){
        dismissDocumentDialog();saveNow();sequence++;cancelPending();ready=false;current=null;reader.clear();if(engine!=null){engine.close();engine=null;}setBusy(false,null);reading.setVisibility(View.GONE);
        if(home!=null)root.removeView(home);home=column();home.setBackgroundColor(Color.WHITE);home.setPadding(dp(20),dp(20),dp(20),dp(16));
        home.addView(label("PDF 阅读器",28,true));home.addView(label("专注 PDF · 裁切与缩放 · 实体键阅读",15,false));
        home.addView(button("打开 PDF",this::pickPdf));
        if(Build.VERSION.SDK_INT<=29)home.addView(button("浏览设备文件",this::browseFiles));
        home.addView(label("最近阅读",20,true));
        ScrollView recentScroll=new ScrollView(this);LinearLayout list=column();recentScroll.addView(list);home.addView(recentScroll,new LinearLayout.LayoutParams(-1,0,1));
        List<ReadingState> recent=store.recent();
        if(recent.isEmpty())list.addView(label("选择一份 PDF 开始阅读。\n也可以在文件管理器长按 PDF，使用“其他应用打开”。",17,false));
        for(ReadingState book:recent){Button item=button(book.name+"\n第 "+(book.page+1)+" / "+book.pageCount+" 页"+(book.hasBookmark()?" · 书签第 "+(book.bookmarkPage+1)+" 页":""),()->showBook(book,null));item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);list.addView(item);
            item.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(book.name).setMessage("移除本地副本及阅读记录？来源 PDF 不会被删除。").setNegativeButton("取消",null).setPositiveButton("移除本地副本",(d,w)->{try{store.remove(book.md5);showHome();}catch(Exception e){error("移除失败",e);}}).show();return true;});}
        home.addView(label("文件导入为本地副本；记录按内容 MD5 关联，改名仍能续读。\n阅读时单击页面显示/隐藏工具栏，双指缩放，单指拖动。",14,false));
        root.addView(home,new FrameLayout.LayoutParams(-1,-1));root.bringChildToFront(loading);home.setFocusableInTouchMode(true);home.requestFocus();
    }
    private void pickPdf(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/pdf");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(intent,PICK_PDF);}catch(ActivityNotFoundException e){
            Intent fallback=new Intent(Intent.ACTION_GET_CONTENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE);
            try{startActivityForResult(fallback,PICK_PDF);}catch(ActivityNotFoundException ignored){if(Build.VERSION.SDK_INT<=29)browseFiles();else toast("系统没有 PDF 文件选择器，请从文件管理器使用“其他应用打开”。");}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_PDF&&result==RESULT_OK&&data!=null&&data.getData()!=null)openUri(data.getData());}
    private void browseFiles(){
        if(Build.VERSION.SDK_INT>29){pickPdf();return;}
        if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},READ_FILES);return;}
        browse(Environment.getExternalStorageDirectory(),Environment.getExternalStorageDirectory());
    }
    private boolean needsFilePermission(Uri uri){
        if(Build.VERSION.SDK_INT>29||!"file".equals(uri.getScheme())||checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED)return false;
        try{return !new File(uri.getPath()).getCanonicalPath().startsWith(getDataDir().getCanonicalPath()+File.separator);}catch(Exception e){return true;}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==READ_FILES){Uri pending=pendingFileUri;pendingFileUri=null;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(pending!=null)openUri(pending);else browseFiles();}else{toast("未获得读取权限，可使用系统文件选择器或支持内容授权的文件管理器打开。");if(current==null)showHome();}}}
    private void browse(File dir,File rootDirectory){
        File[] entries=dir.listFiles(f->!f.isHidden()&&(f.isDirectory()||f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")));
        if(entries==null){toast("无法读取此目录");return;}
        Arrays.sort(entries,(a,b)->a.isDirectory()!=b.isDirectory()?(a.isDirectory()?-1:1):a.getName().compareToIgnoreCase(b.getName()));
        List<File> files=new ArrayList<>(Arrays.asList(entries));boolean parent=!dir.equals(rootDirectory);List<String> names=new ArrayList<>();if(parent)names.add("↑ 上一级");for(File file:files)names.add((file.isDirectory()?"[文件夹] ":"")+file.getName());
        new AlertDialog.Builder(this).setTitle(dir.equals(rootDirectory)?"设备文件（仅 PDF）":dir.getName()).setItems(names.toArray(new String[0]),(d,which)->{
            if(parent&&which==0){browse(dir.getParentFile(),rootDirectory);return;}File chosen=files.get(which-(parent?1:0));
            if(chosen.isDirectory())browse(chosen,rootDirectory);else openUri(Uri.fromFile(chosen));
        }).setNegativeButton("取消",null).show();
    }
    void changePage(int page,int edge){
        if(!ready||busy||current==null||page<0||page>=current.pageCount)return;dismissDocumentDialog();saveNow();reader.preparePageChange();
        final int token=++sequence;final PdfEngine expected=engine;setBusy(true,"正在读取第 "+(page+1)+" 页…");
        engine.show(page,new PdfEngine.PageCallback(){
            @Override public void ready(int renderToken,int index,int count,int width,int height,Bitmap preview){
                if(destroyed||token!=sequence||engine!=expected){preview.recycle();return;}current.page=index;current.pageCount=count;
                setBusy(false,null);reader.setPage(engine,renderToken,index,count,width,height,preview,current,edge);saveNow();
            }
            @Override public void error(Exception error){if(token!=sequence||destroyed)return;showHome();MainActivity.this.error("这一页无法渲染，已保留上次阅读位置",error);}
        });
    }
    private void jumpDialog(){
        if(current==null||busy)return;dismissDocumentDialog();ReadingState owner=current;int ownerSequence=sequence;EditText page=new EditText(this);
        // Request a numeric keyboard without silently filtering pasted '-5' to
        // '5' or '1.5' to '15'; normalization must see the user's actual input.
        page.setKeyListener(android.text.method.TextKeyListener.getInstance());page.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        page.setSingleLine(true);page.setText(String.valueOf(current.page+1));page.selectAll();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("跳转页码（1–"+current.pageCount+"）").setView(page).setNegativeButton("取消",null).setPositiveButton("跳转",null).create();
        documentDialog=dialog;dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(current!=owner||sequence!=ownerSequence){dismissDocumentDialog();return;}int number=PageNumbers.normalize(page.getText().toString(),owner.pageCount);page.setText(String.valueOf(number));page.setSelection(page.length());dialog.dismiss();changePage(number-1,1);}));dialog.show();
    }
    private void cropDialog(){
        if(current==null||busy||reader.preview()==null)return;dismissDocumentDialog();ReadingState owner=current;int ownerSequence=sequence;
        Dialog dialog=new Dialog(this,R.style.FullScreenCropDialog);
        LinearLayout content=column();content.setBackgroundColor(Color.WHITE);content.setPadding(dp(12),dp(8),dp(12),dp(8));
        content.setOnApplyWindowInsetsListener((view,insets)->{
            int left=insets.getSystemWindowInsetLeft(),top=insets.getSystemWindowInsetTop(),right=insets.getSystemWindowInsetRight(),bottom=insets.getSystemWindowInsetBottom();
            if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());top=Math.max(top,cutout.getSafeInsetTop());right=Math.max(right,cutout.getSafeInsetRight());bottom=Math.max(bottom,cutout.getSafeInsetBottom());}
            view.setPadding(dp(12)+left,dp(8)+top,dp(12)+right,dp(8)+bottom);return insets;
        });
        content.addView(label("裁切整份 PDF",22,true));
        content.addView(label("拖动四条边的中心点，每个点只调整一条边。整份 PDF 共用裁切，不修改原文件。",15,false));
        CropSelectionView selection=new CropSelectionView(this,reader.preview(),current.crop);
        content.addView(selection,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=row();add(actions,button("重置",selection::reset));add(actions,button("取消",dialog::dismiss));
        add(actions,button("应用到整份 PDF",()->{if(current==owner&&sequence==ownerSequence&&!busy){current.crop=selection.crop();reader.applyCrop(current.crop);saveNow();}dialog.dismiss();}));content.addView(actions);
        dialog.setContentView(content,new ViewGroup.LayoutParams(-1,-1));documentDialog=dialog;dialog.show();
        Window window=dialog.getWindow();if(window!=null){
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        content.requestApplyInsets();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int direction=ReaderGeometry.keyDirection(event.getScanCode(),event.getKeyCode());
        if(direction!=0&&current!=null&&reading.getVisibility()==View.VISIBLE){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&!busy&&ready){long now=SystemClock.uptimeMillis();boolean repeat=event.getRepeatCount()>0;
                if(!repeat||now-lastKeyAt>=180){lastKeyAt=now;int delta=reader.keyStep(direction,repeat);if(delta!=0)changePage(current.page+delta,delta);}}
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void changed(){
        if(current==null||busy||!ready)return;zoomLabel.setText(Math.round(reader.geometry.zoom*100)+"%");status.setText((current.page+1)+" / "+current.pageCount);
        previous.setEnabled(current.page>0);next.setEnabled(current.page+1<current.pageCount);
        updateBookmarkIcon();
        main.removeCallbacks(saveLater);main.postDelayed(saveLater,500);
    }
    @Override public void tap(){if(current==null||busy)return;chrome=!chrome;header.setVisibility(chrome?View.VISIBLE:View.GONE);footer.setVisibility(chrome?View.VISIBLE:View.GONE);reader.requestFocus();}
    private void saveNow(){
        main.removeCallbacks(saveLater);if(current==null||!ready||busy||reader.preview()==null||!reader.positionReady())return;
        current.zoom=reader.geometry.zoom;current.px=reader.geometry.pageX();current.py=reader.geometry.pageY();current.crop=reader.geometry.crop;current.opened=System.currentTimeMillis();
        try{store.put(current);}catch(Exception e){toast("阅读记录未保存："+e.getMessage());}
    }
    @Override protected void onPause(){saveNow();super.onPause();}
    @Override public Object onRetainNonConfigurationInstance(){return Boolean.TRUE;}
    @Override protected void onSaveInstanceState(Bundle state){saveNow();if(current!=null)state.putString("book",current.md5);if(pendingFileUri!=null)state.putString("pendingFileUri",pendingFileUri.toString());super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){if(busy||current!=null){showHome();return;}super.onBackPressed();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(reader!=null&&level>=TRIM_MEMORY_RUNNING_LOW)reader.trim();}
    @Override protected void onDestroy(){saveNow();destroyed=true;sequence++;dismissDocumentDialog();cancelPending();main.removeCallbacksAndMessages(null);imports.shutdownNow();reader.clear();if(engine!=null)engine.close();super.onDestroy();}
    private void error(String title,Exception e){new AlertDialog.Builder(this).setTitle(title).setMessage(e.getClass().getSimpleName()+"："+e.getMessage()).setPositiveButton("知道了",null).show();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout row(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);return layout;}
    private TextView label(String text,int sp,boolean bold){TextView view=new TextView(this);view.setText(text);view.setTextColor(Color.BLACK);view.setTextSize(sp);view.setPadding(0,dp(5),0,dp(5));if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;}
    private Button button(String text,Runnable action){Button view=new Button(this);view.setText(text);view.setTextColor(Color.BLACK);view.setTextSize(15);view.setAllCaps(false);view.setMinHeight(dp(48));view.setPadding(dp(4),0,dp(4),0);view.setStateListAnimator(null);view.setOnClickListener(v->{action.run();if(current!=null)reader.requestFocus();else if(home!=null)home.requestFocus();});return view;}
    private ImageButton icon(int image,String description,Runnable action){ImageButton button=new ImageButton(this);button.setImageResource(image);button.setContentDescription(description);button.setScaleType(ImageView.ScaleType.CENTER);button.setBackgroundColor(Color.WHITE);button.setOnClickListener(v->action.run());return button;}
    private void add(LinearLayout row,View view){row.addView(view,new LinearLayout.LayoutParams(0,dp(48),1));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}

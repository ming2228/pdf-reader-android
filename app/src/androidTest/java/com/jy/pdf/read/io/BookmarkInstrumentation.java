package com.jy.pdf.read.io;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public final class BookmarkInstrumentation extends Instrumentation {
    private final List<String> checks=new ArrayList<>();
    private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private <T>T ui(Callable<T> action){AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{value.set(action.call());}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());return value.get();}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);Bundle out=new Bundle();out.putString("stream","PASS "+text+"\n");sendStatus(0,out);}
    private void waitPage(int index)throws Exception{for(int i=0;i<200;i++){if(ui(()->activity.current!=null&&activity.current.page==index&&activity.reader.isEnabled()&&activity.reader.positionReady()))return;Thread.sleep(50);}throw new AssertionError("Page did not open: "+index);}
    private void open(String name)throws Exception{getContext().startActivity(new Intent().setClassName("com.jy.pdf.read.io.test","com.jy.pdf.read.io.FixtureLauncher").putExtra("name",name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));for(int i=0;i<200;i++){if(ui(()->activity.current!=null&&activity.current.name.equals(name)&&activity.reader.isEnabled()&&activity.reader.positionReady()))return;Thread.sleep(50);}throw new AssertionError("PDF did not open: "+name);}
    private void bookmark(){ui(()->{int id=getTargetContext().getResources().getIdentifier("bookmark_toggle","id",getTargetContext().getPackageName());View icon=activity.findViewById(id);if(icon==null)throw new AssertionError("bookmark icon is missing");icon.performClick();return null;});}
    private JSONObject saved(String id)throws Exception{return new DocumentStore(getTargetContext()).get(id).json();}
    private void closeBook(){ui(()->{activity.onBackPressed();return null;});}
    private android.widget.EditText edit(View root){if(root instanceof android.widget.EditText)return (android.widget.EditText)root;if(root instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)root;for(int i=0;i<g.getChildCount();i++){android.widget.EditText e=edit(g.getChildAt(i));if(e!=null)return e;}}return null;}
    private void jump(String input,int expected)throws Exception{
        jump(input,expected,false);
    }
    private void jump(String input,int expected,boolean pasted)throws Exception{
        AtomicReference<android.widget.EditText> field=new AtomicReference<>();
        ui(()->{activity.findViewById(R.id.reader_status).performClick();return null;});waitForIdleSync();
        ui(()->{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("documentDialog");f.setAccessible(true);AlertDialog dialog=(AlertDialog)f.get(activity);android.widget.EditText value=edit(dialog.getWindow().getDecorView());if(pasted)value.getText().replace(0,value.length(),input);else value.setText(input);field.set(value);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();return null;});
        waitPage(expected-1);check(ui(()->field.get().getText().toString().equals(String.valueOf(expected))),"jump input '"+input+"' normalizes field and navigates to "+expected);
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        JSONObject legacy=new JSONObject().put("md5","0123456789abcdef0123456789abcdef").put("name","legacy.pdf").put("page",2).put("pageCount",3);
        ReadingState old=ReadingState.read(legacy);check(old.page==2&&old.json().optInt("bookmarkPage",-1)==-1,"legacy progress remains readable without bookmark");
        JSONObject marked=new JSONObject(legacy.toString()).put("bookmarkPage",1);
        check(ReadingState.read(marked).json().optInt("bookmarkPage",-1)==1,"bookmark page survives JSON roundtrip");
        activity=(MainActivity)startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        open("bookmark-test.pdf");String id=ui(()->activity.current.md5);
        ui(()->{activity.current.bookmarkPage=-1;return null;});
        ui(()->{activity.changePage(1,1);return null;});waitPage(1);bookmark();
        check(saved(id).optInt("bookmarkPage",-1)==1,"setting a bookmark persists the current page");
        ui(()->{activity.reader.zoomTo(2);activity.reader.geometry.x=180;activity.reader.geometry.y=300;activity.changePage(2,1);return null;});waitPage(2);
        check(ui(()->activity.reader.geometry.x==0&&activity.reader.geometry.y==0),"next page resets both scroll offsets");
        ui(()->{activity.reader.geometry.x=120;activity.reader.geometry.y=250;activity.changePage(1,-1);return null;});waitPage(1);
        check(ui(()->activity.reader.geometry.x==0&&activity.reader.geometry.y==0),"previous page resets both scroll offsets");
        ui(()->{activity.changePage(2,1);return null;});waitPage(2);closeBook();open("bookmark-renamed.pdf");waitPage(1);
        check(ui(()->activity.current.md5.equals(id)&&activity.reader.geometry.x==0&&activity.reader.geometry.y==0),"bookmark wins over later reading progress on renamed PDF reopen");
        ui(()->{activity.changePage(0,-1);return null;});waitPage(0);bookmark();
        check(saved(id).optInt("bookmarkPage",-1)==0,"setting a new bookmark replaces the only old bookmark");
        ui(()->{activity.changePage(2,1);return null;});waitPage(2);closeBook();open("bookmark-replaced.pdf");waitPage(0);
        check(ui(()->activity.current.page==0),"replacement bookmark is used on reopen");
        bookmark();check(saved(id).optInt("bookmarkPage",-2)==-1,"clicking the marked page clears its bookmark");
        ui(()->{activity.changePage(1,1);return null;});waitPage(1);ui(()->{activity.reader.zoomTo(2);activity.reader.geometry.x=120;activity.reader.geometry.y=400;return null;});
        double px=ui(()->activity.reader.geometry.pageX()),py=ui(()->activity.reader.geometry.pageY());closeBook();open("bookmark-cleared.pdf");waitPage(1);
        check(ui(()->Math.abs(activity.reader.geometry.pageX()-px)<.001&&Math.abs(activity.reader.geometry.pageY()-py)<.001),"no bookmark restores last page and within-page reading position");
        bookmark();open("unique-independent-bookmark.pdf");
        check(ui(()->!activity.current.md5.equals(id))&&saved(ui(()->activity.current.md5)).optInt("bookmarkPage",-1)==-1,"bookmarks are independent between PDFs");
        jump("999",3);jump("99999999999999999999999",3);jump("0",1);jump("-5",1);jump("abc",1);jump("",1);jump("-5",1,true);jump("1.5",1,true);jump("3x",1,true);
        StringBuilder longText=new StringBuilder();for(int i=0;i<4;i++)longText.append("这是一份非常非常长的PDF文件名称用于验证标题超出范围时出现省略号并保持书签按钮始终可见");String longName=longText+".pdf";
        open(longName);Thread.sleep(150);
        check(ui(()->{android.widget.TextView title=activity.findViewById(R.id.reader_title);View icon=activity.findViewById(R.id.bookmark_toggle);return title.getMaxLines()==1&&title.getLayout()!=null&&title.getLayout().getEllipsisCount(0)>0&&icon.isShown()&&icon.getWidth()>0&&icon.getRight()<=((View)icon.getParent()).getWidth();}),"long filename ellipsizes while bookmark icon remains visible");
        result.putString("stream","\nBOOKMARK_ALL_PASS "+checks.size()+" checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){StringWriter text=new StringWriter();error.printStackTrace(new PrintWriter(text));result.putString("stream","\nBOOKMARK_FAILED after "+checks.size()+" checks\n"+text);finish(Activity.RESULT_CANCELED,result);}}
}

package com.jy.pdf.read.io;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Runs as a different app UID and grants its private pipe-backed PDF to the reader. */
public final class FixtureLauncher extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);String name=getIntent().getStringExtra("name");if(name==null)name="sample.pdf";
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.jy.pdf.read.io.test.fixture/"+name),"application/pdf")
                .setClassName("com.jy.pdf.read.io","com.jy.pdf.read.io.MainActivity").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);finish();}
}

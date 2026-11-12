package com.jy.pdf.read.io;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** White floating chrome with a dashed boundary facing the document. */
final class ChromeBackground extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint background=new Paint();
    private final boolean bottomEdge;
    ChromeBackground(float density,boolean bottomEdge){
        this.bottomEdge=bottomEdge;background.setColor(Color.WHITE);paint.setColor(Color.BLACK);paint.setStrokeWidth(Math.max(1,density));
        paint.setStyle(Paint.Style.STROKE);paint.setPathEffect(new DashPathEffect(new float[]{6*density,4*density},0));
    }
    @Override public void draw(Canvas canvas){Rect bounds=getBounds();canvas.drawRect(bounds,background);float half=paint.getStrokeWidth()/2;
        float y=bottomEdge?bounds.bottom-half:bounds.top+half;canvas.drawLine(bounds.left,y,bounds.right,y,paint);}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);background.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}

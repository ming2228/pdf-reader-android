package com.jy.pdf.read.io;

final class ReaderGeometry {
    static final class Crop {
        final double left, top, right, bottom;
        Crop(double l, double t, double r, double b) { left=l; top=t; right=r; bottom=b; }
        boolean valid() { return Double.isFinite(left) && Double.isFinite(top) && Double.isFinite(right) && Double.isFinite(bottom)
                && left>=0 && top>=0 && right<=1 && bottom<=1 && right-left>=.05 && bottom-top>=.05; }
        static Crop full() { return new Crop(0,0,1,1); }
    }
    double pageW=600, pageH=800, viewW=600, viewH=800, zoom=1, x, y;
    Crop crop=Crop.full();
    double scale() { return Math.max(1,viewW)/(Math.max(1,pageW)*(crop.right-crop.left))*zoom; }
    double width() { return pageW*(crop.right-crop.left)*scale(); }
    double height() { return pageH*(crop.bottom-crop.top)*scale(); }
    double maxX() { return Math.max(0,width()-viewW); }
    double maxY() { return Math.max(0,height()-viewH); }
    void clamp() { x=bound(x,0,maxX());y=bound(y,0,maxY()); }
    void zoomTo(double value, double fx, double fy) {
        double oldScale=scale();double px=(x+fx)/oldScale,py=(y+fy)/oldScale;
        zoom=bound(value,1,4);x=px*scale()-fx;y=py*scale()-fy;clamp();
    }
    void cropTo(Crop value) { crop=value!=null && value.valid()?value:Crop.full();x=0;y=0;clamp(); }
    double pageX() { return crop.left+x/scale()/pageW; }
    double pageY() { return crop.top+y/scale()/pageH; }
    void restore(double px, double py) { x=(px-crop.left)*pageW*scale();y=(py-crop.top)*pageH*scale();clamp(); }
    int step(int direction, boolean repeat, int pageIndex, int count) {
        if(direction==0) return 0;
        if(direction<0) {
            if(y>.5) {y=Math.max(0,y-viewH*.8);return 0;}
            return !repeat && pageIndex>0?-1:0;
        }
        if(y<maxY()-.5) {y=Math.min(maxY(),y+viewH*.8);return 0;}
        return !repeat && pageIndex+1<count?1:0;
    }
    static int keyDirection(int scan, int keyCode) {
        if(scan==115) return -1;if(scan==114) return 1;
        if(keyCode==24 || keyCode==92) return -1;if(keyCode==25 || keyCode==93) return 1;return 0;
    }
    static double bound(double value, double min, double max) { return Double.isFinite(value)?Math.max(min,Math.min(max,value)):min; }
}

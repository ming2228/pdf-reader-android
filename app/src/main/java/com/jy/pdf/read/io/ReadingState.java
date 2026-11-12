package com.jy.pdf.read.io;

import org.json.JSONObject;

final class ReadingState {
    final String md5;
    String name;
    int page, pageCount;
    int bookmarkPage=-1;
    double zoom=1, px, py;
    ReaderGeometry.Crop crop=ReaderGeometry.Crop.full();
    long opened;
    ReadingState(String md5,String name) { this.md5=md5;this.name=name; }
    boolean hasBookmark(){return bookmarkPage>=0&&(pageCount<=0||bookmarkPage<pageCount);}
    int openingPage(){return hasBookmark()?bookmarkPage:page;}
    JSONObject json() throws Exception {
        JSONObject value=new JSONObject();value.put("md5",md5);value.put("name",name);value.put("page",page);value.put("pageCount",pageCount);
        value.put("bookmarkPage",hasBookmark()?bookmarkPage:-1);
        value.put("zoom",zoom);value.put("px",px);value.put("py",py);value.put("opened",opened);
        JSONObject c=new JSONObject();c.put("left",crop.left);c.put("top",crop.top);c.put("right",crop.right);c.put("bottom",crop.bottom);value.put("crop",c);return value;
    }
    static ReadingState read(JSONObject object) {
        String id=object.optString("md5","");if(!id.matches("[0-9a-f]{32}")) return null;
        ReadingState state=new ReadingState(id,object.optString("name","未命名.pdf"));
        state.page=Math.max(0,object.optInt("page",0));state.pageCount=Math.max(0,object.optInt("pageCount",0));
        state.bookmarkPage=object.optInt("bookmarkPage",-1);if(!state.hasBookmark())state.bookmarkPage=-1;
        state.zoom=ReaderGeometry.bound(object.optDouble("zoom",1),1,4);
        state.px=ReaderGeometry.bound(object.optDouble("px",0),0,1);state.py=ReaderGeometry.bound(object.optDouble("py",0),0,1);
        state.opened=object.optLong("opened",0);JSONObject c=object.optJSONObject("crop");
        if(c!=null) {ReaderGeometry.Crop crop=new ReaderGeometry.Crop(c.optDouble("left",0),c.optDouble("top",0),c.optDouble("right",1),c.optDouble("bottom",1));if(crop.valid())state.crop=crop;}
        return state;
    }
}

package com.jy.pdf.read.io;

final class PageNumbers {
    private PageNumbers() {}
    static int normalize(String input,int maximum){
        int upper=Math.max(1,maximum);if(input==null)return 1;String text=input.trim();
        if(text.isEmpty())return 1;
        for(int i=0;i<text.length();i++)if(text.charAt(i)<'0'||text.charAt(i)>'9')return 1;
        int first=0;while(first<text.length()-1&&text.charAt(first)=='0')first++;text=text.substring(first);
        String limit=String.valueOf(upper);
        if(text.length()>limit.length()||(text.length()==limit.length()&&text.compareTo(limit)>0))return upper;
        return Math.max(1,Integer.parseInt(text));
    }
}

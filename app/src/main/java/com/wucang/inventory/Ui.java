package com.wucang.inventory;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG=Color.rgb(248,249,251), WHITE=Color.WHITE, INK=Color.rgb(35,43,54),
        MUTED=Color.rgb(115,124,139), LINE=Color.rgb(232,235,239), ACCENT=Color.rgb(217,87,50),
        PALE=Color.rgb(253,239,232), GREEN=Color.rgb(44,126,96), RED=Color.rgb(190,67,64);
    private Ui() {}
    public static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    public static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static TextView text(Context c,String s,int size,int color){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
    public static TextView bold(Context c,String s,int size,int color){TextView v=text(c,s,size,color);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    public static GradientDrawable bg(int color,float radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static GradientDrawable outline(Context c,int color){GradientDrawable d=bg(color,12,c);d.setStroke(dp(c,1),LINE);return d;}
    public static void pad(View v,int l,int t,int r,int b){Context c=v.getContext();v.setPadding(dp(c,l),dp(c,t),dp(c,r),dp(c,b));}
    public static void gap(LinearLayout p,int h){p.addView(new View(p.getContext()),new LinearLayout.LayoutParams(1,dp(p.getContext(),h)));}
    public static TextView button(Context c,String title,boolean primary){TextView v=bold(c,title,15,primary?WHITE:INK);v.setGravity(Gravity.CENTER);pad(v,16,12,16,12);v.setMinHeight(dp(c,48));v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18000000),bg(primary?ACCENT:WHITE,12,c),null));v.setClickable(true);v.setFocusable(true);return v;}
    public static TextView chip(Context c,String title,boolean selected){TextView v=bold(c,title,12,selected?ACCENT:MUTED);v.setGravity(Gravity.CENTER);pad(v,13,9,13,9);v.setMinHeight(dp(c,40));v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x12000000),bg(selected?PALE:WHITE,20,c),null));v.setClickable(true);v.setFocusable(true);return v;}
    public static EditText input(Context c,String hint){EditText e=new EditText(c);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);pad(e,14,12,14,12);e.setMinHeight(dp(c,52));e.setBackground(outline(c,WHITE));return e;}
    public static void insets(Activity a,View root){
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(WHITE);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=30){
            a.getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets s=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int bottom=Math.max(s.bottom,i.getInsets(WindowInsets.Type.ime()).bottom);v.setPadding(s.left,s.top,s.right,bottom);return WindowInsets.CONSUMED;});
        } else root.setFitsSystemWindows(true);
    }
    public static View icon(Context c,String name,int color){return new Icon(c,name,color);}
    public static TextView section(Context c,String title){TextView t=bold(c,title,15,INK);pad(t,0,8,0,12);return t;}
    public static final class Icon extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String name;private final int color;
        public Icon(Context c,String name,int color){super(c);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();float side=Math.min(getWidth(),getHeight());canvas.translate((getWidth()-side)/2,(getHeight()-side)/2);canvas.scale(side/24f,side/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(name.equals("search")){canvas.drawCircle(10.5f,10.5f,6.5f,p);canvas.drawLine(15.5f,15.5f,21,21,p);}
            else if(name.equals("plus")){canvas.drawLine(12,5,12,19,p);canvas.drawLine(5,12,19,12,p);}
            else if(name.equals("back")){path(canvas,16,4,8,12,16,20);}
            else if(name.equals("export")){path(canvas,12,3,12,15);path(canvas,7,10,12,15,17,10);path(canvas,4,15,4,21,20,21,20,15);}
            else if(name.equals("camera")){path(canvas,3,7,7,7,9,4,15,4,17,7,21,7,21,20,3,20,3,7);canvas.drawCircle(12,13,4,p);}
            else if(name.equals("tag")){path(canvas,3,3,12,3,22,13,13,22,3,12,3,3);canvas.drawCircle(7.5f,7.5f,1,p);}
            else if(name.equals("grid")){canvas.drawRoundRect(3,3,10,10,1.5f,1.5f,p);canvas.drawRoundRect(14,3,21,10,1.5f,1.5f,p);canvas.drawRoundRect(3,14,10,21,1.5f,1.5f,p);canvas.drawRoundRect(14,14,21,21,1.5f,1.5f,p);}
            else if(name.equals("chart")){path(canvas,3,3,3,21,22,21);canvas.drawLine(8,16,8,11,p);canvas.drawLine(13,16,13,5,p);canvas.drawLine(18,16,18,8,p);}
            else {path(canvas,3,7,12,2,21,7,21,17,12,22,3,17,3,7,12,12,21,7);path(canvas,12,12,12,22);path(canvas,7.5f,4.5f,16.5f,9.5f);}
            canvas.restore();}
        private void path(Canvas c,float...coords){Path path=new Path();path.moveTo(coords[0],coords[1]);for(int i=2;i<coords.length;i+=2)path.lineTo(coords[i],coords[i+1]);c.drawPath(path,p);}
    }
}

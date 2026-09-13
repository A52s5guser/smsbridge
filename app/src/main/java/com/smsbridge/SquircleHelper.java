package com.smsbridge;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

public final class SquircleHelper {
    private SquircleHelper(){}

    public static float dp(Context c, float dp){ return dp * c.getResources().getDisplayMetrics().density; }

    public static void apply(View v, int fillColor, float radiusDp, float strokeDp, int strokeColor){
        if(v==null) return;
        Context c=v.getContext();
        float r=dp(c, radiusDp);
        float sw=dp(c, strokeDp);
        SquircleDrawable d=new SquircleDrawable(fillColor, r, sw, strokeColor);
        v.setBackground(d);
        v.setClipToOutline(false);
        v.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view, Outline outline){
                int w=view.getWidth(), h=view.getHeight();
                if(w<=0||h<=0) return;
                float rad=Math.min(r, Math.min(w,h)/2f);
                // Use convex path for true G2 shadow if possible, else fallback to round rect
                try{
                    android.graphics.Path p=new android.graphics.Path();
                    float c1=rad*0.55f;
                    p.moveTo(rad,0); p.lineTo(w-rad,0);
                    p.cubicTo(w-c1,0,w, c1, w, rad);
                    p.lineTo(w, h-rad); p.cubicTo(w, h-c1, w-c1, h, w-rad, h);
                    p.lineTo(rad, h); p.cubicTo(c1, h, 0, h-c1, 0, h-rad);
                    p.lineTo(0, rad); p.cubicTo(0, c1, c1, 0, rad, 0);
                    p.close();
                    if(p.isConvex()) outline.setConvexPath(p);
                    else outline.setRoundRect(0,0,w,h, rad);
                }catch(Throwable t){ outline.setRoundRect(0,0,w,h, rad); }
            }
        });
        v.setClipToOutline(false);
    }
    public static void applyAsym(View v, int fillColor, float tl, float tr, float br, float bl, float strokeDp, int strokeColor){
        if(v==null) return;
        Context c=v.getContext();
        float tldp=dp(c,tl), trdp=dp(c,tr), brdp=dp(c,br), bldp=dp(c,bl);
        SquircleDrawable d=new SquircleDrawable(fillColor, tldp, trdp, brdp, bldp, dp(c,strokeDp), strokeColor);
        v.setBackground(d);
        v.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view, Outline outline){
                int w=view.getWidth(), h=view.getHeight();
                if(w<=0||h<=0) return;
                try{
                    android.graphics.Path p=new android.graphics.Path();
                    float c1;
                    p.moveTo(tldp,0); p.lineTo(w-trdp,0);
                    c1=trdp*0.55f; p.cubicTo(w-c1,0,w, c1, w, trdp);
                    p.lineTo(w, h-brdp); c1=brdp*0.55f; p.cubicTo(w, h-c1, w-c1, h, w-brdp, h);
                    p.lineTo(bldp, h); c1=bldp*0.55f; p.cubicTo(c1, h, 0, h-c1, 0, h-bldp);
                    p.lineTo(0, tldp); c1=tldp*0.55f; p.cubicTo(0, c1, c1, 0, tldp, 0);
                    p.close();
                    if(p.isConvex()) outline.setConvexPath(p);
                    else outline.setRoundRect(0,0,w,h, Math.min(tldp, Math.min(w,h)/2f));
                }catch(Throwable t){ outline.setRoundRect(0,0,w,h, Math.min(tldp, Math.min(w,h)/2f)); }
            }
        });
    }
    public static void applyRipple(View v, int fillColor, int rippleColor, float radiusDp, float strokeDp, int strokeColor){
        if(v==null) return;
        Context c=v.getContext();
        float r=dp(c, radiusDp);
        float sw=dp(c, strokeDp);
        SquircleDrawable content=new SquircleDrawable(fillColor, r, sw, strokeColor);
        SquircleDrawable mask=new SquircleDrawable(0xFFFFFFFF, r, 0, 0);
        RippleDrawable rd=new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
        v.setBackground(rd);
        v.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view, Outline outline){
                int w=view.getWidth(), h=view.getHeight();
                if(w<=0||h<=0) return;
                float rad=Math.min(r, Math.min(w,h)/2f);
                try{
                    android.graphics.Path p=new android.graphics.Path();
                    float c1=rad*0.55f;
                    p.moveTo(rad,0); p.lineTo(w-rad,0);
                    p.cubicTo(w-c1,0,w, c1, w, rad);
                    p.lineTo(w, h-rad); p.cubicTo(w, h-c1, w-c1, h, w-rad, h);
                    p.lineTo(rad, h); p.cubicTo(c1, h, 0, h-c1, 0, h-rad);
                    p.lineTo(0, rad); p.cubicTo(0, c1, c1, 0, rad, 0);
                    p.close();
                    if(p.isConvex()) outline.setConvexPath(p);
                    else outline.setRoundRect(0,0,w,h, rad);
                }catch(Throwable t){ outline.setRoundRect(0,0,w,h, rad); }
            }
        });
    }
    public static void applyAll(Context ctx, View root){
        if(root==null) return;
        // helper to find and apply - called from MainActivity
    }
}

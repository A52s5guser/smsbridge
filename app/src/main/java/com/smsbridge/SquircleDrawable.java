package com.smsbridge;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Classic squircle (iOS continuous corner) - uses standard rounded rect
 * with matching curvature for highlighted part. No G2 aberrations.
 */
public class SquircleDrawable extends Drawable {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private float radius;
    private float tl,tr,br,bl;
    private boolean asymmetric=false;
    private float strokeWidth;
    private int strokeColor;

    public SquircleDrawable(int fillColor, float radiusPx, float strokeWidthPx, int strokeColor) {
        this.radius = radiusPx;
        this.tl=this.tr=this.br=this.bl=radiusPx;
        this.strokeWidth = strokeWidthPx;
        this.strokeColor = strokeColor;
        fillPaint.setColor(fillColor);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setColor(strokeColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidthPx);
    }

    public SquircleDrawable(int fillColor, float tlPx, float trPx, float brPx, float blPx, float strokeWidthPx, int strokeColor){
        this.radius = Math.max(Math.max(tlPx,trPx), Math.max(brPx,blPx));
        this.tl=tlPx; this.tr=trPx; this.br=brPx; this.bl=blPx; this.asymmetric=true;
        this.strokeWidth=strokeWidthPx; this.strokeColor=strokeColor;
        fillPaint.setColor(fillColor);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setColor(strokeColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidthPx);
    }
    public void setFillColor(int c){ fillPaint.setColor(c); invalidateSelf(); }
    public void setStrokeColor(int c){ strokeColor=c; strokePaint.setColor(c); invalidateSelf(); }
    public void setRadius(float r){ radius=r; tl=tr=br=bl=r; asymmetric=false; updatePath(); invalidateSelf(); }

    @Override protected void onBoundsChange(android.graphics.Rect bounds){
        super.onBoundsChange(bounds);
        rect.set(bounds);
        updatePath();
    }

    private void updatePath(){
        path.reset();
        if(rect.isEmpty()) return;
        float inset = strokeWidth/2f;
        float left = rect.left + inset;
        float top = rect.top + inset;
        float right = rect.right - inset;
        float bottom = rect.bottom - inset;
        float w = right - left;
        float h = bottom - top;
        if(w<=0||h<=0) return;
        float tlR = asymmetric ? tl : radius;
        float trR = asymmetric ? tr : radius;
        float brR = asymmetric ? br : radius;
        float blR = asymmetric ? bl : radius;
        tlR=Math.min(tlR, Math.min(w,h)/2f); trR=Math.min(trR, Math.min(w,h)/2f); brR=Math.min(brR, Math.min(w,h)/2f); blR=Math.min(blR, Math.min(w,h)/2f);
        // Classic squircle via addRoundRect - matches highlighted part curvature exactly, no aberrations
        float[] radii = new float[]{tlR,tlR, trR,trR, brR,brR, blR,blR};
        path.addRoundRect(left, top, right, bottom, radii, Path.Direction.CW);
        path.close();
    }

    @Override public void draw(Canvas canvas){
        if(fillPaint.getColor()!=0) canvas.drawPath(path, fillPaint);
        if(strokeWidth>0 && strokeColor!=0) canvas.drawPath(path, strokePaint);
    }
    @Override public void setAlpha(int alpha){ fillPaint.setAlpha(alpha); strokePaint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf){ fillPaint.setColorFilter(cf); strokePaint.setColorFilter(cf); }
    @Override public int getOpacity(){ return PixelFormat.TRANSLUCENT; }
}

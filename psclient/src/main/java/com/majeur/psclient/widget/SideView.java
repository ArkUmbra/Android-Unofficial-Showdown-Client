package com.majeur.psclient.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.Nullable;
import com.majeur.psclient.model.common.Colors;
import com.majeur.psclient.util.Utils;

import java.util.Map;

public class SideView extends View {

    private Map<String, Integer> mCurrentSides;
    private int mGravity;
    private int mWidth;

    private Paint mPaint;
    private Path mPath;
    private Rect mTempRect;
    private Canvas mMockCanvas;
    private Point mMeasurePoint;
    private int mShadowRadius;
    private int mRectRadius;
    private int mTextSize;
    private int mVerticalMargin;

    public SideView(Context context) {
        this(context, null);
    }

    public SideView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SideView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mCurrentSides = new ArrayMap<>();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        mPath = new Path();
        mTempRect = new Rect();
        mMockCanvas = new Canvas();
        mMeasurePoint = new Point();

        mShadowRadius = Utils.dpToPx(2);
        mVerticalMargin = Utils.dpToPx(4);
        mRectRadius = Utils.dpToPx(2);
        mTextSize = Utils.dpToPx(9);
    }

    public void setGravity(int gravity) {
        mGravity = gravity;
    }

    public void sideStart(String rawSide) {
        String key = rawSideToKey(rawSide);
        Integer currentCount = mCurrentSides.get(key);
        if (currentCount == null) currentCount = 0;
        mCurrentSides.put(key, currentCount + 1);
        requestLayout();
        invalidate();
    }

    public void sideEnd(String rawSide) {
        String key = rawSideToKey(rawSide);
        mCurrentSides.remove(key);
        requestLayout();
        invalidate();
    }

    public void clearAllSides() {
        mCurrentSides.clear();
        requestLayout();
        invalidate();
    }

    private String rawSideToKey(String rawSide) {
        rawSide = rawSide.trim();
        int spaceIndex = rawSide.indexOf(' ');
        if (spaceIndex >= 0)
            return (rawSide.substring(0, 1) + rawSide.substring(spaceIndex+1, spaceIndex+2))
                    .toUpperCase();
        return rawSide.substring(0, 2).toUpperCase();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mMeasurePoint.set(0, 0);
        drawContent(mMockCanvas, mMeasurePoint);
        setMeasuredDimension(mMeasurePoint.x, mMeasurePoint.y);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawContent(canvas, null);
    }

    private void drawContent(Canvas canvas, Point measurePoint) {
        boolean gravityEnd = (mGravity & Gravity.END) == Gravity.END;
        int yOffset = mShadowRadius;
        for (Map.Entry<String, Integer> entry : mCurrentSides.entrySet()) {
            mPaint.setTextSize(mTextSize);
            int count = entry.getValue();
            String countString = (count > 1 ? (gravityEnd ? "" : " ") + "x" + count + (gravityEnd ? " " : ""): "");
            String text = gravityEnd ? countString + entry.getKey() : entry.getKey() + countString;
            mPaint.getTextBounds(text, 0, text.length(), mTempRect);

            mPaint.setShadowLayer(mShadowRadius, 0, mShadowRadius / 3, Colors.BLACK);
            mPaint.setColor(Colors.sideColor(entry.getKey()));
            int w = mTempRect.width() + 2 * mRectRadius + mShadowRadius;
            int h = mTempRect.height() + 2 * mRectRadius;
            if (gravityEnd) {
                drawRoundRect(canvas, mWidth - w + mShadowRadius, yOffset, mWidth, yOffset + h, true);
                mPaint.clearShadowLayer();
                mPaint.setColor(Colors.WHITE);
                canvas.drawText(text, mWidth - w + mRectRadius + mShadowRadius, mRectRadius + yOffset + mTempRect.height(), mPaint);
            } else {
                drawRoundRect(canvas, 0, yOffset, w - mShadowRadius, yOffset + h, false);
                mPaint.clearShadowLayer();
                mPaint.setColor(Colors.WHITE);
                canvas.drawText(text, mRectRadius, mRectRadius + yOffset + mTempRect.height(), mPaint);
            }

            yOffset += h + mVerticalMargin;
            if (measurePoint != null) {
                if (measurePoint.x < w) measurePoint.x = w;
                if (measurePoint.y < yOffset) measurePoint.y = yOffset;
            }
        }
    }

    private void drawRoundRect(Canvas canvas, int left, int top, int right, int bottom, boolean leftSide) {
        mPath.reset();
        if (leftSide) {
            mPath.moveTo(right, top);
            mPath.lineTo(left + mRectRadius, top);
            mPath.quadTo(left, top, left, top + mRectRadius);
            mPath.lineTo(left, bottom - mRectRadius);
            mPath.quadTo(left, bottom, left + mRectRadius, bottom);
            mPath.lineTo(right, bottom);
            mPath.lineTo(right, top);
        } else {
            mPath.moveTo(left, top);
            mPath.lineTo(right - mRectRadius, top);
            mPath.quadTo(right, top, right, top + mRectRadius);
            mPath.lineTo(right, bottom - mRectRadius);
            mPath.quadTo(right, bottom, right - mRectRadius, bottom);
            mPath.lineTo(left, bottom);
            mPath.lineTo(left, top);
        }
        canvas.drawPath(mPath, mPaint);
    }
}

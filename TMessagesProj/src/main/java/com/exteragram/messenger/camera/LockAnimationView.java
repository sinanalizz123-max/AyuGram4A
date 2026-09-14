package com.exteragram.messenger.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class LockAnimationView extends LinearLayout {
    private float yAdd = 0;
    private boolean isLocked = false;
    private ImageView imageView;

    public LockAnimationView(Context context) {
        super(context);
        setGravity(Gravity.CENTER_HORIZONTAL);
        imageView = new ImageView(context) {
            float idleProgress;
            boolean incIdle;
            private final int lockColor = Theme.getColor(Theme.key_chat_messagePanelVoiceLock);
            private final int backgroundLockColor = Theme.getColor(Theme.key_chat_messagePanelVoiceLockBackground);
            private final Paint lockOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint backgroundCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rectF = new RectF();
            private final RectF rectF2 = new RectF();
            private final Path clipPath = new Path();
            private boolean detached;

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                detached = false;
                invalidate();
            }

            @Override
            protected void onDetachedFromWindow() {
                detached = true;
                super.onDetachedFromWindow();
            }

            @SuppressLint("DrawAllocation")
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int mHeight = Math.max(0, getMeasuredHeight());
                if (incIdle) {
                    idleProgress += 0.03f;
                    if (idleProgress > 1f) {
                        incIdle = false;
                        idleProgress = 1f;
                    }
                } else {
                    idleProgress -= 0.03f;
                    if (idleProgress < 0) {
                        incIdle = true;
                        idleProgress = 0;
                    }
                }
                if (isLocked) {
                    if (yAdd >= 0) {
                        yAdd -= 0.2f;
                        if (yAdd < 0) {
                            yAdd = 0;
                        }
                    }
                } else {
                    if (yAdd < 0) {
                        yAdd = 0;
                    } else if (yAdd > 1) {
                        yAdd = 1;
                    }
                }

                lockOutlinePaint.setStyle(Paint.Style.STROKE);
                lockOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
                lockOutlinePaint.setColor(lockColor);

                backgroundCircle.setStyle(Paint.Style.FILL);
                backgroundCircle.setColor(backgroundLockColor);

                // SIZES
                int sizeLock = AndroidUtilities.dp2(28);
                int sizeCircleBackground = Math.round(((sizeLock >> 1) * 150f) / 100f);
                int sizeLockPart = Math.round(((sizeLock - ((sizeLock * 40f) / 100f)) / 150f) * 100f);
                float strokeWidth = (sizeLockPart * 18.18f) / 100f;
                int heightWithLock = Math.max(0, Math.round(mHeight - sizeLock - strokeWidth - (sizeCircleBackground - sizeLock)));
                //ANIMATION
                float clampedY = Math.max(0f, Math.min(1f, yAdd));
                float moveProgress = 1.0f - clampedY;
                float lockRotation = 9 * (1f - moveProgress);
                // SIZES
                lockOutlinePaint.setStrokeWidth(strokeWidth);
                int totalLine = sizeLockPart >> 1;
                int sizeLine = Math.round((totalLine * 15.2f) / 100f);
                int sizeLineAnimated = Math.round((totalLine * 38f) / 100f);
                int radius = Math.round((sizeLockPart * 18f) / 100f);
                int cx = (getMeasuredWidth() >> 1) - (sizeLockPart >> 1);
                int circleY = Math.round(heightWithLock + ((sizeLock + strokeWidth) / 2f));

                rectF.set(
                        cx,
                        heightWithLock,
                        cx + sizeLockPart,
                        heightWithLock + sizeLockPart
                );
                canvas.save();
                int alpha = isLocked ? Math.max(0, Math.min(255, Math.round(255 * clampedY))) : 255;
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), alpha, Canvas.ALL_SAVE_FLAG);
                canvas.translate(0, -(AndroidUtilities.dpf2(50) / 2f - idleProgress * AndroidUtilities.dpf2(3f)));
                canvas.save();
                int startCy = Math.round(rectF.bottom);
                int sizeLockBottom = (sizeLockPart * 150) / 100;
                int sizeCircle = ((sizeLockBottom * 25) / 100) >> 1;
                int cx2 = (getMeasuredWidth() >> 1) - (sizeLockBottom >> 1);
                rectF2.set(
                        cx2,
                        startCy,
                        cx2 + sizeLockBottom,
                        startCy + sizeLockBottom
                );
                canvas.translate(0, Math.max(-((heightWithLock - sizeLock) * (1f - moveProgress)), -(heightWithLock - sizeLock - AndroidUtilities.dpf2(6f))));
                canvas.rotate(lockRotation, rectF2.centerX(), rectF2.centerY());
                canvas.drawCircle(getMeasuredWidth() >> 1, circleY, sizeCircleBackground, backgroundCircle);
                clipPath.reset();
                clipPath.addCircle(rectF2.centerX(), rectF2.centerY(), Math.max(0, sizeCircle), Path.Direction.CW);
                canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
                for (int i = 0; i < 2; i++) {
                    canvas.drawRoundRect(rectF2, radius, radius, lockOutlinePaint);
                    lockOutlinePaint.setStyle(Paint.Style.FILL);
                }
                lockOutlinePaint.setStyle(Paint.Style.STROKE);

                canvas.save();
                if (lockRotation > 0) {
                    canvas.rotate(lockRotation, rectF.centerX(), rectF.centerY());
                }
                canvas.drawArc(rectF, 0, -180, false, lockOutlinePaint);
                canvas.drawLine(
                        cx,
                        rectF.bottom - (sizeLockPart >> 1),
                        cx,
                        rectF.bottom - (sizeLockPart >> 1) + (sizeLine + sizeLineAnimated) * (1f - idleProgress) * moveProgress,
                        lockOutlinePaint
                );
                canvas.drawLine(
                        rectF.right, rectF.bottom - (sizeLockPart >> 1),
                        rectF.right, rectF.bottom - (sizeLockPart >> 1) + totalLine,
                        lockOutlinePaint
                );
                canvas.restore();
                canvas.restore();
                canvas.restore();
                if (!detached) {
                    invalidate();
                }
            }
        };
        addView(imageView, LayoutHelper.createLinear(AndroidUtilities.dp(50), LayoutHelper.MATCH_PARENT));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (imageView != null) {
            imageView.animate().cancel();
        }
    }

    public void setCurrentMove(float value) {
        if (!isLocked) {
            if (value < 0) {
                value = 0;
            } else if (value > 1) {
                value = 1;
            }
            yAdd = value;
        }
    }

    public void setLocked(boolean isLocked) {
        this.isLocked = isLocked;
    }
}

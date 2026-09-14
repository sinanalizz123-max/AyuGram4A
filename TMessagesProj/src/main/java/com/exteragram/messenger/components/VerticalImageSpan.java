/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class VerticalImageSpan extends ImageSpan {

    public VerticalImageSpan(Drawable drawable) {
        super(drawable);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fontMetricsInt) {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            return 0;
        }
        Rect rect = drawable.getBounds();
        if (fontMetricsInt != null) {
            Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
            int fontHeight = fmPaint.descent - fmPaint.ascent;
            int drHeight = rect.bottom - rect.top;
            int centerY = fmPaint.ascent + fontHeight / 2;

            fontMetricsInt.ascent = centerY - drHeight / 2;
            fontMetricsInt.top = fontMetricsInt.ascent;
            fontMetricsInt.bottom = centerY + drHeight / 2;
            fontMetricsInt.descent = fontMetricsInt.bottom;
        }
        return rect.right;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        Drawable drawable = getDrawable();
        if (drawable == null || canvas == null) {
            return;
        }
        canvas.save();
        Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
        int fontHeight = fmPaint.descent - fmPaint.ascent;
        int centerY = y + fmPaint.descent - fontHeight / 2;
        int transY = centerY - (drawable.getBounds().bottom - drawable.getBounds().top) / 2;
        canvas.translate(x, transY);
        if (LocaleController.isRTL) {
            int iw = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : drawable.getBounds().width();
            int ih = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : drawable.getBounds().height();
            canvas.scale(-1, 1, iw >> 1, ih >> 1);
        }
        drawable.draw(canvas);
        canvas.restore();
    }

    public static SpannableStringBuilder createSpan(Context context, int resId, String text, String replace, int color) {
        return createSpan(context, resId, text, replace, color, null);
    }

    public static SpannableStringBuilder createSpan(Context context, int resId, String text, String replace, int color, Theme.ResourcesProvider resourcesProvider) {
        if (text == null) {
            text = "";
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        if (replace == null || replace.isEmpty() || context == null) {
            return builder;
        }
        List<Integer> beginIndexes = new ArrayList<>();
        int index = text.indexOf(replace);
        while (index >= 0) {
            beginIndexes.add(index);
            if (index + 1 >= text.length()) {
                break;
            }
            index = text.indexOf(replace, index + 1);
        }
        Drawable drawable;
        try {
            drawable = context.getDrawable(resId);
        } catch (Exception e) {
            return builder;
        }
        if (drawable == null) {
            return builder;
        }
        int iw = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : AndroidUtilities.dp(20);
        int ih = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : AndroidUtilities.dp(20);
        drawable.setBounds(0, 0, iw, ih);
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(color, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        if (!beginIndexes.isEmpty()) {
            for (int begin : beginIndexes) {
                builder.setSpan(new VerticalImageSpan(drawable), begin, begin + replace.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }
}

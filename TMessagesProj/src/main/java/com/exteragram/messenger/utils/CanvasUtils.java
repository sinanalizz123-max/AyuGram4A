/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RoundRectShape;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;

import java.util.Objects;

public class CanvasUtils {

    public static Drawable createFabBackground() {
        return createFabBackground(false);
    }

    public static Drawable createFabBackground(boolean altColor) {
        int r = AndroidUtilities.dp(ExteraConfig.squareFab ? 16 : 100);
        int c = Theme.getColor(altColor ? Theme.key_dialogFloatingButton : Theme.key_chats_actionBackground);
        int pc = Theme.getColor(altColor ? Theme.key_dialogFloatingButtonPressed : Theme.key_chats_actionPressedBackground);
        return Theme.createSimpleSelectorRoundRectDrawable(r, c, pc);
    }

    public static CombinedDrawable createCircleDrawableWithIcon(Context context, int iconRes, int size) {
        try {
            Drawable drawable = null;
            if (iconRes != 0 && context != null) {
                try {
                    drawable = ContextCompat.getDrawable(context, iconRes);
                } catch (Exception e) {
                    drawable = null;
                }
                if (drawable != null) {
                    drawable = drawable.mutate();
                }
            }
            OvalShape ovalShape = new OvalShape();
            ovalShape.resize(size, size);
            ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
            Paint paint = defaultDrawable.getPaint();
            paint.setColor(0xffffffff);
            CombinedDrawable combinedDrawable = new CombinedDrawable(defaultDrawable, drawable);
            combinedDrawable.setCustomSize(size, size);
            return combinedDrawable;
        } catch (Exception e) {
            return null;
        }
    }

    public static CombinedDrawable createRoundRectDrawableWithIcon(int size, int rad, int iconRes) {
        try {
            ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
            defaultDrawable.getPaint().setColor(0xffffffff);
            Drawable drawable = null;
            if (iconRes != 0 && ApplicationLoader.applicationContext != null) {
                try {
                    drawable = ApplicationLoader.applicationContext.getResources().getDrawable(iconRes);
                } catch (Exception e) {
                    drawable = null;
                }
                if (drawable != null) {
                    try {
                        drawable = drawable.mutate();
                    } catch (Exception e) {
                        drawable = null;
                    }
                }
            }
            CombinedDrawable combinedDrawable = new CombinedDrawable(defaultDrawable, drawable);
            combinedDrawable.setCustomSize(size, size);
            return combinedDrawable;
        } catch (Exception e) {
            return null;
        }
    }
}

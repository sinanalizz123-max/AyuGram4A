/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.ArrayList;
import java.util.List;

public class DoubleTapCell extends LinearLayout {

    private final RectF rect = new RectF();
    private final Rect rect1 = new Rect();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] circleOutlinePaint = new Paint[]{new Paint(Paint.ANTI_ALIAS_FLAG), new Paint(Paint.ANTI_ALIAS_FLAG)};
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Theme.MessageDrawable[] messages = new Theme.MessageDrawable[]{
            new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, false),
            new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, false)
    };

    private static final int[] doubleTapIcons = new int[]{
            R.drawable.msg_block,
            R.drawable.msg_reactions,
            R.drawable.msg_reply,
            R.drawable.msg_copy,
            R.drawable.msg_forward,
            R.drawable.msg_edit,
            R.drawable.msg_saved,
            R.drawable.msg_delete
    };

    private static final int[] ICON_WIDTH = new int[]{AndroidUtilities.dp(12), AndroidUtilities.dp(12)};

    private final ValueAnimator[] animator = new ValueAnimator[2];
    private final ValueAnimator[] circleAnimator = new ValueAnimator[4];
    private final ValueAnimator[] circleSizeAnimator = new ValueAnimator[4];
    private final float[] circleSizeProgress = new float[4];
    private final float[] iconChangingProgress = new float[2];
    private final float[] circleProgress = new float[4];
    private final int[] actionIcon = new int[2];

    private final FrameLayout preview;

    public DoubleTapCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(10));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
        outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(1f)));

        doubleTapIcons[1] = ExteraConfig.useSolarIcons ? R.drawable.msg_reactions : R.drawable.msg_saved_14;

        preview = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                int color = Theme.getColor(Theme.key_switchTrack);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                float stroke = outlinePaint.getStrokeWidth() / 2;
                fillPaint.setColor(Color.argb(20, r, g, b));

                canvas.save();
                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        rect.set(stroke + AndroidUtilities.dp(8), stroke + AndroidUtilities.dp(10), getMeasuredWidth() / 2 - AndroidUtilities.dp(8) - stroke, AndroidUtilities.dp(75) - stroke);
                    } else {
                        canvas.translate(0, AndroidUtilities.dp(80));
                        rect.set(stroke + getMeasuredWidth() / 2 + AndroidUtilities.dp(8), stroke + AndroidUtilities.dp(5), getMeasuredWidth() - AndroidUtilities.dp(8) - stroke, AndroidUtilities.dp(70) - stroke);
                    }
                    rect.round(rect1);
                    messages[i].setBounds(rect1);
                    messages[i].draw(canvas, fillPaint);
                    messages[i].draw(canvas, outlinePaint);

                    for (int j = 0; j < 2; j++) {
                        circleOutlinePaint[j].setStyle(Paint.Style.STROKE);
                        circleOutlinePaint[j].setColor(ColorUtils.blendARGB(0x00, Color.argb(76, r, g, b), circleProgress[i + 2 * j]));
                        circleOutlinePaint[j].setStrokeWidth(AndroidUtilities.dp(1.5f) * circleProgress[i + 2 * j] * circleProgress[i + 2 * j]);
                        canvas.drawCircle((i == 0 ? 1 : 3) * getMeasuredWidth() / 4, getMeasuredHeight() / 4 + AndroidUtilities.dpf2(i == 0 ? 3f : -2f), AndroidUtilities.dp(25 - 6 * j) * circleSizeProgress[i + 2 * j], circleOutlinePaint[j]);
                    }

                    Drawable icon = null;
                    try {
                        Drawable d = ContextCompat.getDrawable(context, actionIcon[i]);
                        if (d != null) {
                            icon = d.mutate();
                        }
                    } catch (Exception ignored) {
                    }
                    if (icon == null) {
                        continue;
                    }
                    if (i == 0)
                        icon.setBounds(getMeasuredWidth() / 4 - ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 - ICON_WIDTH[i] + AndroidUtilities.dpf2(3f)), getMeasuredWidth() / 4 + ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 + ICON_WIDTH[i] + AndroidUtilities.dpf2(3f)));
                    else
                        icon.setBounds(3 * getMeasuredWidth() / 4 - ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 - ICON_WIDTH[i] - AndroidUtilities.dpf2(2f)), 3 * getMeasuredWidth() / 4 + ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 + ICON_WIDTH[i] - AndroidUtilities.dpf2(2f)));

                    icon.setBounds(
                            icon.getBounds().left - AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().top - AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().right + AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().bottom + AndroidUtilities.dp(4 - 4 * iconChangingProgress[i])
                    );
                    icon.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_chats_menuItemIcon), iconChangingProgress[i]), PorterDuff.Mode.MULTIPLY));
                    icon.draw(canvas);
                }
                canvas.restore();
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateIcons(0, false);
    }

    @SuppressLint("Recycle")
    public void updateIcons(int inv, boolean animate) {
        for (int i = 0; i < 2; i++) {
            if (i == 0 && inv == 2 || i == 1 && inv == 1) continue;
            if (animator[i] != null) {
                animator[i].cancel();
                animator[i] = null;
            }
            if (animate) {
                int finalI = i;
                for (int j = 0; j < 2; j++) {
                    int finalJ = j;
                    int slot = finalI * 2 + finalJ;
                    if (circleSizeAnimator[slot] != null) {
                        circleSizeAnimator[slot].cancel();
                    }
                    if (circleAnimator[slot] != null) {
                        circleAnimator[slot].cancel();
                    }
                    circleSizeAnimator[slot] = ValueAnimator.ofFloat(0f, 1f).setDuration(1300);
                    circleSizeAnimator[slot].setStartDelay(j * 60L);
                    circleSizeAnimator[slot].setInterpolator(Easings.easeInOutQuad);
                    circleSizeAnimator[slot].addUpdateListener(animation -> {
                        circleSizeProgress[finalJ * 2 + finalI] = (Float) animation.getAnimatedValue();
                        invalidate();
                    });

                    circleAnimator[slot] = ValueAnimator.ofFloat(0f, 1f).setDuration(700);
                    circleAnimator[slot].setStartDelay(150 + j * 80L);
                    circleAnimator[slot].setInterpolator(Easings.easeInOutQuad);
                    circleAnimator[slot].addUpdateListener(animation -> {
                        circleProgress[finalJ * 2 + finalI] = (Float) animation.getAnimatedValue();
                        invalidate();
                    });
                    circleAnimator[slot].addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            ValueAnimator a = circleAnimator[finalI * 2 + finalJ];
                            if (a == null || a != animation) {
                                return;
                            }
                            a.setFloatValues(1f, 0f);
                            a.setDuration(700);
                            a.removeAllListeners();
                            a.start();
                        }
                    });
                    circleSizeAnimator[slot].start();
                    circleAnimator[slot].start();
                }

                animator[i] = ValueAnimator.ofFloat(1f, 0f).setDuration(250);
                animator[i].setInterpolator(Easings.easeInOutQuad);
                animator[i].addUpdateListener(animation -> {
                    iconChangingProgress[finalI] = (Float) animation.getAnimatedValue();
                    invalidate();
                });
                animator[i].addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        actionIcon[finalI] = finalI == 0 ? doubleTapIcons[ExteraConfig.doubleTapAction] : doubleTapIcons[ExteraConfig.doubleTapActionOutOwner];
                        animator[finalI].setFloatValues(0f, 1f);
                        animator[finalI].removeAllListeners();
                        animator[finalI].addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            }
                        });
                        animator[finalI].start();
                    }
                });
                animator[i].start();
            } else {
                circleSizeProgress[i] = 0f;
                circleProgress[i] = 0f;
                iconChangingProgress[i] = 1f;
                actionIcon[i] = i == 0 ? doubleTapIcons[ExteraConfig.doubleTapAction] : doubleTapIcons[ExteraConfig.doubleTapActionOutOwner];
                invalidate();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int i = 0; i < animator.length; i++) {
            if (animator[i] != null) {
                animator[i].cancel();
                animator[i] = null;
            }
        }
        for (int i = 0; i < circleAnimator.length; i++) {
            if (circleAnimator[i] != null) {
                circleAnimator[i].cancel();
                circleAnimator[i] = null;
            }
            if (circleSizeAnimator[i] != null) {
                circleSizeAnimator[i].cancel();
                circleSizeAnimator[i] = null;
            }
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (preview != null) {
            preview.invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!ExteraConfig.disableDividers)
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(170), MeasureSpec.EXACTLY));
    }

    public static class SetReactionCell extends FrameLayout {

        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable imageDrawable;

        public SetReactionCell(Context context) {
            super(context);

            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            TextView textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setText(LocaleController.getString("DoubleTapSetting", R.string.DoubleTapSetting));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, 48, 0));

            imageDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(24));
        }

        public void update(boolean animated) {
            String reactionString = MediaDataController.getInstance(UserConfig.selectedAccount).getDoubleTapReaction();
            if (reactionString != null && reactionString.startsWith("animated_")) {
                try {
                    long documentId = Long.parseLong(reactionString.substring(9));
                    imageDrawable.set(documentId, animated);
                    return;
                } catch (Exception ignore) {
                }
            }
            TLRPC.TL_availableReaction reaction = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(reactionString);
            if (reaction != null) {
                imageDrawable.set(reaction.static_icon, animated);
            }
        }

        public void updateImageBounds() {
            imageDrawable.setBounds(
                    getWidth() - imageDrawable.getIntrinsicWidth() - AndroidUtilities.dp(21),
                    (getHeight() - imageDrawable.getIntrinsicHeight()) / 2,
                    getWidth() - AndroidUtilities.dp(21),
                    (getHeight() + imageDrawable.getIntrinsicHeight()) / 2
            );
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            updateImageBounds();
            imageDrawable.draw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageDrawable.detach();
            if (selectAnimatedEmojiDialog != null) {
                try {
                    selectAnimatedEmojiDialog.dismiss();
                } catch (Exception ignored) {
                }
                selectAnimatedEmojiDialog = null;
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageDrawable.attach();
        }

        private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;

        public int getDialogHeight() {
            if (selectAnimatedEmojiDialog == null) {
                return 0;
            }
            return selectAnimatedEmojiDialog.getHeight();
        }

        public static void showSelectStatusDialog(SetReactionCell cell, BaseFragment fragment) {
            if (fragment == null || fragment.getContext() == null || cell == null || cell.getParent() == null) {
                return;
            }
            if (cell.selectAnimatedEmojiDialog != null) {
                return;
            }
            final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
            int xoff = 0, yoff = 0;
            AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrimDrawable = null;
            View scrimDrawableParent = null;
            if (cell != null) {
                scrimDrawable = cell.imageDrawable;
                scrimDrawableParent = cell;
                if (cell.imageDrawable != null) {
                    cell.imageDrawable.play();
                    cell.updateImageBounds();
                    AndroidUtilities.rectTmp2.set(cell.imageDrawable.getBounds());
                    yoff = -(cell.getHeight() - AndroidUtilities.rectTmp2.centerY()) - AndroidUtilities.dp(16);
                    int popupWidth = (int) Math.min(AndroidUtilities.dp(340 - 16), AndroidUtilities.displaySize.x * .95f);
                    xoff = AndroidUtilities.rectTmp2.centerX() - (AndroidUtilities.displaySize.x - popupWidth);
                }
            }
            SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(fragment, fragment.getContext(), false, xoff, SelectAnimatedEmojiDialog.TYPE_SET_DEFAULT_REACTION, null) {
                @Override
                protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, Integer until) {
                    if (documentId == null) {
                        return;
                    }
                    MediaDataController.getInstance(UserConfig.selectedAccount).setDoubleTapReaction("animated_" + documentId);
                    if (cell != null) {
                        cell.update(true);
                    }
                    if (popup[0] != null) {
                        cell.selectAnimatedEmojiDialog = null;
                        popup[0].dismiss();
                    }
                }

                @Override
                protected void onReactionClick(ImageViewEmoji emoji, ReactionsLayoutInBubble.VisibleReaction reaction) {
                    MediaDataController.getInstance(UserConfig.selectedAccount).setDoubleTapReaction(reaction.emojicon);
                    if (cell != null) {
                        cell.update(true);
                    }
                    if (popup[0] != null) {
                        cell.selectAnimatedEmojiDialog = null;
                        popup[0].dismiss();
                    }
                }
            };
            String selectedReaction = MediaDataController.getInstance(UserConfig.selectedAccount).getDoubleTapReaction();
            if (selectedReaction != null && selectedReaction.startsWith("animated_")) {
                try {
                    popupLayout.setSelected(Long.parseLong(selectedReaction.substring(9)));
                } catch (Exception ignored) {
                }
            }
            List<TLRPC.TL_availableReaction> availableReactions = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsList();
            ArrayList<ReactionsLayoutInBubble.VisibleReaction> reactions = new ArrayList<>(20);
            for (int i = 0; i < availableReactions.size(); ++i) {
                ReactionsLayoutInBubble.VisibleReaction reaction = new ReactionsLayoutInBubble.VisibleReaction();
                TLRPC.TL_availableReaction tlreaction = availableReactions.get(i);
                reaction.emojicon = tlreaction.reaction;
                reactions.add(reaction);
            }
            popupLayout.setRecentReactions(reactions);
            popupLayout.setSaveState(3);
            popupLayout.setScrimDrawable(scrimDrawable, scrimDrawableParent);
            popup[0] = cell.selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    cell.selectAnimatedEmojiDialog = null;
                }
            };
            if (cell.getParent() == null) {
                cell.selectAnimatedEmojiDialog = null;
                return;
            }
            try {
                popup[0].showAsDropDown(cell, 0, yoff, Gravity.TOP | Gravity.RIGHT);
            } catch (Exception e) {
                cell.selectAnimatedEmojiDialog = null;
                return;
            }
            popup[0].dimBehind();
        }
    }
}

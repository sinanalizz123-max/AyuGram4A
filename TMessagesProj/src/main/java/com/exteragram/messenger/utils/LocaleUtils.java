/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.View;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.URLSpanNoUnderline;

public class LocaleUtils {
    public static String getActionBarTitle() {
        try {
            String title;
            int actionBarTitle = ExteraConfig.titleText;
            switch (actionBarTitle) {
                case 0:
                    title = LocaleController.getString("exteraAppName", R.string.exteraAppName);
                    break;
                case 3:
                    title = LocaleController.getString(R.string.FilterChats);
                    break;
                default:
                    var config = UserConfig.getInstance(UserConfig.selectedAccount);
                    TLRPC.User user = config != null ? config.getCurrentUser() : null;
                    if (user == null) {
                        title = LocaleController.getString("exteraAppName", R.string.exteraAppName);
                    } else {
                        title = actionBarTitle == 1 && !TextUtils.isEmpty(UserObject.getPublicUsername(user)) ? UserObject.getPublicUsername(user) : UserObject.getFirstName(user);
                    }
                    break;
            }
            return title;
        } catch (Exception e) {
            FileLog.e(e);
            return "AyuGram";
        }
    }

    public static CharSequence formatWithUsernames(String text, BaseFragment fragment) {
        if (text == null) {
            return null;
        }
        if (fragment == null) {
            return text;
        }
        int start = -1, end;
        boolean parse = false;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '@') {
                start = i;
                parse = true;
            }
            if (parse && (i + 1 == text.length() || (!Character.isAlphabetic(text.charAt(i + 1)) && !Character.isDigit(text.charAt(i + 1))))) {
                end = i + 1;
                parse = false;
                String username = text.substring(start, end);
                try {
                    URLSpanNoUnderline urlSpan = new URLSpanNoUnderline(username) {
                        @Override
                        public void onClick(View widget) {
                            try {
                                var controller = ChatUtils.getMessagesController();
                                if (controller != null && username != null && username.length() > 1) {
                                    controller.openByUserName(username.substring(1), fragment, 1);
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    };
                    stringBuilder.setSpan(urlSpan, start, end, 0);
                    if (i + 1 == text.length()) {
                        return stringBuilder;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    return text;
                }
            }
        }
        return text;
    }

    public static CharSequence formatWithURLs(CharSequence charSequence) {
        if (charSequence == null) {
            return null;
        }
        try {
            Spannable spannable = new SpannableString(charSequence);
            URLSpan[] spans = spannable.getSpans(0, charSequence.length(), URLSpan.class);
            if (spans == null) {
                return spannable;
            }
            for (URLSpan urlSpan : spans) {
                if (urlSpan == null) {
                    continue;
                }
                URLSpan span = urlSpan;
                int start = spannable.getSpanStart(span), end = spannable.getSpanEnd(span);
                spannable.removeSpan(span);
                String url = span.getURL();
                if (url == null) {
                    continue;
                }
                span = new URLSpanNoUnderline(url) {
                    @Override
                    public void onClick(View widget) {
                        super.onClick(widget);
                    }
                };
                spannable.setSpan(span, start, end, 0);
            }
            return spannable;
        } catch (Exception e) {
            FileLog.e(e);
            return charSequence;
        }
    }

    public static String capitalize(String s) {
        if (s == null)
            return null;
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i == 0) {
                chars[i] = Character.toUpperCase(chars[i]);
            } else if (Character.isLetter(chars[i])) {
                chars[i] = Character.toLowerCase(chars[i]);
            }
        }
        return new String(chars);
    }

    public static String getAppName() {
        try {
            return ApplicationLoader.applicationContext.getString(R.string.exteraAppName);
        } catch (Exception e) {
            return "AyuGram";
        }
    }
}

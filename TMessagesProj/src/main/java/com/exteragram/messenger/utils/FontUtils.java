//  @Nekogram

package com.exteragram.messenger.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

public class FontUtils {

    private static volatile String TEST_TEXT = null;
    private static final Object testTextSync = new Object();
    private static String getTestText() {
        String t = TEST_TEXT;
        if (t == null) {
            synchronized (testTextSync) {
                t = TEST_TEXT;
                if (t == null) {
                    try {
                        var controller = LocaleController.getInstance();
                        var locale = controller != null ? controller.getCurrentLocale() : null;
                        if (locale != null && List.of("zh", "ja", "ko").contains(locale.getLanguage())) {
                            t = "日";
                        } else {
                            t = "R";
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                        t = "R";
                    }
                    TEST_TEXT = t;
                }
            }
        }
        return t;
    }
    private static int getCanvasSize() {
        try {
            int s = AndroidUtilities.dp(12);
            return s > 0 ? s : 12;
        } catch (Exception e) {
            FileLog.e(e);
            return 12;
        }
    }
    private static final Paint PAINT = new Paint() {{
        setTextSize(12);
        setAntiAlias(false);
        setSubpixelText(false);
        setFakeBoldText(false);
    }};

    private static final Object mediumLock = new Object();
    private static final Object italicLock = new Object();
    private static volatile Boolean mediumWeightSupported = null;
    private static volatile Boolean italicSupported = null;

    public static boolean loadSystemEmojiFailed = false;
    private static volatile Typeface systemEmojiTypeface;

    public static boolean isMediumWeightSupported() {
        Boolean v = mediumWeightSupported;
        if (v == null) {
            synchronized (mediumLock) {
                v = mediumWeightSupported;
                if (v == null) {
                    try {
                        v = testTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    } catch (Exception e) {
                        FileLog.e(e);
                        v = false;
                    }
                    FileLog.d("mediumWeightSupported = " + v);
                    mediumWeightSupported = v;
                }
            }
        }
        return v != null && v;
    }

    public static boolean isItalicSupported() {
        Boolean v = italicSupported;
        if (v == null) {
            synchronized (italicLock) {
                v = italicSupported;
                if (v == null) {
                    try {
                        v = testTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                    } catch (Exception e) {
                        FileLog.e(e);
                        v = false;
                    }
                    FileLog.d("italicSupported = " + v);
                    italicSupported = v;
                }
            }
        }
        return v != null && v;
    }

    private static boolean testTypeface(Typeface typeface) {
        int canvasSize = getCanvasSize();
        if (canvasSize <= 0) {
            return false;
        }
        String testText = getTestText();
        if (testText == null) {
            return false;
        }
        Bitmap bitmap1 = null;
        Bitmap bitmap2 = null;
        try {
            Canvas canvas = new Canvas();
            bitmap1 = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ALPHA_8);
            if (bitmap1 == null) {
                return false;
            }
            canvas.setBitmap(bitmap1);
            synchronized (PAINT) {
                PAINT.setTextSize(canvasSize);
                PAINT.setTypeface(null);
                canvas.drawText(testText, 0, canvasSize, PAINT);
                bitmap2 = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ALPHA_8);
                if (bitmap2 == null) {
                    return false;
                }
                canvas.setBitmap(bitmap2);
                PAINT.setTypeface(typeface);
                canvas.drawText(testText, 0, canvasSize, PAINT);
            }
            return !bitmap1.sameAs(bitmap2);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        } finally {
            try {
                if (bitmap1 != null) {
                    bitmap1.recycle();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                if (bitmap2 != null) {
                    bitmap2.recycle();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static File getSystemEmojiFontPath() {
        try (var br = new BufferedReader(new FileReader("/system/etc/fonts.xml"))) {
            String line;
            var ignored = false;
            while ((line = br.readLine()) != null) {
                var trimmed = line.trim();
                if (trimmed.startsWith("<family") && trimmed.contains("ignore=\"true\"")) {
                    ignored = true;
                } else if (trimmed.startsWith("</family>")) {
                    ignored = false;
                } else if (trimmed.startsWith("<font") && !ignored) {
                    var start = trimmed.indexOf(">");
                    var end = trimmed.indexOf("<", 1);
                    if (start > 0 && end > 0) {
                        var font = trimmed.substring(start + 1, end);
                        if (font.toLowerCase().contains("emoji")) {
                            File file = new File("/system/fonts/" + font);
                            if (file.exists()) {
                                FileLog.d("emoji font file fonts.xml = " + font);
                                return file;
                            }
                        }
                    }
                }
            }
            br.close();

            var fileAOSP = new File("/system/fonts/NotoColorEmoji.ttf");
            if (fileAOSP.exists()) {
                return fileAOSP;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static Typeface getSystemEmojiTypeface() {
        if (!loadSystemEmojiFailed && systemEmojiTypeface == null) {
            synchronized (FontUtils.class) {
                if (!loadSystemEmojiFailed && systemEmojiTypeface == null) {
                    try {
                        var font = getSystemEmojiFontPath();
                        if (font != null) {
                            try {
                                systemEmojiTypeface = Typeface.createFromFile(font);
                            } catch (Exception | Error e) {
                                FileLog.e(e);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (systemEmojiTypeface == null) {
                        loadSystemEmojiFailed = true;
                    }
                }
            }
        }
        return systemEmojiTypeface;
    }
}


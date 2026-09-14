/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.camera.video.Quality;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.camera.CameraXUtils;
import com.exteragram.messenger.preferences.components.CameraTypeSelector;
import com.exteragram.messenger.utils.AyuDownloadEngine;
import com.exteragram.messenger.utils.LocaleUtils;
import com.exteragram.messenger.utils.PopupUtils;
import com.radolyn.ayugram.download.AyuDownloadSpeedTest;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class GeneralPreferencesActivity extends BasePreferencesActivity {

    private final CharSequence[] tabletMode = new CharSequence[]{
            LocaleController.getString("DistanceUnitsAutomatic", R.string.DistanceUnitsAutomatic),
            LocaleController.getString("PasswordOn", R.string.PasswordOn),
            LocaleController.getString("PasswordOff", R.string.PasswordOff)
    }, id = new CharSequence[]{
            LocaleController.getString("Hide", R.string.Hide),
            "Telegram API",
            "Bot API"
    };

    private int cameraTypeHeaderRow;
    private int cameraTypeSelectorRow;
    private int cameraXOptimizeRow;
    private int cameraXQualityRow;
    private int cameraTypeDividerRow;

    private int speedBoostersHeaderRow;
    private int downloadSpeedChooserRow;
    private int uploadSpeedBoostRow;
    private int speedBoostersDividerRow;

    private int dlAccelHeaderRow;
    private int dlEnabledRow;
    private int dlModeRow;
    private int dlWifiRow;
    private int dlMobileRow;
    private int dlMaxSimRow;
    private int dlPerFileRow;
    private int dlTotalRow;
    private int dlSpeedTestRow;
    private int dlHintRow;

    private AyuDownloadSpeedTest activeSpeedTest;
    private AlertDialog speedTestDialog;

    private int generalHeaderRow;
    private int formatTimeWithSecondsRow;
    private int disableNumberRoundingRow;
    private int tabletModeRow;
    private int generalDividerRow;

    private int profileHeaderRow;
    private int showIdAndDcRow;
    private int hidePhoneNumberRow;
    private int profileDividerRow;

    private int archiveHeaderRow;
    private int archiveOnPullRow;
    private int disableUnarchiveSwipeRow;
    private int archiveDividerRow;

    @Override
    protected void updateRowsId() {
        super.updateRowsId();

        cameraTypeHeaderRow = -1;
        cameraTypeSelectorRow = -1;
        cameraXOptimizeRow = -1;
        cameraXQualityRow = -1;
        cameraTypeDividerRow = -1;

        if (CameraXUtils.isCameraXSupported()) {
            cameraTypeHeaderRow = newRow();
            cameraTypeSelectorRow = newRow();
            if (ExteraConfig.cameraType == 1) {
                cameraXOptimizeRow = newRow();
                cameraXQualityRow = newRow();
            }
            cameraTypeDividerRow = newRow();
        }

        generalHeaderRow = newRow();
        disableNumberRoundingRow = newRow();
        formatTimeWithSecondsRow = newRow();
        tabletModeRow = newRow();
        generalDividerRow = newRow();

        speedBoostersHeaderRow = newRow();
        downloadSpeedChooserRow = newRow();
        uploadSpeedBoostRow = newRow();
        speedBoostersDividerRow = newRow();

        dlAccelHeaderRow = newRow();
        dlEnabledRow = newRow();
        dlModeRow = newRow();
        dlWifiRow = newRow();
        dlMobileRow = newRow();
        dlMaxSimRow = -1;
        dlPerFileRow = -1;
        dlTotalRow = -1;
        if (AyuDownloadEngine.getMode() == AyuDownloadEngine.MODE_CUSTOM) {
            dlMaxSimRow = newRow();
            dlPerFileRow = newRow();
            dlTotalRow = newRow();
        }
        dlSpeedTestRow = newRow();
        dlHintRow = newRow();

        profileHeaderRow = newRow();
        hidePhoneNumberRow = newRow();
        showIdAndDcRow = newRow();
        profileDividerRow = newRow();

        archiveHeaderRow = newRow();
        archiveOnPullRow = newRow();
        disableUnarchiveSwipeRow = newRow();
        archiveDividerRow = newRow();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == disableNumberRoundingRow) {
            ExteraConfig.editor.putBoolean("disableNumberRounding", ExteraConfig.disableNumberRounding ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableNumberRounding);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == formatTimeWithSecondsRow) {
            ExteraConfig.editor.putBoolean("formatTimeWithSeconds", ExteraConfig.formatTimeWithSeconds ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.formatTimeWithSeconds);
            LocaleController.getInstance().recreateFormatters();
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == tabletModeRow) {
            if (getParentActivity() == null) {
                return;
            }
            int tabletSelected = ExteraConfig.tabletMode >= 0 && ExteraConfig.tabletMode < tabletMode.length ? ExteraConfig.tabletMode : 0;
            PopupUtils.showDialog(tabletMode, LocaleController.getString("TabletMode", R.string.TabletMode), tabletSelected, getContext(), i -> {
                ExteraConfig.editor.putInt("tabletMode", ExteraConfig.tabletMode = i).apply();
                listAdapter.notifyItemChanged(tabletModeRow, payload);
                showBulletin();
            });
        } else if (position == archiveOnPullRow) {
            ExteraConfig.editor.putBoolean("archiveOnPull", ExteraConfig.archiveOnPull ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.archiveOnPull);
        } else if (position == disableUnarchiveSwipeRow) {
            ExteraConfig.editor.putBoolean("disableUnarchiveSwipe", ExteraConfig.disableUnarchiveSwipe ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableUnarchiveSwipe);
        } else if (position == hidePhoneNumberRow) {
            ExteraConfig.editor.putBoolean("hidePhoneNumber", ExteraConfig.hidePhoneNumber ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hidePhoneNumber);
            parentLayout.rebuildAllFragmentViews(false, false);
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
        } else if (position == showIdAndDcRow) {
            if (getParentActivity() == null) {
                return;
            }
            int idSelected = ExteraConfig.showIdAndDc >= 0 && ExteraConfig.showIdAndDc < id.length ? ExteraConfig.showIdAndDc : 0;
            PopupUtils.showDialog(id, LocaleController.getString("ShowIdAndDc", R.string.ShowIdAndDc), idSelected, getContext(), i -> {
                ExteraConfig.editor.putInt("showIdAndDc", ExteraConfig.showIdAndDc = i).apply();
                parentLayout.rebuildAllFragmentViews(false, false);
                listAdapter.notifyItemChanged(showIdAndDcRow, payload);
            });
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == uploadSpeedBoostRow) {
            ExteraConfig.editor.putBoolean("uploadSpeedBoost", ExteraConfig.uploadSpeedBoost ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.uploadSpeedBoost);
        } else if (position == dlEnabledRow) {
            AyuDownloadEngine.setEnabled(!AyuDownloadEngine.isEnabled());
            ((TextCheckCell) view).setChecked(AyuDownloadEngine.isEnabled());
        } else if (position == dlModeRow) {
            if (getParentActivity() == null) {
                return;
            }
            PopupUtils.showDialog(AyuDownloadSpeedTest.MODE_NAMES, "Download Acceleration", AyuDownloadEngine.getMode(), getContext(), i -> {
                if (i < 0 || i >= AyuDownloadSpeedTest.MODE_NAMES.length) {
                    return;
                }
                AyuDownloadEngine.setMode(i);
                refreshDlCustomRows();
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(dlModeRow, payload);
                }
            });
        } else if (position == dlWifiRow) {
            AyuDownloadEngine.setWifiAcceleration(!AyuDownloadEngine.isWifiAcceleration());
            ((TextCheckCell) view).setChecked(AyuDownloadEngine.isWifiAcceleration());
        } else if (position == dlMobileRow) {
            AyuDownloadEngine.setMobileAcceleration(!AyuDownloadEngine.isMobileAcceleration());
            ((TextCheckCell) view).setChecked(AyuDownloadEngine.isMobileAcceleration());
        } else if (position == dlMaxSimRow) {
            if (getParentActivity() == null) {
                return;
            }
            PopupUtils.showDialog(numberItems(1, 8), "Max Simultaneous", Math.min(7, Math.max(0, AyuDownloadEngine.getCustomMaxSimultaneous() - 1)), getContext(), i -> {
                AyuDownloadEngine.setCustomMaxSimultaneous(1 + i);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(dlMaxSimRow, payload);
                }
            });
        } else if (position == dlPerFileRow) {
            if (getParentActivity() == null) {
                return;
            }
            PopupUtils.showDialog(numberItems(2, 16), "Connections Per File", Math.min(14, Math.max(0, AyuDownloadEngine.getCustomConnectionsPerDownload() - 2)), getContext(), i -> {
                AyuDownloadEngine.setCustomConnectionsPerDownload(2 + i);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(dlPerFileRow, payload);
                }
            });
        } else if (position == dlTotalRow) {
            if (getParentActivity() == null) {
                return;
            }
            PopupUtils.showDialog(numberItems(4, 32), "Total Connections", Math.min(28, Math.max(0, AyuDownloadEngine.getCustomMaxTotalConnections() - 4)), getContext(), i -> {
                AyuDownloadEngine.setCustomMaxTotalConnections(4 + i);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(dlTotalRow, payload);
                }
            });
        } else if (position == dlSpeedTestRow) {
            runSpeedTest();
        } else if (position == cameraXOptimizeRow) {
            ExteraConfig.editor.putBoolean("useCameraXOptimizedMode", ExteraConfig.useCameraXOptimizedMode ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.useCameraXOptimizedMode);
        } else if (position == cameraXQualityRow) {
            Map<Quality, Size> availableSizes = CameraXUtils.getAvailableVideoSizes();
            if (availableSizes == null || availableSizes.isEmpty()) {
                return;
            }
            ArrayList<Integer> types = new ArrayList<>();
            for (Size s : availableSizes.values()) {
                if (s != null) {
                    types.add(s.getHeight());
                }
            }
            if (types.isEmpty()) {
                return;
            }
            Collections.sort(types, Collections.reverseOrder());
            ArrayList<String> arrayList = new ArrayList<>(types.size());
            for (Integer p : types) {
                arrayList.add(p + "p");
            }
            int selected = types.indexOf(ExteraConfig.cameraResolution);
            if (selected < 0) {
                selected = 0;
            }
            PopupUtils.showDialog(arrayList, LocaleController.getString("CameraQuality", R.string.CameraQuality), selected, getContext(), i -> {
                if (i < 0 || i >= types.size()) {
                    return;
                }
                ExteraConfig.editor.putInt("cameraResolution", ExteraConfig.cameraResolution = types.get(i)).apply();
                listAdapter.notifyItemChanged(cameraXQualityRow, payload);
            });
        }
    }

    private static CharSequence[] numberItems(int min, int max) {
        if (max < min) {
            return new CharSequence[]{"0"};
        }
        CharSequence[] items = new CharSequence[max - min + 1];
        for (int i = min; i <= max; i++) {
            items[i - min] = String.valueOf(i);
        }
        return items;
    }

    private void refreshDlCustomRows() {
        boolean wantCustom = AyuDownloadEngine.getMode() == AyuDownloadEngine.MODE_CUSTOM;
        boolean hasCustom = dlMaxSimRow != -1;
        if (wantCustom == hasCustom || listAdapter == null) {
            if (listAdapter == null) {
                updateRowsId();
            }
            return;
        }
        if (wantCustom) {
            updateRowsId();
            if (dlMaxSimRow != -1) {
                listAdapter.notifyItemRangeInserted(dlMaxSimRow, 3);
            }
        } else {
            int oldRow = dlMaxSimRow;
            updateRowsId();
            if (oldRow != -1) {
                listAdapter.notifyItemRangeRemoved(oldRow, 3);
            }
        }
    }

    private void runSpeedTest() {
        if (getParentActivity() == null || activeSpeedTest != null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Speed Test");
        builder.setMessage("Starting...");
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> cancelSpeedTest());
        builder.setOnCancelListener(dialog -> cancelSpeedTest());
        speedTestDialog = builder.show();
        AyuDownloadSpeedTest test = new AyuDownloadSpeedTest(AyuDownloadSpeedTest.resolveAccount(currentAccount));
        activeSpeedTest = test;
        test.start(new AyuDownloadSpeedTest.Listener() {
            @Override
            public void onProgress(String status) {
                if (speedTestDialog != null && status != null) {
                    try {
                        speedTestDialog.setMessage(status);
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onFinished(AyuDownloadSpeedTest.Result result) {
                if (activeSpeedTest != test) {
                    return;
                }
                activeSpeedTest = null;
                try {
                    if (speedTestDialog != null) {
                        speedTestDialog.dismiss();
                    }
                } catch (Exception ignored) {
                }
                speedTestDialog = null;
                if (getParentActivity() == null || result == null) {
                    return;
                }
                AlertDialog.Builder resultBuilder = new AlertDialog.Builder(getParentActivity());
                resultBuilder.setTitle("Speed Test");
                resultBuilder.setMessage(AyuDownloadSpeedTest.buildResultText(result));
                resultBuilder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                try {
                    resultBuilder.show();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void cancelSpeedTest() {
        if (activeSpeedTest != null) {
            try {
                activeSpeedTest.cancel();
            } catch (Exception ignored) {
            }
            activeSpeedTest = null;
        }
        if (speedTestDialog != null) {
            try {
                speedTestDialog.dismiss();
            } catch (Exception ignored) {
            }
            speedTestDialog = null;
        }
    }

    @Override
    public void onFragmentDestroy() {
        cancelSpeedTest();
        super.onFragmentDestroy();
    }

    @Override
    protected String getTitle() {
        return LocaleController.getString("General", R.string.General);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == 17) {
                CameraTypeSelector cameraTypeSelector = new CameraTypeSelector(mContext) {
                    @Override
                    protected void onSelectedCamera(int cameraSelected) {
                        super.onSelectedCamera(cameraSelected);
                        int oldValue = ExteraConfig.cameraType;
                        ExteraConfig.editor.putInt("cameraType", ExteraConfig.cameraType = cameraSelected).apply();
                        if (listAdapter == null) {
                            updateRowsId();
                        } else if (cameraSelected == 1) {
                            updateRowsId();
                            if (cameraXOptimizeRow != -1) {
                                listAdapter.notifyItemRangeInserted(cameraXOptimizeRow, 2);
                            }
                            if (cameraTypeDividerRow != -1) {
                                listAdapter.notifyItemChanged(cameraTypeDividerRow);
                            }
                        } else if (oldValue == 1) {
                            int oldOptimizeRow = cameraXOptimizeRow;
                            updateRowsId();
                            if (oldOptimizeRow != -1) {
                                listAdapter.notifyItemRangeRemoved(oldOptimizeRow, 2);
                            }
                            if (cameraTypeDividerRow != -1) {
                                listAdapter.notifyItemChanged(cameraTypeDividerRow);
                            }
                        } else {
                            listAdapter.notifyItemChanged(cameraTypeDividerRow);
                        }
                    }
                };
                cameraTypeSelector.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(cameraTypeSelector);
            }
            return super.onCreateViewHolder(parent, type);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case 1:
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == generalHeaderRow) {
                        headerCell.setText(LocaleController.getString("General", R.string.General));
                    } else if (position == archiveHeaderRow) {
                        headerCell.setText(LocaleController.getString("ArchivedChats", R.string.ArchivedChats));
                    } else if (position == profileHeaderRow) {
                        headerCell.setText(LocaleController.getString("Profile", R.string.Profile));
                    } else if (position == speedBoostersHeaderRow) {
                        headerCell.setText(LocaleController.getString("DownloadSpeedBoost", R.string.DownloadSpeedBoost));
                    } else if (position == dlAccelHeaderRow) {
                        headerCell.setText("Download Acceleration");
                    } else if (position == cameraTypeHeaderRow) {
                        headerCell.setText(LocaleController.getString("CameraType", R.string.CameraType));
                    }
                    break;
                case 5:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setEnabled(true, null);
                    if (position == disableNumberRoundingRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("DisableNumberRounding", R.string.DisableNumberRounding), "1.23K -> 1,234", ExteraConfig.disableNumberRounding, true, true);
                    } else if (position == formatTimeWithSecondsRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("FormatTimeWithSeconds", R.string.FormatTimeWithSeconds), "12:34 -> 12:34:56", ExteraConfig.formatTimeWithSeconds, true, true);
                    } else if (position == disableUnarchiveSwipeRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableUnarchiveSwipe", R.string.DisableUnarchiveSwipe), ExteraConfig.disableUnarchiveSwipe, false);
                    } else if (position == archiveOnPullRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("ArchiveOnPull", R.string.ArchiveOnPull), ExteraConfig.archiveOnPull, true);
                    } else if (position == hidePhoneNumberRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HidePhoneNumber", R.string.HidePhoneNumber), ExteraConfig.hidePhoneNumber, true);
                    } else if (position == uploadSpeedBoostRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("UploadSpeedBoost", R.string.UploadSpeedBoost), ExteraConfig.uploadSpeedBoost, false);
                    } else if (position == dlWifiRow) {
                        textCheckCell.setTextAndCheck("Accelerate on Wi-Fi", AyuDownloadEngine.isWifiAcceleration(), true);
                    } else if (position == dlMobileRow) {
                        textCheckCell.setTextAndCheck("Accelerate on Mobile Data", AyuDownloadEngine.isMobileAcceleration(), false);
                    } else if (position == dlEnabledRow) {
                        textCheckCell.setTextAndCheck("Enable Acceleration", AyuDownloadEngine.isEnabled(), true);
                    } else if (position == cameraXOptimizeRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("PerformanceMode", R.string.PerformanceMode), LocaleController.getString("PerformanceModeInfo", R.string.PerformanceModeInfo), ExteraConfig.useCameraXOptimizedMode, true, true);
                    }
                    break;
                case 7:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    if (position == cameraXQualityRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("CameraQuality", R.string.CameraQuality), ExteraConfig.cameraResolution + "p", payload, false);
                    } else if (position == tabletModeRow) {
                        int tabletIdx = ExteraConfig.tabletMode >= 0 && ExteraConfig.tabletMode < tabletMode.length ? ExteraConfig.tabletMode : 0;
                        textSettingsCell.setTextAndValue(LocaleController.getString("TabletMode", R.string.TabletMode), tabletMode[tabletIdx], payload, false);
                    } else if (position == showIdAndDcRow) {
                        int idIdx = ExteraConfig.showIdAndDc >= 0 && ExteraConfig.showIdAndDc < id.length ? ExteraConfig.showIdAndDc : 0;
                        textSettingsCell.setTextAndValue(LocaleController.getString("ShowIdAndDc", R.string.ShowIdAndDc), id[idIdx], payload, false);
                    } else if (position == dlModeRow) {
                        textSettingsCell.setTextAndValue("Mode", AyuDownloadSpeedTest.getModeName(AyuDownloadEngine.getMode()), payload, true);
                    } else if (position == dlMaxSimRow) {
                        textSettingsCell.setTextAndValue("Max Simultaneous", String.valueOf(AyuDownloadEngine.getCustomMaxSimultaneous()), payload, true);
                    } else if (position == dlPerFileRow) {
                        textSettingsCell.setTextAndValue("Connections Per File", String.valueOf(AyuDownloadEngine.getCustomConnectionsPerDownload()), payload, true);
                    } else if (position == dlTotalRow) {
                        textSettingsCell.setTextAndValue("Total Connections", String.valueOf(AyuDownloadEngine.getCustomMaxTotalConnections()), payload, true);
                    } else if (position == dlSpeedTestRow) {
                        textSettingsCell.setTextAndValue("Speed Test", "Run", payload, false);
                    }
                    break;
                case 8:
                    TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == cameraTypeDividerRow) {
                        String advise;
                        switch (ExteraConfig.cameraType) {
                            case 0:
                                advise = LocaleController.getString("DefaultCameraInfo", R.string.DefaultCameraInfo);
                                break;
                            case 1:
                                advise = LocaleController.getString("CameraXInfo", R.string.CameraXInfo);
                                break;
                            case 2:
                            default:
                                advise = LocaleController.getString("SystemCameraInfo", R.string.SystemCameraInfo);
                                break;
                        }
                        Spannable htmlParsed;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            htmlParsed = new SpannableString(Html.fromHtml(advise, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            htmlParsed = new SpannableString(Html.fromHtml(advise));
                        }
                        textInfoPrivacyCell.setText(LocaleUtils.formatWithURLs(htmlParsed));
                    } else if (position == speedBoostersDividerRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("SpeedBoostInfo", R.string.SpeedBoostInfo));
                    } else if (position == dlHintRow) {
                        textInfoPrivacyCell.setText("Custom limits apply in Custom mode. While acceleration is active, live speed and peak appear in message details.");
                    } else if (position == profileDividerRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("ShowIdAndDcInfo", R.string.ShowIdAndDcInfo));
                    } else if (position == archiveDividerRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("DisableUnarchiveSwipeInfo", R.string.DisableUnarchiveSwipeInfo));
                    }
                    break;
                case 13:
                    SlideChooseView slide = (SlideChooseView) holder.itemView;
                    if (position == downloadSpeedChooserRow) {
                        slide.setNeedDivider(true);
                        slide.setCallback(index -> ExteraConfig.editor.putInt("downloadSpeedBoost", ExteraConfig.downloadSpeedBoost = index).apply());
                        slide.setOptions(ExteraConfig.downloadSpeedBoost, LocaleController.getString("BlurOff", R.string.BlurOff), LocaleController.getString("SpeedFast", R.string.SpeedFast), LocaleController.getString("Ultra", R.string.Ultra));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == generalDividerRow) {
                return 1;
            } else if (position == generalHeaderRow || position == archiveHeaderRow || position == profileHeaderRow ||
                    position == speedBoostersHeaderRow || position == cameraTypeHeaderRow || position == dlAccelHeaderRow) {
                return 3;
            } else if (position == cameraXQualityRow || position == tabletModeRow || position == showIdAndDcRow ||
                    position == dlModeRow || position == dlMaxSimRow || position == dlPerFileRow || position == dlTotalRow || position == dlSpeedTestRow) {
                return 7;
            } else if (position == cameraTypeDividerRow || position == speedBoostersDividerRow || position == profileDividerRow  || position == archiveDividerRow || position == dlHintRow) {
                return 8;
            } else if (position == downloadSpeedChooserRow) {
                return 13;
            } else if (position == cameraTypeSelectorRow) {
                return 17;
            }
            return 5;
        }
    }
}

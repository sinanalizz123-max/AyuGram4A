package com.exteragram.messenger.camera;

import static android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;
import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CameraXUtils {

    private static Map<Quality, Size> qualityToSize;
    private static Exception qualityException;
    private static int cameraResolution = -1;

    public static boolean isCameraXSupported() {
        return SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE;
    }

    public static synchronized boolean isWideAngleAvailable(ProcessCameraProvider provider) {
        if (provider == null) {
            return false;
        }
        try {
            return getWideCameraId(provider) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    public static CameraSelector getDefaultWideAngleCamera(ProcessCameraProvider provider) {
        String wideCamera = getWideCameraId(provider);
        if (wideCamera != null) {
            return new CameraSelector.Builder().addCameraFilter(cameraInfo -> {
                List<CameraInfo> cameraFiltered = new ArrayList<>();
                for (int i = 0; i < cameraInfo.size(); i++) {
                    CameraInfo c = cameraInfo.get(i);
                    String id = Camera2CameraInfo.from(c).getCameraId();
                    if (id.equals(wideCamera)) {
                        cameraFiltered.add(c);
                    }
                }
                return cameraFiltered;
            }).build();
        }
        throw new IllegalArgumentException("This device doesn't support wide camera! "
                + "isWideAngleAvailable should be checked first before calling "
                + "getDefaultWideAngleCamera.");
    }

    public static synchronized Map<Quality, Size> getAvailableVideoSizes() throws IllegalStateException {
        if (qualityException != null) {
            throw new IllegalStateException("CameraX sizes failed to load!", qualityException);
        }
        return qualityToSize == null ? new HashMap<>() : qualityToSize;
    }

    public static synchronized void loadCameraXSizes() {
        if (qualityToSize != null || qualityException != null) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        ListenableFuture<ProcessCameraProvider> providerFtr = ProcessCameraProvider.getInstance(context);
        providerFtr.addListener(() -> {
            ProcessCameraProvider provider = null;
            try {
                CameraSelector.Builder cameraBuilder = new CameraSelector.Builder();
                provider = providerFtr.get();
                CameraSelector camera = cameraBuilder.build();
                Map<Quality, Size> sizes = getAvailableVideoSizes(camera, provider);
                synchronized (CameraXUtils.class) {
                    qualityToSize = sizes;
                }
                loadSuggestedResolution();
            } catch (Exception e) {
                synchronized (CameraXUtils.class) {
                    qualityException = e;
                }
            } finally {
                if (provider != null) {
                    try {
                        provider.unbindAll();
                    } catch (Exception ignored) {
                    }
                }
            }
        }, Runnable::run);
    }

    private static Map<Quality, Size> getAvailableVideoSizes(CameraSelector cameraSelector, ProcessCameraProvider provider) {
        return cameraSelector.filter(provider.getAvailableCameraInfos()).parallelStream()
                .findFirst()
                .map(camInfo ->
                        QualitySelector.getSupportedQualities(camInfo).parallelStream().collect(
                                Collectors.toMap(
                                        Function.identity(),
                                        quality -> Optional.ofNullable(QualitySelector.getResolution(camInfo, quality))
                                                .orElse(new Size(0, 0))
                                )
                        )
                ).orElse(new HashMap<>());
    }

    public static synchronized void loadSuggestedResolution() {
        int suggestedRes = getSuggestedResolution(false);
        Map<Quality, Size> sizes;
        try {
            sizes = getAvailableVideoSizes();
        } catch (IllegalStateException e) {
            return;
        }
        if (sizes == null || sizes.isEmpty()) {
            return;
        }

        int min = sizes.values().parallelStream()
                .mapToInt(Size::getHeight)
                .min().orElse(0);

        int max = sizes.values().parallelStream()
                .mapToInt(Size::getHeight)
                .max().orElse(0);

        try {
            getAvailableVideoSizes().values().parallelStream()
                    .sorted(Comparator.comparingInt(Size::getHeight).reversed())
                    .mapToInt(Size::getHeight)
                    .filter(height -> height <= suggestedRes)
                    .findFirst()
                    .ifPresent(height -> {
                        cameraResolution = height;
                        if (ExteraConfig.cameraResolution == -1 || ExteraConfig.cameraResolution > max || ExteraConfig.cameraResolution < min) {
                            ExteraConfig.cameraResolution = height;
                            if (ExteraConfig.editor != null) {
                                ExteraConfig.editor.putInt("cameraResolution", height).apply();
                            }
                        }
                    });
        } catch (IllegalStateException ignored) {
        }
    }

    public static synchronized int getCameraResolution() {
        return cameraResolution;
    }

    public static synchronized Size getPreviewBestSize() {
        int suggestedRes = getSuggestedResolution(true);
        try {
            return getAvailableVideoSizes().values().parallelStream()
                    .filter(size -> size != null && size.getHeight() <= ExteraConfig.cameraResolution && size.getHeight() <= suggestedRes)
                    .max(Comparator.comparingInt(Size::getHeight))
                    .orElse(new Size(0, 0));
        } catch (IllegalStateException e) {
            return new Size(0, 0);
        }
    }

    public static synchronized Quality getVideoQuality() {
        try {
            return getAvailableVideoSizes().entrySet().parallelStream()
                    .filter(entry -> entry != null && entry.getValue() != null && entry.getValue().getHeight() == ExteraConfig.cameraResolution)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(Quality.HIGHEST);
        } catch (IllegalStateException e) {
            return Quality.HIGHEST;
        }
    }

    private static int getSuggestedResolution(boolean isPreview) {
        int suggestedRes;
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                suggestedRes = 720;
                break;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                suggestedRes = 1080;
                break;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
            default:
                suggestedRes = ExteraConfig.useCameraXOptimizedMode && isPreview ? 1080 : 2160;
                break;
        }
        return suggestedRes;
    }

    @SuppressLint({"RestrictedApi", "UnsafeOptInUsageError"})
    public static synchronized String getWideCameraId(ProcessCameraProvider provider) {
        if (provider == null) {
            return null;
        }
        float lowestAngledCamera = Integer.MAX_VALUE;
        List<CameraInfo> cameraInfoList;
        try {
            cameraInfoList = provider.getAvailableCameraInfos();
        } catch (Exception e) {
            return null;
        }
        if (cameraInfoList == null) {
            return null;
        }
        String cameraId = null;
        int availableBackCamera = 0;
        boolean foundWideAngleOnPrimaryCamera = false;
        for (int i = 0; i < cameraInfoList.size(); i++) {
            CameraInfo cameraInfo = cameraInfoList.get(i);
            String id = Camera2CameraInfo.from(cameraInfo).getCameraId();
            CameraCharacteristics cameraCharacteristics = Camera2CameraInfo.from(cameraInfo).getCameraCharacteristicsMap().get(id);
            if (cameraCharacteristics != null) {
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == LENS_FACING_BACK) {
                    availableBackCamera++;
                    ZoomState zoomState = cameraInfo.getZoomState().getValue();
                    if (zoomState != null && zoomState.getMinZoomRatio() < 1.0F && zoomState.getMinZoomRatio() > 0) {
                        foundWideAngleOnPrimaryCamera = true;
                    }
                    float[] listLensAngle = cameraCharacteristics.get(LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (listLensAngle != null && listLensAngle.length > 0) {
                        if (listLensAngle[0] < 3.0f && listLensAngle[0] < lowestAngledCamera) {
                            lowestAngledCamera = listLensAngle[0];
                            cameraId = id;
                        }
                    }
                }
            }
        }
        return availableBackCamera >= 2 && !foundWideAngleOnPrimaryCamera ? cameraId : null;
    }
}

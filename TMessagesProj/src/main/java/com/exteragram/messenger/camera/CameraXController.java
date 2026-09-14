package com.exteragram.messenger.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Range;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.core.impl.utils.Exif;
import androidx.camera.core.internal.compat.workaround.ExifRotationAvailability;
import androidx.camera.extensions.ExtensionMode;
import androidx.camera.extensions.ExtensionsManager;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.exteragram.messenger.ExteraConfig;
import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.Size;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class CameraXController {

    private boolean isFrontface;
    private boolean isInitiated = false;
    private final CameraLifecycle lifecycle;
    private ProcessCameraProvider provider;
    private static Camera camera;
    private CameraSelector cameraSelector;
    private CameraXView.VideoSavedCallback videoSavedCallback;
    private boolean abandonCurrentVideo = false;
    private ImageCapture iCapture;
    private Preview previewUseCase;
    private VideoCapture<Recorder> vCapture;
    private Recording recording;
    private final MeteringPointFactory meteringPointFactory;
    private final Preview.SurfaceProvider surfaceProvider;
    private ExtensionsManager extensionsManager;
    private ListenableFuture<ProcessCameraProvider> providerFuture;
    private ListenableFuture<ExtensionsManager> extensionsFuture;
    private boolean stableFPSPreviewOnly = false;
    private boolean noSupportedSurfaceCombinationWorkaround = false;
    public static final int CAMERA_NONE = 0;
    public static final int CAMERA_NIGHT = 1;
    public static final int CAMERA_HDR = 2;
    public static final int CAMERA_AUTO = 3;
    public static final int CAMERA_WIDE = 4;
    public float oldZoomSelection = 0F;
    private int selectedEffect = CAMERA_NONE;

    public static class CameraLifecycle implements LifecycleOwner {

        private final LifecycleRegistry lifecycleRegistry;

        public CameraLifecycle() {
            lifecycleRegistry = new LifecycleRegistry(this);
            lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        }

        public void start() {
            try {
                lifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
            } catch (IllegalStateException ignored) {
            }
        }

        public void stop() {
            try {
                lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
            } catch (IllegalStateException ignored) {
            }
        }

        @NonNull
        public Lifecycle getLifecycle() {
            return lifecycleRegistry;
        }

    }

    public CameraXController(CameraLifecycle lifecycle, MeteringPointFactory factory, Preview.SurfaceProvider surfaceProvider) {
        this.lifecycle = lifecycle;
        this.meteringPointFactory = factory;
        this.surfaceProvider = surfaceProvider;
    }

    public boolean isInitied() {
        return isInitiated;
    }

    public boolean setFrontFace(boolean isFrontFace) {
        return this.isFrontface = isFrontFace;
    }

    public boolean isFrontface() {
        return isFrontface;
    }

    public void setStableFPSPreviewOnly(boolean isEnabled) {
        stableFPSPreviewOnly = isEnabled;
    }

    public void initCamera(Context context, boolean isInitialFrontface, Runnable onPreInit) {
        this.isFrontface = isInitialFrontface;
        ListenableFuture<ProcessCameraProvider> providerFtr = ProcessCameraProvider.getInstance(context);
        providerFuture = providerFtr;
        providerFtr.addListener(
                () -> {
                    try {
                        provider = providerFtr.get();
                        ListenableFuture<ExtensionsManager> extensionFuture = ExtensionsManager.getInstanceAsync(context, provider);
                        extensionsFuture = extensionFuture;
                        extensionFuture.addListener(() -> {
                            try {
                                extensionsManager = extensionFuture.get();
                                bindUseCases();
                                lifecycle.start();
                                if (onPreInit != null) {
                                    onPreInit.run();
                                }
                                isInitiated = true;
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                e.printStackTrace();
                            }
                        }, ContextCompat.getMainExecutor(context));
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                }, ContextCompat.getMainExecutor(context)
        );
    }

    public void setCameraEffect(@EffectFacing int effect) {
        selectedEffect = effect;
        bindUseCases();
    }

    public int getCameraEffect() {
        return selectedEffect;
    }

    public void switchCamera() {
        isFrontface ^= true;
        bindUseCases();
    }

    public void closeCamera() {
        lifecycle.stop();
        if (providerFuture != null) {
            providerFuture.cancel(true);
        }
        if (extensionsFuture != null) {
            extensionsFuture.cancel(true);
        }
        if (provider != null) {
            try {
                provider.unbindAll();
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressLint("RestrictedApi")
    public boolean hasFrontFaceCamera() {
        if (provider == null) {
            return false;
        }
        try {
            return provider.hasCamera(
                    new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build());
        } catch (CameraInfoUnavailableException e) {
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    public static boolean hasGoodCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private int getNextFlashMode(int legacyMode) {
        switch (legacyMode) {
            case ImageCapture.FLASH_MODE_AUTO:
                return ImageCapture.FLASH_MODE_ON;
            case ImageCapture.FLASH_MODE_ON:
                return ImageCapture.FLASH_MODE_OFF;
            default:
                return ImageCapture.FLASH_MODE_AUTO;
        }
    }

    public int setNextFlashMode() {
        if (iCapture == null) {
            return ImageCapture.FLASH_MODE_OFF;
        }
        int next = getNextFlashMode(iCapture.getFlashMode());
        iCapture.setFlashMode(next);
        return next;
    }

    public int getCurrentFlashMode() {
        if (iCapture == null) {
            return ImageCapture.FLASH_MODE_OFF;
        }
        return iCapture.getFlashMode();
    }

    public static boolean isFlashAvailable() {
        if (camera == null) {
            return false;
        }
        try {
            return camera.getCameraInfo().hasFlashUnit();
        } catch (Exception e) {
            return false;
        }
    }

    public static void setTorchEnabled(boolean enabled) {
        if (camera == null) {
            return;
        }
        if (isFlashAvailable()) {
            try {
                camera.getCameraControl().enableTorch(enabled);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isAvailableHdrMode() {
        if (extensionsManager != null) {
            return extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.HDR);
        } else {
            return false;
        }
    }

    public boolean isAvailableNightMode() {
        if (extensionsManager != null) {
            return extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.NIGHT);
        } else {
            return false;
        }
    }

    public boolean isAvailableWideMode() {
        if (provider != null) {
            return CameraXUtils.isWideAngleAvailable(provider);
        } else {
            return false;
        }
    }

    public boolean isAvailableAutoMode() {
        if (extensionsManager != null) {
            return extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.AUTO);
        } else {
            return false;
        }
    }

    public android.util.Size getVideoBestSize() {
        int w, h;
        android.util.Size size = CameraXUtils.getPreviewBestSize();
        w = size.getWidth();
        h = size.getHeight();
        if ((getDisplayOrientation() == 0 || getDisplayOrientation() == 180) && getDeviceDefaultOrientation() == Configuration.ORIENTATION_PORTRAIT) {
            return new android.util.Size(h, w);
        } else {
            return new android.util.Size(w, h);
        }
    }

    @SuppressLint({"RestrictedApi", "UnsafeExperimentalUsageError", "UnsafeOptInUsageError"})
    public synchronized void bindUseCases() {
        if (provider == null) return;
        android.util.Size targetSize = getVideoBestSize();
        Preview.Builder previewBuilder = new Preview.Builder();
        previewBuilder.setTargetResolution(targetSize);
        if (!isFrontface && selectedEffect == CAMERA_WIDE) {
            try {
                cameraSelector = CameraXUtils.getDefaultWideAngleCamera(provider);
            } catch (IllegalArgumentException e) {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            }
        } else {
            cameraSelector = isFrontface ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        }

        if (!isFrontface && extensionsManager != null) {
            switch (selectedEffect) {
                case CAMERA_NIGHT:
                    cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, ExtensionMode.NIGHT);
                    break;
                case CAMERA_HDR:
                    cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, ExtensionMode.HDR);
                    break;
                case CAMERA_AUTO:
                    cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, ExtensionMode.AUTO);
                    break;
                default:
                    cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, ExtensionMode.NONE);
                    break;
            }
        }

        Quality quality = CameraXUtils.getVideoQuality();
        QualitySelector selector = QualitySelector.from(quality, FallbackStrategy.higherQualityOrLowerThan(quality));
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(selector)
                .build();
        vCapture = VideoCapture.withOutput(recorder);

        ImageCapture.Builder iCaptureBuilder = new ImageCapture.Builder()
                .setCaptureMode(ExteraConfig.useCameraXOptimizedMode ? ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG : ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_16_9);

        provider.unbindAll();
        previewUseCase = previewBuilder.build();
        previewUseCase.setSurfaceProvider(surfaceProvider);

        if (lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
        if (stableFPSPreviewOnly) {
            camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, vCapture);
        } else {
            iCapture = iCaptureBuilder.build();
            try {
                camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, vCapture, iCapture);
                noSupportedSurfaceCombinationWorkaround = false;
            } catch (IllegalArgumentException e) {
                noSupportedSurfaceCombinationWorkaround = true;
                try {
                    camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, iCapture);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (camera != null) {
            camera.getCameraControl().setLinearZoom(oldZoomSelection);
        }
    }

    public void setZoom(float value) {
        if (camera == null) {
            return;
        }
        if (value < 0f) {
            value = 0f;
        } else if (value > 1f) {
            value = 1f;
        }
        camera.getCameraControl().setLinearZoom(oldZoomSelection = value);
    }

    public float resetZoom() {
        if (camera != null) {
            camera.getCameraControl().setZoomRatio(1.0f);
            ZoomState zoomStateLiveData = camera.getCameraInfo().getZoomState().getValue();
            if (zoomStateLiveData != null) {
                oldZoomSelection = zoomStateLiveData.getLinearZoom();
                return oldZoomSelection;
            }
        }
        return 0.0f;
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    public boolean isExposureCompensationSupported() {
        if (camera == null) {
            return false;
        }
        return camera.getCameraInfo().getExposureState().isExposureCompensationSupported();
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    public void setExposureCompensation(float value) {
        if (camera == null) {
            return;
        }
        if (!camera.getCameraInfo().getExposureState().isExposureCompensationSupported()) return;
        Range<Integer> evRange = camera.getCameraInfo().getExposureState().getExposureCompensationRange();
        if (evRange == null) {
            return;
        }
        if (value < 0f) {
            value = 0f;
        } else if (value > 1f) {
            value = 1f;
        }
        int index = (int) (mix(evRange.getLower().floatValue(), evRange.getUpper().floatValue(), value) + 0.5f);
        camera.getCameraControl().setExposureCompensationIndex(index);
    }

    @SuppressLint({"UnsafeExperimentalUsageError", "RestrictedApi"})
    public void setTargetOrientation(int rotation) {
        if (previewUseCase != null) {
            previewUseCase.setTargetRotation(rotation);
        }
        if (iCapture != null) {
            iCapture.setTargetRotation(rotation);
        }
        if (vCapture != null) {
            vCapture.setTargetRotation(rotation);
        }
    }

    @SuppressLint({"UnsafeExperimentalUsageError", "RestrictedApi"})
    public void setWorldCaptureOrientation(int rotation) {
        if (iCapture != null) {
            iCapture.setTargetRotation(rotation);
        }
        if (vCapture != null) {
            vCapture.setTargetRotation(rotation);
        }
    }

    @SuppressLint({"UnsafeExperimentalUsageError", "RestrictedApi"})
    public void focusToPoint(int x, int y) {
        if (camera == null) {
            return;
        }
        MeteringPoint point = meteringPointFactory.createPoint(x, y);

        FocusMeteringAction action = new FocusMeteringAction
                .Builder(point, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AWB)
                //.disableAutoCancel()
                .build();

        camera.getCameraControl().startFocusAndMetering(action);
    }


    @SuppressLint({"RestrictedApi", "MissingPermission"})
    public void recordVideo(final File path, boolean mirror, CameraXView.VideoSavedCallback onStop) {
        if (provider == null || vCapture == null || path == null) {
            return;
        }
        if (noSupportedSurfaceCombinationWorkaround) {
            try {
                provider.unbindAll();
                provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, vCapture);
            } catch (Exception ignored) {
            }
        }
        videoSavedCallback = onStop;
        FileOutputOptions fileOpt = new FileOutputOptions
                .Builder(path)
                .build();

        boolean torchOn = iCapture != null && iCapture.getFlashMode() == ImageCapture.FLASH_MODE_ON;
        if (torchOn && camera != null) {
            try {
                camera.getCameraControl().enableTorch(true);
            } catch (Exception ignored) {
            }
        }
        boolean hasAudioPermission = ContextCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        androidx.camera.video.PendingRecording pending = vCapture.getOutput()
                .prepareRecording(ApplicationLoader.applicationContext, fileOpt);
        if (hasAudioPermission) {
            try {
                pending = pending.withAudioEnabled();
            } catch (Exception ignored) {
            }
        }
        recording = pending
                .start(AsyncTask.THREAD_POOL_EXECUTOR, videoRecordEvent -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) videoRecordEvent;
                        if (finalize.hasError()) {
                            if (torchOn && camera != null) {
                                try {
                                    camera.getCameraControl().enableTorch(false);
                                } catch (Exception ignored) {
                                }
                            }
                            if (noSupportedSurfaceCombinationWorkaround) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (provider == null) {
                                        return;
                                    }
                                    try {
                                        provider.unbindAll();
                                        provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, iCapture);
                                    } catch (Exception ignored) {
                                    }
                                });
                            }
                            FileLog.e(finalize.getCause());
                        } else {
                            if (noSupportedSurfaceCombinationWorkaround) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (provider == null) {
                                        return;
                                    }
                                    try {
                                        provider.unbindAll();
                                        provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, iCapture);
                                    } catch (Exception ignored) {
                                    }
                                });
                            }

                            if (abandonCurrentVideo) {
                                abandonCurrentVideo = false;
                                if (torchOn && camera != null) {
                                    try {
                                        camera.getCameraControl().enableTorch(false);
                                    } catch (Exception ignored) {
                                    }
                                }
                            } else {
                                finishRecordingVideo(path, mirror);
                                if (torchOn && camera != null) {
                                    try {
                                        camera.getCameraControl().enableTorch(false);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    }
                });
    }

    private void finishRecordingVideo(final File path, boolean mirror) {
        MediaMetadataRetriever mediaMetadataRetriever = null;
        long duration = 0;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(path.getAbsolutePath());
            String d = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) {
                duration = (int) Math.ceil(Long.parseLong(d) / 1000.0f);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        Bitmap bitmap = SendMessagesHelper.createVideoThumbnail(path.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
        if (mirror && bitmap != null) {
            Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            canvas.scale(-1, 1, b.getWidth() >> 1, b.getHeight() >> 1);
            canvas.drawBitmap(bitmap, 0, 0, null);
            bitmap.recycle();
            bitmap = b;
        }
        String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
        final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(cacheFile);
            if (bitmap != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
        SharedConfig.saveConfig();
        final long durationFinal = duration;
        final Bitmap bitmapFinal = bitmap;
        AndroidUtilities.runOnUIThread(() -> {
            if (videoSavedCallback != null) {
                String cachePath = cacheFile.getAbsolutePath();
                if (bitmapFinal != null) {
                    ImageLoader.getInstance().putImageToCache(new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmapFinal), Utilities.MD5(cachePath), false);
                }
                videoSavedCallback.onFinishVideoRecording(cachePath, durationFinal);
                videoSavedCallback = null;
            }
        });
    }


    @SuppressLint("RestrictedApi")
    public void stopVideoRecording(final boolean abandon) {
        abandonCurrentVideo = abandon;
        if (recording != null) {
            try {
                recording.stop();
            } catch (Exception ignored) {
            }
            recording = null;
        }
    }


    public void takePicture(final File file, Runnable onTake) {
        if (stableFPSPreviewOnly) return;
        if (iCapture == null) {
            return;
        }
        iCapture.takePicture(AsyncTask.THREAD_POOL_EXECUTOR, new ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                int orientation = image.getImageInfo().getRotationDegrees();
                FileOutputStream output = null;
                try {
                    output = new FileOutputStream(file);

                    int flipState = 0;
                    if (isFrontface && (orientation == 90 || orientation == 270)) {
                        flipState = JpegImageUtils.FLIP_Y;
                    } else if (isFrontface && (orientation == 0 || orientation == 180)) {
                        flipState = JpegImageUtils.FLIP_X;
                    }

                    byte[] jpegByteArray = JpegImageUtils.imageToJpegByteArray(image, flipState);
                    if (jpegByteArray != null) {
                        output.write(jpegByteArray);
                    }
                    Exif exif = Exif.createFromFile(file);
                    exif.attachTimestamp();

                    if (new ExifRotationAvailability().shouldUseExifOrientation(image)) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        buffer.rewind();
                        byte[] data = new byte[buffer.capacity()];
                        buffer.get(data);
                        InputStream inputStream = new ByteArrayInputStream(data);
                        try {
                            Exif originalExif = Exif.createFromInputStream(inputStream);
                            exif.setOrientation(originalExif.getOrientation());
                        } finally {
                            try {
                                inputStream.close();
                            } catch (IOException ignored) {
                            }
                        }
                    } else {
                        exif.rotate(orientation);
                    }
                    exif.save();
                } catch (JpegImageUtils.CodecFailedException | IOException e) {
                    e.printStackTrace();
                    FileLog.e(e);
                } finally {
                    if (output != null) {
                        try {
                            output.close();
                        } catch (IOException ignored) {
                        }
                    }
                    try {
                        image.close();
                    } catch (Exception ignored) {
                    }
                }
                if (onTake != null) {
                    AndroidUtilities.runOnUIThread(onTake);
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                FileLog.e(exception);
            }
        });
    }

    @SuppressLint("RestrictedApi")
    public Size getPreviewSize() {
        Size size = new Size(0, 0);
        if (previewUseCase != null) {
            android.util.Size s = previewUseCase.getAttachedSurfaceResolution();
            if (s != null) {
                size = new Size(s.getWidth(), s.getHeight());
            }
        }
        return size;
    }

    public int getDisplayOrientation() {
        WindowManager mgr = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        if (mgr == null || mgr.getDefaultDisplay() == null) {
            return 0;
        }
        int rotation = mgr.getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_0:
            default:
                return 0;
        }
    }

    private int getDeviceDefaultOrientation() {
        WindowManager windowManager = (WindowManager) (ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE));
        Configuration config = ApplicationLoader.applicationContext.getResources().getConfiguration();
        if (windowManager == null || windowManager.getDefaultDisplay() == null || config == null) {
            return Configuration.ORIENTATION_PORTRAIT;
        }
        int rotation = windowManager.getDefaultDisplay().getRotation();

        if (((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) && config.orientation == Configuration.ORIENTATION_LANDSCAPE) ||
                ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) && config.orientation == Configuration.ORIENTATION_PORTRAIT)) {
            return Configuration.ORIENTATION_LANDSCAPE;
        } else {
            return Configuration.ORIENTATION_PORTRAIT;
        }
    }

    private float mix(Float x, Float y, Float f) {
        if (x == null || y == null || f == null) {
            return 0f;
        }
        float ff = f;
        if (ff < 0f) {
            ff = 0f;
        } else if (ff > 1f) {
            ff = 1f;
        }
        return x * (1 - ff) + y * ff;
    }

    @IntDef({CAMERA_NONE, CAMERA_AUTO, CAMERA_HDR, CAMERA_NIGHT})
    @Retention(RetentionPolicy.SOURCE)
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public @interface EffectFacing {
    }
}

package org.opencv.samples.puzzle15;

import android.content.Context;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Constructor;

public final class CameraActivity extends Activity {

    public static final String EXTRA_NO_CAMERA = "io.card.payment.noCamera";
    public static final String EXTRA_SCAN_RESULT = "io.card.payment.scanResult";
    public static final String EXTRA_SUPPRESS_MANUAL_ENTRY = "io.card.payment.suppressManual";
    public static final String EXTRA_GUIDE_COLOR = "io.card.payment.guideColor";
    public static final String EXTRA_HIDE_CARDIO_LOGO = "io.card.payment.hideLogo";
    public static final String EXTRA_SCAN_INSTRUCTIONS = "io.card.payment.scanInstructions";
    public static final String EXTRA_SUPPRESS_SCAN = "io.card.payment.suppressScan";
    public static final String EXTRA_CAPTURED_CARD_IMAGE = "io.card.payment.capturedCardImage";
    public static final String EXTRA_RETURN_CARD_IMAGE = "io.card.payment.returnCardImage";
    public static final String EXTRA_SCAN_OVERLAY_LAYOUT_ID = "io.card.payment.scanOverlayLayoutId";
    public static final String EXTRA_KEEP_APPLICATION_THEME = "io.card.payment.keepApplicationTheme";
    static final String PRIVATE_EXTRA_CAMERA_BYPASS_TEST_MODE = "io.card.payment.cameraBypassTestMode";
    private static int lastResult = 0xca8d10;
    public static final int RESULT_CARD_INFO = lastResult++;
    public static final int RESULT_ENTRY_CANCELED = lastResult++;
    public static final int RESULT_SCAN_NOT_AVAILABLE = lastResult++;
    private static final String TAG = CameraActivity.class.getSimpleName();

    private static final int DEGREE_DELTA = 15;

    private static final int ORIENTATION_PORTRAIT = 1;
    private static final int ORIENTATION_PORTRAIT_UPSIDE_DOWN = 2;
    private static final int ORIENTATION_LANDSCAPE_RIGHT = 3;
    private static final int ORIENTATION_LANDSCAPE_LEFT = 4;

    private static final int FRAME_ID = 1;
    private static final int UIBAR_ID = 2;

    private static final String BUNDLE_WAITING_FOR_PERMISSION = "io.card.payment.waitingForPermission";

    private static final float UIBAR_VERTICAL_MARGIN_DP = 15.0f;

    private static final long[] VIBRATE_PATTERN = { 0, 70, 10, 40 };

    private static final int TOAST_OFFSET_Y = -75;

    private static final int DATA_ENTRY_REQUEST_ID = 10;
    private static final int PERMISSION_REQUEST_ID = 11;

    private OverlayView mOverlay;
    private OrientationEventListener orientationListener;

    // TODO: the preview is accessed by the scanner. Not the best practice.
    Preview mPreview;

    private Rect mGuideFrame;
    private int mLastDegrees;
    private int mFrameOrientation;
    private boolean suppressManualEntry;
    private boolean mDetectOnly;
    private LinearLayout customOverlayLayout;
    private boolean waitingForPermission;
    private static TextView textView;

    private RelativeLayout mUIBar;
    private RelativeLayout mMainLayout;
    private boolean useApplicationTheme;

    static private int numActivityAllocations;

    private FaceScanner mCardScanner;

    private boolean manualEntryFallbackOrForced = false;

 
    static Bitmap markedCardImage = null;

    static FaceOverlayView modifiedImage;

    static Activity thisActivity = null;
	
    public static void showScore(String score)
    {
        textView.setText(score);
    }

    class MyGLSurfaceView extends GLSurfaceView {

        private final GameGLRenderer mRenderer;

        public MyGLSurfaceView(Context context) {
            super(context);
            setEGLContextClientVersion(2);
            mRenderer = new GameGLRenderer();
            super.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            setRenderer(mRenderer);
        }
    }

    public static void setBitMapImage(Bitmap bitmap){
        bitmap = RotateBitmap(bitmap,-90);
        modifiedImage.setBitmap(bitmap);
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thisActivity = this;
        numActivityAllocations++;
        if (numActivityAllocations != 1) {
            Log.i(TAG, String.format(
                    "INTERNAL WARNING: There are %d (not 1) CardIOActivity allocations!",
                    numActivityAllocations));
        }
        final Intent clientData = this.getIntent();
        useApplicationTheme = getIntent().getBooleanExtra(CameraActivity.EXTRA_KEEP_APPLICATION_THEME, false);
        mDetectOnly = clientData.getBooleanExtra(EXTRA_SUPPRESS_SCAN, false);
        ResolveInfo resolveInfo;
        String errorMsg;
        resolveInfo = getPackageManager().resolveActivity(clientData,
                PackageManager.MATCH_DEFAULT_ONLY);
        errorMsg = Util.manifestHasConfigChange(resolveInfo, CameraActivity.class);
        if (errorMsg != null) {
            throw new RuntimeException(errorMsg);
        }

        suppressManualEntry = clientData.getBooleanExtra(EXTRA_SUPPRESS_MANUAL_ENTRY, false);

        if (savedInstanceState != null) {
            waitingForPermission = savedInstanceState.getBoolean(BUNDLE_WAITING_FOR_PERMISSION);
        }

        if (clientData.getBooleanExtra(EXTRA_NO_CAMERA, false)) {
            Log.i(Util.PUBLIC_LOG_TAG, "EXTRA_NO_CAMERA set to true. Skipping camera.");
            manualEntryFallbackOrForced = true;
        }else {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (!waitingForPermission) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_DENIED) {
                            Log.d(TAG, "permission denied to camera - requesting it");
                            String[] permissions = {Manifest.permission.CAMERA};
                            waitingForPermission = true;
                            requestPermissions(permissions, PERMISSION_REQUEST_ID);
                        } else {
                            checkCamera();
                            android23AndAboveHandleCamera();
                        }
                    }
                } else {
                    checkCamera();
                    android22AndBelowHandleCamera();
                }
            } catch (Exception e) {
                handleGeneralExceptionError(e);
            }
        }
    }

    private void android23AndAboveHandleCamera() {
        if (manualEntryFallbackOrForced) {
            finishIfSuppressManualEntry();
        } else {
            // Guaranteed to be called in API 23+
            showCameraScannerOverlay();
        }
    }


    private void android22AndBelowHandleCamera() {
        if (manualEntryFallbackOrForced) {
            finishIfSuppressManualEntry();
        } else {
            // guaranteed to be called in onCreate on API < 22, so it's ok that we're removing the window feature here
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            showCameraScannerOverlay();
        }
    }

    private void finishIfSuppressManualEntry() {
        if (suppressManualEntry) {
            Log.i(Util.PUBLIC_LOG_TAG, "Camera not available and manual entry suppressed.");
            setResultAndFinish(RESULT_SCAN_NOT_AVAILABLE, null);
        }
    }

    private void checkCamera() {
        try {
            if (!Util.hardwareSupported()) {
                manualEntryFallbackOrForced = true;
            }
        } catch (Exception e) {
            Toast toast = Toast.makeText(this, "camera is unavailable", Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, TOAST_OFFSET_Y);
            toast.show();
            manualEntryFallbackOrForced = true;
        }
    }

    private void showCameraScannerOverlay() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            View decorView = getWindow().getDecorView();
            // Hide the status bar.
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            // Remember that you should never show the action bar if the
            // status bar is hidden, so hide that too if necessary.
            ActionBar actionBar = getActionBar();
            if (null != actionBar) {
                actionBar.hide();
            }
        }

        try {
            mGuideFrame = new Rect();

            mFrameOrientation = ORIENTATION_PORTRAIT;

            if (getIntent().getBooleanExtra(PRIVATE_EXTRA_CAMERA_BYPASS_TEST_MODE, false)) {
                if (!this.getPackageName().contentEquals("io.card.development")) {
                    throw new IllegalStateException("Illegal access of private extra");
                }
                // use reflection here so that the tester can be safely stripped for release
                // builds.
                Class<?> testScannerClass = Class.forName("io.card.payment.CardScannerTester");
                Constructor<?> cons = testScannerClass.getConstructor(this.getClass(),
                        Integer.TYPE);
                mCardScanner = (FaceScanner) cons.newInstance(new Object[] { this,
                        mFrameOrientation });
            } else {
                mCardScanner = new FaceScanner(this, mFrameOrientation);
            }

            mCardScanner.prepareScanner();

            setPreviewLayout();

            orientationListener = new OrientationEventListener(this,
                    SensorManager.SENSOR_DELAY_UI) {
                @Override
                public void onOrientationChanged(int orientation) {
                    doOrientationChange(orientation);
                }
            };

        } catch (Exception e) {
            handleGeneralExceptionError(e);
        }
    }

    private void handleGeneralExceptionError(Exception e) {
        Log.e(Util.PUBLIC_LOG_TAG, "Unknown exception, please post the stack trace as a GitHub issue", e);
        Toast toast = Toast.makeText(this, "exception", Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, TOAST_OFFSET_Y);
        toast.show();
        manualEntryFallbackOrForced = true;
    }

    private void doOrientationChange(int orientation) {
        if (orientation < 0 || mCardScanner == null) {
            return;
        }

        orientation += mCardScanner.getRotationalOffset();

        // Check if we have gone too far forward with
        // rotation adjustment, keep the result between 0-360
        if (orientation > 360) {
            orientation -= 360;
        }
        int degrees;

        degrees = -1;

        if (orientation < DEGREE_DELTA || orientation > 360 - DEGREE_DELTA) {
            degrees = 0;
            mFrameOrientation = ORIENTATION_PORTRAIT;
        } else if (orientation > 90 - DEGREE_DELTA && orientation < 90 + DEGREE_DELTA) {
            degrees = 90;
            mFrameOrientation = ORIENTATION_LANDSCAPE_LEFT;
        } else if (orientation > 180 - DEGREE_DELTA && orientation < 180 + DEGREE_DELTA) {
            degrees = 180;
            mFrameOrientation = ORIENTATION_PORTRAIT_UPSIDE_DOWN;
        } else if (orientation > 270 - DEGREE_DELTA && orientation < 270 + DEGREE_DELTA) {
            degrees = 270;
            mFrameOrientation = ORIENTATION_LANDSCAPE_RIGHT;
        }

        if (degrees >= 0 && degrees != mLastDegrees) {
            if (degrees == 90) {
                rotateCustomOverlay(270);
            } else if (degrees == 270) {
                rotateCustomOverlay(90);
            } else {
                rotateCustomOverlay(degrees);
            }
        }
    }

    /**
     * Suspend/resume camera preview as part of the {@link android.app.Activity} life cycle (side note: we reuse the
     * same buffer for preview callbacks to greatly reduce the amount of required GC).
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (!waitingForPermission) {
            if (manualEntryFallbackOrForced) {
                if (suppressManualEntry) {
                    finishIfSuppressManualEntry();
                    return;
                } else {
                    return;
                }
            }

            Util.logNativeMemoryStats();

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            orientationListener.enable();

            if (!restartPreview()) {
                Log.e(TAG, "Could not connect to camera.");
            } else {
                // Turn flash off
                setFlashOn(false);
            }

            doOrientationChange(mLastDegrees);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(BUNDLE_WAITING_FOR_PERMISSION, waitingForPermission);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (orientationListener != null) {
            orientationListener.disable();
        }
        setFlashOn(false);

        if (mCardScanner != null) {
            mCardScanner.pauseScanning();
        }
    }

    @Override
    protected void onDestroy() {
        mOverlay = null;
        numActivityAllocations--;

        if (orientationListener != null) {
            orientationListener.disable();
        }
        setFlashOn(false);

        if (mCardScanner != null) {
            mCardScanner.endScanning();
            mCardScanner = null;
        }

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_ID) {
            waitingForPermission = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCameraScannerOverlay();
            } else {
                // show manual entry - handled in onResume()
                manualEntryFallbackOrForced = true;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case DATA_ENTRY_REQUEST_ID:
                if (resultCode == RESULT_CANCELED) {
                    Log.d(TAG, "ignoring onActivityResult(RESULT_CANCELED) caused only when Camera Permissions are Denied in Android 23");
                } else if (resultCode == RESULT_CARD_INFO || resultCode == RESULT_ENTRY_CANCELED
                        || manualEntryFallbackOrForced) {
                    if (data != null && data.hasExtra(EXTRA_SCAN_RESULT)) {
                        Log.v(TAG, "EXTRA_SCAN_RESULT: " + data.getParcelableExtra(EXTRA_SCAN_RESULT));
                    } else {
                        Log.d(TAG, "no data in EXTRA_SCAN_RESULT");
                    }
                    setResultAndFinish(resultCode, data);

                } else {
                    if (mUIBar != null) {
                        mUIBar.setVisibility(View.VISIBLE);
                    }
                }
                break;
        }
    }

    /**
     * This {@link android.app.Activity} overrides back button handling to handle back presses properly given the
     * various states this {@link android.app.Activity} can be in.
     * <br><br>
     * This method is called by Android, never directly by application code.
     */
    @Override
    public void onBackPressed() {
        if (!manualEntryFallbackOrForced && mOverlay.isAnimating()) {
            try {
                restartPreview();
            } catch (RuntimeException re) {
                Log.w(TAG, "*** could not return to preview: " + re);
            }
        } else if (mCardScanner != null) {
            super.onBackPressed();
        }
    }

    void onFirstFrame(int orientation) {
        SurfaceView sv = mPreview.getSurfaceView();
        if (mOverlay != null) {
            //mOverlay.setCameraPreviewRect(new Rect(sv.getLeft(), sv.getTop(), sv.getRight(), sv.getBottom()));
        }
        mFrameOrientation = ORIENTATION_PORTRAIT;
        if (orientation != mFrameOrientation) {
            Log.wtf(Util.PUBLIC_LOG_TAG,
                    "the orientation of the scanner doesn't match the orientation of the activity");
        }

    }


    private boolean restartPreview() {
        assert mPreview != null;
        boolean success = mCardScanner.resumeScanning(mPreview.getSurfaceHolder());
        if (success) {
            mUIBar.setVisibility(View.VISIBLE);
        }

        return success;
    }

    // Called by OverlayView
    void toggleFlash() {
        setFlashOn(!mCardScanner.isFlashOn());
    }

    void setFlashOn(boolean b) {
        boolean success = (mPreview != null && mOverlay != null && mCardScanner.setFlashOn(b));
        if (success) {
        }
    }

    void triggerAutoFocus() {
        mCardScanner.triggerAutoFocus(true);
    }

    /**
     * Manually set up the layout for this {@link android.app.Activity}. It may be possible to use the standard xml
     * layout mechanism instead, but to know for sure would require more work
     */
    private void setPreviewLayout() {
        // top level container
        mMainLayout = new RelativeLayout(this);
        mMainLayout.setBackgroundColor(Color.BLACK);
        mMainLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setId(FRAME_ID);

        mPreview = new Preview(this, null, mCardScanner.mPreviewWidth, mCardScanner.mPreviewHeight);
        mPreview.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT, Gravity.TOP));
        previewFrame.addView(mPreview);

        mOverlay = new OverlayView(this, null, Util.deviceSupportsTorch(this));
        mOverlay.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        if (getIntent() != null) {
            int color = getIntent().getIntExtra(EXTRA_GUIDE_COLOR, 0);
            if (color != 0) {
                // force 100% opaque guide colors.
                int alphaRemovedColor = color | 0xFF000000;
                if (color != alphaRemovedColor) {
                    Log.w(Util.PUBLIC_LOG_TAG, "Removing transparency from provided guide color.");
                }
                mOverlay.setGuideColor(alphaRemovedColor);
            } else {
                // default to greeeeeen
                mOverlay.setGuideColor(Color.GREEN);
            }

            boolean hideCardIOLogo = getIntent().getBooleanExtra(EXTRA_HIDE_CARDIO_LOGO, false);
            mOverlay.setHideCardIOLogo(hideCardIOLogo);

            String scanInstructions = getIntent().getStringExtra(EXTRA_SCAN_INSTRUCTIONS);
            if (scanInstructions != null) {
                mOverlay.setScanInstructions(scanInstructions);
            }
        }

        previewFrame.addView(mOverlay);

        RelativeLayout.LayoutParams previewParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        previewParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        previewParams.addRule(RelativeLayout.ABOVE, UIBAR_ID);
        mMainLayout.addView(previewFrame, previewParams);

        RelativeLayout rel = new RelativeLayout(this);

        modifiedImage = new FaceOverlayView(this);
        textView = new TextView(this);
        textView.setText("0");
        textView.setTextSize(60);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(Color.WHITE);
        //rel.addView(modifiedImage);
        rel.addView(textView);
        mMainLayout.addView(rel);

        mUIBar = new RelativeLayout(this);
        mUIBar.setGravity(Gravity.BOTTOM);
        RelativeLayout.LayoutParams mUIBarParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        previewParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        mUIBar.setLayoutParams(mUIBarParams);

        mUIBar.setId(UIBAR_ID);

        mUIBar.setGravity(Gravity.BOTTOM | Gravity.RIGHT);

        // Show the keyboard button
        // Device has a flash, show the flash button
        RelativeLayout.LayoutParams uiParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        uiParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        final float scale = getResources().getDisplayMetrics().density;
        int uiBarMarginPx = (int) (UIBAR_VERTICAL_MARGIN_DP * scale + 0.5f);
        uiParams.setMargins(0, uiBarMarginPx, 0, uiBarMarginPx);
        mMainLayout.addView(mUIBar, uiParams);

        if (getIntent() != null) {
            if (customOverlayLayout != null) {
                mMainLayout.removeView(customOverlayLayout);
                customOverlayLayout = null;
            }

            int resourceId = getIntent().getIntExtra(EXTRA_SCAN_OVERLAY_LAYOUT_ID, -1);
            if (resourceId != -1) {
                customOverlayLayout = new LinearLayout(this);
                customOverlayLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));

                LayoutInflater inflater = this.getLayoutInflater();

                inflater.inflate(resourceId, customOverlayLayout);
                mMainLayout.addView(customOverlayLayout);
            }
        }

        this.setContentView(mMainLayout);
    }

    private void rotateCustomOverlay(float degrees) {
        if (customOverlayLayout != null) {
            float pivotX = customOverlayLayout.getWidth() / 2;
            float pivotY = customOverlayLayout.getHeight() / 2;

            Animation an = new RotateAnimation(0, degrees, pivotX, pivotY);
            an.setDuration(0);
            an.setRepeatCount(0);
            an.setFillAfter(true);

            customOverlayLayout.setAnimation(an);
        }
    }

    private void setResultAndFinish(final int resultCode, final Intent data) {
        setResult(resultCode, data);
        markedCardImage = null;
        finish();
    }

}

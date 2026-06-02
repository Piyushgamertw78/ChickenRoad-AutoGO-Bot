package com.chickenroad.autobot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class AutoClickService extends AccessibilityService {

    private static final String TAG = "AutoClickService";
    public static final String ACTION_ARM = "com.chickenroad.autobot.ARM";
    public static final String ACTION_STOP = "com.chickenroad.autobot.STOP";
    public static AutoClickService instance;

    private SharedPreferences prefs;
    private Handler handler;
    private Runnable monitorRunnable;

    // Detection state
    private boolean isArmed = false;
    private boolean hasTriggered = false;

    // Saved colors for detection
    private int[] refColors = null;
    private int[] detectionX;
    private int[] detectionY;
    private int goX, goY;

    // Screen capture
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;

    private BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_ARM.equals(action)) {
                int projResultCode = intent.getIntExtra("resultCode", 0);
                Intent projData = intent.getParcelableExtra("data");
                armBot(projResultCode, projData);
            } else if (ACTION_STOP.equals(action)) {
                stopBot();
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = getSharedPreferences("AutoBotPrefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ARM);
        filter.addAction(ACTION_STOP);
        registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        Log.d(TAG, "AutoClickService connected! Screen: " + screenWidth + "x" + screenHeight);
        showToast("✅ Bot Service Active!");
    }

    private void armBot(int resultCode, Intent data) {
        isArmed = true;
        hasTriggered = false;

        // Load saved coordinates
        goX = prefs.getInt("go_x", screenWidth / 2);
        goY = prefs.getInt("go_y", (int)(screenHeight * 0.85f));

        // Detection zone points (3 points across the lane)
        int zoneX1 = prefs.getInt("zone_x1", screenWidth / 4);
        int zoneY1 = prefs.getInt("zone_y1", screenHeight / 3);
        int zoneX2 = prefs.getInt("zone_x2", screenWidth * 3 / 4);
        int zoneY2 = prefs.getInt("zone_y2", screenHeight / 3);

        // Sample 5 points across detection zone
        detectionX = new int[]{
            zoneX1,
            (zoneX1 + zoneX2) / 2,
            zoneX2,
            (zoneX1 * 3 + zoneX2) / 4,
            (zoneX1 + zoneX2 * 3) / 4
        };
        detectionY = new int[]{zoneY1, zoneY1, zoneY1, zoneY2, zoneY2};

        // Setup screen capture
        if (resultCode != 0 && data != null) {
            setupScreenCapture(resultCode, data);
        } else {
            // Fallback: use pixel sampling without media projection
            startMonitoringWithoutProjection();
        }

        showToast("🟢 ARMED! Gaadi ka wait kar raha hoon...");
        Log.d(TAG, "Bot ARMED. GO at: " + goX + "," + goY);
    }

    private void setupScreenCapture(int resultCode, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "BotCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, null
        );

        // Wait 1 second for first frame, then capture reference
        handler.postDelayed(this::captureReferenceColors, 1500);
    }

    private void startMonitoringWithoutProjection() {
        // Simple time-based trigger fallback (won't be as accurate)
        showToast("⚠️ Screen capture permission needed for best accuracy");
    }

    private void captureReferenceColors() {
        if (!isArmed) return;

        Bitmap bmp = captureScreen();
        if (bmp == null) {
            handler.postDelayed(this::captureReferenceColors, 500);
            return;
        }

        refColors = new int[detectionX.length];
        for (int i = 0; i < detectionX.length; i++) {
            int px = Math.min(detectionX[i], bmp.getWidth() - 1);
            int py = Math.min(detectionY[i], bmp.getHeight() - 1);
            refColors[i] = bmp.getPixel(px, py);
        }
        bmp.recycle();

        Log.d(TAG, "Reference colors captured. Starting monitor loop...");
        startMonitorLoop();
    }

    private void startMonitorLoop() {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isArmed || hasTriggered) return;

                try {
                    Bitmap bmp = captureScreen();
                    if (bmp != null) {
                        int changedCount = 0;
                        int threshold = prefs.getInt("sensitivity", 30);

                        for (int i = 0; i < detectionX.length; i++) {
                            int px = Math.min(detectionX[i], bmp.getWidth() - 1);
                            int py = Math.min(detectionY[i], bmp.getHeight() - 1);
                            int curColor = bmp.getPixel(px, py);

                            int rDiff = Math.abs(((curColor >> 16) & 0xFF) - ((refColors[i] >> 16) & 0xFF));
                            int gDiff = Math.abs(((curColor >> 8) & 0xFF) - ((refColors[i] >> 8) & 0xFF));
                            int bDiff = Math.abs((curColor & 0xFF) - (refColors[i] & 0xFF));

                            if ((rDiff + gDiff + bDiff) > threshold) {
                                changedCount++;
                            }
                        }
                        bmp.recycle();

                        // If 2+ detection points changed → VEHICLE DETECTED!
                        if (changedCount >= 2) {
                            triggerGO();
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Monitor error: " + e.getMessage());
                }

                // Loop every 50ms (20 times/sec) for fast detection
                if (isArmed && !hasTriggered) {
                    handler.postDelayed(this, 50);
                }
            }
        };

        handler.post(monitorRunnable);
    }

    private void triggerGO() {
        if (hasTriggered) return;
        hasTriggered = true;
        isArmed = false;

        Log.d(TAG, "🚗 VEHICLE DETECTED! Clicking GO at: " + goX + "," + goY);

        // Click GO button using Accessibility gesture
        Path clickPath = new Path();
        clickPath.moveTo(goX, goY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 50));

        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "✅ GO Clicked!");
                handler.post(() -> {
                    showToast("⚡ GO CLICKED! Bot done. Restart karo dobara khelne ke liye.");
                    // Notify MainActivity
                    Intent intent = new Intent("com.chickenroad.autobot.TRIGGERED");
                    sendBroadcast(intent);
                });
                cleanup();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.e(TAG, "Gesture cancelled");
            }
        }, null);
    }

    private Bitmap captureScreen() {
        if (imageReader == null) return null;
        try {
            Image image = imageReader.acquireLatestImage();
            if (image == null) return null;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bmp = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            );
            bmp.copyPixelsFromBuffer(buffer);
            image.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private void stopBot() {
        isArmed = false;
        hasTriggered = false;
        refColors = null;
        if (handler != null && monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
        }
        cleanup();
        showToast("⏹ Bot Stopped & Reset");
        Log.d(TAG, "Bot STOPPED");
    }

    private void cleanup() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        imageReader = null;
    }

    private void showToast(String msg) {
        handler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        stopBot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBot();
        try { unregisterReceiver(commandReceiver); } catch (Exception ignored) {}
        instance = null;
    }

    public static boolean isRunning() {
        return instance != null;
    }
}

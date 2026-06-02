package com.chickenroad.autobot;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {

    private static final int MEDIA_PROJECTION_REQUEST = 100;

    private SharedPreferences prefs;
    private TextView statusText, logText, goCoordText, zoneCoordText;
    private Button armBtn, stopBtn, setGoBtn, setZoneBtn;
    private SeekBar sensitivityBar;
    private TextView sensitivityValue;
    private ScrollView logScroll;
    private FrameLayout rootFrame;

    // Overlay views for setting
    private View dimOverlay;
    private ZoneDrawView zoneDrawView;
    private TextView overlayInstruction;
    private Button overlayCancelBtn;

    private boolean settingGO = false;
    private boolean settingZone = false;
    private float zoneStartX, zoneStartY;
    private boolean zonePhase1 = true;
    private int screenW, screenH;

    private BroadcastReceiver triggeredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(() -> {
                setStatus("✅ GO CLICKED! Done.", "#00ff88");
                armBtn.setEnabled(true);
                armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
                stopBtn.setEnabled(false);
                addLog("⚡ GO button clicked! Bot band hua.");
                addLog("🔄 Dobara khelne ke liye phir ARM dabao.");
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("AutoBotPrefs", MODE_PRIVATE);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        buildUI();

        IntentFilter filter = new IntentFilter("com.chickenroad.autobot.TRIGGERED");
        registerReceiver(triggeredReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        addLog("🐔 Bot ready! Steps follow karo.");
        checkAccessibilityService();
        refreshCoordDisplays();
    }

    private void buildUI() {
        // Root FrameLayout — so we can overlay things on top
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Color.parseColor("#0d1117"));

        // Scrollable main content
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 56, 28, 28);
        sv.addView(root);
        rootFrame.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ── Title ──
        TextView title = new TextView(this);
        title.setText("🐔 Chicken Road\nAuto-GO Bot");
        title.setTextColor(Color.parseColor("#00ff88"));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        // ── Status ──
        statusText = new TextView(this);
        statusText.setText("⏸ Pehle setup karo...");
        statusText.setTextColor(Color.parseColor("#ffcc00"));
        statusText.setTextSize(13);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackgroundColor(Color.parseColor("#161b22"));
        statusText.setPadding(16, 14, 16, 14);
        root.addView(statusText);
        gap(root, 20);

        // ── STEP 1: Zone ──
        sectionLabel(root, "📍 STEP 1: Detection Zone (Car wali Lane)");

        zoneCoordText = new TextView(this);
        zoneCoordText.setTextColor(Color.parseColor("#ff6666"));
        zoneCoordText.setTextSize(12);
        root.addView(zoneCoordText);
        gap(root, 6);

        setZoneBtn = btn(root, "📍 Lane Area Draw Karo (App ke andar)", "#1a4a7a");
        setZoneBtn.setOnClickListener(v -> startZoneDrawing());
        gap(root, 20);

        // ── STEP 2: GO Button ──
        sectionLabel(root, "🎯 STEP 2: GO Button Position");

        goCoordText = new TextView(this);
        goCoordText.setTextColor(Color.parseColor("#ff6666"));
        goCoordText.setTextSize(12);
        root.addView(goCoordText);
        gap(root, 6);

        setGoBtn = btn(root, "🎯 GO Button Pe Tap Karo (App ke andar)", "#1a4a7a");
        setGoBtn.setOnClickListener(v -> startGOTap());
        gap(root, 20);

        // ── Sensitivity ──
        sectionLabel(root, "⚙️ Sensitivity (Kitna change ho to trigger ho):");

        TextView sensHint = new TextView(this);
        sensHint.setText("  कम = ज्यादा sensitive | ज्यादा = कम sensitive (Default: 30)");
        sensHint.setTextColor(Color.parseColor("#888888"));
        sensHint.setTextSize(11);
        root.addView(sensHint);

        LinearLayout sensRow = new LinearLayout(this);
        sensitivityBar = new SeekBar(this);
        sensitivityBar.setMax(95);
        int savedSens = prefs.getInt("sensitivity", 30);
        sensitivityBar.setProgress(savedSens - 5);
        sensitivityValue = new TextView(this);
        sensitivityValue.setText(String.valueOf(savedSens));
        sensitivityValue.setTextColor(Color.parseColor("#00ff88"));
        sensitivityValue.setTextSize(15);
        sensitivityValue.setTypeface(null, android.graphics.Typeface.BOLD);
        sensitivityValue.setPadding(16, 0, 0, 0);
        sensitivityValue.setMinWidth(80);
        sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                int val = p + 5;
                sensitivityValue.setText(String.valueOf(val));
                prefs.edit().putInt("sensitivity", val).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        sensRow.addView(sensitivityBar, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        sensRow.addView(sensitivityValue);
        root.addView(sensRow);
        gap(root, 28);

        // ── ARM / STOP ──
        armBtn = btn(root, "🟢  ARM / START", "#00aa44");
        armBtn.setEnabled(false);
        armBtn.setTextSize(17);
        armBtn.setOnClickListener(v -> requestProjectionAndArm());
        gap(root, 10);

        stopBtn = btn(root, "🔴  STOP / RESET", "#aa0000");
        stopBtn.setEnabled(false);
        stopBtn.setTextSize(17);
        stopBtn.setOnClickListener(v -> stopBot());
        gap(root, 20);

        // ── Accessibility ──
        Button accBtn = btn(root, "⚙️ Accessibility Service ON Karo", "#2a2a55");
        accBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        gap(root, 20);

        // ── Log ──
        sectionLabel(root, "📋 Log:");
        logScroll = new ScrollView(this);
        logText = new TextView(this);
        logText.setTextColor(Color.parseColor("#00ff88"));
        logText.setTextSize(11);
        logText.setTypeface(android.graphics.Typeface.MONOSPACE);
        logText.setPadding(10, 8, 10, 8);
        logScroll.addView(logText);
        logScroll.setBackgroundColor(Color.parseColor("#0a0a1a"));
        root.addView(logScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 350));

        gap(root, 10);
        TextView footer = new TextView(this);
        footer.setText("⚡ ~50ms trigger speed | 1 baar click → auto stop");
        footer.setTextColor(Color.parseColor("#444466"));
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);

        setContentView(rootFrame);
    }

    // ════════════════════════════════════════════════
    // ZONE DRAWING — App ke andar overlay draw karo
    // ════════════════════════════════════════════════
    private void startZoneDrawing() {
        settingZone = true;
        settingGO = false;
        zonePhase1 = true;

        // Dim overlay
        dimOverlay = new View(this);
        dimOverlay.setBackgroundColor(Color.parseColor("#CC000000"));
        rootFrame.addView(dimOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Drawing canvas
        zoneDrawView = new ZoneDrawView(this);
        rootFrame.addView(zoneDrawView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Instruction text
        overlayInstruction = new TextView(this);
        overlayInstruction.setText("📍 Jis LANE se car aati hai\nus area pe DRAG karo (finger slide)");
        overlayInstruction.setTextColor(Color.WHITE);
        overlayInstruction.setTextSize(17);
        overlayInstruction.setTypeface(null, android.graphics.Typeface.BOLD);
        overlayInstruction.setGravity(Gravity.CENTER);
        overlayInstruction.setBackgroundColor(Color.parseColor("#CC1a4a7a"));
        overlayInstruction.setPadding(20, 16, 20, 16);
        FrameLayout.LayoutParams instrParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        instrParams.gravity = Gravity.TOP;
        instrParams.topMargin = 60;
        rootFrame.addView(overlayInstruction, instrParams);

        // Cancel button
        overlayCancelBtn = new Button(this);
        overlayCancelBtn.setText("✖ Cancel");
        overlayCancelBtn.setBackgroundColor(Color.parseColor("#aa0000"));
        overlayCancelBtn.setTextColor(Color.WHITE);
        overlayCancelBtn.setOnClickListener(v -> cancelOverlay());
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cancelParams.bottomMargin = 120;
        rootFrame.addView(overlayCancelBtn, cancelParams);

        addLog("📍 Zone draw mode: Drag karo jahan se car aati hai");
    }

    // ════════════════════════════════════════════════
    // GO BUTTON TAP — App ke andar hi tap karo
    // ════════════════════════════════════════════════
    private void startGOTap() {
        settingGO = true;
        settingZone = false;

        // Dim overlay
        dimOverlay = new View(this);
        dimOverlay.setBackgroundColor(Color.parseColor("#CC000000"));
        rootFrame.addView(dimOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Instruction
        overlayInstruction = new TextView(this);
        overlayInstruction.setText("🎯 Abhi apne phone screen pe\nChicken Road ka GO button\nJIS JAGAH pe hai — WAHAN TAP KARO\n\n(Neeche ka green GO button ka position)");
        overlayInstruction.setTextColor(Color.WHITE);
        overlayInstruction.setTextSize(18);
        overlayInstruction.setTypeface(null, android.graphics.Typeface.BOLD);
        overlayInstruction.setGravity(Gravity.CENTER);
        overlayInstruction.setBackgroundColor(Color.parseColor("#CC1a5a1a"));
        overlayInstruction.setPadding(24, 20, 24, 20);
        FrameLayout.LayoutParams instrParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        instrParams.gravity = Gravity.CENTER_VERTICAL;
        rootFrame.addView(overlayInstruction, instrParams);

        // Target crosshair view
        View crosshair = new View(this) {
            @Override
            protected void onDraw(Canvas c) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.parseColor("#00ff88"));
                p.setStrokeWidth(4);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                // Circle
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(cx, cy, 60, p);
                // Cross lines
                c.drawLine(cx - 90, cy, cx - 20, cy, p);
                c.drawLine(cx + 20, cy, cx + 90, cy, p);
                c.drawLine(cx, cy - 90, cx, cy - 20, p);
                c.drawLine(cx, cy + 20, cx, cy + 90, p);
            }
        };
        FrameLayout.LayoutParams chParams = new FrameLayout.LayoutParams(200, 200);
        chParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        chParams.bottomMargin = 250;
        rootFrame.addView(crosshair, chParams);

        // Cancel button
        overlayCancelBtn = new Button(this);
        overlayCancelBtn.setText("✖ Cancel");
        overlayCancelBtn.setBackgroundColor(Color.parseColor("#aa0000"));
        overlayCancelBtn.setTextColor(Color.WHITE);
        overlayCancelBtn.setOnClickListener(v -> cancelOverlay());
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cancelParams.bottomMargin = 120;
        rootFrame.addView(overlayCancelBtn, cancelParams);

        // Touch listener on dimOverlay to capture tap
        dimOverlay.setOnTouchListener((v2, event) -> {
            if (!settingGO) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int gx = (int) event.getRawX();
                int gy = (int) event.getRawY();
                prefs.edit().putInt("go_x", gx).putInt("go_y", gy).apply();
                settingGO = false;
                cancelOverlay();
                runOnUiThread(() -> {
                    refreshCoordDisplays();
                    addLog("✅ GO Button saved: (" + gx + ", " + gy + ")");
                    setStatus("✅ GO set! Ab ARM karo.", "#00ff88");
                    checkArmReady();
                    // Show confirmation toast
                    Toast.makeText(this, "✅ GO Position: " + gx + ", " + gy, Toast.LENGTH_SHORT).show();
                });
                return true;
            }
            return false;
        });

        addLog("🎯 GO tap mode: Screen pe GO button ki jagah tap karo");
    }

    private void cancelOverlay() {
        settingGO = false;
        settingZone = false;
        zonePhase1 = true;
        // Remove all overlay views
        runOnUiThread(() -> {
            if (dimOverlay != null && dimOverlay.getParent() != null)
                rootFrame.removeView(dimOverlay);
            if (zoneDrawView != null && zoneDrawView.getParent() != null)
                rootFrame.removeView(zoneDrawView);
            if (overlayInstruction != null && overlayInstruction.getParent() != null)
                rootFrame.removeView(overlayInstruction);
            if (overlayCancelBtn != null && overlayCancelBtn.getParent() != null)
                rootFrame.removeView(overlayCancelBtn);
            // Remove any extra child views added (crosshair etc)
            // Keep only first 2 children (ScrollView + overlay base if any)
            while (rootFrame.getChildCount() > 1) {
                rootFrame.removeViewAt(1);
            }
            dimOverlay = null;
            zoneDrawView = null;
            overlayInstruction = null;
            overlayCancelBtn = null;
        });
    }

    // Called from ZoneDrawView when user finishes drawing
    void onZoneDrawn(int x1, int y1, int x2, int y2) {
        prefs.edit()
                .putInt("zone_x1", x1).putInt("zone_y1", y1)
                .putInt("zone_x2", x2).putInt("zone_y2", y2)
                .apply();
        settingZone = false;
        cancelOverlay();
        runOnUiThread(() -> {
            refreshCoordDisplays();
            addLog("✅ Zone saved: (" + x1 + "," + y1 + ") → (" + x2 + "," + y2 + ")");
            setStatus("✅ Zone set! Ab GO button set karo.", "#00ff88");
            checkArmReady();
            Toast.makeText(this, "✅ Zone set!", Toast.LENGTH_SHORT).show();
        });
    }

    // ════════════════════════════════════════════════
    // ARM / STOP
    // ════════════════════════════════════════════════
    private void requestProjectionAndArm() {
        if (!AutoClickService.isRunning()) {
            Toast.makeText(this, "❌ Accessibility Service ON karo pehle!", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PROJECTION_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Intent intent = new Intent(AutoClickService.ACTION_ARM);
            intent.putExtra("resultCode", resultCode);
            intent.putExtra("data", data);
            sendBroadcast(intent);

            setStatus("🟢 ARMED! Gaadi ka intezaar hai...", "#00ff88");
            armBtn.setEnabled(false);
            armBtn.setBackgroundColor(Color.parseColor("#005522"));
            stopBtn.setEnabled(true);
            addLog("🟢 Bot ARMED! GO: " + prefs.getInt("go_x",0) + "," + prefs.getInt("go_y",0));
        } else {
            Toast.makeText(this, "⚠️ Screen capture permission do!", Toast.LENGTH_LONG).show();
        }
    }

    private void stopBot() {
        sendBroadcast(new Intent(AutoClickService.ACTION_STOP));
        setStatus("⏹ Stopped. Dobara ARM karo.", "#ff6666");
        armBtn.setEnabled(prefs.contains("go_x") && prefs.contains("zone_x1"));
        armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
        stopBtn.setEnabled(false);
        addLog("⏹ Bot STOPPED/RESET.");
    }

    // ════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════
    private void checkArmReady() {
        if (prefs.contains("go_x") && prefs.contains("zone_x1")) {
            armBtn.setEnabled(true);
            armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
            setStatus("✅ Ready! ARM dabao.", "#00ff88");
        }
    }

    private void checkAccessibilityService() {
        if (!AutoClickService.isRunning()) {
            setStatus("⚠️ Pehle Accessibility Service ON karo!", "#ff4444");
            addLog("⚠️ Accessibility Service band hai - button dabao enable karne ke liye");
        } else {
            setStatus("✅ Service Active. Ab Zone aur GO set karo.", "#00ff88");
            addLog("✅ Accessibility Service active!");
        }
    }

    private void refreshCoordDisplays() {
        if (prefs.contains("go_x")) {
            goCoordText.setText("✅ GO Button: (" + prefs.getInt("go_x",0) + ", " + prefs.getInt("go_y",0) + ")");
            goCoordText.setTextColor(Color.parseColor("#00ff88"));
        } else {
            goCoordText.setText("❌ GO Button not set");
            goCoordText.setTextColor(Color.parseColor("#ff6666"));
        }
        if (prefs.contains("zone_x1")) {
            int x1=prefs.getInt("zone_x1",0), y1=prefs.getInt("zone_y1",0);
            int x2=prefs.getInt("zone_x2",0), y2=prefs.getInt("zone_y2",0);
            zoneCoordText.setText("✅ Zone: (" + x1 + "," + y1 + ") → (" + x2 + "," + y2 + ")");
            zoneCoordText.setTextColor(Color.parseColor("#00ff88"));
        } else {
            zoneCoordText.setText("❌ Zone not set");
            zoneCoordText.setTextColor(Color.parseColor("#ff6666"));
        }
    }

    private void setStatus(String msg, String color) {
        statusText.setText(msg);
        statusText.setTextColor(Color.parseColor(color));
    }

    private void addLog(String msg) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        logText.setText(logText.getText() + "\n[" + time + "] " + msg);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private Button btn(LinearLayout parent, String text, String color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        b.setTextSize(14);
        b.setAllCaps(false);
        parent.addView(b, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private void sectionLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#00ccff"));
        tv.setTextSize(13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 4, 0, 4);
        parent.addView(tv);
    }

    private void gap(LinearLayout parent, int dp) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp));
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccessibilityService();
        checkArmReady();
        refreshCoordDisplays();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(triggeredReceiver); } catch (Exception ignored) {}
    }
}

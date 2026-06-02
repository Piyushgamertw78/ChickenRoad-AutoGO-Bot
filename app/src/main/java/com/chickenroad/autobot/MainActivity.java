package com.chickenroad.autobot;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int MEDIA_PROJECTION_REQUEST = 100;

    private SharedPreferences prefs;
    private TextView statusText, logText, goCoordText, zoneCoordText;
    private Button armBtn, stopBtn, setGoBtn, setZoneBtn;
    private SeekBar sensitivityBar;
    private TextView sensitivityValue;
    private ScrollView logScroll;

    private boolean settingGO = false;
    private boolean settingZone = false;
    private float zoneStartX, zoneStartY;
    private boolean zonePhase1 = true; // true = first corner, false = second corner

    private int screenW, screenH;

    // Overlay for zone/point selection
    private android.view.WindowManager.LayoutParams overlayParams;
    private View overlayView;

    private BroadcastReceiver triggeredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(() -> {
                statusText.setText("✅ GO CLICKED! Bot ne kaam kar diya!");
                statusText.setTextColor(Color.parseColor("#00ff88"));
                armBtn.setEnabled(true);
                armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
                stopBtn.setEnabled(false);
                addLog("⚡ GO button clicked! Bot band hua.");
                addLog("🔄 Dobara khelne ke liye STOP/RESET phir ARM dabao.");
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

        addLog("🐔 Chicken Road Auto-GO Bot Ready!");
        addLog("📋 Steps: 1) Zone set karo 2) GO button set karo 3) ARM karo");
        checkAccessibilityService();
    }

    private void buildUI() {
        // Main scroll layout
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0d1117"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);
        sv.addView(root);

        // Title
        TextView title = new TextView(this);
        title.setText("🐔 Chicken Road\nAuto-GO Bot");
        title.setTextColor(Color.parseColor("#00ff88"));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        // Status
        statusText = new TextView(this);
        statusText.setText("⏸ Setup karo pehle...");
        statusText.setTextColor(Color.parseColor("#ffcc00"));
        statusText.setTextSize(14);
        statusText.setGravity(android.view.Gravity.CENTER);
        statusText.setBackgroundColor(Color.parseColor("#161b22"));
        statusText.setPadding(16, 12, 16, 12);
        root.addView(statusText);
        addMargin(root, 16);

        // STEP 1 - Zone
        TextView step1Title = makeLabel(root, "📍 STEP 1: Detection Zone");
        zoneCoordText = new TextView(this);
        zoneCoordText.setText("❌ Zone not set");
        zoneCoordText.setTextColor(Color.parseColor("#ff6666"));
        zoneCoordText.setTextSize(12);
        root.addView(zoneCoordText);

        setZoneBtn = makeButton(root, "📍 Detection Zone Set Karo", "#0f3460");
        setZoneBtn.setOnClickListener(v -> startZoneSetting());
        addMargin(root, 16);

        // STEP 2 - GO Button
        TextView step2Title = makeLabel(root, "🎯 STEP 2: GO Button Location");
        goCoordText = new TextView(this);
        goCoordText.setText("❌ GO Button not set");
        goCoordText.setTextColor(Color.parseColor("#ff6666"));
        goCoordText.setTextSize(12);
        root.addView(goCoordText);

        setGoBtn = makeButton(root, "🎯 GO Button Set Karo (3 sec)", "#0f3460");
        setGoBtn.setOnClickListener(v -> startGOSetting());
        addMargin(root, 16);

        // Sensitivity
        makeLabel(root, "⚙️ Detection Sensitivity:");
        LinearLayout sensRow = new LinearLayout(this);
        sensitivityBar = new SeekBar(this);
        sensitivityBar.setMax(95);
        sensitivityBar.setProgress(prefs.getInt("sensitivity", 30) - 5);
        sensitivityValue = new TextView(this);
        sensitivityValue.setText(String.valueOf(prefs.getInt("sensitivity", 30)));
        sensitivityValue.setTextColor(Color.WHITE);
        sensitivityValue.setTextSize(14);
        sensitivityValue.setPadding(16, 0, 0, 0);
        sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                int val = p + 5;
                sensitivityValue.setText(String.valueOf(val));
                prefs.edit().putInt("sensitivity", val).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        sensRow.addView(sensitivityBar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        sensRow.addView(sensitivityValue);
        root.addView(sensRow);
        addMargin(root, 24);

        // ARM / STOP buttons
        armBtn = makeButton(root, "🟢 ARM / START", "#00aa44");
        armBtn.setEnabled(false);
        armBtn.setOnClickListener(v -> requestMediaProjectionAndArm());
        addMargin(root, 8);

        stopBtn = makeButton(root, "🔴 STOP / RESET", "#aa0000");
        stopBtn.setEnabled(false);
        stopBtn.setOnClickListener(v -> stopBot());
        addMargin(root, 16);

        // Accessibility check button
        Button accessBtn = makeButton(root, "⚙️ Accessibility Service Enable Karo", "#333355");
        accessBtn.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
        });
        addMargin(root, 16);

        // Log area
        makeLabel(root, "📋 Log:");
        logScroll = new ScrollView(this);
        logText = new TextView(this);
        logText.setTextColor(Color.parseColor("#00ff88"));
        logText.setTextSize(11);
        logText.setTypeface(android.graphics.Typeface.MONOSPACE);
        logText.setPadding(8, 8, 8, 8);
        logScroll.addView(logText);
        logScroll.setBackgroundColor(Color.parseColor("#0a0a1a"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 400);
        root.addView(logScroll, lp);

        // Footer
        TextView footer = new TextView(this);
        footer.setText("⚡ ~50-100ms trigger speed | Sirf 1 baar kaam karta hai");
        footer.setTextColor(Color.parseColor("#444466"));
        footer.setTextSize(10);
        footer.setGravity(android.view.Gravity.CENTER);
        footer.setPadding(0, 16, 0, 0);
        root.addView(footer);

        setContentView(sv);
    }

    private void startZoneSetting() {
        addLog("📍 Zone set mode: Screen pe us LANE area ko tap karo jahan se CAR aati hai");
        addLog("   Pehle tap = Zone ka upar-baya corner");
        addLog("   Doosra tap = Zone ka neeche-daaya corner");

        zonePhase1 = true;
        settingZone = true;
        settingGO = false;
        statusText.setText("📍 STEP 1: Pehle lane ke UPAR-BAYE corner pe tap karo");
        statusText.setTextColor(Color.parseColor("#00ccff"));

        showOverlay("📍 Lane ke UPAR-BAYE corner pe tap karo\n(Jahan se car aati hai us lane ke start pe)");
    }

    private void startGOSetting() {
        addLog("🎯 GO button set: 3 second baad position capture hogi...");
        addLog("   Abhi GO button pe APNI UNGLI RAKHKE HOLD karo!");

        settingGO = true;
        settingZone = false;
        statusText.setText("🎯 GO button pe finger rakhkar 3 sec hold karo!");
        statusText.setTextColor(Color.parseColor("#ffaa00"));

        showOverlay("🎯 GO button pe FINGER RAKHKE 3 SECOND HOLD karo!\n(Screen pe neeche green GO button pe)");

        // Countdown
        android.os.Handler h = new android.os.Handler(getMainLooper());
        for (int i = 3; i >= 1; i--) {
            final int sec = i;
            h.postDelayed(() -> {
                statusText.setText("🎯 GO button hold karo... " + sec + "s");
            }, (3 - i) * 1000L);
        }

        h.postDelayed(() -> {
            settingGO = false;
            hideOverlay();
            statusText.setText("✅ Tap the GO button NOW on your game!");
            showOverlay("⬇️ AB GO BUTTON PE TAP KARO! ⬇️\n(Neeche game ka green GO button)");

            // After 4 seconds, cancel if not set
            h.postDelayed(() -> {
                if (settingGO || !prefs.contains("go_x")) {
                    hideOverlay();
                    statusText.setText("❌ GO not set. Try again.");
                }
            }, 4000);
        }, 3000);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getRawX();
            float y = event.getRawY();

            if (settingGO) {
                prefs.edit().putInt("go_x", (int) x).putInt("go_y", (int) y).apply();
                settingGO = false;
                hideOverlay();
                goCoordText.setText("✅ GO Button: (" + (int)x + ", " + (int)y + ")");
                goCoordText.setTextColor(Color.parseColor("#00ff88"));
                addLog("✅ GO Button set: (" + (int)x + ", " + (int)y + ")");
                statusText.setText("✅ GO Button set! Ab ARM karo.");
                statusText.setTextColor(Color.parseColor("#00ff88"));
                checkArmReady();
                return true;
            }

            if (settingZone) {
                if (zonePhase1) {
                    zoneStartX = x;
                    zoneStartY = y;
                    zonePhase1 = false;
                    addLog("📍 Zone corner 1: (" + (int)x + ", " + (int)y + ")");
                    statusText.setText("📍 STEP 2: Ab NEECHE-DAAYE corner pe tap karo");
                    hideOverlay();
                    showOverlay("📍 Ab zone ke NEECHE-DAAYE corner pe tap karo");
                } else {
                    float endX = x;
                    float endY = y;
                    int x1 = (int) Math.min(zoneStartX, endX);
                    int y1 = (int) Math.min(zoneStartY, endY);
                    int x2 = (int) Math.max(zoneStartX, endX);
                    int y2 = (int) Math.max(zoneStartY, endY);

                    prefs.edit()
                        .putInt("zone_x1", x1).putInt("zone_y1", y1)
                        .putInt("zone_x2", x2).putInt("zone_y2", y2)
                        .apply();

                    settingZone = false;
                    zonePhase1 = true;
                    hideOverlay();

                    zoneCoordText.setText("✅ Zone: (" + x1 + "," + y1 + ") → (" + x2 + "," + y2 + ")");
                    zoneCoordText.setTextColor(Color.parseColor("#00ff88"));
                    addLog("✅ Zone set: (" + x1 + "," + y1 + ") → (" + x2 + "," + y2 + ")");
                    statusText.setText("✅ Zone set! Ab GO Button set karo.");
                    statusText.setTextColor(Color.parseColor("#00ff88"));
                    checkArmReady();
                    return true;
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void showOverlay(String message) {
        // Simple fullscreen instruction overlay
        runOnUiThread(() -> {
            statusText.setBackgroundColor(Color.parseColor("#CC000000"));
            statusText.setText(message);
            statusText.setTextColor(Color.parseColor("#00ff88"));
            statusText.setTextSize(16);
        });
    }

    private void hideOverlay() {
        runOnUiThread(() -> {
            statusText.setBackgroundColor(Color.parseColor("#161b22"));
            statusText.setTextSize(14);
        });
    }

    private void checkArmReady() {
        if (prefs.contains("go_x") && prefs.contains("zone_x1")) {
            armBtn.setEnabled(true);
            armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
        }
    }

    private void requestMediaProjectionAndArm() {
        if (!AutoClickService.isRunning()) {
            Toast.makeText(this, "❌ Accessibility Service ON karo pehle!", Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
            return;
        }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PROJECTION_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                armBot(resultCode, data);
            } else {
                Toast.makeText(this, "Screen capture permission required!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void armBot(int resultCode, Intent data) {
        Intent intent = new Intent(AutoClickService.ACTION_ARM);
        intent.putExtra("resultCode", resultCode);
        intent.putExtra("data", data);
        sendBroadcast(intent);

        statusText.setText("🟢 ARMED! Gaadi ka intezaar hai...");
        statusText.setTextColor(Color.parseColor("#00ff88"));
        armBtn.setEnabled(false);
        armBtn.setBackgroundColor(Color.parseColor("#005522"));
        stopBtn.setEnabled(true);

        addLog("🟢 Bot ARMED! Vehicle monitor ho raha hai...");
        addLog("   GO at: " + prefs.getInt("go_x",0) + "," + prefs.getInt("go_y",0));
    }

    private void stopBot() {
        sendBroadcast(new Intent(AutoClickService.ACTION_STOP));
        statusText.setText("⏹ STOPPED - Dobara ARM karo");
        statusText.setTextColor(Color.parseColor("#ff6666"));
        armBtn.setEnabled(prefs.contains("go_x") && prefs.contains("zone_x1"));
        armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
        stopBtn.setEnabled(false);
        addLog("⏹ Bot STOPPED/RESET.");
    }

    private void checkAccessibilityService() {
        if (!AutoClickService.isRunning()) {
            statusText.setText("⚠️ Pehle Accessibility Service ON karo!");
            statusText.setTextColor(Color.parseColor("#ff6666"));
            addLog("⚠️ Accessibility Service band hai!");
            addLog("   'Accessibility Service Enable Karo' button dabao");
        } else {
            statusText.setText("✅ Service Active - Setup karo ab");
            statusText.setTextColor(Color.parseColor("#00ff88"));
            addLog("✅ Accessibility Service active hai!");
        }
    }

    private void addLog(String msg) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(new java.util.Date());
        String current = logText.getText().toString();
        logText.setText(current + "\n[" + time + "] " + msg);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private Button makeButton(LinearLayout parent, String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor(color));
        btn.setTextSize(13);
        btn.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT);
        parent.addView(btn, lp);
        return btn;
    }

    private TextView makeLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#00ccff"));
        tv.setTextSize(13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 8, 0, 4);
        parent.addView(tv);
        return tv;
    }

    private void addMargin(LinearLayout parent, int dp) {
        View spacer = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp);
        parent.addView(spacer, lp);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(triggeredReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccessibilityService();
        checkArmReady();

        // Update saved coords display
        if (prefs.contains("go_x")) {
            goCoordText.setText("✅ GO Button: (" + prefs.getInt("go_x",0) + ", " + prefs.getInt("go_y",0) + ")");
            goCoordText.setTextColor(Color.parseColor("#00ff88"));
        }
        if (prefs.contains("zone_x1")) {
            zoneCoordText.setText("✅ Zone: (" + prefs.getInt("zone_x1",0) + "," + prefs.getInt("zone_y1",0) +
                ") → (" + prefs.getInt("zone_x2",0) + "," + prefs.getInt("zone_y2",0) + ")");
            zoneCoordText.setTextColor(Color.parseColor("#00ff88"));
        }
    }
}

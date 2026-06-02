package com.chickenroad.autobot;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

public class MainActivity extends Activity {

    private static final int MEDIA_PROJECTION_REQUEST = 100;
    private static final int OVERLAY_PERMISSION_REQUEST = 200;

    private SharedPreferences prefs;
    private TextView statusText, logText, goCoordText, zoneCoordText;
    private Button armBtn, stopBtn;
    private SeekBar sensitivityBar;
    private TextView sensitivityValue;
    private ScrollView logScroll;

    private int screenW, screenH;

    // The floating overlay shown on top of game
    private WindowManager wm;
    private View floatingPanel;
    private boolean overlayShowing = false;

    // Mode: "zone" or "go" or null
    private String settingMode = null;
    private float zoneX1, zoneY1;
    private boolean zoneFirstTap = true;

    private BroadcastReceiver triggeredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(() -> {
                setStatus("✅ GO CLICKED! Bot done.", "#00ff88");
                armBtn.setEnabled(true);
                armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
                stopBtn.setEnabled(false);
                addLog("⚡ GO clicked! Bot band hua.");
                addLog("Dobara khelne ke liye ARM dabao.");
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("AutoBotPrefs", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        buildUI();

        IntentFilter f = new IntentFilter("com.chickenroad.autobot.TRIGGERED");
        registerReceiver(triggeredReceiver, f, Context.RECEIVER_NOT_EXPORTED);

        addLog("Bot ready! Steps follow karo.");
        refreshCoordDisplays();
        checkAccessibility();
    }

    private void buildUI() {
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
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        gap(root, 16);

        // Status
        statusText = new TextView(this);
        statusText.setTextColor(Color.parseColor("#ffcc00"));
        statusText.setTextSize(13);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackgroundColor(Color.parseColor("#161b22"));
        statusText.setPadding(16, 12, 16, 12);
        root.addView(statusText);
        gap(root, 20);

        // ── HOW IT WORKS ──
        TextView howTo = new TextView(this);
        howTo.setText("📖 Kaise set karo:\n" +
                "1️⃣  Pehle Accessibility ON karo (neeche button)\n" +
                "2️⃣  'ZONE SET' dabao → Game pe jao → Lane pe DRAG karo\n" +
                "3️⃣  'GO SET' dabao → Game pe jao → GO button pe TAP karo\n" +
                "4️⃣  Wapas is app pe aao → ARM dabao → Game khelo!");
        howTo.setTextColor(Color.parseColor("#aaaacc"));
        howTo.setTextSize(12);
        howTo.setBackgroundColor(Color.parseColor("#111122"));
        howTo.setPadding(16, 12, 16, 12);
        root.addView(howTo);
        gap(root, 20);

        // ── STEP 1: Zone ──
        label(root, "📍 STEP 1: Detection Zone (Car wali Lane)");
        zoneCoordText = new TextView(this);
        zoneCoordText.setTextSize(12);
        root.addView(zoneCoordText);
        gap(root, 6);

        Button setZoneBtn = bigBtn(root, "📍 ZONE SET — Game pe jao aur Drag karo", "#0f3460");
        setZoneBtn.setOnClickListener(v -> startOverlaySetting("zone"));
        gap(root, 16);

        // ── STEP 2: GO Button ──
        label(root, "🎯 STEP 2: GO Button Location");
        goCoordText = new TextView(this);
        goCoordText.setTextSize(12);
        root.addView(goCoordText);
        gap(root, 6);

        Button setGoBtn = bigBtn(root, "🎯 GO SET — Game pe jao aur GO button pe Tap karo", "#0f3460");
        setGoBtn.setOnClickListener(v -> startOverlaySetting("go"));
        gap(root, 20);

        // ── Sensitivity ──
        label(root, "⚙️ Sensitivity:");
        LinearLayout sensRow = new LinearLayout(this);
        sensitivityBar = new SeekBar(this);
        sensitivityBar.setMax(95);
        int s = prefs.getInt("sensitivity", 30);
        sensitivityBar.setProgress(s - 5);
        sensitivityValue = new TextView(this);
        sensitivityValue.setText(String.valueOf(s));
        sensitivityValue.setTextColor(Color.parseColor("#00ff88"));
        sensitivityValue.setTextSize(14);
        sensitivityValue.setPadding(12, 0, 0, 0);
        sensitivityValue.setMinWidth(70);
        sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f2) {
                int v = p + 5;
                sensitivityValue.setText(String.valueOf(v));
                prefs.edit().putInt("sensitivity", v).apply();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        sensRow.addView(sensitivityBar, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        sensRow.addView(sensitivityValue);
        root.addView(sensRow);
        gap(root, 28);

        // ── ARM / STOP ──
        armBtn = bigBtn(root, "🟢  ARM / START", "#00aa44");
        armBtn.setEnabled(false);
        armBtn.setTextSize(17);
        armBtn.setOnClickListener(v -> doArm());
        gap(root, 10);

        stopBtn = bigBtn(root, "🔴  STOP / RESET", "#aa0000");
        stopBtn.setEnabled(false);
        stopBtn.setTextSize(17);
        stopBtn.setOnClickListener(v -> stopBot());
        gap(root, 20);

        // ── Accessibility ──
        Button accBtn = bigBtn(root, "⚙️ 1. Accessibility Service ON Karo", "#222244");
        accBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        gap(root, 8);

        Button overlayBtn = bigBtn(root, "🪟 2. Display Over Apps Permission Do", "#222244");
        overlayBtn.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, OVERLAY_PERMISSION_REQUEST);
        });
        gap(root, 20);

        // ── Log ──
        label(root, "📋 Log:");
        logScroll = new ScrollView(this);
        logText = new TextView(this);
        logText.setTextColor(Color.parseColor("#00ff88"));
        logText.setTextSize(11);
        logText.setTypeface(android.graphics.Typeface.MONOSPACE);
        logText.setPadding(8, 6, 8, 6);
        logScroll.addView(logText);
        logScroll.setBackgroundColor(Color.parseColor("#0a0a1a"));
        root.addView(logScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300));

        setContentView(sv);
    }

    // ════════════════════════════════════════════════════════════
    // OVERLAY SETTING — Game ke upar floating panel dikhao
    // ════════════════════════════════════════════════════════════
    private void startOverlaySetting(String mode) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "❌ Pehle 'Display Over Apps' permission do!", Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, OVERLAY_PERMISSION_REQUEST);
            return;
        }

        settingMode = mode;
        zoneFirstTap = true;

        // Remove old overlay if any
        removeFloatingPanel();

        // Build full-screen transparent touch overlay
        floatingPanel = buildFloatingOverlay(mode);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        wm.addView(floatingPanel, params);
        overlayShowing = true;

        // Minimize this activity so game is visible
        moveTaskToBack(true);

        addLog(mode.equals("zone")
                ? "📍 Game pe jao → Car wali lane pe DRAG karo"
                : "🎯 Game pe jao → GO button pe TAP karo");
    }

    private View buildFloatingOverlay(String mode) {
        // Full-screen FrameLayout
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this) {
            // Drawing vars for zone rect
            float sx, sy, ex, ey;
            boolean dragging = false;
            android.graphics.Paint borderP, fillP, textP, bgP;

            {
                borderP = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                borderP.setColor(Color.parseColor("#00ff88"));
                borderP.setStyle(android.graphics.Paint.Style.STROKE);
                borderP.setStrokeWidth(6f);
                borderP.setPathEffect(new android.graphics.DashPathEffect(new float[]{20, 10}, 0));

                fillP = new android.graphics.Paint();
                fillP.setColor(Color.parseColor("#5500ff88"));
                fillP.setStyle(android.graphics.Paint.Style.FILL);

                textP = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                textP.setColor(Color.WHITE);
                textP.setTextSize(48f);
                textP.setTextAlign(android.graphics.Paint.Align.CENTER);
                textP.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

                bgP = new android.graphics.Paint();
                bgP.setColor(Color.parseColor("#BB000000"));
                bgP.setStyle(android.graphics.Paint.Style.FILL);
            }

            @Override
            protected void dispatchDraw(android.graphics.Canvas canvas) {
                super.dispatchDraw(canvas);
                int w = getWidth(), h = getHeight();

                if (mode.equals("zone") && dragging) {
                    float l = Math.min(sx, ex), t = Math.min(sy, ey);
                    float r = Math.max(sx, ex), b = Math.max(sy, ey);
                    canvas.drawRect(l, t, r, b, fillP);
                    canvas.drawRect(l, t, r, b, borderP);
                    textP.setTextSize(38f);
                    canvas.drawText((int)(r-l) + "×" + (int)(b-t) + "px",
                            (l+r)/2f, t - 20, textP);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (mode.equals("go")) {
                    // Single tap = save GO position
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        int gx = (int) event.getRawX();
                        int gy = (int) event.getRawY();
                        prefs.edit().putInt("go_x", gx).putInt("go_y", gy).apply();
                        removeFloatingPanel();
                        settingMode = null;
                        // Come back to app
                        Intent bk = new Intent(getApplicationContext(), MainActivity.class);
                        bk.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(bk);
                        runOnUiThread(() -> {
                            refreshCoordDisplays();
                            addLog("✅ GO saved: (" + gx + ", " + gy + ")");
                            setStatus("✅ GO set! Ab ARM karo.", "#00ff88");
                            checkArmReady();
                            Toast.makeText(getApplicationContext(),
                                    "✅ GO Position saved!", Toast.LENGTH_SHORT).show();
                        });
                        return true;
                    }
                } else if (mode.equals("zone")) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            sx = event.getRawX(); sy = event.getRawY();
                            ex = sx; ey = sy;
                            dragging = true;
                            invalidate();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            ex = event.getRawX(); ey = event.getRawY();
                            invalidate();
                            break;
                        case MotionEvent.ACTION_UP:
                            ex = event.getRawX(); ey = event.getRawY();
                            dragging = false;
                            int x1 = (int) Math.min(sx, ex), y1 = (int) Math.min(sy, ey);
                            int x2 = (int) Math.max(sx, ex), y2 = (int) Math.max(sy, ey);
                            if ((x2-x1) > 40 && (y2-y1) > 40) {
                                prefs.edit()
                                        .putInt("zone_x1", x1).putInt("zone_y1", y1)
                                        .putInt("zone_x2", x2).putInt("zone_y2", y2)
                                        .apply();
                                invalidate();
                                postDelayed(() -> {
                                    removeFloatingPanel();
                                    settingMode = null;
                                    Intent bk = new Intent(getApplicationContext(), MainActivity.class);
                                    bk.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    startActivity(bk);
                                    runOnUiThread(() -> {
                                        refreshCoordDisplays();
                                        addLog("✅ Zone saved: (" + x1+","+y1+") → ("+x2+","+y2+")");
                                        setStatus("✅ Zone set! Ab GO set karo.", "#00ff88");
                                        checkArmReady();
                                        Toast.makeText(getApplicationContext(),
                                                "✅ Zone saved!", Toast.LENGTH_SHORT).show();
                                    });
                                }, 500);
                            }
                            break;
                    }
                    return true;
                }
                return false;
            }
        };
        fl.setWillNotDraw(false);

        // Semi-transparent instruction bar at top
        LinearLayout topBar = new LinearLayout(this);
        topBar.setBackgroundColor(Color.parseColor("#DD000000"));
        topBar.setPadding(20, 16, 20, 16);
        topBar.setOrientation(LinearLayout.VERTICAL);

        TextView instrText = new TextView(this);
        if (mode.equals("zone")) {
            instrText.setText("📍 Car wali LANE pe DRAG karo\n(Finger press karke slide karo)");
        } else {
            instrText.setText("🎯 GO button pe TAP karo\n(Green GO button ki jagah pe ek tap)");
        }
        instrText.setTextColor(Color.parseColor("#00ff88"));
        instrText.setTextSize(18);
        instrText.setTypeface(null, android.graphics.Typeface.BOLD);
        instrText.setGravity(Gravity.CENTER);
        topBar.addView(instrText);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("✖  Cancel (Wapas jao)");
        cancelBtn.setBackgroundColor(Color.parseColor("#aa0000"));
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setTextSize(13);
        cancelBtn.setAllCaps(false);
        cancelBtn.setOnClickListener(v -> {
            removeFloatingPanel();
            settingMode = null;
            Intent bk = new Intent(getApplicationContext(), MainActivity.class);
            bk.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(bk);
        });
        topBar.addView(cancelBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.FrameLayout.LayoutParams tbParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        tbParams.gravity = Gravity.TOP;
        fl.addView(topBar, tbParams);

        return fl;
    }

    private void removeFloatingPanel() {
        if (overlayShowing && floatingPanel != null) {
            try { wm.removeView(floatingPanel); } catch (Exception ignored) {}
            floatingPanel = null;
            overlayShowing = false;
        }
    }

    // ════════════════════════════════════════════════════════════
    // ARM / STOP
    // ════════════════════════════════════════════════════════════
    private void doArm() {
        if (!AutoClickService.isRunning()) {
            Toast.makeText(this, "❌ Accessibility Service ON karo pehle!", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == MEDIA_PROJECTION_REQUEST && res == Activity.RESULT_OK && data != null) {
            Intent i = new Intent(AutoClickService.ACTION_ARM);
            i.putExtra("resultCode", res);
            i.putExtra("data", data);
            sendBroadcast(i);
            setStatus("🟢 ARMED! Car ka wait...", "#00ff88");
            armBtn.setEnabled(false);
            armBtn.setBackgroundColor(Color.parseColor("#005522"));
            stopBtn.setEnabled(true);
            addLog("🟢 Bot ARMED! GO: " + prefs.getInt("go_x",0) + "," + prefs.getInt("go_y",0));
        }
    }

    private void stopBot() {
        sendBroadcast(new Intent(AutoClickService.ACTION_STOP));
        setStatus("⏹ Stopped.", "#ff6666");
        armBtn.setEnabled(prefs.contains("go_x") && prefs.contains("zone_x1"));
        armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
        stopBtn.setEnabled(false);
        addLog("⏹ Bot reset.");
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════
    private void checkArmReady() {
        if (prefs.contains("go_x") && prefs.contains("zone_x1")) {
            armBtn.setEnabled(true);
            armBtn.setBackgroundColor(Color.parseColor("#00aa44"));
            if (!stopBtn.isEnabled())
                setStatus("✅ Ready! ARM dabao ab.", "#00ff88");
        }
    }

    private void checkAccessibility() {
        if (!AutoClickService.isRunning()) {
            setStatus("⚠️ Pehle Accessibility ON karo (neeche button)", "#ff4444");
            addLog("⚠️ Accessibility Service OFF hai");
        } else {
            setStatus("✅ Service Active! Zone aur GO set karo.", "#00ff88");
            addLog("✅ Service active hai!");
        }
    }

    private void refreshCoordDisplays() {
        if (prefs.contains("go_x")) {
            goCoordText.setText("✅ GO: (" + prefs.getInt("go_x",0) + ", " + prefs.getInt("go_y",0) + ")");
            goCoordText.setTextColor(Color.parseColor("#00ff88"));
        } else {
            goCoordText.setText("❌ Not set");
            goCoordText.setTextColor(Color.parseColor("#ff6666"));
        }
        if (prefs.contains("zone_x1")) {
            zoneCoordText.setText("✅ Zone: (" + prefs.getInt("zone_x1",0) + "," + prefs.getInt("zone_y1",0)
                    + ") → (" + prefs.getInt("zone_x2",0) + "," + prefs.getInt("zone_y2",0) + ")");
            zoneCoordText.setTextColor(Color.parseColor("#00ff88"));
        } else {
            zoneCoordText.setText("❌ Not set");
            zoneCoordText.setTextColor(Color.parseColor("#ff6666"));
        }
    }

    private void setStatus(String msg, String color) {
        statusText.setText(msg);
        statusText.setTextColor(Color.parseColor(color));
    }

    private void addLog(String msg) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date());
        logText.setText(logText.getText() + "\n[" + t + "] " + msg);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private Button bigBtn(LinearLayout p, String text, String color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setPadding(16, 8, 16, 8);
        p.addView(b, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private void label(LinearLayout p, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#00ccff"));
        tv.setTextSize(13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 4, 0, 4);
        p.addView(tv);
    }

    private void gap(LinearLayout p, int dp) {
        View v = new View(this);
        p.addView(v, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp));
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccessibility();
        checkArmReady();
        refreshCoordDisplays();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeFloatingPanel();
        try { unregisterReceiver(triggeredReceiver); } catch (Exception ignored) {}
    }
}

package com.meilit.halo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int BG = 0xFF101418, SURFACE = 0xFF172028, SURFACE_HIGH = 0xFF202B35;
    private static final int OUTLINE = 0xFF394854, TEXT = 0xFFE7F0F4, MUTED = 0xFFA7B6C1;
    private static final int PRIMARY = 0xFF7DD3FC, PRIMARY_CONTAINER = 0xFF103747;
    private static final int SECONDARY = 0xFFF0B7D4, TERTIARY = 0xFFC7DCA7, DANGER = 0xFFFFB4AB, DANGER_CONTAINER = 0xFF5F1D19;
    private static final String[] EFFECTS = {"0 Off", "1 White", "2 Red", "3 Green", "4 Blue", "5 Half white", "6 Breath white", "7 Breath red", "8 Breath green", "9 Breath blue", "10 Breath orange", "11 Breath yellow", "12 Breath pink", "13 Breath tender green", "14 Breath cyan", "15 Breath purple", "16 test.bin"};
    private static final int[] PRESET_COLORS = {0x00FFFFFF, 0x00FF1744, 0x0000E676, 0x002964FF, 0x0000E5FF, 0x00FFEA00, 0x00FF9100, 0x00FF4FD8, 0x00B388FF, 0x00A7FF83};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Aw20072Controller controller = new Aw20072Controller();
    private HaloEngine engine;
    private AudioManager audioManager;
    private HaloRingView ringView;
    private TextView statusView, selectedLedView, brightnessView, colorView, imaxView, volumeRhythmView;
    private Button[] ledButtons;
    private EditText regAddressInput, regValueInput;
    private ContentObserver volumeObserver;
    private Thread animationThread;
    private AtomicBoolean animationRunning = new AtomicBoolean(false);
    private int selectedLed = 1, brightness = 32, imax = 8, selectedColor = 0x00FF1744, lastMusicVolume = -1;
    private boolean volumeRhythmEnabled, i2cLogEnabled;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        engine = HaloEngine.get(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildContent());
        requestRuntimePermissions();
        refreshStatus();
    }

    @Override protected void onDestroy() {
        stopAnimation();
        stopVolumeRhythm();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(root);
        root.addView(header());
        root.addView(preview());
        root.addView(quick());
        root.addView(colorPanel());
        root.addView(ledPanel());
        root.addView(effectPanel());
        root.addView(playPanel());
        root.addView(backgroundPanel());
        root.addView(musicPanel());
        root.addView(notificationPanel());
        root.addView(volumePanel());
        root.addView(advancedPanel());
        return scroll;
    }

    private View header() {
        LinearLayout v = column();
        v.setPadding(dp(2), 0, dp(2), dp(12));
        v.addView(text("Meilit Halo", 30, TEXT, Typeface.BOLD));
        TextView sub = text("AW20072 / 16 RGB LEDs / sysfs + root", 14, MUTED, Typeface.NORMAL);
        sub.setPadding(0, dp(2), 0, dp(10));
        v.addView(sub);
        statusView = text("", 13, TEXT, Typeface.BOLD);
        statusView.setPadding(dp(12), dp(9), dp(12), dp(9));
        statusView.setBackground(round(PRIMARY_CONTAINER, dp(18), 0));
        v.addView(statusView, match());
        return v;
    }

    private View preview() {
        LinearLayout s = section("Preview");
        ringView = new HaloRingView(this);
        s.addView(ringView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(250)));
        return s;
    }

    private View quick() {
        LinearLayout s = section("Quick control");
        LinearLayout r1 = row();
        Button refresh = button("Refresh", PRIMARY_CONTAINER, PRIMARY);
        refresh.setOnClickListener(v -> refreshStatus());
        Button off = button("Off", DANGER_CONTAINER, DANGER);
        off.setOnClickListener(v -> run("effect=0", c -> c.setEffect(0), () -> ringView.clear()));
        r1.addView(refresh, weight()); r1.addView(off, weight()); s.addView(r1);
        LinearLayout r2 = row();
        Button all = button("All light", 0xFF243816, TERTIARY);
        all.setOnClickListener(v -> run("all_light", c -> c.setAllLight(brightness, selectedColor), () -> ringView.setAll(brightness, selectedColor)));
        Button clear = button("Clear stacked", SURFACE_HIGH, TEXT);
        clear.setOnClickListener(v -> run("alone_light off", c -> c.setAloneLight(0, 0, 0), () -> ringView.clear()));
        r2.addView(all, weight()); r2.addView(clear, weight()); s.addView(r2);
        Switch root = new Switch(this);
        root.setText("Prefer su writes"); root.setTextColor(TEXT); root.setTextSize(14);
        root.setOnCheckedChangeListener((b, checked) -> { controller.setForceRoot(checked); engine.setForceRoot(checked); refreshStatus(); });
        s.addView(root);
        return s;
    }

    private View colorPanel() {
        LinearLayout s = section("Color and brightness");
        colorView = text("", 18, TEXT, Typeface.BOLD); colorView.setGravity(Gravity.CENTER); colorView.setPadding(0, dp(14), 0, dp(14)); s.addView(colorView, match());
        LinearLayout swatches = row();
        for (int color : PRESET_COLORS) { Button b = new Button(this); b.setText(""); b.setMinHeight(dp(40)); b.setBackground(round(color | 0xFF000000, dp(20), 0x55FFFFFF)); b.setOnClickListener(v -> { selectedColor = color; updateColorPreview(); }); swatches.addView(b, weight()); }
        s.addView(swatches);
        s.addView(colorSeek("R", Color.red(selectedColor))); s.addView(colorSeek("G", Color.green(selectedColor))); s.addView(colorSeek("B", Color.blue(selectedColor)));
        brightnessView = text("", 14, MUTED, Typeface.BOLD); s.addView(brightnessView);
        SeekBar seek = new SeekBar(this); seek.setMax(62); seek.setProgress(brightness - 1); seek.setOnSeekBarChangeListener(simpleSeek(p -> { brightness = p + 1; updateBrightnessLabel(); })); s.addView(seek);
        updateColorPreview(); updateBrightnessLabel(); return s;
    }

    private View ledPanel() {
        LinearLayout s = section("LEDs"); selectedLedView = text("", 16, TEXT, Typeface.BOLD); s.addView(selectedLedView);
        ledButtons = new Button[Aw20072Controller.LED_COUNT];
        for (int r = 0; r < 4; r++) { LinearLayout row = row(); for (int c = 0; c < 4; c++) { int led = r * 4 + c + 1; Button b = button(String.valueOf(led), SURFACE_HIGH, TEXT); b.setOnClickListener(v -> selectLed(led)); ledButtons[led - 1] = b; row.addView(b, weight()); } s.addView(row); }
        LinearLayout actions = row();
        Button light = button("Exclusive", PRIMARY_CONTAINER, PRIMARY); light.setOnClickListener(v -> run("light", c -> c.setLight(selectedLed, brightness, selectedColor), () -> { ringView.clear(); ringView.setLed(selectedLed, brightness, selectedColor); }));
        Button alone = button("Stack", 0xFF3A2742, SECONDARY); alone.setOnClickListener(v -> run("alone_light", c -> c.setAloneLight(selectedLed, brightness, selectedColor), () -> ringView.setLed(selectedLed, brightness, selectedColor)));
        actions.addView(light, weight()); actions.addView(alone, weight()); s.addView(actions);
        Button off = button("Selected off", SURFACE_HIGH, TEXT); off.setOnClickListener(v -> run("alone_light 0", c -> c.setAloneLight(selectedLed, 0, selectedColor), () -> ringView.setLed(selectedLed, 0, selectedColor))); s.addView(off, match());
        selectLed(1); return s;
    }

    private View effectPanel() {
        LinearLayout s = section("Firmware effects"); Spinner spinner = new Spinner(this); ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, EFFECTS); a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(a); s.addView(spinner, match());
        Button apply = button("Play effect", PRIMARY_CONTAINER, PRIMARY); apply.setOnClickListener(v -> run("effect", c -> c.setEffect(spinner.getSelectedItemPosition()), null)); s.addView(apply, match()); return s;
    }

    private View playPanel() {
        LinearLayout s = section("Software play modes"); LinearLayout r1 = row();
        Button chase = button("Chase", PRIMARY_CONTAINER, PRIMARY); chase.setOnClickListener(v -> startChase());
        Button rainbow = button("Rainbow", 0xFF3A2742, SECONDARY); rainbow.setOnClickListener(v -> startRainbow());
        r1.addView(chase, weight()); r1.addView(rainbow, weight()); s.addView(r1);
        LinearLayout r2 = row(); Button breath = button("Breath", 0xFF243816, TERTIARY); breath.setOnClickListener(v -> startBreath()); Button stop = button("Stop", DANGER_CONTAINER, DANGER); stop.setOnClickListener(v -> { stopAnimation(); setStatus("Play mode stopped"); }); r2.addView(breath, weight()); r2.addView(stop, weight()); s.addView(r2); return s;
    }

    private View backgroundPanel() { LinearLayout s = section("Background engine"); LinearLayout r = row(); Button start = button("Start service", PRIMARY_CONTAINER, PRIMARY); start.setOnClickListener(v -> service(HaloForegroundService.ACTION_START)); Button stop = button("Stop service", DANGER_CONTAINER, DANGER); stop.setOnClickListener(v -> service(HaloForegroundService.ACTION_STOP)); r.addView(start, weight()); r.addView(stop, weight()); s.addView(r); Button battery = button("Battery whitelist", SURFACE_HIGH, TEXT); battery.setOnClickListener(v -> requestBattery()); s.addView(battery, match()); return s; }
    private View musicPanel() { LinearLayout s = section("Music rhythm"); LinearLayout r1 = row(); Button start = button("Start music", PRIMARY_CONTAINER, PRIMARY); start.setOnClickListener(v -> service(HaloForegroundService.ACTION_START_MUSIC)); Button stop = button("Stop music", DANGER_CONTAINER, DANGER); stop.setOnClickListener(v -> service(HaloForegroundService.ACTION_STOP_MUSIC)); r1.addView(start, weight()); r1.addView(stop, weight()); s.addView(r1); LinearLayout r2 = row(); Button test = button("Test rhythm", 0xFF243816, TERTIARY); test.setOnClickListener(v -> service(HaloForegroundService.ACTION_TEST_MUSIC)); Button fallback = button("Volume fallback", SURFACE_HIGH, TEXT); fallback.setOnClickListener(v -> service(HaloForegroundService.ACTION_START_VOLUME)); r2.addView(test, weight()); r2.addView(fallback, weight()); s.addView(r2); return s; }
    private View notificationPanel() { LinearLayout s = section("Notification rhythm"); LinearLayout r = row(); Button settings = button("Listener settings", PRIMARY_CONTAINER, PRIMARY); settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); Button test = button("Test notice", 0xFF3A2742, SECONDARY); test.setOnClickListener(v -> service(HaloForegroundService.ACTION_TEST_NOTIFICATION)); r.addView(settings, weight()); r.addView(test, weight()); s.addView(r); return s; }

    private View volumePanel() { LinearLayout s = section("Media volume fallback"); volumeRhythmView = text("", 14, MUTED, Typeface.BOLD); s.addView(volumeRhythmView); LinearLayout r = row(); Button start = button("Start listener", PRIMARY_CONTAINER, PRIMARY); start.setOnClickListener(v -> startVolumeRhythm()); Button stop = button("Stop listener", DANGER_CONTAINER, DANGER); stop.setOnClickListener(v -> { stopVolumeRhythm(); updateVolumeRhythmLabel(); setStatus("Media volume stopped"); }); r.addView(start, weight()); r.addView(stop, weight()); s.addView(r); Button apply = button("Apply current volume", SURFACE_HIGH, TEXT); apply.setOnClickListener(v -> applyVolumeRhythm(true)); s.addView(apply, match()); updateVolumeRhythmLabel(); return s; }

    private View advancedPanel() { LinearLayout s = section("Advanced nodes"); imaxView = text("", 14, MUTED, Typeface.BOLD); s.addView(imaxView); SeekBar imaxSeek = new SeekBar(this); imaxSeek.setMax(15); imaxSeek.setProgress(imax); imaxSeek.setOnSeekBarChangeListener(simpleSeek(p -> { imax = p; updateImaxLabel(); })); s.addView(imaxSeek);
        LinearLayout r1 = row(); Button imaxButton = button("Write IMAX", PRIMARY_CONTAINER, PRIMARY); imaxButton.setOnClickListener(v -> run("imax", c -> c.setImax(imax), null)); Button reset = button("HW reset", DANGER_CONTAINER, DANGER); reset.setOnClickListener(v -> run("hwen", c -> c.setHwen(1), () -> ringView.clear())); r1.addView(imaxButton, weight()); r1.addView(reset, weight()); s.addView(r1);
        LinearLayout r2 = row(); Button rgbColor = button("LED 6-bit", SURFACE_HIGH, TEXT); rgbColor.setOnClickListener(v -> run("rgbcolor", c -> c.setRgbColor(selectedLed, selectedColor), null)); Button allRgbColor = button("All 6-bit", SURFACE_HIGH, TEXT); allRgbColor.setOnClickListener(v -> run("allrgbcolor", c -> c.setAllRgbColor(selectedColor), null)); r2.addView(rgbColor, weight()); r2.addView(allRgbColor, weight()); s.addView(r2);
        LinearLayout r3 = row(); Button rgbBrightness = button("LED 8-bit", SURFACE_HIGH, TEXT); rgbBrightness.setOnClickListener(v -> run("rgbbrightness", c -> c.setRgbBrightness(selectedLed, selectedColor), null)); Button allRgbBrightness = button("All 8-bit", SURFACE_HIGH, TEXT); allRgbBrightness.setOnClickListener(v -> run("allrgbbrightness", c -> c.setAllRgbBrightness(selectedColor), null)); r3.addView(rgbBrightness, weight()); r3.addView(allRgbBrightness, weight()); s.addView(r3);
        LinearLayout r4 = row(); Button i2c = button("I2C Log: OFF", SURFACE_HIGH, TEXT); i2c.setOnClickListener(v -> { i2cLogEnabled = !i2cLogEnabled; i2c.setText(i2cLogEnabled ? "I2C Log: ON" : "I2C Log: OFF"); run("i2c_log", c -> c.setI2cLog(i2cLogEnabled ? 1 : 0), null); }); Button low = button("Reset low", SURFACE_HIGH, TEXT); low.setOnClickListener(v -> run("hwen low", c -> c.setHwen(0), () -> ringView.clear())); r4.addView(i2c, weight()); r4.addView(low, weight()); s.addView(r4);
        LinearLayout reg = row(); regAddressInput = edit("addr", "03"); regValueInput = edit("value", "80"); Button write = button("Write REG", PRIMARY_CONTAINER, PRIMARY); write.setOnClickListener(v -> writeReg()); reg.addView(regAddressInput, weight()); reg.addView(regValueInput, weight()); reg.addView(write, weight()); s.addView(reg); updateImaxLabel(); return s; }

    private View colorSeek(String label, int initial) { LinearLayout w = row(); TextView t = text(label, 13, MUTED, Typeface.BOLD); t.setGravity(Gravity.CENTER); w.addView(t, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT)); SeekBar sb = new SeekBar(this); sb.setMax(255); sb.setProgress(initial); sb.setTag(label); sb.setOnSeekBarChangeListener(simpleSeek(p -> updateColorFromSliders(w))); w.addView(sb, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); return w; }
    private void updateColorFromSliders(View source) { LinearLayout section = (LinearLayout) source.getParent(); selectedColor = Color.rgb(findProgress(section, "R"), findProgress(section, "G"), findProgress(section, "B")) & 0x00FFFFFF; updateColorPreview(); }
    private int findProgress(LinearLayout section, String tag) { for (int i = 0; i < section.getChildCount(); i++) { View row = section.getChildAt(i); if (!(row instanceof LinearLayout)) continue; LinearLayout lr = (LinearLayout) row; for (int j = 0; j < lr.getChildCount(); j++) { View child = lr.getChildAt(j); if (child instanceof SeekBar && tag.equals(child.getTag())) return ((SeekBar) child).getProgress(); } } return 0; }
    private void selectLed(int led) { selectedLed = led; ringView.setSelectedLed(led); selectedLedView.setText(String.format(Locale.US, "Selected LED: %d / 16", led)); updateLedButtons(); }
    private void updateBrightnessLabel() { brightnessView.setText(String.format(Locale.US, "Brightness: %d / 63", brightness)); }
    private void updateImaxLabel() { imaxView.setText(String.format(Locale.US, "IMAX: 0x%X / 0xF", imax)); }
    private void updateVolumeRhythmLabel() { if (volumeRhythmView != null) volumeRhythmView.setText(String.format(Locale.US, "Status: %s   media volume: %d / %d", volumeRhythmEnabled ? "listening" : "idle", getMusicVolume(), getMaxMusicVolume())); }
    private void updateColorPreview() { colorView.setText(String.format(Locale.US, "#%06X", selectedColor & 0x00FFFFFF)); colorView.setTextColor(isDark(selectedColor) ? Color.WHITE : Color.BLACK); colorView.setBackground(round(selectedColor | 0xFF000000, dp(24), 0x44FFFFFF)); }
    private void updateLedButtons() { if (ledButtons == null) return; for (int i = 0; i < ledButtons.length; i++) styleButton(ledButtons[i], (i + 1) == selectedLed ? PRIMARY_CONTAINER : SURFACE_HIGH, (i + 1) == selectedLed ? PRIMARY : TEXT); }
    private void refreshStatus() { setStatus((controller.isPresent() ? "Device nodes ready" : "Device nodes not found") + " / " + controller.describeAvailability()); }
    private void writeReg() { try { int addr = Integer.parseInt(regAddressInput.getText().toString().trim(), 16); int value = Integer.parseInt(regValueInput.getText().toString().trim(), 16); run("reg", c -> c.setReg(addr, value), null); } catch (NumberFormatException e) { setStatus("Failed: reg addr/value must be hex"); } }
    private void run(String label, ControllerCall call, Runnable onSuccess) { stopAnimation(); setStatus(label + " writing..."); ioExecutor.execute(() -> { Aw20072Controller.CommandResult result = call.run(controller); mainHandler.post(() -> { setStatus((result.ok ? "OK: " : "Failed: ") + result.message); if (result.ok && onSuccess != null) onSuccess.run(); }); }); }
    private void service(String action) { Intent intent = new Intent(this, HaloForegroundService.class); intent.setAction(action); if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent); setStatus("Service action: " + action); }
    private void requestBattery() { if (Build.VERSION.SDK_INT >= 23) { Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); intent.setData(Uri.parse("package:" + getPackageName())); startActivity(intent); } }
    private void requestRuntimePermissions() { if (Build.VERSION.SDK_INT < 23) return; List<String> p = new ArrayList<>(); if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.RECORD_AUDIO); if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS); if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 7202); }

    private void startChase() { startAnimation("chase", running -> { int previous = 0; while (running.get()) for (int led = 1; led <= Aw20072Controller.LED_COUNT && running.get(); led++) { if (previous > 0 && !handle(controller.setAloneLight(previous, 0, selectedColor), running)) return; if (!handle(controller.setAloneLight(led, brightness, selectedColor), running)) return; int current = led, faded = previous; mainHandler.post(() -> { if (faded > 0) ringView.setLed(faded, 0, selectedColor); ringView.setLed(current, brightness, selectedColor); }); previous = led; sleep(80, running); } }); }
    private void startRainbow() { startAnimation("rainbow", running -> { int step = 0; while (running.get()) { int[] colors = new int[Aw20072Controller.LED_COUNT], levels = new int[Aw20072Controller.LED_COUNT]; for (int led = 1; led <= Aw20072Controller.LED_COUNT && running.get(); led++) { int color = Color.HSVToColor(new float[]{(step * 12 + led * 22.5f) % 360f, 0.95f, 1f}) & 0x00FFFFFF; if (!handle(controller.setAloneLight(led, brightness, color), running)) return; colors[led - 1] = color; levels[led - 1] = brightness; } mainHandler.post(() -> ringView.setSnapshot(colors, levels)); step++; sleep(180, running); } }); }
    private void startBreath() { startAnimation("breath", running -> { int level = 1, direction = 1; while (running.get()) { if (!handle(controller.setAllLight(level, selectedColor), running)) return; int current = level; mainHandler.post(() -> ringView.setAll(current, selectedColor)); level += direction; if (level >= brightness) { level = brightness; direction = -1; } else if (level <= 1) { level = 1; direction = 1; } sleep(35, running); } }); }
    private void startAnimation(String label, AnimationLoop loop) { stopAnimation(); AtomicBoolean running = new AtomicBoolean(true); animationRunning = running; animationThread = new Thread(() -> loop.run(running), "aw20072-" + label); animationThread.start(); setStatus(label + " running"); }
    private void stopAnimation() { animationRunning.set(false); if (animationThread != null) { animationThread.interrupt(); animationThread = null; } }
    private void startVolumeRhythm() { stopAnimation(); if (volumeRhythmEnabled) { applyVolumeRhythm(true); return; } volumeRhythmEnabled = true; lastMusicVolume = -1; volumeObserver = new ContentObserver(mainHandler) { @Override public void onChange(boolean selfChange) { onVolumeMaybeChanged(); } }; getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver); updateVolumeRhythmLabel(); applyVolumeRhythm(true); setStatus("Media volume listening"); }
    private void stopVolumeRhythm() { volumeRhythmEnabled = false; if (volumeObserver != null) { getContentResolver().unregisterContentObserver(volumeObserver); volumeObserver = null; } }
    private void onVolumeMaybeChanged() { if (!volumeRhythmEnabled) return; int current = getMusicVolume(); if (current == lastMusicVolume) { updateVolumeRhythmLabel(); return; } applyVolumeRhythm(false); }
    private void applyVolumeRhythm(boolean force) { int current = getMusicVolume(); if (!force && current == lastMusicVolume) return; lastMusicVolume = current; int max = getMaxMusicVolume(); float ratio = Math.max(0f, Math.min(1f, current / (float) max)); int level = Math.max(1, Math.round(8 + ratio * 55)); int color = Color.HSVToColor(new float[]{200f - ratio * 165f, 0.82f, 1f}) & 0x00FFFFFF; updateVolumeRhythmLabel(); ioExecutor.execute(() -> { Aw20072Controller.CommandResult result = controller.setAllLight(level, color); mainHandler.post(() -> { if (result.ok) { ringView.setAll(level, color); setStatus(String.format(Locale.US, "Media volume: %d/%d -> brightness %d", current, max, level)); } else setStatus("Media volume failed: " + result.message); }); }); }
    private int getMusicVolume() { return audioManager == null ? 0 : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC); }
    private int getMaxMusicVolume() { return audioManager == null ? 1 : Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)); }
    private boolean handle(Aw20072Controller.CommandResult result, AtomicBoolean running) { if (result.ok) return true; running.set(false); mainHandler.post(() -> setStatus("Play failed: " + result.message)); return false; }
    private void sleep(long ms, AtomicBoolean running) { if (!running.get()) return; try { Thread.sleep(ms); } catch (InterruptedException e) { running.set(false); Thread.currentThread().interrupt(); } }

    private LinearLayout section(String title) { LinearLayout s = column(); s.setPadding(dp(14), dp(12), dp(14), dp(14)); s.setBackground(round(SURFACE, dp(22), OUTLINE)); TextView titleView = text(title, 17, TEXT, Typeface.BOLD); titleView.setPadding(0, 0, 0, dp(10)); s.addView(titleView); LinearLayout.LayoutParams p = match(); p.setMargins(0, dp(12), 0, 0); s.setLayoutParams(p); return s; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(0, dp(4), 0, dp(4)); return l; }
    private Button button(String label, int bg, int fg) { Button b = new Button(this); b.setText(label); b.setTextSize(14); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setGravity(Gravity.CENTER); styleButton(b, bg, fg); return b; }
    private void styleButton(Button b, int bg, int fg) { b.setTextColor(fg); b.setBackground(round(bg, dp(20), 0)); }
    private EditText edit(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setSingleLine(true); e.setInputType(InputType.TYPE_CLASS_TEXT); e.setPadding(dp(12), 0, dp(12), 0); e.setMinHeight(dp(48)); e.setBackground(round(SURFACE_HIGH, dp(16), OUTLINE)); return e; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); if (stroke != 0) d.setStroke(dp(1), stroke); return d; }
    private TextView text(String value, int sp, int color, int style) { TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(Typeface.DEFAULT, style); return t; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(4), dp(4), dp(4), dp(4)); return p; }
    private LinearLayout.LayoutParams match() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(4), 0, dp(4)); return p; }
    private SeekBar.OnSeekBarChangeListener simpleSeek(ProgressChanged changed) { return new SeekBar.OnSeekBarChangeListener() { @Override public void onProgressChanged(SeekBar s, int p, boolean f) { changed.onProgressChanged(p); } @Override public void onStartTrackingTouch(SeekBar s) {} @Override public void onStopTrackingTouch(SeekBar s) {} }; }
    private void setStatus(String status) { statusView.setText(status); }
    private boolean isDark(int color) { return Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 < 150; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private interface ControllerCall { Aw20072Controller.CommandResult run(Aw20072Controller controller); }
    private interface AnimationLoop { void run(AtomicBoolean running); }
    private interface ProgressChanged { void onProgressChanged(int progress); }
}

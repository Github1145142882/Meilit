package com.meilit.halo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xFF101418;
    private static final int PANEL = 0xFF18212A;
    private static final int TEXT = 0xFFE8EEF2;
    private static final int MUTED = 0xFFAAB7C4;
    private static final int ACCENT = 0xFF00B8D4;
    private static final int DANGER = 0xFFFF5252;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Aw20072Controller controller = new Aw20072Controller();
    private final AtomicBoolean animationRunning = new AtomicBoolean(false);

    private TextView statusView;
    private TextView ledLabel;
    private TextView brightnessLabel;
    private TextView colorLabel;
    private HaloRingView ringView;

    private int selectedLed = 1;
    private int brightness = 32;
    private int selectedColor = 0x00FF0000;
    private Thread animationThread;

    private static final String[] EFFECTS = {
            "0 关灯", "1 白色常亮", "2 红色常亮", "3 绿色常亮", "4 蓝色常亮",
            "5 半亮白色", "6 白色呼吸", "7 红色呼吸", "8 绿色呼吸", "9 蓝色呼吸",
            "10 橙色呼吸", "11 黄色呼吸", "12 粉色呼吸", "13 嫩绿呼吸",
            "14 青色呼吸", "15 紫色呼吸", "16 test.bin"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        setContentView(buildContent());
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        stopAnimation();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);

        root.addView(text("灵动光环 AW20072", 24, TEXT, Typeface.BOLD));
        statusView = text("", 13, MUTED, Typeface.NORMAL);
        statusView.setPadding(0, dp(6), 0, dp(12));
        root.addView(statusView);

        ringView = new HaloRingView(this);
        root.addView(ringView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));

        root.addView(deviceSection());
        root.addView(colorSection());
        root.addView(singleLedSection());
        root.addView(allLedSection());
        root.addView(effectSection());
        root.addView(playSection());
        root.addView(advancedSection());
        return scroll;
    }

    private View deviceSection() {
        LinearLayout s = section("设备");
        LinearLayout row = row();
        Button refresh = button("刷新", ACCENT);
        refresh.setOnClickListener(v -> refreshStatus());
        Button off = button("关灯", DANGER);
        off.setOnClickListener(v -> runCommand("关灯", () -> controller.setEffect(0), () -> ringView.clear()));
        row.addView(refresh, weight());
        row.addView(off, weight());
        s.addView(row);

        Switch rootSwitch = new Switch(this);
        rootSwitch.setText("使用 su 写入");
        rootSwitch.setTextColor(TEXT);
        rootSwitch.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> {
            controller.setForceRoot(checked);
            refreshStatus();
        });
        s.addView(rootSwitch);
        return s;
    }

    private View colorSection() {
        LinearLayout s = section("颜色");
        colorLabel = text("", 14, Color.WHITE, Typeface.BOLD);
        colorLabel.setGravity(Gravity.CENTER);
        colorLabel.setPadding(0, dp(10), 0, dp(10));
        s.addView(colorLabel, match());
        s.addView(colorSeek("R", 255));
        s.addView(colorSeek("G", 0));
        s.addView(colorSeek("B", 0));
        updateColorLabel();
        return s;
    }

    private View singleLedSection() {
        LinearLayout s = section("单颗灯珠");
        ledLabel = text("", 15, TEXT, Typeface.BOLD);
        s.addView(ledLabel);
        for (int r = 0; r < 4; r++) {
            LinearLayout row = row();
            for (int c = 0; c < 4; c++) {
                int led = r * 4 + c + 1;
                Button b = button(String.valueOf(led), 0xFF263340);
                b.setOnClickListener(v -> selectLed(led));
                row.addView(b, weight());
            }
            s.addView(row);
        }
        brightnessLabel = text("", 13, MUTED, Typeface.NORMAL);
        s.addView(brightnessLabel);
        SeekBar seek = new SeekBar(this);
        seek.setMax(62);
        seek.setProgress(brightness - 1);
        seek.setOnSeekBarChangeListener(seek(progress -> {
            brightness = progress + 1;
            updateBrightnessLabel();
        }));
        s.addView(seek);

        LinearLayout row = row();
        Button light = button("独占点亮", ACCENT);
        light.setOnClickListener(v -> runCommand("light", () -> controller.setLight(selectedLed, brightness, selectedColor), () -> {
            ringView.clear();
            ringView.setLed(selectedLed, brightness, selectedColor);
        }));
        Button alone = button("叠加点亮", ACCENT);
        alone.setOnClickListener(v -> runCommand("alone_light", () -> controller.setAloneLight(selectedLed, brightness, selectedColor), () -> ringView.setLed(selectedLed, brightness, selectedColor)));
        row.addView(light, weight());
        row.addView(alone, weight());
        s.addView(row);
        selectLed(1);
        updateBrightnessLabel();
        return s;
    }

    private View allLedSection() {
        LinearLayout s = section("全部灯珠");
        LinearLayout row = row();
        Button all = button("全部点亮", ACCENT);
        all.setOnClickListener(v -> runCommand("all_light", () -> controller.setAllLight(brightness, selectedColor), () -> ringView.setAll(brightness, selectedColor)));
        Button clear = button("清空叠加", 0xFF455A64);
        clear.setOnClickListener(v -> runCommand("alone_light off", () -> controller.setAloneLight(0, 0, 0), () -> ringView.clear()));
        row.addView(all, weight());
        row.addView(clear, weight());
        s.addView(row);
        return s;
    }

    private View effectSection() {
        LinearLayout s = section("固件效果");
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, EFFECTS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        s.addView(spinner, match());
        Button apply = button("播放效果", ACCENT);
        apply.setOnClickListener(v -> runCommand("effect", () -> controller.setEffect(spinner.getSelectedItemPosition()), null));
        s.addView(apply, match());
        return s;
    }

    private View playSection() {
        LinearLayout s = section("玩法");
        LinearLayout row = row();
        Button chase = button("流光", ACCENT);
        chase.setOnClickListener(v -> startChase());
        Button breath = button("呼吸", ACCENT);
        breath.setOnClickListener(v -> startBreath());
        Button stop = button("停止", DANGER);
        stop.setOnClickListener(v -> stopAnimation());
        row.addView(chase, weight());
        row.addView(breath, weight());
        row.addView(stop, weight());
        s.addView(row);
        return s;
    }

    private View advancedSection() {
        LinearLayout s = section("高级");
        LinearLayout row = row();
        Button imax = button("IMAX 0x8", ACCENT);
        imax.setOnClickListener(v -> runCommand("imax", () -> controller.setImax(8), null));
        Button reset = button("硬复位", DANGER);
        reset.setOnClickListener(v -> runCommand("hwen", () -> controller.setHwen(1), () -> ringView.clear()));
        row.addView(imax, weight());
        row.addView(reset, weight());
        s.addView(row);
        return s;
    }

    private View colorSeek(String tag, int initial) {
        LinearLayout row = row();
        TextView label = text(tag, 13, MUTED, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        row.addView(label, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));
        SeekBar seek = new SeekBar(this);
        seek.setTag(tag);
        seek.setMax(255);
        seek.setProgress(initial);
        seek.setOnSeekBarChangeListener(seek(progress -> updateColorFromSliders((LinearLayout) row.getParent())));
        row.addView(seek, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void updateColorFromSliders(LinearLayout section) {
        int red = slider(section, "R");
        int green = slider(section, "G");
        int blue = slider(section, "B");
        selectedColor = Color.rgb(red, green, blue) & 0x00FFFFFF;
        updateColorLabel();
    }

    private int slider(LinearLayout section, String tag) {
        if (section == null) return 0;
        for (int i = 0; i < section.getChildCount(); i++) {
            View child = section.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) child;
            for (int j = 0; j < row.getChildCount(); j++) {
                View item = row.getChildAt(j);
                if (item instanceof SeekBar && tag.equals(item.getTag())) return ((SeekBar) item).getProgress();
            }
        }
        return 0;
    }

    private void startChase() {
        startAnimation(() -> {
            while (animationRunning.get()) {
                for (int led = 1; led <= Aw20072Controller.LED_COUNT && animationRunning.get(); led++) {
                    int current = led;
                    Aw20072Controller.CommandResult result = controller.setLight(current, brightness, selectedColor);
                    if (!result.ok) {
                        postStatus("玩法失败: " + result.message);
                        animationRunning.set(false);
                        return;
                    }
                    mainHandler.post(() -> {
                        ringView.clear();
                        ringView.setLed(current, brightness, selectedColor);
                    });
                    sleep(90);
                }
            }
        });
    }

    private void startBreath() {
        startAnimation(() -> {
            int level = 1;
            int direction = 1;
            while (animationRunning.get()) {
                Aw20072Controller.CommandResult result = controller.setAllLight(level, selectedColor);
                if (!result.ok) {
                    postStatus("玩法失败: " + result.message);
                    animationRunning.set(false);
                    return;
                }
                int current = level;
                mainHandler.post(() -> ringView.setAll(current, selectedColor));
                level += direction;
                if (level >= brightness) direction = -1;
                if (level <= 1) direction = 1;
                sleep(35);
            }
        });
    }

    private void startAnimation(Runnable runnable) {
        stopAnimation();
        animationRunning.set(true);
        animationThread = new Thread(runnable, "aw20072-play");
        animationThread.start();
        setStatus("玩法运行中");
    }

    private void stopAnimation() {
        animationRunning.set(false);
        if (animationThread != null) animationThread.interrupt();
        animationThread = null;
        if (statusView != null) setStatus("玩法已停止");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            animationRunning.set(false);
            Thread.currentThread().interrupt();
        }
    }

    private void runCommand(String label, ControllerCall call, Runnable onSuccess) {
        stopAnimation();
        setStatus(label + " ...");
        ioExecutor.execute(() -> {
            Aw20072Controller.CommandResult result = call.run();
            mainHandler.post(() -> {
                setStatus((result.ok ? "OK: " : "失败: ") + result.message);
                if (result.ok && onSuccess != null) onSuccess.run();
            });
        });
    }

    private void selectLed(int led) {
        selectedLed = led;
        if (ringView != null) ringView.setSelectedLed(led);
        if (ledLabel != null) ledLabel.setText(String.format(Locale.US, "当前灯珠: %d / 16", led));
    }

    private void refreshStatus() {
        setStatus((controller.isPresent() ? "已检测到节点" : "未检测到节点") + "   " + controller.describeAvailability());
    }

    private void updateBrightnessLabel() {
        brightnessLabel.setText(String.format(Locale.US, "亮度: %d / 63", brightness));
    }

    private void updateColorLabel() {
        String hex = String.format(Locale.US, "#%06X", selectedColor & 0x00FFFFFF);
        colorLabel.setText(hex);
        colorLabel.setTextColor(isDark(selectedColor) ? Color.WHITE : Color.BLACK);
        colorLabel.setBackgroundColor(selectedColor | 0xFF000000);
    }

    private LinearLayout section(String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(12), dp(14), dp(14));
        section.setBackground(panelBackground());
        TextView titleView = text(title, 16, TEXT, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, dp(8));
        section.addView(titleView);
        LinearLayout.LayoutParams params = match();
        params.setMargins(0, dp(12), 0, 0);
        section.setLayoutParams(params);
        return section;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(6));
        button.setBackground(drawable);
        return button;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        return text;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(PANEL);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), 0xFF2E3C48);
        return drawable;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private SeekBar.OnSeekBarChangeListener seek(ProgressChanged changed) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { changed.onProgressChanged(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void postStatus(String status) {
        mainHandler.post(() -> setStatus(status));
    }

    private void setStatus(String status) {
        statusView.setText(status);
    }

    private boolean isDark(int color) {
        return Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 < 150;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ControllerCall { Aw20072Controller.CommandResult run(); }
    private interface ProgressChanged { void onProgressChanged(int progress); }
}

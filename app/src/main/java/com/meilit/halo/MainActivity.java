package com.meilit.halo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
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
    private static final int BG = 0xFF101418;
    private static final int SURFACE = 0xFF172028;
    private static final int SURFACE_HIGH = 0xFF202B35;
    private static final int OUTLINE = 0xFF394854;
    private static final int TEXT = 0xFFE7F0F4;
    private static final int MUTED = 0xFFA7B6C1;
    private static final int PRIMARY = 0xFF7DD3FC;
    private static final int PRIMARY_CONTAINER = 0xFF103747;
    private static final int SECONDARY = 0xFFF0B7D4;
    private static final int TERTIARY = 0xFFC7DCA7;
    private static final int DANGER = 0xFFFFB4AB;
    private static final int DANGER_CONTAINER = 0xFF5F1D19;

    private static final String[] EFFECTS = {
            "0 关灯", "1 白色常亮", "2 红色常亮", "3 绿色常亮", "4 蓝色常亮",
            "5 半亮白色", "6 白色呼吸", "7 红色呼吸", "8 绿色呼吸", "9 蓝色呼吸",
            "10 橙色呼吸", "11 黄色呼吸", "12 粉色呼吸", "13 嫩绿呼吸",
            "14 青色呼吸", "15 紫色呼吸", "16 test.bin"
    };

    private static final int[] PRESET_COLORS = {
            0x00FFFFFF, 0x00FF1744, 0x0000E676, 0x002964FF, 0x0000E5FF,
            0x00FFEA00, 0x00FF9100, 0x00FF4FD8, 0x00B388FF, 0x00A7FF83
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Aw20072Controller controller = new Aw20072Controller();

    private HaloRingView ringView;
    private TextView statusView;
    private TextView selectedLedView;
    private TextView brightnessView;
    private TextView colorView;
    private TextView imaxView;
    private Button[] ledButtons;
    private EditText regAddressInput;
    private EditText regValueInput;

    private int selectedLed = 1;
    private int brightness = 32;
    private int imax = 8;
    private int selectedColor = 0x00FF1744;
    private boolean i2cLogEnabled;
    private Thread animationThread;
    private AtomicBoolean animationRunning = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
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
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(header());
        root.addView(buildPreviewSection());
        root.addView(buildQuickActionsSection());
        root.addView(buildColorSection());
        root.addView(buildLedSection());
        root.addView(buildEffectSection());
        root.addView(buildPlaySection());
        root.addView(buildAdvancedSection());
        return scroll;
    }

    private View header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(2), 0, dp(2), dp(12));

        TextView title = text("灵动光环", 30, TEXT, Typeface.BOLD);
        header.addView(title);

        TextView subtitle = text("AW20072 · 16 组 RGB 灯珠控制台", 14, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(2), 0, dp(10));
        header.addView(subtitle);

        statusView = text("", 13, TEXT, Typeface.BOLD);
        statusView.setPadding(dp(12), dp(9), dp(12), dp(9));
        statusView.setBackground(roundRect(PRIMARY_CONTAINER, dp(18), 0));
        header.addView(statusView, matchParams());
        return header;
    }

    private View buildPreviewSection() {
        LinearLayout section = section("预览");
        ringView = new HaloRingView(this);
        section.addView(ringView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(250)));
        return section;
    }

    private View buildQuickActionsSection() {
        LinearLayout section = section("快捷控制");
        LinearLayout firstRow = row();
        Button refresh = button("刷新", PRIMARY_CONTAINER, PRIMARY);
        refresh.setOnClickListener(v -> refreshStatus());
        Button off = button("关灯", DANGER_CONTAINER, DANGER);
        off.setOnClickListener(v -> runController("effect=0", () -> controller.setEffect(0), () -> ringView.clear()));
        firstRow.addView(refresh, weightParams());
        firstRow.addView(off, weightParams());
        section.addView(firstRow);

        LinearLayout secondRow = row();
        Button all = button("全部点亮", 0xFF243816, TERTIARY);
        all.setOnClickListener(v -> runController("all_light", () -> controller.setAllLight(brightness, selectedColor), () -> ringView.setAll(brightness, selectedColor)));
        Button clear = button("清空叠加", SURFACE_HIGH, TEXT);
        clear.setOnClickListener(v -> runController("alone_light off", () -> controller.setAloneLight(0, 0, 0), () -> ringView.clear()));
        secondRow.addView(all, weightParams());
        secondRow.addView(clear, weightParams());
        section.addView(secondRow);

        Switch rootSwitch = new Switch(this);
        rootSwitch.setText("优先使用 su 写入");
        rootSwitch.setTextColor(TEXT);
        rootSwitch.setTextSize(14);
        rootSwitch.setPadding(dp(4), dp(8), 0, 0);
        rootSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            controller.setForceRoot(isChecked);
            refreshStatus();
        });
        section.addView(rootSwitch);
        return section;
    }

    private View buildColorSection() {
        LinearLayout section = section("颜色与亮度");
        colorView = text("", 18, TEXT, Typeface.BOLD);
        colorView.setGravity(Gravity.CENTER);
        colorView.setPadding(0, dp(14), 0, dp(14));
        section.addView(colorView, matchParams());

        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setPadding(0, dp(8), 0, dp(10));
        for (int color : PRESET_COLORS) {
            Button swatch = new Button(this);
            swatch.setText("");
            swatch.setMinHeight(dp(40));
            swatch.setBackground(roundRect(color | 0xFF000000, dp(20), 0x55FFFFFF));
            swatch.setOnClickListener(v -> {
                selectedColor = color;
                updateColorPreview();
            });
            swatches.addView(swatch, weightParams());
        }
        section.addView(swatches);

        section.addView(colorSeek("R", Color.red(selectedColor)));
        section.addView(colorSeek("G", Color.green(selectedColor)));
        section.addView(colorSeek("B", Color.blue(selectedColor)));

        brightnessView = text("", 14, MUTED, Typeface.BOLD);
        brightnessView.setPadding(0, dp(8), 0, 0);
        section.addView(brightnessView);
        SeekBar brightnessSeek = new SeekBar(this);
        brightnessSeek.setMax(62);
        brightnessSeek.setProgress(brightness - 1);
        brightnessSeek.setOnSeekBarChangeListener(simpleSeek(progress -> {
            brightness = progress + 1;
            updateBrightnessLabel();
        }));
        section.addView(brightnessSeek);

        updateColorPreview();
        updateBrightnessLabel();
        return section;
    }

    private View buildLedSection() {
        LinearLayout section = section("灯珠");
        selectedLedView = text("", 16, TEXT, Typeface.BOLD);
        selectedLedView.setPadding(0, 0, 0, dp(8));
        section.addView(selectedLedView);

        ledButtons = new Button[Aw20072Controller.LED_COUNT];
        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout gridRow = row();
            for (int column = 0; column < 4; column++) {
                int led = rowIndex * 4 + column + 1;
                Button button = button(String.valueOf(led), SURFACE_HIGH, TEXT);
                button.setMinHeight(dp(46));
                button.setOnClickListener(v -> selectLed(led));
                ledButtons[led - 1] = button;
                gridRow.addView(button, weightParams());
            }
            section.addView(gridRow);
        }

        LinearLayout actions = row();
        Button light = button("独占点亮", PRIMARY_CONTAINER, PRIMARY);
        light.setOnClickListener(v -> runController("light", () -> controller.setLight(selectedLed, brightness, selectedColor), () -> {
            ringView.clear();
            ringView.setLed(selectedLed, brightness, selectedColor);
        }));
        Button alone = button("叠加点亮", 0xFF3A2742, SECONDARY);
        alone.setOnClickListener(v -> runController("alone_light", () -> controller.setAloneLight(selectedLed, brightness, selectedColor), () -> ringView.setLed(selectedLed, brightness, selectedColor)));
        actions.addView(light, weightParams());
        actions.addView(alone, weightParams());
        section.addView(actions);

        Button clearSelected = button("熄灭当前灯珠", SURFACE_HIGH, TEXT);
        clearSelected.setOnClickListener(v -> runController("alone_light brightness 0", () -> controller.setAloneLight(selectedLed, 0, selectedColor), () -> ringView.setLed(selectedLed, 0, selectedColor)));
        section.addView(clearSelected, matchParams());

        selectLed(1);
        return section;
    }

    private View buildEffectSection() {
        LinearLayout section = section("固件效果");
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, EFFECTS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        section.addView(spinner, matchParams());

        Button apply = button("播放固件效果", PRIMARY_CONTAINER, PRIMARY);
        apply.setOnClickListener(v -> runController("effect", () -> controller.setEffect(spinner.getSelectedItemPosition()), null));
        section.addView(apply, matchParams());
        return section;
    }

    private View buildPlaySection() {
        LinearLayout section = section("玩法");
        LinearLayout firstRow = row();
        Button chase = button("流光", PRIMARY_CONTAINER, PRIMARY);
        chase.setOnClickListener(v -> startChase());
        Button rainbow = button("彩虹旋转", 0xFF3A2742, SECONDARY);
        rainbow.setOnClickListener(v -> startRainbow());
        firstRow.addView(chase, weightParams());
        firstRow.addView(rainbow, weightParams());
        section.addView(firstRow);

        LinearLayout secondRow = row();
        Button breath = button("呼吸", 0xFF243816, TERTIARY);
        breath.setOnClickListener(v -> startBreath());
        Button stop = button("停止玩法", DANGER_CONTAINER, DANGER);
        stop.setOnClickListener(v -> {
            stopAnimation();
            setStatus("玩法已停止");
        });
        secondRow.addView(breath, weightParams());
        secondRow.addView(stop, weightParams());
        section.addView(secondRow);
        return section;
    }

    private View buildAdvancedSection() {
        LinearLayout section = section("高级节点");
        imaxView = text("", 14, MUTED, Typeface.BOLD);
        imaxView.setPadding(0, 0, 0, dp(4));
        section.addView(imaxView);
        SeekBar imaxSeek = new SeekBar(this);
        imaxSeek.setMax(15);
        imaxSeek.setProgress(imax);
        imaxSeek.setOnSeekBarChangeListener(simpleSeek(progress -> {
            imax = progress;
            updateImaxLabel();
        }));
        section.addView(imaxSeek);

        LinearLayout nodeRowOne = row();
        Button imaxButton = button("写 IMAX", PRIMARY_CONTAINER, PRIMARY);
        imaxButton.setOnClickListener(v -> runController("imax", () -> controller.setImax(imax), null));
        Button reset = button("硬复位", DANGER_CONTAINER, DANGER);
        reset.setOnClickListener(v -> runController("hwen", () -> controller.setHwen(1), () -> ringView.clear()));
        nodeRowOne.addView(imaxButton, weightParams());
        nodeRowOne.addView(reset, weightParams());
        section.addView(nodeRowOne);

        LinearLayout colorNodeRow = row();
        Button rgbColor = button("单灯6bit色", SURFACE_HIGH, TEXT);
        rgbColor.setOnClickListener(v -> runController("rgbcolor", () -> controller.setRgbColor(selectedLed, selectedColor), null));
        Button allRgbColor = button("全灯6bit色", SURFACE_HIGH, TEXT);
        allRgbColor.setOnClickListener(v -> runController("allrgbcolor", () -> controller.setAllRgbColor(selectedColor), null));
        colorNodeRow.addView(rgbColor, weightParams());
        colorNodeRow.addView(allRgbColor, weightParams());
        section.addView(colorNodeRow);

        LinearLayout brightNodeRow = row();
        Button rgbBrightness = button("单灯8bit亮度", SURFACE_HIGH, TEXT);
        rgbBrightness.setOnClickListener(v -> runController("rgbbrightness", () -> controller.setRgbBrightness(selectedLed, selectedColor), null));
        Button allRgbBrightness = button("全灯8bit亮度", SURFACE_HIGH, TEXT);
        allRgbBrightness.setOnClickListener(v -> runController("allrgbbrightness", () -> controller.setAllRgbBrightness(selectedColor), null));
        brightNodeRow.addView(rgbBrightness, weightParams());
        brightNodeRow.addView(allRgbBrightness, weightParams());
        section.addView(brightNodeRow);

        LinearLayout logRow = row();
        Button i2cLog = button("I2C Log: OFF", SURFACE_HIGH, TEXT);
        i2cLog.setOnClickListener(v -> {
            i2cLogEnabled = !i2cLogEnabled;
            i2cLog.setText(i2cLogEnabled ? "I2C Log: ON" : "I2C Log: OFF");
            runController("i2c_log", () -> controller.setI2cLog(i2cLogEnabled ? 1 : 0), null);
        });
        Button hwenLow = button("拉低复位", SURFACE_HIGH, TEXT);
        hwenLow.setOnClickListener(v -> runController("hwen low", () -> controller.setHwen(0), () -> ringView.clear()));
        logRow.addView(i2cLog, weightParams());
        logRow.addView(hwenLow, weightParams());
        section.addView(logRow);

        TextView regLabel = text("寄存器写入（十六进制）", 14, MUTED, Typeface.BOLD);
        regLabel.setPadding(0, dp(10), 0, dp(4));
        section.addView(regLabel);
        LinearLayout regRow = row();
        regAddressInput = edit("addr", "03");
        regValueInput = edit("value", "80");
        Button regWrite = button("写 REG", PRIMARY_CONTAINER, PRIMARY);
        regWrite.setOnClickListener(v -> writeReg());
        regRow.addView(regAddressInput, weightParams());
        regRow.addView(regValueInput, weightParams());
        regRow.addView(regWrite, weightParams());
        section.addView(regRow);

        updateImaxLabel();
        return section;
    }

    private View colorSeek(String label, int initialProgress) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setPadding(0, dp(4), 0, dp(4));
        TextView text = text(label, 13, MUTED, Typeface.BOLD);
        text.setGravity(Gravity.CENTER);
        wrapper.addView(text, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(255);
        seekBar.setProgress(initialProgress);
        seekBar.setTag(label);
        seekBar.setOnSeekBarChangeListener(simpleSeek(progress -> updateColorFromSliders(wrapper)));
        wrapper.addView(seekBar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return wrapper;
    }

    private void updateColorFromSliders(View source) {
        LinearLayout section = (LinearLayout) source.getParent();
        selectedColor = Color.rgb(findColorProgress(section, "R"), findColorProgress(section, "G"), findColorProgress(section, "B")) & 0x00FFFFFF;
        updateColorPreview();
    }

    private int findColorProgress(LinearLayout section, String tag) {
        for (int i = 0; i < section.getChildCount(); i++) {
            View row = section.getChildAt(i);
            if (!(row instanceof LinearLayout)) continue;
            LinearLayout linearRow = (LinearLayout) row;
            for (int j = 0; j < linearRow.getChildCount(); j++) {
                View child = linearRow.getChildAt(j);
                if (child instanceof SeekBar && tag.equals(child.getTag())) return ((SeekBar) child).getProgress();
            }
        }
        return 0;
    }

    private void selectLed(int led) {
        selectedLed = led;
        ringView.setSelectedLed(led);
        selectedLedView.setText(String.format(Locale.US, "当前灯珠: %d / 16", led));
        updateLedButtons();
    }

    private void updateBrightnessLabel() {
        brightnessView.setText(String.format(Locale.US, "亮度: %d / 63", brightness));
    }

    private void updateImaxLabel() {
        imaxView.setText(String.format(Locale.US, "IMAX: 0x%X / 0xF", imax));
    }

    private void updateColorPreview() {
        String hex = String.format(Locale.US, "#%06X", selectedColor & 0x00FFFFFF);
        colorView.setText(hex);
        colorView.setTextColor(isDark(selectedColor) ? Color.WHITE : Color.BLACK);
        colorView.setBackground(roundRect(selectedColor | 0xFF000000, dp(24), 0x44FFFFFF));
    }

    private void updateLedButtons() {
        if (ledButtons == null) return;
        for (int i = 0; i < ledButtons.length; i++) {
            boolean selected = (i + 1) == selectedLed;
            styleButton(ledButtons[i], selected ? PRIMARY_CONTAINER : SURFACE_HIGH, selected ? PRIMARY : TEXT);
        }
    }

    private void refreshStatus() {
        setStatus((controller.isPresent() ? "节点已就绪" : "未检测到节点") + "  ·  " + controller.describeAvailability());
    }

    private void writeReg() {
        try {
            int addr = Integer.parseInt(regAddressInput.getText().toString().trim(), 16);
            int value = Integer.parseInt(regValueInput.getText().toString().trim(), 16);
            runController("reg", () -> controller.setReg(addr, value), null);
        } catch (NumberFormatException error) {
            setStatus("失败: reg 请输入十六进制 addr/value");
        }
    }

    private void runController(String label, ControllerCall call, Runnable onSuccess) {
        stopAnimation();
        setStatus(label + " 写入中...");
        ioExecutor.execute(() -> {
            Aw20072Controller.CommandResult result = call.run();
            mainHandler.post(() -> {
                setStatus((result.ok ? "OK: " : "失败: ") + result.message);
                if (result.ok && onSuccess != null) onSuccess.run();
            });
        });
    }

    private void startChase() {
        startAnimation("流光", running -> {
            while (running.get()) {
                for (int led = 1; led <= Aw20072Controller.LED_COUNT && running.get(); led++) {
                    Aw20072Controller.CommandResult result = controller.setLight(led, brightness, selectedColor);
                    if (!handleAnimationResult(result, running)) return;
                    int currentLed = led;
                    mainHandler.post(() -> {
                        ringView.clear();
                        ringView.setLed(currentLed, brightness, selectedColor);
                    });
                    sleep(80, running);
                }
            }
        });
    }

    private void startRainbow() {
        startAnimation("彩虹旋转", running -> {
            int step = 0;
            while (running.get()) {
                Aw20072Controller.CommandResult clear = controller.setAloneLight(0, 0, 0);
                if (!handleAnimationResult(clear, running)) return;
                int[] colors = new int[Aw20072Controller.LED_COUNT];
                int[] levels = new int[Aw20072Controller.LED_COUNT];
                for (int led = 1; led <= Aw20072Controller.LED_COUNT && running.get(); led++) {
                    int color = Color.HSVToColor(new float[]{(step * 12 + led * 22.5f) % 360f, 0.95f, 1f}) & 0x00FFFFFF;
                    Aw20072Controller.CommandResult result = controller.setAloneLight(led, brightness, color);
                    if (!handleAnimationResult(result, running)) return;
                    colors[led - 1] = color;
                    levels[led - 1] = brightness;
                }
                mainHandler.post(() -> ringView.setSnapshot(colors, levels));
                step++;
                sleep(140, running);
            }
        });
    }

    private void startBreath() {
        startAnimation("呼吸", running -> {
            int level = 1;
            int direction = 1;
            while (running.get()) {
                Aw20072Controller.CommandResult result = controller.setAllLight(level, selectedColor);
                if (!handleAnimationResult(result, running)) return;
                int currentLevel = level;
                mainHandler.post(() -> ringView.setAll(currentLevel, selectedColor));
                level += direction;
                if (level >= brightness) {
                    level = brightness;
                    direction = -1;
                } else if (level <= 1) {
                    level = 1;
                    direction = 1;
                }
                sleep(35, running);
            }
        });
    }

    private void startAnimation(String label, AnimationLoop loop) {
        stopAnimation();
        AtomicBoolean running = new AtomicBoolean(true);
        animationRunning = running;
        animationThread = new Thread(() -> loop.run(running), "aw20072-" + label);
        animationThread.start();
        setStatus(label + " 运行中");
    }

    private void stopAnimation() {
        animationRunning.set(false);
        if (animationThread != null) {
            animationThread.interrupt();
            animationThread = null;
        }
    }

    private boolean handleAnimationResult(Aw20072Controller.CommandResult result, AtomicBoolean running) {
        if (result.ok) return true;
        running.set(false);
        mainHandler.post(() -> setStatus("玩法失败: " + result.message));
        return false;
    }

    private void sleep(long millis, AtomicBoolean running) {
        if (!running.get()) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            running.set(false);
            Thread.currentThread().interrupt();
        }
    }

    private LinearLayout section(String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(12), dp(14), dp(14));
        section.setBackground(roundRect(SURFACE, dp(22), OUTLINE));
        TextView titleView = text(title, 17, TEXT, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, dp(10));
        section.addView(titleView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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

    private Button button(String label, int bg, int fg) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(fg);
        button.setMinHeight(dp(48));
        button.setGravity(Gravity.CENTER);
        styleButton(button, bg, fg);
        return button;
    }

    private void styleButton(Button button, int bg, int fg) {
        button.setTextColor(fg);
        button.setBackground(roundRect(bg, dp(20), 0));
    }

    private EditText edit(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
        editText.setTextColor(TEXT);
        editText.setHintTextColor(MUTED);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setPadding(dp(12), 0, dp(12), 0);
        editText.setMinHeight(dp(48));
        editText.setBackground(roundRect(SURFACE_HIGH, dp(16), OUTLINE));
        return editText;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        return textView;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }

    private LinearLayout.LayoutParams matchParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private SeekBar.OnSeekBarChangeListener simpleSeek(ProgressChanged changed) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { changed.onProgressChanged(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
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
    private interface AnimationLoop { void run(AtomicBoolean running); }
    private interface ProgressChanged { void onProgressChanged(int progress); }
}

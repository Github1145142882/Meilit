package com.meilit.halo;

import android.content.Context;
import android.graphics.Color;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class HaloEngine {
    private static volatile HaloEngine instance;
    private static final long MUSIC_FRAME_MS = 45L;

    private final Aw20072Controller controller = new Aw20072Controller();
    private final ExecutorService queue = Executors.newSingleThreadExecutor();
    private final int[] ledColors = new int[Aw20072Controller.LED_COUNT];
    private final int[] ledLevels = new int[Aw20072Controller.LED_COUNT];
    private int lastAllBrightness = -1;
    private int lastAllColor = -1;
    private int restoreBrightness = 12;
    private int restoreColor = 0x00FFFFFF;
    private long lastMusicFrameAt;

    private HaloEngine(Context context) {
    }

    static HaloEngine get(Context context) {
        if (instance == null) {
            synchronized (HaloEngine.class) {
                if (instance == null) {
                    instance = new HaloEngine(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    void setForceRoot(boolean forceRoot) {
        controller.setForceRoot(forceRoot);
    }

    Aw20072Controller.CommandResult setAllLightSync(int brightness, int color) {
        int safeBrightness = clamp(brightness, 1, 63);
        int safeColor = color & 0x00FFFFFF;
        if (safeBrightness == lastAllBrightness && safeColor == lastAllColor) {
            return Aw20072Controller.CommandResult.ok("skipped duplicate all_light");
        }
        Aw20072Controller.CommandResult result = controller.setAllLight(safeBrightness, safeColor);
        if (result.ok) {
            lastAllBrightness = safeBrightness;
            lastAllColor = safeColor;
            restoreBrightness = safeBrightness;
            restoreColor = safeColor;
            Arrays.fill(ledColors, safeColor);
            Arrays.fill(ledLevels, safeBrightness);
        }
        return result;
    }

    Aw20072Controller.CommandResult setAloneLightSync(int led, int brightness, int color) {
        if (led == 0) {
            Aw20072Controller.CommandResult result = controller.setAloneLight(0, 0, 0);
            if (result.ok) {
                Arrays.fill(ledColors, 0);
                Arrays.fill(ledLevels, 0);
                lastAllBrightness = -1;
                lastAllColor = -1;
            }
            return result;
        }
        int index = led - 1;
        int safeBrightness = clamp(brightness, 0, 63);
        int safeColor = color & 0x00FFFFFF;
        if (index >= 0 && index < ledLevels.length && ledLevels[index] == safeBrightness && ledColors[index] == safeColor) {
            return Aw20072Controller.CommandResult.ok("skipped duplicate alone_light");
        }
        Aw20072Controller.CommandResult result = controller.setAloneLight(led, safeBrightness, safeColor);
        if (result.ok && index >= 0 && index < ledLevels.length) {
            ledLevels[index] = safeBrightness;
            ledColors[index] = safeColor;
            lastAllBrightness = -1;
            lastAllColor = -1;
            if (safeBrightness > 0) {
                restoreBrightness = safeBrightness;
                restoreColor = safeColor;
            }
        }
        return result;
    }

    void pulseNotification(int color) {
        queue.execute(() -> {
            int safeColor = color & 0x00FFFFFF;
            controller.setAllLight(50, safeColor);
            sleep(110);
            controller.setAllLight(18, safeColor);
            sleep(80);
            controller.setAllLight(42, safeColor);
            sleep(120);
            controller.setAllLight(Math.max(1, restoreBrightness), restoreColor);
        });
    }

    void applyMusicEnergy(float low, float mid, float high, float energy) {
        long now = System.currentTimeMillis();
        if (now - lastMusicFrameAt < MUSIC_FRAME_MS) {
            return;
        }
        lastMusicFrameAt = now;
        queue.execute(() -> {
            float safeEnergy = clamp01(energy);
            int active = clamp(Math.round(3 + safeEnergy * Aw20072Controller.LED_COUNT), 1, Aw20072Controller.LED_COUNT);
            int level = clamp(Math.round(4 + clamp01(low) * 59), 1, 63);
            float hueBase = (System.currentTimeMillis() / 28f + clamp01(high) * 140f) % 360f;
            for (int i = 1; i <= Aw20072Controller.LED_COUNT; i++) {
                int ledLevel = i <= active ? level : Math.max(1, level / 5);
                float hue = (hueBase + i * (14f + clamp01(mid) * 10f)) % 360f;
                int ledColor = Color.HSVToColor(new float[]{hue, 0.92f, 1f}) & 0x00FFFFFF;
                if (!setAloneLightSync(i, ledLevel, ledColor).ok) {
                    return;
                }
            }
        });
    }

    void fadeToIdle(int color) {
        queue.execute(() -> setAllLightSync(5, color));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

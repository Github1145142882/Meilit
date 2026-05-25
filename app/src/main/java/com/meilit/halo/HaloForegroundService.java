package com.meilit.halo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

public final class HaloForegroundService extends Service {
    static final String ACTION_START = "com.meilit.halo.action.START";
    static final String ACTION_STOP = "com.meilit.halo.action.STOP";
    static final String ACTION_START_MUSIC = "com.meilit.halo.action.START_MUSIC";
    static final String ACTION_STOP_MUSIC = "com.meilit.halo.action.STOP_MUSIC";
    static final String ACTION_START_VOLUME = "com.meilit.halo.action.START_VOLUME";
    static final String ACTION_TEST_MUSIC = "com.meilit.halo.action.TEST_MUSIC";
    static final String ACTION_TEST_NOTIFICATION = "com.meilit.halo.action.TEST_NOTIFICATION";

    private static final String CHANNEL_ID = "halo_engine";
    private static final int NOTIFICATION_ID = 7202;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HaloEngine engine;
    private AudioManager audioManager;
    private Visualizer visualizer;
    private ContentObserver volumeObserver;
    private PowerManager.WakeLock wakeLock;
    private boolean volumeFallbackRunning;
    private int activeFftFrames;
    private long lastIdleWriteAt;

    @Override
    public void onCreate() {
        super.onCreate();
        engine = HaloEngine.get(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification(statusText()));
        acquireWakeLock();
        if (ACTION_START_MUSIC.equals(action)) {
            startVisualizerOrFallback();
        } else if (ACTION_STOP_MUSIC.equals(action)) {
            stopVisualizer();
            stopVolumeFallback();
            engine.fadeToIdle(0x002964FF);
        } else if (ACTION_START_VOLUME.equals(action)) {
            startVolumeFallback();
        } else if (ACTION_TEST_MUSIC.equals(action)) {
            engine.applyMusicEnergy(1f, 0.7f, 0.8f, 1f);
        } else if (ACTION_TEST_NOTIFICATION.equals(action)) {
            engine.pulseNotification(0x00FF4FD8);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVisualizer();
        stopVolumeFallback();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startVisualizerOrFallback() {
        stopVisualizer();
        activeFftFrames = 0;
        lastIdleWriteAt = 0L;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startVolumeFallback();
            return;
        }
        try {
            visualizer = new Visualizer(0);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    applyFft(fft);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
            stopVolumeFallback();
        } catch (RuntimeException error) {
            stopVisualizer();
            startVolumeFallback();
        }
    }

    private void applyFft(byte[] fft) {
        if (fft == null || fft.length < 8) {
            return;
        }
        int bins = fft.length / 2;
        float low = bandEnergy(fft, 2, Math.max(3, bins / 8));
        float mid = bandEnergy(fft, Math.max(3, bins / 8), Math.max(4, bins / 3));
        float high = bandEnergy(fft, Math.max(4, bins / 3), bins);
        float energy = Math.min(1f, low * 0.55f + mid * 0.3f + high * 0.15f);
        if (energy < 0.08f) {
            activeFftFrames = 0;
            long now = System.currentTimeMillis();
            if (now - lastIdleWriteAt > 900L) {
                lastIdleWriteAt = now;
                engine.fadeToIdle(0x002964FF);
            }
            return;
        }
        activeFftFrames++;
        if (activeFftFrames < 3) {
            return;
        }
        engine.applyMusicEnergy(low, mid, high, energy);
    }

    private static float bandEnergy(byte[] fft, int startBin, int endBin) {
        int count = 0;
        double sum = 0d;
        for (int i = startBin; i < endBin && i * 2 + 1 < fft.length; i++) {
            sum += Math.hypot(fft[i * 2], fft[i * 2 + 1]);
            count++;
        }
        return count == 0 ? 0f : Math.min(1f, (float) (sum / count / 92d));
    }

    private void startVolumeFallback() {
        if (volumeFallbackRunning) {
            applyVolumeFrame();
            return;
        }
        volumeObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                applyVolumeFrame();
            }
        };
        getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver);
        volumeFallbackRunning = true;
        applyVolumeFrame();
    }

    private void stopVolumeFallback() {
        if (volumeObserver != null) {
            getContentResolver().unregisterContentObserver(volumeObserver);
            volumeObserver = null;
        }
        volumeFallbackRunning = false;
    }

    private void applyVolumeFrame() {
        if (audioManager == null) {
            return;
        }
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float ratio = Math.max(0f, Math.min(1f, current / (float) max));
        engine.applyMusicEnergy(ratio, ratio * 0.5f, ratio * 0.25f, ratio);
    }

    private void stopVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
            } catch (RuntimeException ignored) {
            }
            visualizer.release();
            visualizer = null;
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeilitHalo:engine");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(6 * 60 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "灵动光环后台", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("灵动光环")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private String statusText() {
        if (visualizer != null) {
            return "音乐频谱监听中";
        }
        if (volumeFallbackRunning) {
            return "音量回退监听中";
        }
        return "后台服务运行中";
    }
}

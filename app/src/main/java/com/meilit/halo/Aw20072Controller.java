package com.meilit.halo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;

final class Aw20072Controller {
    static final int LED_COUNT = 16;

    private static final String SYSFS_DIR = "/sys/class/leds/aw20072_led";
    private static final String DEV_NODE = "/dev/aw20072_led";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final File sysfsDir = new File(SYSFS_DIR);
    private final File devNode = new File(DEV_NODE);
    private boolean forceRoot;

    void setForceRoot(boolean forceRoot) {
        this.forceRoot = forceRoot;
    }

    boolean isPresent() {
        return sysfsDir.exists() || devNode.exists();
    }

    String describeAvailability() {
        return "sysfs: " + (sysfsDir.exists() ? "found" : "missing")
                + "   dev: " + (devNode.exists() ? "found" : "missing")
                + "   mode: " + (forceRoot ? "su" : "direct first");
    }

    CommandResult setEffect(int effect) {
        return writeNode("effect", String.format(Locale.US, "%d\n", clamp(effect, 0, 16)));
    }

    CommandResult setImax(int imax) {
        return writeNode("imax", String.format(Locale.US, "%x\n", clamp(imax, 0, 0x0F)));
    }

    CommandResult setHwen(int value) {
        return writeNode("hwen", String.format(Locale.US, "%x\n", value));
    }

    CommandResult setLight(int led, int brightness, int color) {
        CommandResult check = validateLed(led);
        if (!check.ok) return check;
        if (brightness <= 0) return CommandResult.fail("light brightness must be 1..63");
        return writeNode("light", String.format(Locale.US, "%d %d %06x\n", led, clamp(brightness, 1, 63), color24(color)));
    }

    CommandResult setAllLight(int brightness, int color) {
        if (brightness <= 0) return CommandResult.fail("all_light brightness must be 1..63");
        return writeNode("all_light", String.format(Locale.US, "%d %06x\n", clamp(brightness, 1, 63), color24(color)));
    }

    CommandResult setAloneLight(int led, int brightness, int color) {
        if (led == 0) return writeNode("alone_light", "0 0 0\n");
        CommandResult check = validateLed(led);
        if (!check.ok) return check;
        return writeNode("alone_light", String.format(Locale.US, "%d %d %06x\n", led, clamp(brightness, 0, 63), color24(color)));
    }

    CommandResult setRgbColor(int led, int color) {
        CommandResult check = validateLed(led);
        if (!check.ok) return check;
        return writeNode("rgbcolor", String.format(Locale.US, "%d %06x\n", led, color24(color)));
    }

    CommandResult setAllRgbColor(int color) {
        return writeNode("allrgbcolor", String.format(Locale.US, "%06x\n", color24(color)));
    }

    CommandResult setRgbBrightness(int led, int rgb) {
        CommandResult check = validateLed(led);
        if (!check.ok) return check;
        return writeNode("rgbbrightness", String.format(Locale.US, "%d %06x\n", led, color24(rgb)));
    }

    CommandResult setAllRgbBrightness(int rgb) {
        return writeNode("allrgbbrightness", String.format(Locale.US, "%06x\n", color24(rgb)));
    }

    CommandResult setI2cLog(int enabled) {
        return writeNode("i2c_log", String.format(Locale.US, "%d\n", enabled == 0 ? 0 : 1));
    }

    CommandResult readNode(String node) {
        File target = new File(sysfsDir, node);
        if (!target.exists()) return CommandResult.fail("missing node: " + target.getAbsolutePath());
        try {
            return CommandResult.ok(readDirect(target).trim());
        } catch (IOException directError) {
            CommandResult rootResult = readWithSu(target);
            if (!rootResult.ok) return CommandResult.fail(rootResult.message + "; direct read failed: " + directError.getMessage());
            return rootResult;
        }
    }

    private CommandResult writeNode(String node, String payload) {
        File target = new File(sysfsDir, node);
        if (!target.exists()) return CommandResult.fail("missing node: " + target.getAbsolutePath());

        IOException directError = null;
        if (!forceRoot) {
            try {
                writeDirect(target, payload);
                return CommandResult.ok("wrote " + node + " directly");
            } catch (IOException error) {
                directError = error;
            }
        }

        CommandResult rootResult = writeWithSu(target, payload);
        if (!rootResult.ok && directError != null) {
            return CommandResult.fail(rootResult.message + "; direct write failed: " + directError.getMessage());
        }
        return rootResult;
    }

    private void writeDirect(File target, String payload) throws IOException {
        FileOutputStream output = new FileOutputStream(target);
        try {
            output.write(payload.getBytes(UTF_8));
            output.flush();
        } finally {
            output.close();
        }
    }

    private String readDirect(File target) throws IOException {
        FileInputStream input = new FileInputStream(target);
        try {
            return readAll(input);
        } finally {
            input.close();
        }
    }

    private CommandResult writeWithSu(File target, String payload) {
        String command = "printf '%s' " + shellQuote(payload) + " > " + shellQuote(target.getAbsolutePath());
        return runSu(command, "wrote " + target.getName() + " with su");
    }

    private CommandResult readWithSu(File target) {
        return runSu("cat " + shellQuote(target.getAbsolutePath()), null);
    }

    private CommandResult runSu(String command, String successMessage) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            int exitCode = process.waitFor();
            String stdout = readAll(process.getInputStream()).trim();
            String stderr = readAll(process.getErrorStream()).trim();
            if (exitCode == 0) return CommandResult.ok(successMessage == null ? stdout : successMessage);
            String details = stderr.length() == 0 ? stdout : stderr;
            return CommandResult.fail("su failed (" + exitCode + "): " + details);
        } catch (IOException error) {
            return CommandResult.fail("su unavailable: " + error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return CommandResult.fail("su interrupted");
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
        return new String(bytes.toByteArray(), UTF_8);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static int color24(int color) {
        return color & 0x00FFFFFF;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static CommandResult validateLed(int led) {
        if (led < 1 || led > LED_COUNT) return CommandResult.fail("light number must be 1..16");
        return CommandResult.ok("ok");
    }

    static final class CommandResult {
        final boolean ok;
        final String message;

        private CommandResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        static CommandResult ok(String message) {
            return new CommandResult(true, message);
        }

        static CommandResult fail(String message) {
            return new CommandResult(false, message);
        }
    }
}

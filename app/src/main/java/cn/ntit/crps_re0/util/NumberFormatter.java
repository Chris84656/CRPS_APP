package cn.ntit.crps_re0.util;

import java.util.Locale;

public class NumberFormatter {

    public static String voltage(float v) {
        return String.format(Locale.US, "%.1f", v);
    }

    public static String current(float a) {
        return String.format(Locale.US, "%.2f", a);
    }

    public static String power(float w) {
        return String.format(Locale.US, "%.1f", w);
    }

    public static String efficiency(int eff) {
        return String.valueOf(eff);
    }

    public static String temperature(float t) {
        return String.format(Locale.US, "%.1f", t);
    }

    public static String fanSpeed(int rpm) {
        return String.valueOf(rpm);
    }

    public static String runtime(long seconds) {
        if (seconds < 99 * 3600) {
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            long s = seconds % 60;
            return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return String.format(Locale.US, "%dD%dH", days, hours);
        }
    }

    public static String hex(int value) {
        return String.format(Locale.US, "0x%04X", value & 0xFFFF);
    }
}

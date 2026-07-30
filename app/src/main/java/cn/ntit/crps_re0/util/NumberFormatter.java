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
        if (seconds <= 0) return "0秒";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("时");
        if (minutes > 0) sb.append(minutes).append("分");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("秒");
        return sb.toString();
    }

    public static String hex(int value) {
        return String.format(Locale.US, "0x%04X", value & 0xFFFF);
    }
}

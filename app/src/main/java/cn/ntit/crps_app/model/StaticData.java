package cn.ntit.crps_app.model;

import com.google.gson.annotations.SerializedName;

public class StaticData {
    @SerializedName("mid") public String mid;
    @SerializedName("mm") public String mm;
    @SerializedName("msn") public String msn;
    @SerializedName("mloc") public String mloc;
    @SerializedName("mdate") public String mdate;
    @SerializedName("mrev") public String mrev;
    @SerializedName("prev") public int prev;
    @SerializedName("rimax") public float rimax;
    @SerializedName("rpmax") public float rpmax;
    @SerializedName("rt1") public float rt1;
    @SerializedName("rt2") public float rt2;

    public String getMidDisplay() {
        return cleanString(mid);
    }

    public String getMmDisplay() {
        return cleanString(mm);
    }

    public String getMsnDisplay() {
        return cleanString(msn);
    }

    public String getMlocDisplay() {
        return cleanString(mloc);
    }

    public String getMdateDisplay() {
        return cleanString(mdate);
    }

    public String getMrevDisplay() {
        return cleanString(mrev);
    }

    public String getPrevDisplay() {
        switch (prev) {
            case 0: return "V1.0";
            case 17: return "V1.1";
            case 34: return "V1.2";
            case 51: return "V1.3";
            case 255: return "未知";
            default: return String.format("(0x%02X)", prev);
        }
    }

    public String getRimaxDisplay() {
        return rimax == 0 ? "未知" : String.valueOf((int) rimax);
    }

    public String getRpmaxDisplay() {
        return rpmax == 0 ? "未知" : String.valueOf((int) rpmax);
    }

    public String getRt1Display() {
        return rt1 == 0 ? "未知" : String.valueOf((int) rt1);
    }

    public String getRt2Display() {
        return rt2 == 0 ? "未知" : String.valueOf((int) rt2);
    }

    /**
     * 去掉 # 和 0xFF 填充，空则返回"未知"
     */
    private String cleanString(String s) {
        if (s == null) return "未知";
        String cleaned = s.replace("#", "").replace("\u00FF", "").trim();
        return cleaned.isEmpty() ? "未知" : cleaned;
    }
}

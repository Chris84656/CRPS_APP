package cn.ntit.crps_re0.model;

import com.google.gson.annotations.SerializedName;

public class DynamicData {
    @SerializedName("vin") public float vin;
    @SerializedName("iin") public float iin;
    @SerializedName("vout") public float vout;
    @SerializedName("iout") public float iout;
    @SerializedName("pout") public float pout;
    @SerializedName("eff") public int eff;
    @SerializedName("fan") public int fan;
    @SerializedName("t1") public float t1;
    @SerializedName("t2") public float t2;
    @SerializedName("rt") public long rt;
    @SerializedName("pwr") public int pwr;
    @SerializedName("si") public int si;
    @SerializedName("sn") public int sn;
    @SerializedName("st") public int st;
    @SerializedName("sf") public int sf;

    public float getPin() {
        return vin * iin;
    }

    public boolean isPowerOn() {
        return pwr == 1;
    }
}

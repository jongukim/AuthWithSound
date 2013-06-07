package kr.ac.ajou.dv.authwithsound;

import java.util.HashMap;
import java.util.Map;

public class QRCodeHandler {
    public static final String IP = "IP_ADDR";
    public static final String PORT = "PORT_NUM";
    public static final String SSID = "AP_SSID";

    public static Map<String, String> decode(String qrStr) {
        String[] strs = qrStr.split(",");
        if (strs.length != 3) return null;

        HashMap<String, String> map = new HashMap<String, String>();
        map.put(SSID, strs[0]);
        map.put(IP, strs[1]);
        map.put(PORT, strs[2]);
        return map;
    }

    public static String encode(Map<String, String> map) {
        return map.get(SSID) + "," + map.get(IP) + "," + map.get(PORT);
    }
}

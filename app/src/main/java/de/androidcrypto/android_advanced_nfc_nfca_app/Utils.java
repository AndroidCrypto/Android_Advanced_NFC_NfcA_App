package de.androidcrypto.android_advanced_nfc_nfca_app;

/**
 * This class holds all helper methods for the app
 */

public class Utils {
    private static final String TAG = "Utils";
    public static final String version = "1.00";

    public static String byteToHex(Byte input) {
        return String.format("%02X", input);
        //return String.format("0x%02X", input);
    }

    public static String bytesToHexNpe(byte[] bytes) {
        if (bytes == null) return "";
        StringBuffer result = new StringBuffer();
        for (byte b : bytes)
            result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString().toUpperCase();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static String printData(String dataName, byte[] data) {
        int dataLength;
        String dataString = "";
        if (data == null) {
            dataLength = 0;
            dataString = "IS NULL";
        } else {
            dataLength = data.length;
            dataString = Utils.bytesToHexNpe(data).toUpperCase();
        }
        StringBuilder sb = new StringBuilder();
        sb
                .append(dataName)
                .append(" length: ")
                .append(dataLength)
                .append(" data: ")
                .append(dataString);
        return sb.toString();
    }

    public static int byteToUpperNibbleInt(Byte input) {
        return (input & 0xF0 ) >> 4;
    }

    public static int byteToLowerNibbleInt(Byte input) {
        return input & 0x0F;
    }
}

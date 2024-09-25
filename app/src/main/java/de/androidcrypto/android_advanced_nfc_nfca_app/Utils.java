package de.androidcrypto.android_advanced_nfc_nfca_app;

import android.annotation.SuppressLint;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

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

    public static byte[] concatenateByteArrays(byte[] dataA, byte[] dataB) {
        byte[] concatenated = new byte[dataA.length + dataB.length];
        for (int i = 0; i < dataA.length; i++) {
            concatenated[i] = dataA[i];
        }
        for (int i = 0; i < dataB.length; i++) {
            concatenated[dataA.length + i] = dataB[i];
        }
        return concatenated;
    }

    // 'LSB' means Lowest Significant Byte first = reversed data, e.g. the value 2 is encoded 0x020000h
    public static int intFrom3ByteArrayLsb(byte[] bytes) {
        return  ((bytes[2] & 0xFF) << 16) |
                ((bytes[1] & 0xFF) << 8 ) |
                ((bytes[0] & 0xFF) << 0 );
    }

    public static int byteToUpperNibbleInt(Byte input) {
        return (input & 0xF0 ) >> 4;
    }
    public static int byteToLowerNibbleInt(Byte input) {
        return input & 0x0F;
    }

    // https://stackoverflow.com/a/29396837/8166854
    public static boolean testBit(byte b, int n) {
        int mask = 1 << n; // equivalent of 2 to the nth power
        return (b & mask) != 0;
    }

    // position is 0 based starting from right to left
    public static byte setBitInByte(byte input, int pos) {
        return (byte) (input | (1 << pos));
    }

    // position is 0 based starting from right to left
    public static byte unsetBitInByte(byte input, int pos) {
        return (byte) (input & ~(1 << pos));
    }

    // get a 4 bytes long array with current hour and minute in ASCII encoding
    // returns 0x32333532h = '2352' = 23:52
    // this uses LocalDateTime that is available on SDK 26+
    // by adding Desugaring library available on Android SDK 21 as well
    @SuppressLint("DefaultLocale")
    public static byte[] getTimestamp4Bytes() {
        LocalDateTime lt = LocalDateTime.now();
        return String.format("%02d%02d", lt.getHour(), lt.getMinute()).getBytes(StandardCharsets.UTF_8);
    }
}

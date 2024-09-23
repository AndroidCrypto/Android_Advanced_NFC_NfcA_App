package de.androidcrypto.android_advanced_nfc_nfca_app;


import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.setBitInByte;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.testBit;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.unsetBitInByte;

import java.util.Arrays;

/**
 * This class takes the configuration pages of an NTAG21x or MIFARE Ultralight EV1 tag.
 */
public class ConfigurationPages {

    public static enum TagType {NTAG21x, Ultralight_EV1}

    private Enum tagType;
    private byte[] configurationPages01;
    private byte c0Byte0; // NTAG21x: MIRROR, Ultralight: MOD
    private byte c0Byte1; // RFUI
    private byte c0Byte2; // NTAG21x: MIRROR_PAGE, Ultralight: RFUI
    private byte c0Byte3; // AUTH0
    private byte c1Byte0; // ACCESS
    private byte c1Byte1; // NTAG21x: RFUI, Ultralight: VCTID
    private byte c1Byte2; // RFUI
    private byte c1Byte3; // RFUI
    private boolean isValid = false;

    public ConfigurationPages(Enum tagType, byte[] configurationPages01) {
        this.configurationPages01 = configurationPages01;
        this.tagType = tagType;
        if ((configurationPages01 == null) || (configurationPages01.length != 8)) {
            System.out.println("Error: configurationPages01 are NULL or not of length 8, aborted");
            return;
        }
        c0Byte0 = configurationPages01[0];
        c0Byte1 = configurationPages01[1];
        c0Byte2 = configurationPages01[2];
        c0Byte3 = configurationPages01[3];
        c1Byte0 = configurationPages01[4];
        c1Byte1 = configurationPages01[5];
        c1Byte2 = configurationPages01[6];
        c1Byte3 = configurationPages01[7];
        isValid = true;
    }

    public void setAuthProtectionPage(int pageNumber) {
        // this is available on NTAG21x + Ultralight EV1 tags
        c0Byte3 = (byte) (pageNumber & 0x0ff);
        buildConfigurationPages01();
    }

    public void disableAuthProtectionPage() {
        // this is available on NTAG21x + Ultralight EV1 tags
        c0Byte3 = (byte) (0xff);
        buildConfigurationPages01();
    }

    public void enableNfcReadCounter() {
        // this is available on NTAG21x tags only
        if (tagType == TagType.NTAG21x) {
            c1Byte0 = setBitInByte(c1Byte0, 4);
            buildConfigurationPages01();
        }
    }

    public void disableNfcReadCounter() {
        // this is available on NTAG21x tags only
        if (tagType == TagType.NTAG21x) {
            c1Byte0 = unsetBitInByte(c1Byte0, 4);
            buildConfigurationPages01();
        }
    }

    public void enableNfcReadCounterPwdProt() {
        // this is available on NTAG21x tags only
        if (tagType == TagType.NTAG21x) {
            c1Byte0 = setBitInByte(c1Byte0, 3);
            buildConfigurationPages01();
        }
    }

    public void disableNfcReadCounterPwdProt() {
        // this is available on NTAG21x tags only
        if (tagType == TagType.NTAG21x) {
            c1Byte0 = unsetBitInByte(c1Byte0, 3);
            buildConfigurationPages01();
        }
    }

    public void setAuthProtectionReadWrite() {
        // this is available on NTAG21x + Ultralight EV1 tags
        c1Byte0 = setBitInByte(c1Byte0, 7);
        buildConfigurationPages01();
    }

    public void setAuthProtectionWriteOnly() {
        // this is available on NTAG21x + Ultralight EV1 tags
        c1Byte0 = unsetBitInByte(c1Byte0, 7);
        buildConfigurationPages01();
    }

    public boolean setAsciiMirroring(boolean mirrorUidAscii, boolean mirrorNfcCounter, int startPage, int startByteInPage) {
        // this is available on NTAG21x tags only
        if ((startByteInPage < 0) || (startByteInPage > 3)) {
            System.out.println("Error: startByteInPage < 0 or startByteInPage > 3, aborted");
            return false;
        }
        if (startPage > 221) {
            System.out.println("Error: startPage > 221, aborted");
            return false;
        }
        if (tagType == TagType.NTAG21x) {
            // what should get mirrored ?
            if (mirrorUidAscii) {
                c0Byte0 = setBitInByte(c0Byte0, 6);
            } else {
                c0Byte0 = unsetBitInByte(c0Byte0, 6);
            }
            if (mirrorNfcCounter) {
                c0Byte0 = setBitInByte(c0Byte0, 7);
            } else {
                c0Byte0 = unsetBitInByte(c0Byte0, 7);
            }
            // define the byte number within the mirror page where the mirroring will start
            if (startByteInPage == 0) {
                c0Byte0 = unsetBitInByte(c0Byte0, 4);
                c0Byte0 = unsetBitInByte(c0Byte0, 5);
            } else if (startByteInPage == 1) {
                c0Byte0 = setBitInByte(c0Byte0, 4);
                c0Byte0 = unsetBitInByte(c0Byte0, 5);
            } else if (startByteInPage == 2) {
                c0Byte0 = unsetBitInByte(c0Byte0, 4);
                c0Byte0 = setBitInByte(c0Byte0, 5);
            } else {
                c0Byte0 = setBitInByte(c0Byte0, 4);
                c0Byte0 = setBitInByte(c0Byte0, 5);
            }
            // define the page where the mirroring is going to start
            c0Byte2 = (byte) startPage;
            buildConfigurationPages01();
            return true;
        }
        return false;
    }

    private boolean checkC0B0Bit7() {
        return testBit(c0Byte0, 7);
    }

    private boolean checkC0B0Bit6() {
        return testBit(c0Byte0, 6);
    }

    private boolean checkC0B0Bit5() {
        return testBit(c0Byte0, 5);
    }

    private boolean checkC0B0Bit4() {
        return testBit(c0Byte0, 4);
    }

    private boolean checkC0B0Bit3() {
        return testBit(c0Byte0, 3);
    }

    private boolean checkC0B0Bit2() {
        return testBit(c0Byte0, 2);
    }

    private boolean checkC0B0Bit1() {
        return testBit(c0Byte0, 1);
    }

    private boolean checkC0B0Bit0() {
        return testBit(c0Byte0, 0);
    }

    private boolean checkC1B0Bit7() {
        return testBit(c1Byte0, 7);
    }

    private boolean checkC1B0Bit6() {
        return testBit(c1Byte0, 6);
    }

    private boolean checkC1B0Bit5() {
        return testBit(c1Byte0, 5);
    }

    private boolean checkC1B0Bit4() {
        return testBit(c1Byte0, 4);
    }

    private boolean checkC1B0Bit3() {
        return testBit(c1Byte0, 3);
    }

    private boolean checkC1B0Bit2() {
        return testBit(c1Byte0, 2);
    }

    private boolean checkC1B0Bit1() {
        return testBit(c1Byte0, 1);
    }

    private boolean checkC1B0Bit0() {
        return testBit(c1Byte0, 0);
    }

    public boolean isValid() {
        return isValid;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration Pages dump").append("\n");
        sb.append(printData("data", configurationPages01)).append("\n");
        // mirror feature in NTAG21x in conf page 0 byte 0 and 2
        sb.append("--- Mirror Feature ---").append("\n");
        sb.append("Mirror_PAGE ");
        if (c0Byte2 == (byte) 0xff) {
            sb.append(255).append("\n");
        } else {
            sb.append((int) c0Byte2).append("\n");
        }
        // mirror byte
        sb.append("--- MIRROR Byte ---").append("\n");
        sb.append("CONF COUNT  ");
        if (checkC0B0Bit7() ) {
            sb.append("Enabled").append("\n");
        } else {
            sb.append("Disabled").append("\n");
        }
        sb.append("CONF ASCII  ");
        if (checkC0B0Bit6() ) {
            sb.append("Enabled").append("\n");
        } else {
            sb.append("Disabled").append("\n");
        }
        // mirror byte
        int mirrorByteInt = 0;
        if (checkC1B0Bit4()) mirrorByteInt += 1;
        if (checkC1B0Bit5()) mirrorByteInt += 2;
        sb.append("Mirror Byte ").append(mirrorByteInt).append("\n");
        // bit 3 is RFUI
        sb.append("STRG_MOD_EN ");
        if (checkC0B0Bit2() ) {
            sb.append("Enabled").append("\n");
        } else {
            sb.append("Disabled").append("\n");
        }
        // bits 0 + 1 are RFUI

        sb.append("--- Auth Prot ---").append("\n");
        sb.append("Auth0    ");
        if (c0Byte3 == (byte) 0xff) {
            sb.append(255).append("\n");
        } else {
            sb.append((int) c0Byte3).append("\n");
        }
        sb.append("--- ACCESS Byte ---").append("\n");
        sb.append("Protection ");
        if (checkC1B0Bit7() ) {
            sb.append("Read & Write").append("\n");
        } else {
            sb.append("Write only").append("\n");
        }
        sb.append("Conf.P.Lock ");
        if (checkC1B0Bit6() ) {
            sb.append("TRUE = Locked").append("\n");
        } else {
            sb.append("FALSE = OPEN").append("\n");
        }
        sb.append("NFC_CNT_EN  ");
        if (checkC1B0Bit4() ) {
            sb.append("TRUE = ReadCounter ENABLED").append("\n");
        } else {
            sb.append("FALSE = ReadCounter DISABLED").append("\n");
        }
        sb.append("NFC_CNT_PWD_PROT ");
        if (checkC1B0Bit3() ) {
            sb.append("TRUE = ReadCounter PWD Protected").append("\n");
        } else {
            sb.append("FALSE = ReadCounter PWD DISABLED").append("\n");
        }
        int authLimInt = 0;
        if (checkC1B0Bit0()) authLimInt += 1;
        if (checkC1B0Bit1()) authLimInt += 2;
        if (checkC1B0Bit2()) authLimInt += 4;
        sb.append("AuthLim ").append(authLimInt).append("\n");
        return sb.toString();
    }

    public byte[] getConfigurationPage0() {
        return Arrays.copyOf(configurationPages01, 4);
    }

    public byte[] getConfigurationPage1() {
        return Arrays.copyOfRange(configurationPages01, 4, 8);
    }

    // general getter & setter

    public Enum getTagType() {
        return tagType;
    }

    public void setTagType(Enum tagType) {
        this.tagType = tagType;
    }

    public byte[] getConfigurationPages01() {
        return configurationPages01;
    }

    public void setConfigurationPages01(byte[] configurationPages01) {
        this.configurationPages01 = configurationPages01;
    }

    public byte getC0Byte0() {
        return c0Byte0;
    }

    public void setC0Byte0(byte c0Byte0) {
        this.c0Byte0 = c0Byte0;
    }

    public byte getC0Byte1() {
        return c0Byte1;
    }

    public void setC0Byte1(byte c0Byte1) {
        this.c0Byte1 = c0Byte1;
    }

    public byte getC0Byte2() {
        return c0Byte2;
    }

    public void setC0Byte2(byte c0Byte2) {
        this.c0Byte2 = c0Byte2;
    }

    public byte getC0Byte3() {
        return c0Byte3;
    }

    public void setC0Byte3(byte c0Byte3) {
        this.c0Byte3 = c0Byte3;
    }

    public byte getC1Byte0() {
        return c1Byte0;
    }

    public void setC1Byte0(byte c1Byte0) {
        this.c1Byte0 = c1Byte0;
    }

    public byte getC1Byte1() {
        return c1Byte1;
    }

    public void setC1Byte1(byte c1Byte1) {
        this.c1Byte1 = c1Byte1;
    }

    public byte getC1Byte2() {
        return c1Byte2;
    }

    public void setC1Byte2(byte c1Byte2) {
        this.c1Byte2 = c1Byte2;
    }

    public byte getC1Byte3() {
        return c1Byte3;
    }

    public void setC1Byte3(byte c1Byte3) {
        this.c1Byte3 = c1Byte3;
    }

    // internal
    public void buildConfigurationPages01() {
        configurationPages01[0] = c0Byte0;
        configurationPages01[1] = c0Byte1;
        configurationPages01[2] = c0Byte2;
        configurationPages01[3] = c0Byte3;
        configurationPages01[4] = c1Byte0;
        configurationPages01[5] = c1Byte1;
        configurationPages01[6] = c1Byte2;
        configurationPages01[7] = c1Byte3;
    }
}

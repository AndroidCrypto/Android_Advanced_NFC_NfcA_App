package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.hexStringToByteArray;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;

import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

/**
 * This class takes all technical information's about a tag, taken from the Get Version command
 * and other sources. The data is used define ranges e.g. for the free user memory on the tag.
 */
public class TagInformation {
    private final String TAG = TagInformation.class.getSimpleName();
    public byte[] tagUid;
    public byte[] atqa;
    public byte sak;
    public int maxTransceiveLength;
    public String tagMajorName = "unknown"; // main product name, e.g. 'NTAG21x'
    public String tagMinorName = "unknown"; // minor product name, e.g. 'NTAG216'
    public String mainTechnology = "NfcA";
    public String[] technologies;
    public int userMemory = 0; // e.g. 888 bytes for a NTAG216
    public int bytesPerPage = 4;
    public int userMemoryStartPage = 0;
    public int userMemoryEndPage = 0;
    public int tagMemoryEndPage = 0;
    public int configurationStartPage = 0;
    public boolean tagHasGetVersionCommand = false;
    public boolean tagHasFastReadCommand = false;
    public boolean tagHasAuthentication = false;
    public boolean tagHasPasswordSecurity = false;
    public boolean tagHasDesAuthenticationSecurity = false;
    public int numberOfCounter = 0;
    public boolean isTagWriteProtected = false;
    public boolean isTagReadProtected = false;
    public int startPageWriteProtection = 255;
    public int startPageReadProtection = 255;
    public boolean tagHasOtpArea = false;
    public boolean tagHasPageLockBytes = false;
    // following variables are used to restrict the sample app to a single tag type
    public boolean isTag_MIFARE_ULTRALIGHT_EV1 = false;
    public boolean isTag_NTAG21x = false;
    public boolean isTag_NfcA_Library_Capable = false; // set on true if tag type is NTAG21x, Ultralight EV1 or Ultralight C
    VersionInfo tagVersionData;

    public TagInformation(byte[] tagUid, byte[] atqa, byte sak, int maxTransceiveLength, String[] technologies) {
        this.tagUid = tagUid;
        this.atqa = atqa;
        this.sak = sak;
        this.maxTransceiveLength = maxTransceiveLength;
        this.technologies = technologies;
    }

    // this method is called when a tag responds to a GetVersion command
    public boolean identifyTagOnGetVersion(byte[] getVersionData) {
        Log.d(TAG, "identifyTagOnGetVersion started");
        try {
            tagVersionData = new VersionInfo(getVersionData);
        } catch (IOException e) {
            Log.e(TAG, "identifyTagOnGetVersion failed with IOException " + e.getMessage());
            return false;
        }
        // take some information from Get Version
        tagMajorName = tagVersionData.tagTypeLookup(tagVersionData.getHardwareType());
        // data for NTAG21x
        if (tagMajorName.equals(VersionInfo.MajorTagType.NTAG_21x.toString())) {
            // get more data for NTAG21x family
            if (tagVersionData.getHardStorageSizeRaw() == 15) {
                // Get Version data: 0004040201000F03
                // it is an NTAG213
                tagMinorName = "NTAG213";
                userMemory = 144;
                userMemoryStartPage = 4;
                userMemoryEndPage = 39; // included
                tagMemoryEndPage = 44;
                configurationStartPage = 41;
                isTag_NTAG21x = true;
            } else if (tagVersionData.getHardStorageSizeRaw() == 17) {
                // Get Version data: 0004040201001103
                // it is an NTAG215
                tagMinorName = "NTAG215";
                userMemory = 504;
                userMemoryStartPage = 4;
                userMemoryEndPage = 129; // included
                tagMemoryEndPage = 134;
                configurationStartPage = 131;
                isTag_NTAG21x = true;
            } else if (tagVersionData.getHardStorageSizeRaw() == 19) {
                // Get Version data: 0004040201001303
                // it is an NTAG216
                tagMinorName = "NTAG216";
                userMemory = 888;
                userMemoryStartPage = 4;
                userMemoryEndPage = 225; // included
                tagMemoryEndPage = 230;
                configurationStartPage = 227;
                isTag_NTAG21x = true;
            } else {
                tagMinorName = "NTAG21x Unknown";
                userMemory = 0;
                userMemoryStartPage = 0;
                userMemoryEndPage = 0;
            }
            // general information for NTAG21x family
            tagHasFastReadCommand = true;
            tagHasAuthentication = true;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = true;
            tagHasPageLockBytes = true;
            tagHasOtpArea = true;
            numberOfCounter = 1;
            isTag_NfcA_Library_Capable = true;
            Log.d(TAG, "Tag is of type " + tagMinorName + " with " + userMemory + " bytes user memory");
            return true;
        } else if (tagMajorName.equals(VersionInfo.MajorTagType.MIFARE_Ultralight.toString())) {
            if (tagVersionData.getHardStorageSizeRaw() == 11) {
                // Get Version data: 0004030101000B03
                // it is an Ultralight EV1 with 48 bytes user memory MF0UL11
                tagMinorName = "MF0UL11";
                userMemory = 48;
                userMemoryStartPage = 4;
                userMemoryEndPage = 15; // included
                tagMemoryEndPage = 19;
                configurationStartPage = 16;
                isTag_MIFARE_ULTRALIGHT_EV1 = true;
            } else if (tagVersionData.getHardStorageSizeRaw() == 14) {
                // Get Version data: 0004030101000E03
                // it is an Ultralight EV1 with 128 bytes user memory MF0UL21
                tagMinorName = "MF0UL21";
                userMemory = 128;
                userMemoryStartPage = 4;
                userMemoryEndPage = 35; // included
                tagMemoryEndPage = 40;
                configurationStartPage = 37;
                isTag_MIFARE_ULTRALIGHT_EV1 = true;
            } else {
                tagMinorName = "MF0ULx Unknown";
                userMemory = 0;
                userMemoryStartPage = 0;
                userMemoryEndPage = 0;
            }
            // general information for Ultralight EV1 family
            tagHasFastReadCommand = true;
            tagHasAuthentication = true;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = true;
            tagHasPageLockBytes = true;
            tagHasOtpArea = true;
            numberOfCounter = 3;
            isTag_NfcA_Library_Capable = true;
            Log.d(TAG, "Tag is of type " + tagMinorName + " with " + userMemory + " bytes user memory");
            return true;
        } else if (tagMajorName.equals(VersionInfo.MajorTagType.MIFARE_DESFire.toString())) {
            // this is a MIFARE DESFire tag that requires other commands to work with
            // just analyzing some response data to identify the tag
            // get the sub type EV1 / EV2 / EV3
            String desfireSubType;
            if (tagVersionData.getHardwareVersionMajor() == 1) {
                // Get Version data: 040101010016050401010104160500046D759AA47780B90C224D703722 (EV1 2K)
                desfireSubType = "EV1";
            } else if (tagVersionData.getHardwareVersionMajor() == 18) {
                desfireSubType = "EV2";
            } else if (tagVersionData.getHardwareVersionMajor() == 51) {
                desfireSubType = "EV3";
            } else {
                desfireSubType = "EVx (unknown)";
            }
            // get the storage size
            if (tagVersionData.getHardStorageSizeRaw() == 22) {
                // Get Version data: 040101010016050401010104160500046D759AA47780B90C224D703722
                // it is an DESFire EVx 2K with 2048 bytes user memory
                tagMinorName = "DESFire " + desfireSubType + " 2K";
                userMemory = 2048;
                userMemoryStartPage = 0;
                userMemoryEndPage = 0; // included
            } else if (tagVersionData.getHardStorageSizeRaw() == 24) {
                // Get Version data: 04010112001605040101020116050004464BDAD37580CF5B9665003521
                // it is an DESFire EVx 4K with 4096 bytes user memory
                tagMinorName = "DESFire " + desfireSubType + " 4K";
                userMemory = 4096;
                userMemoryStartPage = 0;
                userMemoryEndPage = 0; // included
            } else if (tagVersionData.getHardStorageSizeRaw() == 26) {
                // it is an DESFire EVx 8K with 8192 bytes user memory
                tagMinorName = "DESFire " + desfireSubType + " 8K";
                userMemory = 8196;
                userMemoryStartPage = 0;
                userMemoryEndPage = 0; // included
            } else if (tagVersionData.getHardStorageSizeRaw() == 28) {
                // it is an DESFire EVx 16K with 4096 bytes user memory
                tagMinorName = "DESFire " + desfireSubType + " 16K";
                userMemory = 16384;
                userMemoryStartPage = 0;
                userMemoryEndPage = 0; // included
            } else if (tagVersionData.getHardStorageSizeRaw() == 30) {
                // it is an DESFire EVx 32K with 4096 bytes user memory
                tagMinorName = "DESFire " + desfireSubType + " 32K";
                userMemory = 32768;
                userMemoryStartPage = 0;
                userMemoryEndPage = 0; // included
            } else {
                tagMinorName = "DESFire " + desfireSubType + " unknown memory";
                userMemory = 0;
                userMemoryStartPage = 0;
                userMemoryEndPage = 0;
            }
            // general information for DESFire EVx family
            tagHasFastReadCommand = false;
            tagHasAuthentication = false;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = false;
            tagHasOtpArea = false;
            numberOfCounter = 0;
            Log.d(TAG, "Tag is of type " + tagMinorName + " with " + userMemory + " bytes user memory");
            return true;
        } else if (tagMajorName.equals(VersionInfo.MajorTagType.MIFARE_DESFire_Light.toString())) {
            // unfortunately the DESFire light tag does not respond on Get Version command
            // when using the NfcA technology (just when using IsoDep class), so this is
            // just a stub and usually this is never called
            tagMajorName = "DESFire light";
            tagMinorName = "DESFire light 640 bytes";
            userMemory = 640;
            userMemoryStartPage = 0;
            userMemoryEndPage = 0; // included
            tagHasFastReadCommand = false;
            tagHasAuthentication = false;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = false;
            tagHasOtpArea = false;
            numberOfCounter = 0;
            Log.d(TAG, "Tag is of type " + tagMinorName + " with " + userMemory + " bytes user memory");
            return true;
        } else {
            tagMajorName = "Unknown1";
            tagMinorName = "Unknown2";
            userMemory = 0;
            userMemoryStartPage = 0;
            userMemoryEndPage = 0; // included
            tagHasFastReadCommand = false;
            tagHasAuthentication = false;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = false;
            tagHasOtpArea = false;
            numberOfCounter = 0;
            Log.d(TAG, "Tag is of type " + tagMinorName + " with " + userMemory + " bytes user memory");
            return false;
        }
    }


    // This method is called when the tag does not respond to a Get Version command
    // It uses some well known ATQA and SAK values to identify the tag, taken from the NXP Tag Identification document
    /*
      IMPORTANT NOTE: It is not advisable to use ATQA and SAK or any other protocol- related parameter to identify PICC's.
      If a system accepts or rejects PICC's based on protocol-related parameters rather than application-specific parameters
      (FCI / GetVersion / AID etc.), it may very well be that future technologies cannot be used in this system. On newer
      PICC generations, the activation parameters are already changeable, so a unique identification is anyhow not possible.
      In general, protocol and application data shall not be mixed at all.
     */
    public boolean identifyTagOnAtqaSak() {
        Log.d(TAG, "identifyTagOnAtqaSak() started");
        Log.d(TAG, printData("atqa", atqa));
        if ((Arrays.equals(atqa, hexStringToByteArray("4400")) && (sak == (byte) 0x00))) {
            Log.d(TAG, "MIFARE Ultralight Family identified");
            // Ultralight Family
            // assume it is an Ultralight C as the first Ultralight tag is no longer used
            tagMajorName = VersionInfo.MajorTagType.MIFARE_Ultralight.toString();
            tagMinorName = "Ultralight C";
            userMemory = 144;
            userMemoryStartPage = 4;
            userMemoryEndPage = 39; // included
            tagMemoryEndPage = 47;
            configurationStartPage = 42;
            tagHasFastReadCommand = false;
            tagHasAuthentication = true;
            tagHasDesAuthenticationSecurity = true;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = true;
            tagHasOtpArea = true;
            numberOfCounter = 1;
            isTag_NfcA_Library_Capable = true;
            return true;
        } else if ((Arrays.equals(atqa, hexStringToByteArray("4403")) && (sak == (byte) 0x20))) {
            Log.d(TAG, "*** Found DESFire light ***");
            // MIFARE DESFire light or NTAG424DNA
            tagMajorName = "Assumed NTAG424 or DESFire light";
            tagMinorName = "Assumed NTAG424 or DESFire light";
            userMemory = 0;
            userMemoryStartPage = 0;
            userMemoryEndPage = 0; // included
            tagMemoryEndPage = 0;
            tagHasFastReadCommand = false;
            tagHasAuthentication = false;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = false;
            tagHasOtpArea = false;
            numberOfCounter = 0;
            return true;
        } else if ((Arrays.equals(atqa, hexStringToByteArray("0400")) && (sak == (byte) 0x08))) {
            // MIFARE DESFire light or NTAG424DNA
            tagMajorName = "Assumed MIFARE Classic";
            tagMinorName = "Assumed MIFARE Classic EV1 2K";
            userMemory = 0;
            userMemoryStartPage = 0;
            userMemoryEndPage = 0; // included
            tagMemoryEndPage = 0;
            tagHasFastReadCommand = false;
            tagHasAuthentication = false;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = false;
            tagHasOtpArea = false;
            numberOfCounter = 0;
            return true;
        } else if ((Arrays.equals(atqa, hexStringToByteArray("0400")) && (sak == (byte) 0x20))) {
            // MIFARE DESFire light or NTAG424DNA
            tagMajorName = "Assumed Credit Card";
            tagMinorName = "Assumed Credit Card";
            userMemory = 0;
            userMemoryStartPage = 0;
            userMemoryEndPage = 0; // included
            tagMemoryEndPage = 0;
            tagHasFastReadCommand = false;
            tagHasAuthentication = false;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = false;
            tagHasOtpArea = false;
            numberOfCounter = 0;
            return true;
        } else if ((Arrays.equals(atqa, hexStringToByteArray("0800")) && (sak == (byte) 0x20))) {
            // MIFARE DESFire light or NTAG424DNA
            tagMajorName = "Assumed German Girocard";
            tagMinorName = "Assumed German Girocard";
            userMemory = 0;
            userMemoryStartPage = 0;
            userMemoryEndPage = 0; // included
            tagMemoryEndPage = 0;
            tagHasFastReadCommand = false;
            tagHasAuthentication = false;
            tagHasDesAuthenticationSecurity = false;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = false;
            tagHasOtpArea = false;
            numberOfCounter = 0;
            return true;
        }
        tagMajorName = "UNKNOWN TAG";
        tagMinorName = "UNKNOWN TAG";
        userMemory = 0;
        userMemoryStartPage = 0;
        userMemoryEndPage = 0; // included
        tagMemoryEndPage = 0;
        tagHasFastReadCommand = false;
        tagHasAuthentication = false;
        tagHasDesAuthenticationSecurity = false;
        tagHasPasswordSecurity = false;
        tagHasPageLockBytes = false;
        tagHasOtpArea = false;
        numberOfCounter = 0;
        return false;
    }

    public VersionInfo getTagVersionData() {
        return tagVersionData;
    }
}


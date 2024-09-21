package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.hexStringToByteArray;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;

import java.util.Arrays;

/**
 * This class takes all technical informations about a tag, taken from the Get Version command
 * and other sources. The data is used define ranges e.g. for the free user memory on the tag.
 *
 */
public class TagInformation {

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

    public TagInformation(byte[] tagUid, byte[] atqa, byte sak) {
        this.tagUid = tagUid;
        this.atqa = atqa;
        this.sak = sak;
    }

    public TagInformation(byte[] tagUid, byte[] atqa, byte sak, int maxTransceiveLength, String[] technologies) {
        this.tagUid = tagUid;
        this.atqa = atqa;
        this.sak = sak;
        this.maxTransceiveLength = maxTransceiveLength;
        this.technologies = technologies;
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
        System.out.println("identifyTagOnAtqaSak() started");
        System.out.println(printData("atqa", atqa));
        if ((Arrays.equals(atqa, hexStringToByteArray("4400")) && (sak == (byte) 0x00))) {
            System.out.println("Ultralight Family identified");
            // Ultralight Family
            // assume it is an Ultralight C as the first Ultralight tag is no longer used
            tagMajorName = VersionInfo.MajorTagType.MIFARE_Ultralight.toString();
            tagMinorName = "Ultralight C";
            userMemory = 144;
            userMemoryStartPage = 4;
            userMemoryEndPage = 39; // included
            tagMemoryEndPage = 47;
            tagHasFastReadCommand = false;
            tagHasAuthentication = true;
            tagHasDesAuthenticationSecurity = true;
            tagHasPasswordSecurity = false;
            tagHasPageLockBytes = true;
            tagHasOtpArea = true;
            numberOfCounter = 1;
            return true;
        } else if((Arrays.equals(atqa, hexStringToByteArray("4403")) && (sak == (byte) 0x20))) {
            System.out.println("*** Found DESFire light ***");
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
            tagHasPageLockBytes = false ;
            tagHasOtpArea = false;
            numberOfCounter = 0;
            return true;
        } else if((Arrays.equals(atqa, hexStringToByteArray("0400")) && (sak == (byte) 0x08))) {
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

    public void identifyTag() {

        // todo fill with life
/*
Ultralight Family

NTAG21x
ATQA: 4400
SAK: 00

DESFire EVx:
ATQA: 4403
SAK: 20

DESFire light:
ATQA: 4403
SAK: 20
in NfcA class does not run getCommand

NTAG424DNA
ATQA: 4403
SAK: 20
in NfcA class does not run getCommand

GiroCard
ATQA: 0800
SAK: 20

CreditCard + VPay Girocard
ATQA: 0400
SAK: 20

 */
    }
}

package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.checkResponse;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.fastReadPage;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.getMoreData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.getVersion;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.lastExceptionString;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readPage;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readSignature;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.resolveCheckResponse;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.verifyNtag21xOriginalitySignature;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.writePage;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.byteToHex;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.bytesToHexNpe;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.concatenateByteArrays;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.hexStringToByteArray;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.testBit;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private TextView textView;
    private NfcAdapter myNfcAdapter;
    private TagInformation ti;
    boolean tagIdentificationAtqaSakSuccess = false; // identification on ATQA & SAK if Get Version fails

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        textView = findViewById(R.id.textView);
        myNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    /**
     * Please note: this method is NOT running in User Interface (UI) Thread, so you cannot write directly
     * to any TextView, Toasts or other elements on your activity. Please use runOnUiThread instead:
     * runOnUiThread(() -> {
     * Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
     * });
     *
     * @param tag
     */
    @Override
    public void onTagDiscovered(Tag tag) {
        String output = "";
        String lineDivider = "--------------------";
        output += "Android Advanced NFC NfcA App" + "\n";
        output += "NFC tag detected" + "\n";

        // examine tag
        byte[] tagUid = tag.getId();
        output += "Tag UID of the discovered tag\n" + printData("UID", tagUid) + "\n";
        String[] techlist = tag.getTechList();
        output += lineDivider + "\n";
        output += "The TechList contains " + techlist.length + " entry/ies:" + "\n";
        for (int i = 0; i < techlist.length; i++) {
            output += "Entry " + i + ": " + techlist[i] + "\n";
        }
        output += lineDivider + "\n";
        output += tag.toString() + "\n";
        output += lineDivider + "\n";
        // if the tag uses the NfcA class I'm connecting the tag now this class
        // I'm trying to use the NfcA class, if it is not supported by the tag an exception is thrown
        NfcA nfcA = null;
        nfcA = NfcA.get(tag);
        if (nfcA == null) {
            output += "This tag is NOT supporting the NfcA class" + "\n";
            output += lineDivider + "\n";
        } else {
            // I'm trying to get more information's about the tag and connect to the tag
            byte[] atqa = nfcA.getAtqa();
            byte sak = (byte) nfcA.getSak();
            int maxTransceiveLength = nfcA.getMaxTransceiveLength();
            output += "-= NfcA Technology data =-" + "\n";
            output += "ATQA: " + bytesToHexNpe(atqa) + "\n";
            output += "SAK: " + byteToHex(sak) + "\n";
            output += "maxTransceiveLength: " + maxTransceiveLength + "\n";
            output += lineDivider + "\n";

            // instantiate a TagInformation object
            ti = new TagInformation(tagUid, atqa, sak, maxTransceiveLength, techlist);

            try {
                nfcA.connect();
                output += "Connected to the tag using NfcA technology" + "\n";
                // in connected state we are doing all our jobs

                // run a 'get version' command
                output += lineDivider + "\n";
                output += "GET VERSION data" + "\n";
                byte[] getVersionData = getVersion(nfcA);
                // Get Version data: 0004040201001303
                boolean getVersionSuccess = false;
                tagIdentificationAtqaSakSuccess = false;
                if (getVersionData == null) {
                    output += "Could not read the version of the tag, maybe it is read protected or does not provide a Get Version command ?" + "\n";
                    output += "Exception from operation: " + lastExceptionString + "\n";
                    // try to identify the tag by atqa and sak values
                    tagIdentificationAtqaSakSuccess = ti.identifyTagOnAtqaSak();
                    System.out.println("Tag Identification: " + tagIdentificationAtqaSakSuccess);

                } else {
                    /**
                     * We got a response but need to check the response data
                     * in case everything was ok we received the 8 bytes long version data
                     * but there is a special case: newer NFC tags from NXP will give more
                     * information on the getVersionCommand that get be included in the 8
                     * bytes response. This is indicated by a trailing "AF" in the response:
                     * Example from MIFARE DESFire EV3:
                     * Get Version data: AF04010133001605
                     * In this case the card asks the reader to get more data by sending
                     * an "0xAF" command.
                     * This is implemented in the following lines
                     */
                    System.out.println(printData("getVersionData", getVersionData));
                    if (getVersionData[0] == (byte) 0xAF) {
                        output += "Received an 'AF' request -> asking for more data" + "\n";
                        // we need to repeat the 'more data' command until no more data is provided
                        byte[] moreData = getMoreData(nfcA);
                        // skip the trailing 'AF' and concatenate it with moreData to get the full get version data
                        byte[] shortedGetVersionData = Arrays.copyOfRange(getVersionData, 1, getVersionData.length);
                        System.out.println(printData("shortedGetVersionData", shortedGetVersionData));
                        getVersionData = concatenateByteArrays(Arrays.copyOfRange(getVersionData, 1, getVersionData.length), moreData);
                    }
                    // in all other cases something went wrong, but those responses are tag type specific
                    if (getVersionData.length > 2) {
                        output += "Get Version data: " + bytesToHexNpe(getVersionData) + "\n";
                        //output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                        getVersionSuccess = true;
                        ti.tagHasGetVersionCommand = true;
                    } else if (Arrays.equals(getVersionData, hexStringToByteArray("04"))) {
                        output += "You probably tried to read a MIFARE Classic tag. This is possible after a successful authentication only." + "\n";
                        output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                        getVersionSuccess = false;
                                // try to identify the tag by atqa and sak values
                        tagIdentificationAtqaSakSuccess = ti.identifyTagOnAtqaSak();
                        System.out.println("Tag Identification 04: " + tagIdentificationAtqaSakSuccess);
                    } else if (Arrays.equals(getVersionData, hexStringToByteArray("1C"))) {
                        output += "You probably tried to read a MIFARE DESFire tag. This is possible using another workflow only." + "\n";
                        output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                        getVersionSuccess = false;
                        // try to identify the tag by atqa and sak values
                        tagIdentificationAtqaSakSuccess = ti.identifyTagOnAtqaSak();
                        System.out.println("Tag Identification 1C: " + tagIdentificationAtqaSakSuccess);
                    } else if (Arrays.equals(getVersionData, hexStringToByteArray("6700"))) {
                        output += "You probably tried to read a Credit Card tag. This is possible using another workflow only." + "\n";
                        output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                        getVersionSuccess = false;
                        // try to identify the tag by atqa and sak values
                        tagIdentificationAtqaSakSuccess = ti.identifyTagOnAtqaSak();
                        System.out.println("Tag Identification 6700: " + tagIdentificationAtqaSakSuccess);
                    } else {
                        getVersionSuccess = false;
                        output += "The tag responded with an unknown response. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                        output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                    }
                }

                // analyze the get version data
                output += lineDivider + "\n";
                if (getVersionSuccess) {
                    output += "Analyze the get version data" + "\n";
                    VersionInfo tagVersionData = new VersionInfo(getVersionData);
                    if (getVersionData.length == 8) {
                        output += tagVersionData.dump8Bytes();
                    } else {
                        output += tagVersionData.dump();
                    }
                    // take some information from Get Version
                    ti.tagMajorName = tagVersionData.tagTypeLookup(tagVersionData.getHardwareType());
                    // data for NTAG21x
                    if (ti.tagMajorName.equals(VersionInfo.MajorTagType.NTAG_21x.toString())) {
                        // get more data for NTAG21x family
                        if (tagVersionData.getHardStorageSizeRaw() == 15) {
                            // Get Version data: 0004040201000F03
                            // it is an NTAG213
                            ti.tagMinorName = "NTAG213";
                            ti.userMemory = 144;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 39; // included
                            ti.tagMemoryEndPage = 44;
                        } else if (tagVersionData.getHardStorageSizeRaw() == 17) {
                            // Get Version data: 0004040201001103
                            // it is an NTAG215
                            ti.tagMinorName = "NTAG215";
                            ti.userMemory = 504;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 129; // included
                            ti.tagMemoryEndPage = 134;
                        } else if (tagVersionData.getHardStorageSizeRaw() == 19) {
                            // Get Version data: 0004040201001303
                            // it is an NTAG216
                            ti.tagMinorName = "NTAG216";
                            ti.userMemory = 888;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 225; // included
                            ti.tagMemoryEndPage = 230;
                        } else {
                            ti.tagMinorName = "NTAG21x Unknown";
                            ti.userMemory = 0;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0;
                        }
                        // general information for NTAG21x family
                        ti.tagHasFastReadCommand = true;
                        ti.tagHasAuthentication = true;
                        ti.tagHasDesAuthenticationSecurity = false;
                        ti.tagHasPasswordSecurity = true;
                        ti.tagHasPageLockBytes = true;
                        ti.tagHasOtpArea = true;
                        ti.numberOfCounter = 1;
                        output += "Tag is of type " + ti.tagMinorName + " with " + ti.userMemory + " bytes user memory" + "\n";
                    } else if (ti.tagMajorName.equals(VersionInfo.MajorTagType.MIFARE_Ultralight.toString())) {
                        if (tagVersionData.getHardStorageSizeRaw() == 11) {
                            // Get Version data: 0004030101000B03
                            // it is an Ultralight EV1 with 48 bytes user memory MF0UL11
                            ti.tagMinorName = "MF0UL11";
                            ti.userMemory = 48;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 15; // included
                            ti.tagMemoryEndPage = 19;
                        } else if (tagVersionData.getHardStorageSizeRaw() == 14) {
                            // Get Version data: 0004030101000E03
                            // it is an Ultralight EV1 with 128 bytes user memory MF0UL21
                            ti.tagMinorName = "MF0UL21";
                            ti.userMemory = 128;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 35; // included
                            ti.tagMemoryEndPage = 40;
                        } else {
                            ti.tagMinorName = "MF0ULx Unknown";
                            ti.userMemory = 0;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0;
                        }
                        // general information for Ultralight EV1 family
                        ti.tagHasFastReadCommand = true;
                        ti.tagHasAuthentication = true;
                        ti.tagHasDesAuthenticationSecurity = false;
                        ti.tagHasPasswordSecurity = true;
                        ti.tagHasPageLockBytes = true;
                        ti.tagHasOtpArea = true;
                        ti.numberOfCounter = 3;
                        output += "Tag is of type " + ti.tagMinorName + " with " + ti.userMemory + " bytes user memory" + "\n";
                    } else if (ti.tagMajorName.equals(VersionInfo.MajorTagType.MIFARE_DESFire.toString())) {
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
                            ti.tagMinorName = "DESFire " + desfireSubType + " 2K";
                            ti.userMemory = 2048;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0; // included
                        } else if (tagVersionData.getHardStorageSizeRaw() == 24) {
                            // Get Version data: 04010112001605040101020116050004464BDAD37580CF5B9665003521
                            // it is an DESFire EVx 4K with 4096 bytes user memory
                            ti.tagMinorName = "DESFire " + desfireSubType + " 4K";
                            ti.userMemory = 4096;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0; // included
                        } else if (tagVersionData.getHardStorageSizeRaw() == 26) {
                            // it is an DESFire EVx 8K with 8192 bytes user memory
                            ti.tagMinorName = "DESFire " + desfireSubType + " 8K";
                            ti.userMemory = 8196;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0; // included
                        } else if (tagVersionData.getHardStorageSizeRaw() == 28) {
                            // it is an DESFire EVx 16K with 4096 bytes user memory
                            ti.tagMinorName = "DESFire " + desfireSubType + " 16K";
                            ti.userMemory = 16384;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0; // included
                        } else if (tagVersionData.getHardStorageSizeRaw() == 30) {
                            // it is an DESFire EVx 32K with 4096 bytes user memory
                            ti.tagMinorName = "DESFire " + desfireSubType + " 32K";
                            ti.userMemory = 32768;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0; // included
                        } else {
                            ti.tagMinorName = "DESFire " + desfireSubType + " unknown memory";
                            ti.userMemory = 0;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0;
                        }
                        // general information for DESFire EVx family
                        ti.tagHasFastReadCommand = false;
                        ti.tagHasAuthentication = false;
                        ti.tagHasDesAuthenticationSecurity = false;
                        ti.tagHasPasswordSecurity = false;
                        ti.tagHasPageLockBytes = false;
                        ti.tagHasOtpArea = false;
                        output += "Tag is of type " + ti.tagMinorName + " with " + ti.userMemory + " bytes user memory" + "\n";
                    } else if (ti.tagMajorName.equals(VersionInfo.MajorTagType.MIFARE_DESFire_Light.toString())) {
                        // unfortunately the DESFire light tag does not respond on Get Version command
                        // when using the NfcA technology (just when using IsoDep class), so this is
                        // just a stub and usually this is never called
                        ti.tagMajorName = "DESFire light";
                        ti.tagMinorName = "DESFire light 640 bytes";
                        ti.userMemory = 640;
                        ti.userMemoryStartPage = 0;
                        ti.userMemoryEndPage = 0; // included
                        ti.tagHasFastReadCommand = false;
                        ti.tagHasAuthentication = false;
                        ti.tagHasDesAuthenticationSecurity = false;
                        ti.tagHasPasswordSecurity = false;
                        ti.tagHasPageLockBytes = false;
                        ti.tagHasOtpArea = false;
                        output += "Tag is of type " + ti.tagMinorName + " with " + ti.userMemory + " bytes user memory" + "\n";
                    } else {
                        if (!tagIdentificationAtqaSakSuccess) {
                            ti.tagMajorName = "Unknown2";
                            ti.tagMinorName = "Unknown3";
                            ti.userMemory = 0;
                            ti.userMemoryStartPage = 0;
                            ti.userMemoryEndPage = 0; // included
                            ti.tagHasFastReadCommand = false;
                            ti.tagHasAuthentication = false;
                            ti.tagHasDesAuthenticationSecurity = false;
                            ti.tagHasPasswordSecurity = false;
                            ti.tagHasPageLockBytes = false;
                            ti.tagHasOtpArea = false;
                            output += "This is an UNKNOWN tag type" + "\n";
                        }
                    }
                } else {
                    output += "Analyzing of the get version data skipped, using ATQA & SAK tag identification" + "\n";
                    if (tagIdentificationAtqaSakSuccess) {
                        output += "Tag is probably of type " + ti.tagMinorName + " with " + ti.userMemory + " bytes user memory" + "\n";
                    }
                }


                // Read the Elliptic Curve Signature
                // read a page from the tag
                output += lineDivider + "\n";
                output += "Read the Signature" + "\n";
                byte[] readSignatureResponse = readSignature(nfcA);
                output += printData("readSignatureResponse", readSignatureResponse) + "\n";
                output += "For verification the signature please read the docs." + "\n";
                boolean signatureVerified = false;
                if (readSignatureResponse == null) {
                    output += "Signature verification skipped as no signature was given" + "\n";
                } else {
                    signatureVerified = verifyNtag21xOriginalitySignature(tagUid, readSignatureResponse);
                    output += "Result of Originality Signature verification: " + signatureVerified + "\n";
                }

                // read a page from the tag
                output += lineDivider + "\n";
                if (ti.userMemory > 0) {
                    output += "Read pages from page 04" + "\n";
                    byte[] pagesData = readPage(nfcA, 2); // page 04 is the first page of the user memory
                    boolean readSuccess = false;
                    if (pagesData == null) {
                        output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                        output += "Exception from operation: " + lastExceptionString + "\n";
                    } else {
                        // we got a response but need to check the response data
                        // in case everything was ok we received the full content of 4 pages = 16 bytes
                        // in all other cases something went wrong, but those responses are tag type specific
                        if (pagesData.length == 16) {
                            output += "data from sectors 4, 5, 6 and 7: " + bytesToHexNpe(pagesData) + "\n";
                            output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                            readSuccess = true;
                        } else if (Arrays.equals(pagesData, hexStringToByteArray("04"))) {
                            output += "You probably tried to read a MIFARE Classic tag. This is possible after a successful authentication only." + "\n";
                            output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                        } else if (Arrays.equals(pagesData, hexStringToByteArray("1C"))) {
                            output += "You probably tried to read a MIFARE DESFire tag. This is possible using another workflow only." + "\n";
                            output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                        } else if (Arrays.equals(pagesData, hexStringToByteArray("6700"))) {
                            output += "You probably tried to read a Credit Card tag. This is possible using another workflow only." + "\n";
                            output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                        } else {
                            output += "The tag responded with an unknown response. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                            output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                        }
                    }
                } else {
                    output += "The tag is ot readable by the READ command, sorry." + "\n";
                }

                // fast read the complete tag content
                output += lineDivider + "\n";
                if (ti.tagHasFastReadCommand) {
                    output += "FastRead pages from page 00" + "\n";
                    //pagesData = fastReadPage(nfcA, 0, 44); // NTAG213 range 00 - 44
                    //pagesData = fastReadPage(nfcA, 0, 130); // NTAG215 range 00 - 134
                    //pagesData = fastReadPage(nfcA, 0, 230); // NTAG216 range 00 - 230

                    //don't extend the maxTransceiveLength as it might gets strange data
                    // simple calculation including some protocol header bytes
                    int maxFastReadPages = ((maxTransceiveLength - 16) / 4);
                    System.out.println("maxFastReadPages: " + maxFastReadPages);
                    // so run this way to read the complete content of the tag
                    byte[] completeContentFastRead = new byte[0];
                    System.out.println("completeContentFastRead length: " + completeContentFastRead.length);
                    System.out.println("ti.tagMemoryEndPage:" + ti.tagMemoryEndPage);
                    int pagesReadSoFar = 0;
                    boolean fastReadSuccess = true;
                    while (pagesReadSoFar < ti.tagMemoryEndPage) {
                        // if we can't read the remaining data in a 'full' read we are just reading the remaining data
                        if ((ti.tagMemoryEndPage - pagesReadSoFar) < maxFastReadPages) {
                            maxFastReadPages = ti.tagMemoryEndPage - pagesReadSoFar + 1;
                            System.out.println("Corrected maxFastReadPages to: " + maxFastReadPages);
                        }
                        System.out.println("fastReadContent from " + pagesReadSoFar + " to " + (pagesReadSoFar + maxFastReadPages - 1));
                        byte[] contentRead = fastReadPage(nfcA, pagesReadSoFar, (pagesReadSoFar + maxFastReadPages - 1));
                        System.out.println(printData("contentRead", contentRead));
                        //System.out.println("contentRead length: " + contentRead.length);
                        System.out.println("(maxFastReadPages * ti.bytesPerPage): " + (maxFastReadPages * ti.bytesPerPage));
                        // did we receive all data ?
                        if ((contentRead != null) && (contentRead.length == (maxFastReadPages * ti.bytesPerPage))) {
                            // we received the complete content
                            completeContentFastRead = concatenateByteArrays(completeContentFastRead, contentRead);
                            pagesReadSoFar += maxFastReadPages;
                        } else {
                            fastReadSuccess = false;
                            pagesReadSoFar = ti.tagMemoryEndPage; // end reading
                        }
                    }
                    if (!fastReadSuccess) {
                        output += "Error while reading the complete content of the tag, e.g. some parts of the tag might be read protected" + "\n";
                        output += "This is the skipped content:\n" + printData("skipped tag content", completeContentFastRead) + "\n";
                    } else {
                        output += printData("Full tag content", completeContentFastRead) + "\n";
                    }
                } else {
                    output += "FastRead skipped, tag has no command" + "\n";
                }



/*
maxTranceiveLength = 253 / 4 = 63.25 -> read 60 pages only
NTAG213 fastRead 0 - 44 length: 180 of 180

NTAG215 fastRead 0 - 100 length: 404 of 540
NTAG215 fastRead 0 - 130 length: 12 of 540
NTAG215 fastRead 0 - 230 length: 412 of 924
NTAG216 fastRead 0 - 230 length: 412 of 924
 */
/*
                readSuccess = false;
                if (pagesData == null) {
                    output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                    output += "Exception from operation: " + lastExceptionString + "\n";
                } else {
                    // we got a response but need to check the response data
                    // in case everything was ok we received the full content of 4 pages = 16 bytes
                    // in all other cases something went wrong, but those responses are tag type specific
                    System.out.println(printData("fastRead", pagesData));
                    if (pagesData.length == 16) {
                        output += "data from sectors 4, 5, 6 and 7: " + bytesToHexNpe(pagesData) + "\n";
                        output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                        readSuccess = true;
                    } else if (Arrays.equals(pagesData, hexStringToByteArray("04"))) {
                        output += "You probably tried to read a MIFARE Classic tag. This is possible after a successful authentication only." + "\n";
                        output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                    } else if (Arrays.equals(pagesData, hexStringToByteArray("1C"))) {
                        output += "You probably tried to read a MIFARE DESFire tag. This is possible using another workflow only." + "\n";
                        output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                    } else if (Arrays.equals(pagesData, hexStringToByteArray("6700"))) {
                        output += "You probably tried to read a Credit Card tag. This is possible using another workflow only." + "\n";
                        output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                    } else {
                        output += "The tag responded with an unknown response. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                        output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                    }
                }
*/

                // todo make this flexible for different tags
                // analyze ACCESS byte
                // todo check for NTAG213
                // AUTH0 is on page 41 byte 4, ACCESS on page 42 byte 0
                /*
                byte[] pagesData41 = readPage(nfcA, 41);
                System.out.println(printData("pagesData41", pagesData41));
                byte auth0 = pagesData41[3];
                output += printData("auth0", new byte[]{auth0}) + "\n";
                byte accessByte = pagesData41[4];
                if (testBit(accessByte, 0)) {
                    output += "Access Byte Bit 0 AuthLim0 1" + "\n";
                } else {
                    output += "Access Byte Bit 0 AuthLim0 0" + "\n";
                }
                if (testBit(accessByte, 1)) {
                    output += "Access Byte Bit 1 AuthLim1 1" + "\n";
                } else {
                    output += "Access Byte Bit 1 AuthLim1 0" + "\n";
                }
                if (testBit(accessByte, 2)) {
                    output += "Access Byte Bit 2 AuthLim2 1" + "\n";
                } else {
                    output += "Access Byte Bit 2 AuthLim2 0" + "\n";
                }
                if (testBit(accessByte, 3)) {
                    output += "Access Byte Bit 3 CountProt 1 enabled" + "\n";
                } else {
                    output += "Access Byte Bit 3 CountProt 0 disabled" + "\n";
                }
                if (testBit(accessByte, 4)) {
                    output += "Access Byte Bit 4 CountEnab 1 enabled" + "\n";
                } else {
                    output += "Access Byte Bit 4 CountEnab 0 disabled" + "\n";
                }
                output += "Access Byte Bit 5 RFU" + "\n";
                if (testBit(accessByte, 6)) {
                    output += "Access Byte Bit 6 ConfigLocked 1 enabled" + "\n";
                } else {
                    output += "Access Byte Bit 6 CountLocked 0 disabled" + "\n";
                }
                if (testBit(accessByte, 7)) {
                    output += "Access Byte Bit 7 1 Read&Write Access AUTH prot" + "\n";
                } else {
                    output += "Access Byte Bit 7 0 Write Access AUTH prot" + "\n";
                }
*/

                //
                output += lineDivider + "\n";
                output += "write on page 04" + "\n";
                byte[] dataToWrite = "1234".getBytes(StandardCharsets.UTF_8);
                byte[] writeResponse = writePage(nfcA, 4, dataToWrite);
                output += printData("writeToPage 04 response", writeResponse) + "\n";
                // I'm using byte 0 only for checking
                output += "Check writeResponse: " + checkResponse(writeResponse[0]) + "\n";
                output += "Check writeResponse: " + resolveCheckResponse(writeResponse[0]) + "\n";

                output += lineDivider + "\n";
                output += "test for data too long" + "\n";
                dataToWrite = "12345".getBytes(StandardCharsets.UTF_8);
                writeResponse = writePage(nfcA, 4, dataToWrite);
                output += printData("writeToPage 04 response", writeResponse) + "\n";
                // I'm using byte 0 only for checking
                output += "Check writeResponse: " + checkResponse(writeResponse[0]) + "\n";
                output += "Check writeResponse: " + resolveCheckResponse(writeResponse[0]) + "\n";

                output += lineDivider + "\n";
                output += "test for page outside of tag range" + "\n";
                dataToWrite = "1234".getBytes(StandardCharsets.UTF_8);
                writeResponse = writePage(nfcA, 254, dataToWrite);
                output += printData("writeToPage 04 response", writeResponse) + "\n";
                // I'm using byte 0 only for checking
                output += "Check writeResponse: " + checkResponse(writeResponse[0]) + "\n";
                output += "Check writeResponse: " + resolveCheckResponse(writeResponse[0]) + "\n";

/*
                // Read the Elliptic Curve Signature
                // read a page from the tag
                output += lineDivider + "\n";
                output += "Read the Signature" + "\n";
                byte[] readSignatureResponse = readSignature(nfcA);
                output += printData("readSignatureResponse", readSignatureResponse);
                */
                /*
                if (ti.userMemory > 0) {
                    output += "Read pages from page 04" + "\n";
                    byte[] pagesData = readPage(nfcA, 2); // page 04 is the first page of the user memory
                    boolean readSuccess = false;
                    if (pagesData == null) {
                        output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                        output += "Exception from operation: " + lastExceptionString + "\n";
                    } else {
                        // we got a response but need to check the response data
                        // in case everything was ok we received the full content of 4 pages = 16 bytes
                        // in all other cases something went wrong, but those responses are tag type specific
                        if (pagesData.length == 16) {
                            output += "data from sectors 4, 5, 6 and 7: " + bytesToHexNpe(pagesData) + "\n";
                            output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                            readSuccess = true;
                        } else if (Arrays.equals(pagesData, hexStringToByteArray("04"))) {
                            output += "You probably tried to read a MIFARE Classic tag. This is possible after a successful authentication only." + "\n";
                            output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                        } else if (Arrays.equals(pagesData, hexStringToByteArray("1C"))) {
                            output += "You probably tried to read a MIFARE DESFire tag. This is possible using another workflow only." + "\n";
                            output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                        } else if (Arrays.equals(pagesData, hexStringToByteArray("6700"))) {
                            output += "You probably tried to read a Credit Card tag. This is possible using another workflow only." + "\n";
                            output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                        } else {
                            output += "The tag responded with an unknown response. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                            output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                        }
                    }
                } else {
                    output += "The tag is ot readable by the READ command, sorry." + "\n";
                }
                */

                nfcA.close();
            } catch (IOException e) {
                output += "NfcA connect to tag IOException: " + e.getMessage() + "\n";
                output += lineDivider + "\n";
            }
        }

        // final output
        String finalOutput = output;

        runOnUiThread(() ->

        {
            textView.setText(finalOutput);
        });
        // output of the logfile to console
        System.out.println(output);

        // a short information about the detection of an NFC tag after all reading is done
        playBeep();
    }

    /**
     * When the activity returns to foreground the ReaderMode gets enabled. Here I'm setting just
     * the NfcA technology flag meaning that only the NfcA NFC technology is allowed to get detected.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (myNfcAdapter != null) {
            if (!myNfcAdapter.isEnabled())
                showWirelessSettings();
            Bundle options = new Bundle();
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
            // Enable ReaderMode for NfcA technology types of cards only and disable platform sounds
            myNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }

        /*
        myNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
         */
    }

    /**
     * When the activity gets inactive the ReaderMode is disabled
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (myNfcAdapter != null)
            myNfcAdapter.disableReaderMode(this);
    }

    /**
     * If the onResume() method detects that the NFC option is not enabled this method will forward you
     * to the Settings to enable NFC.
     */
    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    public void playBeep() {
        ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
    }
}

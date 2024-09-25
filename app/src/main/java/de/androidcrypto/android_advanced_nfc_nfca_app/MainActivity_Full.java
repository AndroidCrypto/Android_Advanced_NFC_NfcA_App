package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.CUSTOM_PACK;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.CUSTOM_PASSWORD;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.DEFAULT_PACK;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.DEFAULT_PASSWORD;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.analyzeConfigurationPages;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.authenticatePassword;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.changePasswordPack;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.checkResponse;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.fastReadPage;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.formatNtag21xToFactorySettings;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.formatUltralightEv1ToFactorySettings;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.getMoreData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.getVersion;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.increaseCounterByOne;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.lastExceptionString;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readConfigurationPages;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readCounter;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readCounterInt;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readFullTag;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readPage;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readSignature;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.reconnect;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.resolveCheckResponse;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.setAsciiMirroring;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.verifyNtag21xOriginalitySignature;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.writeBulkData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.writePage;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.byteToHex;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.bytesToHexNpe;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.concatenateByteArrays;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.getTimestamp4Bytes;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.hexStringToByteArray;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;

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

public class MainActivity_Full extends AppCompatActivity implements NfcAdapter.ReaderCallback {

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
        String chapterDivider = "==============================";
        String lineDivider = "------------------------------";
        output += chapterDivider + "\n";
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
            output += "This tag is NOT supporting the NfcA class, aborted" + "\n";
            output += chapterDivider + "\n";
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

                boolean runGetVersion = true; // don't skip this as we need the tag data for later working
                boolean runReadPages03 = true;
                boolean runReadPages47 = true;
                boolean runFastRead0011 = true;
                boolean runWritePage04 = true;
                boolean runWriteBulkDataPage05 = true;
                boolean runReadCounter = true;
                boolean runIncreaseCounter0 = false;
                boolean runReadSignature = true;
                boolean runFastReadComplete = true;

                boolean runAuthenticationDefault = false;
                boolean runAuthenticationCustom = false;
                boolean runChangePasswordDefaultToCustom = false;
                boolean runChangePasswordCustomToDefault = false;

                boolean runReadConfiguration = false;
                // for enabling the following methods the readConfiguration needs to be true
                boolean runSetAuthProtectionPage05 = false;
                boolean runDisableAuthProtection = false;
                boolean runEnableNfcReadCounter = false;
                boolean runDisableNfcReadCounter = false;
                boolean runSetNfcReadCounterPwdProtected = false;
                boolean runUnsetNfcReadCounterPwdProtected = false;
                boolean runSetAutProtectionWriteOnly = false;
                boolean runSetAuthProtectionReadWrite = false;

                boolean runEnableAsciiMirroring = false;
                boolean runDisableAsciiMirroring = false;
                //boolean runReadPages03 = false;
                //boolean runReadPages47 = false;
                boolean runReadPages8b = false;
                boolean runReadPagesf0 = false; // test the behavior for a request outside the tag memory
                boolean runReadConfigPages = false;
                boolean runFastRead0015 = false;
                //boolean runWritePage04 = false;
                //boolean runWriteBulkDataPage04 = false;
                boolean runFormatTagToFabricSettings = false;

                output += chapterDivider + "\n";
                output += lineDivider + "\n";
                output += "==== Tasks Overview ====" + "\n";
                output += "= Get Version           " + runGetVersion + "\n";
                output += "= Read Pages 0..3       " + runReadPages03 + "\n";
                output += "= Read Pages 4..7       " + runReadPages47 + "\n";
                output += "= FastRead Pages 00-11  " + runFastRead0011 + "\n";
                //output += "= FastRead compl.Tag    " + runFastReadComplete + "\n";
                output += "= Write Page 04         " + runWritePage04 + "\n";
                output += "= Wr. Bulk Data Page 05 " + runWriteBulkDataPage05 + "\n";
                output += "= Read Counter          " + runReadCounter + "\n";
                output += "= Increase Counter 0    " + runIncreaseCounter0 + "\n";
                output += "= Read Signature        " + runReadSignature + "\n";
                output += "= FastRead compl.Tag    " + runFastReadComplete + "\n";
                /*

                output += "= Authenticate Default  " + runAuthenticationDefault + "\n";
                output += "= Authenticate Custom   " + runAuthenticationCustom + "\n";
                output += "= chg Password->Custom  " + runChangePasswordDefaultToCustom + "\n";
                output += "= chg Password->Default " + runChangePasswordCustomToDefault + "\n";
                output += "= Read Configuration    " + runReadConfiguration + "\n";
                output += "= Enable Auth Prot.P05  " + runSetAuthProtectionPage05 + "\n";
                output += "= Disable Auth Prot     " + runDisableAuthProtection + "\n";
                output += "= Enable NFC Read Cnt   " + runEnableNfcReadCounter + "\n";
                output += "= Disable NFC Read Cnt  " + runDisableNfcReadCounter + "\n";
                output += "= Set ReadCnt PWD Prot  " + runSetNfcReadCounterPwdProtected + "\n";
                output += "= UnsetReadCnt PWD Prt  " + runUnsetNfcReadCounterPwdProtected + "\n";
                output += "= Auth Prot. Write      " + runSetAutProtectionWriteOnly + "\n";
                output += "= Auth Prot. ReadWrite  " + runSetAuthProtectionReadWrite + "\n";
                output += "= + ASCII UID/Cnt mirr  " + runEnableAsciiMirroring + "\n";
                output += "= - ASCII UID/Cnt mirr  " + runDisableAsciiMirroring + "\n";

                output += "= Read Pages 8..11      " + runReadPages8b + "\n";
                output += "= Read Pages F0..F3     " + runReadPagesf0 + "\n";
                output += "= Read Config Pages     " + runReadConfigPages + "\n";
                output += "= FastRead Pages 00-15  " + runFastRead0015 + "\n";


                output += "= Format Tag Fabric Set." + runFormatTagToFabricSettings + "\n";
                */

                output += "==== Tasks Overview End ====" + "\n";

                if (runGetVersion) {
                    // run a 'get version' command
                    output += chapterDivider + "\n";
                    output += "GET VERSION data" + "\n";
                    output += "Run the GetVersion command and tries to identify the tag" + "\n";
                    byte[] getVersionData = getVersion(nfcA);
                    // Get Version data: 0004040201001303
                    boolean getVersionSuccess = false;
                    tagIdentificationAtqaSakSuccess = false;
                    if (getVersionData == null) {
                        output += "Could not read the version of the tag, maybe it is read protected or does not provide a Get Version command ?" + "\n";
                        output += "Exception from operation: " + lastExceptionString + "\n";
                        // try to identify the tag by atqa and sak values
                        tagIdentificationAtqaSakSuccess = ti.identifyTagOnAtqaSak();
                        //System.out.println("Tag Identification: " + tagIdentificationAtqaSakSuccess);

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
                        //System.out.println(printData("getVersionData", getVersionData));
                        if (getVersionData[0] == (byte) 0xAF) {
                            output += "Received an 'AF' request -> asking for more data" + "\n";
                            // we need to repeat the 'more data' command until no more data is provided
                            byte[] moreData = getMoreData(nfcA);
                            // skip the trailing 'AF' and concatenate it with moreData to get the full get version data
                            byte[] shortedGetVersionData = Arrays.copyOfRange(getVersionData, 1, getVersionData.length);
                            //System.out.println(printData("shortedGetVersionData", shortedGetVersionData));
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
                            //System.out.println("Tag Identification 04: " + tagIdentificationAtqaSakSuccess);
                        } else if (Arrays.equals(getVersionData, hexStringToByteArray("1C"))) {
                            output += "You probably tried to read a MIFARE DESFire tag. This is possible using another workflow only." + "\n";
                            output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                            getVersionSuccess = false;
                            // try to identify the tag by atqa and sak values
                            tagIdentificationAtqaSakSuccess = ti.identifyTagOnAtqaSak();
                            //System.out.println("Tag Identification 1C: " + tagIdentificationAtqaSakSuccess);
                        } else if (Arrays.equals(getVersionData, hexStringToByteArray("6700"))) {
                            output += "You probably tried to read a Credit Card tag. This is possible using another workflow only." + "\n";
                            output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                            getVersionSuccess = false;
                            // try to identify the tag by atqa and sak values
                            tagIdentificationAtqaSakSuccess = ti.identifyTagOnAtqaSak();
                            //System.out.println("Tag Identification 6700: " + tagIdentificationAtqaSakSuccess);
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
                        boolean identifyTagOnGetVersionSuccess = ti.identifyTagOnGetVersion(getVersionData);
                        output += "Result of tag identification: " + identifyTagOnGetVersionSuccess + "\n";
                        if (identifyTagOnGetVersionSuccess) {
                            //output += "Tag is of type " + ti.tagMinorName + " with " + ti.userMemory + " bytes user memory" + "\n";
                            if (getVersionData.length == 8) {
                                output += ti.tagVersionData.dump8Bytes();
                            } else {
                                output += ti.tagVersionData.dump();
                            }
                        }
                        output += "Tag is of type " + ti.tagMinorName + " with " + ti.userMemory + " bytes user memory" + "\n";
                    } else {
                        output += "Analyzing of the get version data skipped, using ATQA & SAK for tag identification" + "\n";
                        if (tagIdentificationAtqaSakSuccess) {
                            output += "Tag is probably of type " + ti.tagMinorName + " with " + ti.userMemory + " bytes user memory" + "\n";
                        }
                    }
                } else {
                    output += "GetVersion command skipped. Without positive tag identification I can't work properly with the tag, aborted." + "\n";
                    return;
                }



                // todo remove from release
                if (runAuthenticationDefault) {
                    // analyze the configuration
                    output += chapterDivider + "\n";
                    output += "Authenticate with Default Password and PACK" + "\n";
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += "Using DEFAULT Password and PACK" + "\n";
                        boolean authenticationSuccess = authenticatePassword(nfcA, DEFAULT_PASSWORD, DEFAULT_PACK);
                        output += "authentication with DEFAULT password and PACK: " + authenticationSuccess + "\n";

                        if (!authenticationSuccess) {
                            reconnect(nfcA);
                            output += "run authentication with CUSTOM password and PACK" + "\n";
                            authenticationSuccess = authenticatePassword(nfcA, CUSTOM_PASSWORD, CUSTOM_PACK);
                            output += "authentication with CUSTOM password and PACK: " + authenticationSuccess + "\n";
                        }

                    } else {
                        output += "Authenticate with Default Password and PACK is available on NTAG21x or MIFARE Ultralight EV1 tags only, skipped" + "\n";
                    }
                } else {
                    output += lineDivider + "\n";
                    output += "Skipped: Authenticate with Default Password and PACK" + "\n";
                }

                // todo remove from release
                if (runAuthenticationCustom) {
                    // analyze the configuration
                    output += chapterDivider + "\n";
                    output += "Authenticate with Custom Password and PACK" + "\n";
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += "Using CUSTOM Password and PACK" + "\n";
                        boolean authenticationSuccess = authenticatePassword(nfcA, CUSTOM_PASSWORD, CUSTOM_PACK);
                        output += "authentication with CUSTOM password and PACK: " + authenticationSuccess + "\n";

                        if (!authenticationSuccess) {
                            reconnect(nfcA);
                            output += "run authentication with DEFAULT password and PACK" + "\n";
                            authenticationSuccess = authenticatePassword(nfcA, DEFAULT_PASSWORD, DEFAULT_PACK);
                            output += "authentication with DEFAULT password and PACK: " + authenticationSuccess + "\n";
                        }

                    } else {
                        output += "Authenticate with CUSTOM Password and PACK is available on NTAG21x or MIFARE Ultralight EV1 tags only, skipped" + "\n";
                    }
                } else {
                    output += lineDivider + "\n";
                    output += "Skipped: Authenticate with Custom Password and PACK" + "\n";
                }

                // todo remove from release
                if (runChangePasswordDefaultToCustom) {
                    // analyze the configuration
                    output += chapterDivider + "\n";
                    output += "Change Password and PACK from Default to Custom" + "\n";
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        boolean changePasswordPackSuccess = changePasswordPack(nfcA, (ti.tagMemoryEndPage - 1), DEFAULT_PASSWORD, DEFAULT_PACK, CUSTOM_PASSWORD, CUSTOM_PACK);
                        output += "Change Password and PACK from Default to Custom: " + changePasswordPackSuccess + "\n";

                    } else {
                        output += "Change Password and PACK from Default to Custom is available on NTAG21x or MIFARE Ultralight EV1 tags only, skipped" + "\n";
                    }
                } else {
                    output += lineDivider + "\n";
                    output += "Skipped: Change Password and PACK from Default to Custom" + "\n";
                }

                // todo remove from release
                if (runChangePasswordCustomToDefault) {
                    // analyze the configuration
                    output += chapterDivider + "\n";
                    output += "Change Password and PACK from Custom to Default" + "\n";
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        boolean changePasswordPackSuccess = changePasswordPack(nfcA, (ti.tagMemoryEndPage - 1), CUSTOM_PASSWORD, CUSTOM_PACK, DEFAULT_PASSWORD, DEFAULT_PACK);
                        output += "Change Password and PACK from Custom to Default: " + changePasswordPackSuccess + "\n";

                    } else {
                        output += "Change Password and PACK from Custom to Default is available on NTAG21x or MIFARE Ultralight EV1 tags only, skipped" + "\n";
                    }
                } else {
                    output += lineDivider + "\n";
                    output += "Skipped: Change Password and PACK from Custom to Default" + "\n";
                }

                // todo remove from release
                if (runReadConfiguration) {
                    // analyze the configuration
                    output += chapterDivider + "\n";
                    output += "Analyze the Configuration" + "\n";
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        byte[] configurationPages = readConfigurationPages(nfcA, ti.configurationStartPage);
                        output += printData("configurationPages", configurationPages) + "\n";
                        boolean analyzeConfigPagesSuccess;
                        if (ti.isTag_NTAG21x) {
                            analyzeConfigPagesSuccess = analyzeConfigurationPages(ConfigurationPages.TagType.NTAG21x, ti.configurationStartPage, ti.tagMemoryEndPage, configurationPages);
                        } else {
                            analyzeConfigPagesSuccess = analyzeConfigurationPages(ConfigurationPages.TagType.Ultralight_EV1, ti.configurationStartPage, ti.tagMemoryEndPage, configurationPages);
                        }
                        output += "Configuration pages analyzed: " + analyzeConfigPagesSuccess + "\n";
                        ConfigurationPages cp = new ConfigurationPages(ConfigurationPages.TagType.NTAG21x, configurationPages);
                        output += cp.dump() + "\n";

                        if (runSetAuthProtectionPage05) {
                            output += "Enable AuthProtection Read&Write to pages 05ff" + "\n";
                            //ConfigurationPages cp = new ConfigurationPages(ConfigurationPages.TagType.NTAG21x, configurationPages);
                            if (cp.isValid()) {
                                cp.setAuthProtectionPage(5);
                                cp.setAuthProtectionReadWrite();
                                //
                                byte[] configPage0 = cp.getConfigurationPage0();
                                byte[] configPage1 = cp.getConfigurationPage1();
                                output += printData("Configuration Page 0", configPage0) + "\n";
                                output += printData("Configuration Page 1", configPage1) + "\n";
                                // commented out  // just need to write configuration page 1
                                byte[] writeConfig0Response = writePage(nfcA, (ti.configurationStartPage), configPage0);
                                byte[] writeConfig1Response = writePage(nfcA, (ti.configurationStartPage + 1), configPage1);
                                output += printData("writeConfig1Response", writeConfig1Response) + "\n";
                                if (checkResponse(writeConfig1Response[0])) {
                                    output += "Tag response is ACK -> Success" + "\n";
                                } else {
                                    output += "Tag response is NAK -> FAILURE" + "\n";
                                }
                            } else {
                                output += "Configuration Pages are invalid, skipped" + "\n";
                            }
                        }

                        if (runDisableAuthProtection) {
                            output += "Disable AuthProtection" + "\n";
                            //ConfigurationPages cp = new ConfigurationPages(ConfigurationPages.TagType.NTAG21x, configurationPages);
                            if (cp.isValid()) {
                                cp.disableAuthProtectionPage();
                                //
                                byte[] configPage0 = cp.getConfigurationPage0();
                                byte[] configPage1 = cp.getConfigurationPage1();
                                output += printData("Configuration Page 0", configPage0) + "\n";
                                output += printData("Configuration Page 1", configPage1) + "\n";
                                byte[] writeConfig0Response = writePage(nfcA, ti.configurationStartPage, configPage0);
                                byte[] writeConfig1Response = writePage(nfcA, (ti.configurationStartPage + 1), configPage1);
                                output += printData("writeConfig1Response", writeConfig1Response) + "\n";
                                if (checkResponse(writeConfig1Response[0])) {
                                    output += "Tag response is ACK -> Success" + "\n";
                                } else {
                                    output += "Tag response is NAK -> FAILURE" + "\n";
                                }
                            } else {
                                output += "Configuration Pages are invalid, skipped" + "\n";
                            }
                        }


                        if (runEnableNfcReadCounter) {
                            if (ti.isTag_NTAG21x) {
                                output += "Enable NFC Read Counter on NTAG21x" + "\n";
                                //ConfigurationPages cp = new ConfigurationPages(ConfigurationPages.TagType.NTAG21x, configurationPages);
                                if (cp.isValid()) {
                                    cp.enableNfcReadCounter();
                                    //
                                    byte[] configPage0 = cp.getConfigurationPage0();
                                    byte[] configPage1 = cp.getConfigurationPage1();
                                    output += printData("Configuration Page 0", configPage0) + "\n";
                                    output += printData("Configuration Page 1", configPage1) + "\n";
                                    // just need to write configuration page 1
                                    byte[] writeConfig1Response = writePage(nfcA, (ti.configurationStartPage + 1), configPage1);
                                    output += printData("writeConfig1Response", writeConfig1Response) + "\n";
                                    if (checkResponse(writeConfig1Response[0])) {
                                        output += "Tag response is ACK -> Success" + "\n";
                                    } else {
                                        output += "Tag response is NAK -> FAILURE" + "\n";
                                    }
                                } else {
                                    output += "Configuration Pages are invalid, skipped" + "\n";
                                }
                            }
                        }

                        if (runDisableNfcReadCounter) {
                            if (ti.isTag_NTAG21x) {
                                output += "Disable NFC Read Counter on NTAG21x" + "\n";
                                //ConfigurationPages cp = new ConfigurationPages(ConfigurationPages.TagType.NTAG21x, configurationPages);
                                if (cp.isValid()) {
                                    cp.disableNfcReadCounter();
                                    //
                                    byte[] configPage0 = cp.getConfigurationPage0();
                                    byte[] configPage1 = cp.getConfigurationPage1();
                                    output += printData("Configuration Page 0", configPage0) + "\n";
                                    output += printData("Configuration Page 1", configPage1) + "\n";
                                    // just need to write configuration page 1
                                    byte[] writeConfig1Response = writePage(nfcA, (ti.configurationStartPage + 1), configPage1);
                                    output += printData("writeConfig1Response", writeConfig1Response) + "\n";
                                    if (checkResponse(writeConfig1Response[0])) {
                                        output += "Tag response is ACK -> Success" + "\n";
                                    } else {
                                        output += "Tag response is NAK -> FAILURE" + "\n";
                                    }
                                } else {
                                    output += "Configuration Pages are invalid, skipped" + "\n";
                                }
                            }
                        }

                        if (runSetNfcReadCounterPwdProtected) {
                            // todo to implement
                        }

                        if (runUnsetNfcReadCounterPwdProtected) {
                            // todo to implement
                        }

                        // running on NTAG21x and Ultralight EV1
                        if (runSetAutProtectionWriteOnly) {
                            output += "Auth Protection Write Only" + "\n";
                            //ConfigurationPages cp = new ConfigurationPages(ConfigurationPages.TagType.NTAG21x, configurationPages);
                            if (cp.isValid()) {
                                cp.setAuthProtectionWriteOnly();
                                //
                                byte[] configPage0 = cp.getConfigurationPage0();
                                byte[] configPage1 = cp.getConfigurationPage1();
                                output += printData("Configuration Page 0", configPage0) + "\n";
                                output += printData("Configuration Page 1", configPage1) + "\n";
                                // just need to write configuration page 1
                                byte[] writeConfig1Response = writePage(nfcA, (ti.configurationStartPage + 1), configPage1);
                                output += printData("writeConfig1Response", writeConfig1Response) + "\n";
                                if (checkResponse(writeConfig1Response[0])) {
                                    output += "Tag response is ACK -> Success" + "\n";
                                } else {
                                    output += "Tag response is NAK -> FAILURE" + "\n";
                                }
                            } else {
                                output += "Configuration Pages are invalid, skipped" + "\n";
                            }
                        }
                        if (runSetAuthProtectionReadWrite) {
                            output += "Auth Protection Read&Write" + "\n";
                            //ConfigurationPages cp = new ConfigurationPages(ConfigurationPages.TagType.NTAG21x, configurationPages);
                            if (cp.isValid()) {
                                cp.setAuthProtectionReadWrite();
                                //
                                byte[] configPage0 = cp.getConfigurationPage0();
                                byte[] configPage1 = cp.getConfigurationPage1();
                                output += printData("Configuration Page 0", configPage0) + "\n";
                                output += printData("Configuration Page 1", configPage1) + "\n";
                                // just need to write configuration page 1
                                byte[] writeConfig1Response = writePage(nfcA, (ti.configurationStartPage + 1), configPage1);
                                output += printData("writeConfig1Response", writeConfig1Response) + "\n";
                                if (checkResponse(writeConfig1Response[0])) {
                                    output += "Tag response is ACK -> Success" + "\n";
                                } else {
                                    output += "Tag response is NAK -> FAILURE" + "\n";
                                }
                            } else {
                                output += "Configuration Pages are invalid, skipped" + "\n";
                            }
                        }

                    } else {
                        output += "Analyzing the Configuration pages is available on NTAG21x or MIFARE Ultralight EV1 tags only, skipped" + "\n";
                    }
                }

                // todo remove from release
                if (runEnableAsciiMirroring) {
                    output += chapterDivider + "\n";
                    output += "Enable ASCII Mirror on NTAG21x" + "\n";
                    // restricted to NTAG21x
                    if (ti.isTag_NTAG21x) {
                        // setting the parameter
                        int startPage = 6; // here the mirroring will start, 4 .. near end of user memory
                        int startByteInPage = 0; // The first position of the mirror in a page, 0..3
                        boolean enableUidMirroring = true;
                        boolean enableNfcCounterMirroring = true;
                        boolean setAsciiMirrorSuccess = setAsciiMirroring(nfcA, ti.configurationStartPage, enableUidMirroring, enableNfcCounterMirroring, startPage, startByteInPage);
                        if (setAsciiMirrorSuccess) {
                            output += "Set the Ascii Mirror SUCCESS" + "\n";
                        } else {
                            output += "Set the Ascii Mirror FAILURE" + "\n";
                            output += "Last Error: " + lastExceptionString + "\n";
                        }
                    } else {
                        output += "Enabling ASCII Mirroring is available on NTAG21x tags only, skipped" + "\n";
                    }
                }

                // todo remove from release
                if (runDisableAsciiMirroring) {
                    output += chapterDivider + "\n";
                    output += "Disable ASCII Mirror on NTAG21x" + "\n";
                    // restricted to NTAG21x
                    if (ti.isTag_NTAG21x) {
                        // setting the parameter
                        int startPage = 6; // here the mirroring will start, 4 .. near end of user memory
                        int startByteInPage = 0; // The first position of the mirror in a page, 0..3
                        boolean enableUidMirroring = false;
                        boolean enableNfcCounterMirroring = false;
                        boolean setAsciiMirrorSuccess = setAsciiMirroring(nfcA, ti.configurationStartPage, enableUidMirroring, enableNfcCounterMirroring, startPage, startByteInPage);
                        if (setAsciiMirrorSuccess) {
                            output += "Unset the Ascii Mirror SUCCESS" + "\n";
                        } else {
                            output += "Unset the Ascii Mirror FAILURE" + "\n";
                            output += "Last Error: " + lastExceptionString + "\n";
                        }
                    } else {
                        output += "Disabling ASCII Mirroring is available on NTAG21x tags only, skipped" + "\n";
                    }
                }

                if (runReadSignature) {
                    // Read the Elliptic Curve Signature
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        // read a page from the tag
                        output += chapterDivider + "\n";
                        output += "Read the Signature" + "\n";
                        output += "Uses the ReadSig command and gets the 32 bytes long digital signature of the tag." + "\n";
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
                    } else {
                        output += lineDivider + "\n";
                        output += "Read Signature is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                if (runReadCounter) {
                    // read the counter(s) from the tag
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        output += "Read the Counter 2" + "\n";
                        output += "Uses the ReadCnt command to get value of the counter 2. On an NTAG21x with fabric settings this will fail as the counter is not enabled by default." + "\n";
                        byte[] readCounterResponse = readCounter(nfcA, 2);
                        output += printData("readCounter 2 Response", readCounterResponse) + "\n";
                        int readCounterResponseInt = readCounterInt(nfcA, 2);
                        output += "readCounter 2 Response: " + readCounterResponseInt + "\n";
                    } else {
                        output += lineDivider + "\n";
                        output += "Read Counter is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }

                    // restricted to MIFARE Ultralight EV1
                    if (ti.isTag_MIFARE_ULTRALIGHT_EV1) {
                        output += lineDivider + "\n";
                        output += "Read the Counter 0" + "\n";
                        byte[] readCounterResponse = readCounter(nfcA, 0);
                        output += printData("readCounter 0 Response", readCounterResponse) + "\n";
                        int readCounterResponseInt = readCounterInt(nfcA, 0);
                        output += "readCounter 0 Response: " + readCounterResponseInt + "\n";
                        output += lineDivider + "\n";
                        output += "Read the Counter 1" + "\n";
                        readCounterResponse = readCounter(nfcA, 1);
                        output += printData("readCounter 1 Response", readCounterResponse) + "\n";
                        readCounterResponseInt = readCounterInt(nfcA, 1);
                        output += "readCounter 1 Response: " + readCounterResponseInt + "\n";
                    } else {
                        output += lineDivider + "\n";
                        output += "Read Counter 0 + 1 is restricted to MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                if (runIncreaseCounter0) {
                    // increases the counter 0 on the tag by 1
                    // restricted to MIFARE Ultralight EV1
                    if (ti.isTag_MIFARE_ULTRALIGHT_EV1) {
                        output += chapterDivider + "\n";
                        output += "Increase the Counter 0 by 1" + "\n";
                        output += "Uses the INC_CNT command, available on MIFARE Ultralight EV1 tags only. It increase the counter 0 by 1" + "\n";
                        byte[] increaseCounterResponse = increaseCounterByOne(nfcA, 0);
                        if (checkResponse(increaseCounterResponse[0])) {
                            output += "IncreaseCounter 0 Tag response is ACK -> Success" + "\n";
                        } else {
                            output += "IncreaseCounter 0 Tag response is NAK -> FAILURE" + "\n";
                        }
                    } else {
                        output += lineDivider + "\n";
                        output += "Increase Counter is restricted to MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                if (runReadPages03) {
                    // read a page from the tag
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        if (ti.userMemory > 0) {
                            output += "Read pages from page 00" + "\n";
                            output += "Uses the READ command for accessing the content of the pages 0, 1, 2 and 3" + "\n";
                            byte[] pagesData = readPage(nfcA, 00); // page 04 is the first page of the user memory
                            boolean readSuccess = false;
                            if (pagesData == null) {
                                output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                                output += "Exception from operation: " + lastExceptionString + "\n";
                            } else {
                                // we got a response but need to check the response data
                                // in case everything was ok we received the full content of 4 pages = 16 bytes
                                // in all other cases something went wrong, but those responses are tag type specific
                                if (pagesData.length == 16) {
                                    output += "data from pages 0, 1, 2 and 3: " + bytesToHexNpe(pagesData) + "\n";
                                    output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                                    readSuccess = true;
                                } else {
                                    output += "The tag responded with a response indicating that something went wrong. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                                    output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                                }
                            }
                        } else {
                            output += "The tag is not readable by the READ command, sorry." + "\n";
                        }
                    } else {
                        output += lineDivider + "\n";
                        output += "Read Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                if (runReadPages47) {
                    // read a page from the tag
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        if (ti.userMemory > 0) {
                            output += "Read pages from page 04" + "\n";
                            output += "Uses the READ command for accessing the content of the pages 4, 5, 6 and 7" + "\n";
                            byte[] pagesData = readPage(nfcA, 4); // page 04 is the first page of the user memory
                            boolean readSuccess = false;
                            if (pagesData == null) {
                                output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                                output += "Exception from operation: " + lastExceptionString + "\n";
                            } else {
                                // we got a response but need to check the response data
                                // in case everything was ok we received the full content of 4 pages = 16 bytes
                                // in all other cases something went wrong, but those responses are tag type specific
                                if (pagesData.length == 16) {
                                    output += "data from pages 4, 5, 6 and 7: " + bytesToHexNpe(pagesData) + "\n";
                                    output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                                    readSuccess = true;
                                } else {
                                    output += "The tag responded with a response indicating that something went wrong. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                                    output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                                }
                            }
                        } else {
                            output += "The tag is not readable by the READ command, sorry." + "\n";
                        }
                    } else {
                        output += lineDivider + "\n";
                        output += "Read Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                if (runReadPages8b) {
                    // read a page from the tag
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        if (ti.userMemory > 0) {
                            output += "Read pages from page 08" + "\n";
                            output += "Uses the READ command for accessing the content of the pages 8, 9, 10 and 11" + "\n";
                            byte[] pagesData = readPage(nfcA, 8); // page 04 is the first page of the user memory
                            boolean readSuccess = false;
                            if (pagesData == null) {
                                output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                                output += "Exception from operation: " + lastExceptionString + "\n";
                            } else {
                                // we got a response but need to check the response data
                                // in case everything was ok we received the full content of 4 pages = 16 bytes
                                // in all other cases something went wrong, but those responses are tag type specific
                                if (pagesData.length == 16) {
                                    output += "data from pages 8, 9, 10 and 11: " + bytesToHexNpe(pagesData) + "\n";
                                    output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                                    readSuccess = true;
                                } else {
                                    output += "The tag responded with a response indicating that something went wrong. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                                    output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                                }
                            }
                        } else {
                            output += "The tag is not readable by the READ command, sorry." + "\n";
                        }
                    } else {
                        output += lineDivider + "\n";
                        output += "Read Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                if (runReadPagesf0) {
                    // read a page from the tag
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        if (ti.userMemory > 0) {
                            output += "Read pages from page F0 (should give an error as outside tags memory)" + "\n";
                            output += "Uses the READ command for accessing the content of the pages F0 onwards. As this page number is outside the tag memory the call hs to fail." + "\n";
                            byte[] pagesData = readPage(nfcA, 240); // page 240 is outside the tag memory
                            boolean readSuccess = false;
                            if (pagesData == null) {
                                output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                                output += "Exception from operation: " + lastExceptionString + "\n";
                            } else {
                                // we got a response but need to check the response data
                                // in case everything was ok we received the full content of 4 pages = 16 bytes
                                // in all other cases something went wrong, but those responses are tag type specific
                                if (pagesData.length == 16) {
                                    output += "data from pages 240, 241, 242 and 243: " + bytesToHexNpe(pagesData) + "\n";
                                    output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                                    readSuccess = true;
                                } else {
                                    output += "The tag responded with a response indicating that something went wrong. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                                    output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                                }
                            }
                        } else {
                            output += "The tag is not readable by the READ command, sorry." + "\n";
                        }
                    } else {
                        output += lineDivider + "\n";
                        output += "Read Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                // todo remove from release
                if (runReadConfigPages) {
                    // read a page from the tag
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        if (ti.userMemory > 0) {
                            output += "Read Config pages" + "\n";
                            byte[] pagesData = readPage(nfcA, ti.configurationStartPage); // page 04 is the first page of the user memory
                            boolean readSuccess = false;
                            if (pagesData == null) {
                                output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                                output += "Exception from operation: " + lastExceptionString + "\n";
                            } else {
                                // we got a response but need to check the response data
                                // in case everything was ok we received the full content of 4 pages = 16 bytes
                                // in all other cases something went wrong, but those responses are tag type specific
                                if (pagesData.length == 16) {
                                    output += "data from config pages: " + bytesToHexNpe(pagesData) + "\n";
                                    output += "\n" + new String(pagesData, StandardCharsets.UTF_8) + "\n";
                                    readSuccess = true;
                                } else {
                                    output += "The tag responded with a response indicating that something went wrong. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                                    output += "received response: " + bytesToHexNpe(pagesData) + "\n";
                                }
                            }
                        } else {
                            output += "The tag is not readable by the READ command, sorry." + "\n";
                        }
                    } else {
                        output += lineDivider + "\n";
                        output += "Read Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                if (runFastRead0011) {
                    // fast read the pages 00-11 tag content
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        if (ti.tagHasFastReadCommand) {
                            output += "FastRead pages from page 00 to 11" + "\n";
                            output += "Uses the FastRead command to read the content from pages 0 up to 11, in total 48 bytes." + "\n";
                            int startPage = 0;
                            int endPage = 11;
                            byte[] contentRead = fastReadPage(nfcA, startPage, endPage);
                            if ((contentRead != null) && (contentRead.length == (endPage - startPage + 1) * 4)) {
                                output += printData("content pages 00-11\n", contentRead) + "\n";
                                output += lineDivider + "\n";
                                output += "ASCII content" + "\n";
                                output += new String(contentRead, StandardCharsets.UTF_8) + "\n";
                            } else {
                                output += "Error while reading the content in pages 00-11, e.g. some parts of the tag might be read protected" + "\n";

                            }
                        } else {
                            output += "FastRead skipped, tag has no command" + "\n";
                        }
                    } else {
                        output += lineDivider + "\n";
                        output += "FastRead Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }

                if (runFastRead0015) {
                    // fast read the pages 00-15 tag content
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        if (ti.tagHasFastReadCommand) {
                            output += "FastRead pages from page 00 to 15" + "\n";
                            output += "Uses the FastRead command to read the content from pages 0 up to 15, in total 64 bytes." + "\n";
                            int startPage = 0;
                            int endPage = 15;
                            byte[] contentRead = fastReadPage(nfcA, startPage, endPage);
                            if ((contentRead != null) && (contentRead.length == (endPage - startPage + 1) * 4)) {
                                output += printData("content pages 00-15\n", contentRead) + "\n";
                                output += lineDivider + "\n";
                                output += "ASCII content" + "\n";
                                output += new String(contentRead, StandardCharsets.UTF_8) + "\n";
                            } else {
                                output += "Error while reading the content in pages 00-15, e.g. some parts of the tag might be read protected" + "\n";

                            }
                        } else {
                            output += "FastRead skipped, tag has no command" + "\n";
                        }
                    } else {
                        output += lineDivider + "\n";
                        output += "FastRead Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }


                if (runFastReadComplete) {
                    // fast read the complete tag content
                    // restricted to NTAG21x and MIFARE Ultralight EV1
                    if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                        output += chapterDivider + "\n";
                        if (ti.tagHasFastReadCommand) {
                            output += "FastRead pages from page 00-end" + "\n";
                            output += "Uses the FastRead command to read the full content of the tag." + "\n";
                            byte[] completeContentFastRead = readFullTag(nfcA, maxTransceiveLength, ti.tagMemoryEndPage);
//                            System.out.println(printData("fastReadComplete\n", completeContentFastRead));
                            if ((completeContentFastRead != null) && (completeContentFastRead.length == ((ti.tagMemoryEndPage + 1) * ti.bytesPerPage))) {
                                output += printData("Full tag content", completeContentFastRead) + "\n";
                                output += lineDivider + "\n";
                                output += "ASCII content" + "\n";
                                output += new String(completeContentFastRead, StandardCharsets.UTF_8) + "\n";
                            } else {
                                output += "Error while reading the complete content of the tag, e.g. some parts of the tag might be read protected" + "\n";
                            }
                        }
                    } else {
                        output += "FastRead skipped, tag has no command" + "\n";
                    }
                } else {
                    output += lineDivider + "\n";
                    output += "FastRead Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                }

                if (runWritePage04) {
                    output += chapterDivider + "\n";
                    output += "Write on page 04" + "\n";
                    output += "Uses the WRITE command to write a 4 bytes long array to page 4" + "\n";
                    byte[] dataToWrite = getTimestamp4Bytes();
                    output += printData("dataToWrite on page 04", dataToWrite) + "\n";
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
                }

                if (runWriteBulkDataPage05) {
                    output += chapterDivider + "\n";
                    output += "Write bulk data on page 05" + "\n";
                    output += "Uses the WRITEBULKDATA method to write around 30 bytes to the tag." + "\n";
                    byte[] bulkDataToWrite = "AndroidCrypto NFC NfcA Tutorial".getBytes(StandardCharsets.UTF_8);
                    boolean writeBulkDataSuccess = writeBulkData(nfcA, 4, bulkDataToWrite);
                    output += "writeBulkDataToPage 05 success: " + writeBulkDataSuccess + "\n";
                }

                if (runFormatTagToFabricSettings) {
                    output += chapterDivider + "\n";
                    output += "Format the tag to Fabrics Settings" + "\n";
                    output += "Uses the WRITE method to write data to several pages on the tag." + "\n";
                    boolean resetToFabricSettingsSuccess = false;
                    if (ti.isTag_NTAG21x) {
                        resetToFabricSettingsSuccess = formatNtag21xToFactorySettings(nfcA, ti.tagMinorName);
                        output += "Result of Format to Fabrics Settings: " + resetToFabricSettingsSuccess + "\n";
                    } else if(ti.isTag_MIFARE_ULTRALIGHT_EV1) {
                        resetToFabricSettingsSuccess = formatUltralightEv1ToFactorySettings(nfcA, ti.tagMinorName);
                        output += "Result of Format to Fabrics Settings: " + resetToFabricSettingsSuccess + "\n";
                    } else {
                            output += "Reset Tag To Fabric Settings is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }
                output += lineDivider + "\n";

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

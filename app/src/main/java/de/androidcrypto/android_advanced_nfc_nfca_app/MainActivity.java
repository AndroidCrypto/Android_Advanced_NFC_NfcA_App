package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.checkResponse;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.fastReadPage;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.getMoreData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.getVersion;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.increaseCounterByOne;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.lastExceptionString;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readCounter;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readCounterInt;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readFullTag;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readPage;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readSignature;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.resolveCheckResponse;
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
                boolean runFastRead0012 = true;
                boolean runWritePage04 = true;
                boolean runWriteBulkDataPage05 = true;
                boolean runReadCounter = true;
                boolean runIncreaseCounter0 = false;
                boolean runReadSignature = true;
                boolean runFastReadComplete = true;

                output += chapterDivider + "\n";
                output += "==== Tasks Overview ====" + "\n";
                output += "= Get Version           " + runGetVersion + "\n";
                output += "= Read Pages 0..3       " + runReadPages03 + "\n";
                output += "= Read Pages 4..7       " + runReadPages47 + "\n";
                output += "= FastRead Pages 00-12  " + runFastRead0012 + "\n";
                //output += "= FastRead compl.Tag    " + runFastReadComplete + "\n";
                output += "= Write Page 04         " + runWritePage04 + "\n";
                output += "= Wr. Bulk Data Page 05 " + runWriteBulkDataPage05 + "\n";
                output += "= Read Counter          " + runReadCounter + "\n";
                output += "= Increase Counter 0    " + runIncreaseCounter0 + "\n";
                output += "= Read Signature        " + runReadSignature + "\n";
                output += "= FastRead compl.Tag    " + runFastReadComplete + "\n";
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
                    output += "This tag is not of type NTAG21x, MIFARE Ultralight EV1 or MIFARE Ultralight C. The further processing is stopped.GetVersion command skipped. Without positive tag identification I can't work properly with the tag, aborted." + "\n";
                    return;
                }

                if (!ti.isTag_NfcA_Library_Capable) {
                    // this tag is not of type NTAG21x, MIFARE Ultralight EV1 or MIFARE Ultralight C tag type
                    output += chapterDivider + "\n";
                    output += "This tag is not of type NTAG21x, MIFARE Ultralight EV or MIFARE Ultralight C. The further tasks are skipped" + "\n";
                } else {

                    if (runReadPages03) {
                        // read a page from the tag
                        if (ti.userMemory > 16) {
                            // restricted to NTAG21x and MIFARE Ultralight EV1
                            //if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
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
                                        output += "ASCII: " + new String(pagesData, StandardCharsets.UTF_8) + "\n";
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
                            output += chapterDivider + "\n";
                            output += "Read Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                        }
                    }

                    if (runReadPages47) {
                        // read a page from the tag
                        if (ti.userMemory > 16) {
                            // restricted to NTAG21x and MIFARE Ultralight EV1
                            //if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
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
                                        output += "ASCII: " + new String(pagesData, StandardCharsets.UTF_8) + "\n";
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
                            output += chapterDivider + "\n";
                            output += "Read Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                        }
                    }

                    if (runFastRead0012) {
                        // fast read the pages 00-12 tag content
                        // restricted to NTAG21x and MIFARE Ultralight EV1
                        if ((ti.isTag_NTAG21x) || (ti.isTag_MIFARE_ULTRALIGHT_EV1)) {
                            output += chapterDivider + "\n";
                            if (ti.tagHasFastReadCommand) {
                                output += "FastRead pages from page 00 to 12" + "\n";
                                output += "Uses the FastRead command to read the content from pages 0 up to 12, in total 52 bytes." + "\n";
                                int startPage = 0;
                                int endPage = 12;
                                byte[] contentRead = fastReadPage(nfcA, startPage, endPage);
                                if ((contentRead != null) && (contentRead.length == (endPage - startPage + 1) * 4)) {
                                    output += printData("content pages 00-12\n", contentRead) + "\n";
                                    output += lineDivider + "\n";
                                    output += "ASCII: " + "\n";
                                    output += new String(contentRead, StandardCharsets.UTF_8) + "\n";
                                } else {
                                    output += "Error while reading the content in pages 00-12, e.g. some parts of the tag might be read protected" + "\n";

                                }
                            } else {
                                output += "FastRead skipped, tag has no FAST READ command" + "\n";
                            }
                        } else {
                            output += chapterDivider + "\n";
                            output += "FastRead Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                        }
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
                    }

                    if (runWriteBulkDataPage05) {
                        output += chapterDivider + "\n";
                        output += "Write bulk data on pages 05 ff" + "\n";
                        output += "Uses the WRITEBULKDATA method to write 31 bytes to the tag." + "\n";
                        byte[] bulkDataToWrite = "AndroidCrypto NFC NfcA Tutorial".getBytes(StandardCharsets.UTF_8);
                        boolean writeBulkDataSuccess = writeBulkData(nfcA, 5, bulkDataToWrite);
                        output += "writeBulkDataToPage 05 success: " + writeBulkDataSuccess + "\n";
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
                            if (readCounterResponseInt == -1) {
                                output += "As value of -1 can indicate that the Read Counter is not enabled" + "\n";
                            }
                            // restricted to MIFARE Ultralight EV1
                            if (ti.isTag_MIFARE_ULTRALIGHT_EV1) {
                                output += lineDivider + "\n";
                                output += "Read the Counter 0" + "\n";
                                readCounterResponse = readCounter(nfcA, 0);
                                output += printData("readCounter 0 Response", readCounterResponse) + "\n";
                                readCounterResponseInt = readCounterInt(nfcA, 0);
                                output += "readCounter 0 Response: " + readCounterResponseInt + "\n";
                                output += lineDivider + "\n";
                                output += "Read the Counter 1" + "\n";
                                readCounterResponse = readCounter(nfcA, 1);
                                output += printData("readCounter 1 Response", readCounterResponse) + "\n";
                                readCounterResponseInt = readCounterInt(nfcA, 1);
                                output += "readCounter 1 Response: " + readCounterResponseInt + "\n";
                            } else {
                                output += chapterDivider + "\n";
                                output += "Read Counter 0 + 1 is restricted to MIFARE Ultralight EV1 tags, skipped" + "\n";
                            }
                        } else {
                            output += chapterDivider + "\n";
                            output += "Read Counter is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
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
                            output += chapterDivider + "\n";
                            output += "Increase Counter is restricted to MIFARE Ultralight EV1 tags, skipped" + "\n";
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
                        } else {
                            output += chapterDivider + "\n";
                            output += "Read Signature is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
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
                                    output += "ASCII:" + "\n";
                                    output += new String(completeContentFastRead, StandardCharsets.UTF_8) + "\n";
                                } else {
                                    output += "Error while reading the complete content of the tag, e.g. some parts of the tag might be read protected" + "\n";
                                }
                            }
                        } else {
                            output += chapterDivider + "\n";
                            output += "FastRead of the complete tag content skipped, tag has no FAST READ command" + "\n";
                        }
                    } else {
                        output += chapterDivider + "\n";
                        output += "FastRead Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped" + "\n";
                    }
                }
                output += " " + "\n";
                output += "== Processing Ended ==" + "\n";
                output += chapterDivider + "\n";

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

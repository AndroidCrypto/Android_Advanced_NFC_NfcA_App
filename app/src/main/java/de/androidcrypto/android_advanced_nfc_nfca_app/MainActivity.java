package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.getMoreData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.getVersion;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.lastExceptionString;
import static de.androidcrypto.android_advanced_nfc_nfca_app.NfcACommands.readSector;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.byteToHex;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.bytesToHexNpe;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.concatenateByteArrays;
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
                if (getVersionData == null) {
                    output += "Could not read the version of the tag, maybe it is read protected ?" + "\n";
                    output += "Exception from operation: " + lastExceptionString + "\n";
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
                        //output += "\n" + new String(sectorsData, StandardCharsets.UTF_8) + "\n";
                        getVersionSuccess = true;
                        ti.tagHasGetVersionCommand = true;
                    } else if (Arrays.equals(getVersionData, hexStringToByteArray("04"))) {
                        output += "You probably tried to read a MIFARE Classic tag. This is possible after a successful authentication only." + "\n";
                        output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                    } else if (Arrays.equals(getVersionData, hexStringToByteArray("1C"))) {
                        output += "You probably tried to read a MIFARE DESFire tag. This is possible using another workflow only." + "\n";
                        output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                    } else if (Arrays.equals(getVersionData, hexStringToByteArray("6700"))) {
                        output += "You probably tried to read a Credit Card tag. This is possible using another workflow only." + "\n";
                        output += "received response: " + bytesToHexNpe(getVersionData) + "\n";
                    } else {
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
                            // it is an NTAG213
                            ti.tagMinorName = "NTAG213";
                            ti.userMemory = 144;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 39; // included
                        } else if (tagVersionData.getHardStorageSizeRaw() == 17) {
                            // it is an NTAG215
                            ti.tagMinorName = "NTAG215";
                            ti.userMemory = 504;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 129; // included
                        } else if (tagVersionData.getHardStorageSizeRaw() == 19) {
                            // it is an NTAG216
                            ti.tagMinorName = "NTAG216";
                            ti.userMemory = 888;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 225; // included
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
                        output += "Tag is of type " + ti.tagMinorName + "\n";
                    } else if (ti.tagMajorName.equals(VersionInfo.MajorTagType.MIFARE_Ultralight.toString())) {
                        // get more data for Ultralight EV1 family

                        // todo ### get ultralight technical data !!

                        if (tagVersionData.getHardStorageSizeRaw() == 15) {
                            // it is an NTAG213
                            ti.tagMinorName = "NTAG213";
                            ti.userMemory = 144;
                            ti.userMemoryStartPage = 4;
                            ti.userMemoryEndPage = 39; // included
                        }
                        // general information for Ultralight EV1 family
                        ti.tagHasFastReadCommand = true;
                        ti.tagHasAuthentication = true;
                        ti.tagHasDesAuthenticationSecurity = false;
                        ti.tagHasPasswordSecurity = true;
                        ti.tagHasPageLockBytes = true;
                        ti.tagHasOtpArea = true;
                        output += "Tag is of type " + ti.tagMinorName + "\n";
                    } else {
                        output += "This is an UNKNOWN tag type" + "\n";
                    }
                } else {
                    output += "Analyzing of the get version data skipped" + "\n";
                }

                // read a sector from the tag
                output += lineDivider + "\n";
                output += "Read sectors from page 04" + "\n";
                byte[] sectorsData = readSector(nfcA, 2); // page 04 is the first page of the user memory
                boolean readSuccess = false;
                if (sectorsData == null) {
                    output += "Could not read the content of the tag, maybe it is read protected ?" + "\n";
                    output += "Exception from operation: " + lastExceptionString + "\n";
                } else {
                    // we got a response but need to check the response data
                    // in case everything was ok we received the full content of 4 pages = 16 bytes
                    // in all other cases something went wrong, but those responses are tag type specific
                    if (sectorsData.length == 16) {
                        output += "data from sectors 4, 5, 6 and 7: " + bytesToHexNpe(sectorsData) + "\n";
                        output += "\n" + new String(sectorsData, StandardCharsets.UTF_8) + "\n";
                        readSuccess = true;
                    } else if (Arrays.equals(sectorsData, hexStringToByteArray("04"))) {
                        output += "You probably tried to read a MIFARE Classic tag. This is possible after a successful authentication only." + "\n";
                        output += "received response: " + bytesToHexNpe(sectorsData) + "\n";
                    } else if (Arrays.equals(sectorsData, hexStringToByteArray("1C"))) {
                        output += "You probably tried to read a MIFARE DESFire tag. This is possible using another workflow only." + "\n";
                        output += "received response: " + bytesToHexNpe(sectorsData) + "\n";
                    } else if (Arrays.equals(sectorsData, hexStringToByteArray("6700"))) {
                        output += "You probably tried to read a Credit Card tag. This is possible using another workflow only." + "\n";
                        output += "received response: " + bytesToHexNpe(sectorsData) + "\n";
                    } else {
                        output += "The tag responded with an unknown response. You need to read the data sheet of the tag to find out to read that tag, sorry." + "\n";
                        output += "received response: " + bytesToHexNpe(sectorsData) + "\n";
                    }
                }

                //
                output += lineDivider + "\n";
                output += "xxx page 04" + "\n";


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

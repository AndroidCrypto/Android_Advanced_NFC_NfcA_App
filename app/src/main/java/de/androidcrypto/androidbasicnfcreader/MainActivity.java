package de.androidcrypto.androidbasicnfcreader;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcV;
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

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private TextView textView;
    private NfcAdapter myNfcAdapter;

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
        output += "NFC tag detected" + "\n";

        // examine tag
        byte[] tagUid = tag.getId();
        output += "Tag UID length: " + tagUid.length  + " UID: " + bytesToHex(tagUid) + "\n";
        String[] techlist = tag.getTechList();
        output += lineDivider + "\n";
        output += "The TechList contains " + techlist.length + " entry/ies:" + "\n";
        for (int i = 0; i < techlist.length; i++) {
            output += "Entry " + i + ": " + techlist[i]  + "\n";
        }
        output += lineDivider + "\n";
        output += tag.toString()  + "\n";
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
            output += "-= NfcA Technology =-" + "\n";
            output += "ATQA: " + bytesToHex(atqa) + "\n";
            output += "SAK: " + byteToHex(sak) + "\n";
            output += "maxTransceiveLength: " + maxTransceiveLength + "\n";
            output += lineDivider + "\n";

            try {
                nfcA.connect();
                output += "Connected to the tag using NfcA technology" + "\n";
                output += lineDivider + "\n";
                nfcA.close();
            } catch (IOException e) {
                output += "NfcA connect to tag IOException: " + e.getMessage() + "\n";
                output += lineDivider + "\n";
            }
        }

        // connect to NfcV class (e.g. ICODE tags use this technology
        NfcV nfcV = null;
        nfcV = NfcV.get(tag);
        if (nfcV == null) {
            output += "This tag is NOT supporting the NfcV class" + "\n";
            output += lineDivider + "\n";
        } else {
            byte dsfId = nfcV.getDsfId();
            int maxTransceiveLength = nfcV.getMaxTransceiveLength();
            output += "-= NfcV Technology =-" + "\n";
            output += "DsfId: " + byteToHex(dsfId) + "\n";
            output += "maxTransceiveLength: " + maxTransceiveLength + "\n";
            output += lineDivider + "\n";

            try {
                nfcV.connect();
                output += "Connected to the tag using NfcV technology" + "\n";
                output += lineDivider + "\n";
                nfcV.close();
            } catch (IOException e) {
                output += "NfcV connect to tag IOException: " + e.getMessage() + "\n";
                output += lineDivider + "\n";
            }
        }

        // trying to read NDEF content
        Ndef ndef = null;
        ndef = Ndef.get(tag);
        if (ndef == null) {
            output += "This tag is NOT supporting the NDEF class" + "\n";
            output += lineDivider + "\n";
        } else {
            try {
                ndef.connect();
                output += "Connected to the tag using NDEF technology" + "\n";
                output += lineDivider + "\n";
                NdefMessage ndefMessage = ndef.getNdefMessage();
                String ndefMessageString = ndefMessage.toString();
                byte[] ndefMessageBytes = ndefMessage.toByteArray();
                output += "NDEF message: " + ndefMessageString + "\n";
                if (ndefMessageBytes != null) {
                    output += "NDEF message: " + bytesToHex(ndefMessageBytes) + "\n";
                    output += "NDEF message: " + new String(ndefMessageBytes, StandardCharsets.UTF_8) + "\n";
                }
                output += lineDivider + "\n";

                ndef.close();
            } catch (IOException e) {
                output += "NDEF connect to tag IOException: " + e.getMessage() + "\n";
                output += lineDivider + "\n";
            } catch (FormatException e) {
                output += "NDEF connect to tag RunTimeException: " + e.getMessage() + "\n";
                output += lineDivider + "\n";
            }
        }

        // final output
        String finalOutput = output;
        runOnUiThread(() -> {
            textView.setText(finalOutput);
        });
        // output of the logfile to console
        System.out.println(output);
        // a short information about the detection of an NFC tag after all reading is done
        playBeep();
    }

    /**
     * When the activity returns to foreground the ReaderMode gets enabled. Here I'm setting all flags,
     * meaning that all NFC technologies are allowed to get detected. If you remove e.g. the NfcA class
     * you won't detect NTAG21x tags anymore.
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
            // Enable ReaderMode for all types of card and disable platform sounds
            // The option NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK is NOT set
            // to get the data of the tag after reading
            myNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }
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

    public static String byteToHex(Byte input) {
        return String.format("%02X", input);
        //return String.format("0x%02X", input);
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuffer result = new StringBuffer();
        for (byte b : bytes)
            result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    public void playBeep() {
        ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
    }
}

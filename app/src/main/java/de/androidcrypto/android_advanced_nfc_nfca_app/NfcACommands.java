package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.concatenateByteArrays;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.hexStringToByteArray;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.testBit;

import android.nfc.tech.NfcA;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

/**
 * This class takes all commands that are useful to communicate with an NFC tag that supports
 * the NfcA technology.
 * It is completely tested with NFC tags of type NTAG213/215/216, if the commands are running
 * on other tag types depends on their specific command structure. I included the last available
 * data sheet of the NTAG21x series in the  subfolder 'docs'.
 */

public class NfcACommands {

    private static final String TAG = "NfcALib";
    public static final String version = "1.00";
    // the data is never cleared but overwritten in case of an exception during tag operations.
    // Read out the data in case of a failure only
    public static String lastExceptionString = "";


    private static final byte[] atqaUltralight = hexStringToByteArray("4400");
    private static final short sakUltralight = 0;
    public static int pagesToRead = 41; // MFOUL21 tag, can be changed to 20 in case of a MF0UL11 tag
    // acknowledge bytes
    public static final byte ACK = 0x0A; // this is the default response defined by NXP
    // Remark: Any 4-bit response different from Ah shall be interpreted as NAK
    public static final byte NAK_INVALID_ARGUMENT = 0x00; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_PARITY_CRC_ERROR = 0x01; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_INVALID_AUTHENTICATION_COUNTER_OVERFLOW = 0x04; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_EEPROM_WRITE_ERROR = 0x05; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_IOEXCEPTION_ERROR = (byte) 0xFF; // this is the response defined by me
    /**
     * This allows to read the complete memory = all pages of the tag. If a page is not readable the
     * method returns NULL.
     * Note: some pages are not readable by design (e.g. password), they are filled with 0x00h.
     *
     * @param nfcA
     * @param pageNumber
     * @return The command returns 16 bytes (4 pages) with one command. In case of an error the
     * method returns the response of the tag, e.g. '0x6700h' or '0x04h.
     */
    public static byte[] readPage(NfcA nfcA, int pageNumber) {
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0x30, // READ a page command
                    (byte) (pageNumber & 0x0ff)  // page address
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "on page " + pageNumber + " readPage failed with IOException: " + e.getMessage());
            lastExceptionString = "readPage for " + pageNumber + " failed with IOException: " + e.getMessage();
        }
        return null;
    }

    /**
     * This allows to read the complete memory = all pages of the tag with less effort. If a page
     * is not readable the method returns NULL.
     * Note: some pages are not readable by design (e.g. password), they are filled with 0x00h.
     *
     * @param nfcA
     * @param pageNumberStart
     * @param pageNumberEnd
     * @return
     */
    public static byte[] fastReadPage (NfcA nfcA, int pageNumberStart, int pageNumberEnd) {
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0x3A, // FAST READ pages command
                    (byte) (pageNumberStart & 0x0ff),  // first page address to read
                    (byte) (pageNumberEnd & 0x0ff)  // last page address to read
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "on pages range " + pageNumberStart + " to " + pageNumberEnd + " fastReadPage failed with IOException: " + e.getMessage());
            lastExceptionString = "fastReadPage range " + +pageNumberStart + " to " + pageNumberEnd + " fastReadPage failed with IOException: " + e.getMessage();
            return null;
        }
    }

    public static byte[] writePage (NfcA nfcA, int pageNumber, byte[] pageData4Byte) {
        // sanity check
        if (pageNumber < 0) {
            Log.e(TAG, "writePage pageNumber is < 0, aborted");
            return new byte[]{ACK};
        }
        // there is no check on upper limit - this is tag specific
        if (pageData4Byte == null) {
            Log.e(TAG, "writePage pageData4Byte is NULL, aborted");
            return new byte[]{NAK_INVALID_ARGUMENT};
        }
        if (pageData4Byte.length != 4) {
            Log.e(TAG, "writePage pageData4Byte is not of length 4 found " + pageData4Byte.length + ", aborted");
            return new byte[]{NAK_INVALID_ARGUMENT};
        }
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0xA2, // WRITE a page command
                    (byte) (pageNumber & 0x0ff),  // page address
                    pageData4Byte[0], pageData4Byte[1], pageData4Byte[2], pageData4Byte[3]
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "on page " + pageNumber + " readPage failed with IOException: " + e.getMessage());
            lastExceptionString = "readPage for " + pageNumber + " failed with IOException: " + e.getMessage();
            return new byte[]{NAK_IOEXCEPTION_ERROR};
        }
    }

    public static byte[] getVersion(NfcA nfcA) {
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0x60  // Get Version command
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Get Version failed with IOException: " + e.getMessage());
            lastExceptionString = "Get Version failed with IOException: " + e.getMessage();
        }
        return null;
    }

    public static byte[] getMoreData(NfcA nfcA) {
        // we need to run this command as long we are asked for more data by the NFC tag
        boolean moreDataRequested = true;
        byte[] moreDataToReturn = new byte[0];
        while (moreDataRequested == true) {
            byte[] response;
            try {
                response = nfcA.transceive(new byte[]{
                        (byte) 0xAF  // Get More Data command
                });
                System.out.println(printData("gmd response", response));
                if (response.length > 0) {
                    if (response[0] == (byte) 0xAF) {
                        moreDataRequested = true;
                        // we need to skip the trailing 'AF'
                        moreDataToReturn = concatenateByteArrays(moreDataToReturn, Arrays.copyOfRange(response, 1, response.length));
                    } else {
                        moreDataRequested = false;
                        moreDataToReturn = concatenateByteArrays(moreDataToReturn, response);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Get More Data failed with IOException: " + e.getMessage());
                lastExceptionString = "Get Version failed with IOException: " + e.getMessage();
                moreDataRequested = false; // stop reading
                moreDataToReturn = null;
            }
        }
        Log.d(TAG, printData("Get More Data", moreDataToReturn));
        return moreDataToReturn;
    }

    public static boolean checkResponse(byte tagResponse) {
        if (tagResponse == ACK) {
            return true;
        } else {
            return false;
        }
    }

    public static String resolveCheckResponse(byte tagResponse) {
        if (tagResponse == ACK) {
            return "ACK";
        } else if (tagResponse == NAK_INVALID_ARGUMENT) {
            return "NAK INVALID ARGUMENT";
        } else if (tagResponse == NAK_PARITY_CRC_ERROR) {
            return "NAK PARITY OR CRC ERROR";
        }
        else if (tagResponse == NAK_INVALID_AUTHENTICATION_COUNTER_OVERFLOW) {
            return "NAK INVALID AUTHENTICATION COUNTER OVERFLOW";
        }
        else if (tagResponse == NAK_EEPROM_WRITE_ERROR) {
            return "NAK EEPROM WRITE ERROR";
        }
        else if (tagResponse == NAK_IOEXCEPTION_ERROR) {
            return "NAK IOEXCEPTION ERROR";
        } else {
            return "NAK UNKNOWN ERROR";
        }
    }

}


package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.byteToHex;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.concatenateByteArrays;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.hexStringToByteArray;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.intFrom3ByteArrayLsb;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.setBitInByte;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.testBit;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.unsetBitInByte;

import android.nfc.TagLostException;
import android.nfc.tech.NfcA;
import android.util.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
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

    public static final byte ACK = 0x0A; // this is the default response defined by NXP
    // Remark: Any 4-bit response different from Ah shall be interpreted as NAK
    public static final byte NAK_INVALID_ARGUMENT = 0x00; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_PARITY_CRC_ERROR = 0x01; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_INVALID_AUTHENTICATION_COUNTER_OVERFLOW = 0x04; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_EEPROM_WRITE_ERROR = 0x05; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_IOEXCEPTION_ERROR = (byte) 0xFF; // this is the response defined by me
    public static final byte[] DEFAULT_PASSWORD = hexStringToByteArray("FFFFFFFF");
    public static final byte[] DEFAULT_PACK = hexStringToByteArray("0000");
    public static final byte[] CUSTOM_PASSWORD = hexStringToByteArray("98765432");
    public static final byte[] CUSTOM_PACK = hexStringToByteArray("CC00");

    /*
        Available commands
        readPage: reads the content of the <pageNumber> + 3 following pages, returns 4 pages = 16 bytes
        fastReadPage: reads the content of multiple pages, starting with <pageNumberStart> and ending with <pageNumberEnd>
        writePage: writes the content of one page to the taag
        writeBulkData: writes the content of maximum 40 bytes to subsequent pages, starting with <startPageNumber>
        getVersion: returns the the version data of the tag
        getMoreData: reads data from the tag as long the tag indicates that more data is waiting
        readCounterInt: read the value of the one counter (NTAG21x) or up to 3 counters (Ultralight EV1) as an integer value
        readCounter: read the value of the one counter (NTAG21x) or up to 3 counters (Ultralight EV1), returns a 3 bytes long array (LSB encoded)
        readSignature: reads the 32 bytes long Elliptic Curve signature (NTAG21x and Ultralight EV1 only)
        checkResponse: returns true when response is "ACK"
        resolveCheckResponse: returns a string with text encoded error messages
        reconnect: After an error situation it is good practise to reconnect to the tag
        changePasswordPack:
        verifyNtag21xOriginalitySignature:


        // professional
        authenticatePassword:
        readConfigurationPages: reads, analyzes and dumps the Configuration Pages
        analyzeConfigurationPages:
        setAsciiMirroring: enables or disables an ASCII mirroring of tag UID and/or NFC Read Counter (NTAG21x only)



    */

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
        // sanity check
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return null;
        }
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0x30, // READ a page command
                    (byte) (pageNumber & 0xff)  // page address
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
    public static byte[] fastReadPage(NfcA nfcA, int pageNumberStart, int pageNumberEnd) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return null;
        }
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0x3A, // FAST READ pages command
                    (byte) (pageNumberStart & 0xff),  // first page address to read
                    (byte) (pageNumberEnd & 0xff)  // last page address to read
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "on pages range " + pageNumberStart + " to " + pageNumberEnd + " fastReadPage failed with IOException: " + e.getMessage());
            lastExceptionString = "fastReadPage range " + +pageNumberStart + " to " + pageNumberEnd + " fastReadPage failed with IOException: " + e.getMessage();
            return null;
        }
    }

    /**
     * Write data to one page. The data to write need to be exactly 4 bytes long. The page number needs
     * be in the range of the tag memory.
     * @param nfcA
     * @param pageNumber
     * @param pageData4Byte
     * @return is either the Acknowledge Byte ("ACK") or a Not Acknowledge Byte ("NAK")
     */
    public static byte[] writePage(NfcA nfcA, int pageNumber, byte[] pageData4Byte) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return null;
        }
        if (pageNumber < 0) {
            Log.e(TAG, "writePage pageNumber is < 0, aborted");
            lastExceptionString = "writePage pageNumber is < 0, aborted";
            return new byte[]{NAK_INVALID_ARGUMENT};
        }
        // there is no check on upper limit - this is tag specific
        if (pageData4Byte == null) {
            Log.e(TAG, "writePage pageData4Byte is NULL, aborted");
            lastExceptionString = "writePage pageData4Byte is NULL, aborted";
            return new byte[]{NAK_INVALID_ARGUMENT};
        }
        if (pageData4Byte.length != 4) {
            Log.e(TAG, "writePage pageData4Byte is not of length 4 found " + pageData4Byte.length + ", aborted");
            lastExceptionString = "writePage pageData4Byte is not of length 4 found " + pageData4Byte.length + ", aborted";
            return new byte[]{NAK_INVALID_ARGUMENT};
        }
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0xA2, // WRITE a page command
                    (byte) (pageNumber & 0xff),  // page address
                    pageData4Byte[0], pageData4Byte[1], pageData4Byte[2], pageData4Byte[3]
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "writePage to page " + pageNumber + " writePage failed with IOException: " + e.getMessage());
            lastExceptionString = "writePage to page " + pageNumber + " failed with IOException: " + e.getMessage();
            return new byte[]{NAK_IOEXCEPTION_ERROR};
        }
    }

    /**
     * This write method accepts data lengths up to 40 bytes that are split into chunks of 4 bytes each.
     * Beginning with the startPageNumber all data is written subsequently to the pages.
     * @param nfcA
     * @param startPageNumber
     * @param bulkPageData
     * @return
     */
    public static boolean writeBulkData(NfcA nfcA, int startPageNumber, byte[] bulkPageData) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return false;
        }
        if (startPageNumber < 0) {
            Log.e(TAG, "writePage startPageNumber is < 0, aborted");
            lastExceptionString = "writePage startPageNumber is < 0, aborted";
            return false;
        }
        if (bulkPageData == null) {
            Log.e(TAG, "writePage bulkPageData is NULL, aborted");
            lastExceptionString = "writePage bulkPageData is NULL, aborted";
            return false;
        }
        if (bulkPageData.length > 40) {
            Log.e(TAG, "writePage bulkPageData length is >40, aborted");
            lastExceptionString = "writePage bulkPageData length is >40, aborted";
            return false;
        }
        byte[] pageData;
        int remainingBytes = bulkPageData.length;
        int copyIndex = 0; // copy the data from this position
        int pageIndex = startPageNumber;
        byte[] writeResponse;
        System.out.println(printData("bulkData", bulkPageData));
        while (remainingBytes > 0) {
            System.out.println("remainingBytes: " + remainingBytes);
            pageData = new byte[4];
            if (remainingBytes < 5) {
                System.out.println("remainingBytes < 5, copyIndex: " + copyIndex);
                System.arraycopy(bulkPageData, copyIndex, pageData, 0, remainingBytes); // copy the remaining bytes
            } else {
                System.out.println("remainingBytes > 4, copyIndex: " + copyIndex);
                // a new round will follow after this one
                System.arraycopy(bulkPageData, copyIndex, pageData, 0, 4);
            }
            System.out.println("before writePage pageIndex: " + pageIndex);
            writeResponse = writePage(nfcA, pageIndex, pageData);
            if ((writeResponse == null) || (writeResponse.length < 1) || (!checkResponse(writeResponse[0]))) {
                // an error occurred
                return false; // the lastExceptionString was already filled by writePage
            }
            copyIndex = copyIndex + 4;
            pageIndex ++;
            remainingBytes = remainingBytes - 4;
        }
        return true;
    }

    /**
     * Returns the version data of the tag that can be of dirrent length, depending on the tag type.
     * Modern tags respond with about >20 bytes, an NTAG21x or MIFARE Ultralight responds 8 bytes.
     * @param nfcA
     * @return
     */
    public static byte[] getVersion(NfcA nfcA) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return null;
        }
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

    /**
     * When the tags signalizes that there are more data to retrieve it sends an "0xAFh" byte. The
     * NFC reader can simply send an '0xAFh' command and the tag responds with more data.
     * @param nfcA
     * @return
     */
    public static byte[] getMoreData(NfcA nfcA) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return null;
        }
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

    /**
     * Reads the counter on an NTAG21x or MIFARE Ultralight EV1 tag and returns an integer value.
     * Please read the explanation on @readCounter() for additional information.
     *
     * @param nfcA
     * @param counterNumber
     * @return the counter value 0.., in case of any error it returns -1 as value
     */
    public static int readCounterInt(NfcA nfcA, int counterNumber) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return -1;
        }
        byte[] response = readCounter(nfcA, counterNumber);
        if (response == null) {
            return -1;
        } else {
            return intFrom3ByteArrayLsb(response);
        }
    }

    /**
     * Reads the counter on an NTAG21x or MIFARE Ultralight EV1 tag. The NTAG21x has one counter on
     * address 2 whereas the Ultralight EV1 has 3 counters (0..2).
     * The counter on NTAG21x is a READ Counter, meaning it gets increased if a Read Page or Fast
     * Read Page is sent to the tag. This feature needs to get enabled first, so on a tag with fabric
     * settings you will receive an IOException and a NULL byte response.
     *
     * @param nfcA
     * @param counterNumber
     * @return the 24-bit (3 byte) counter in LSB encoding
     */
    public static byte[] readCounter(NfcA nfcA, int counterNumber) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return null;
        }
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0x39,  // Read Counter command
                    (byte) (counterNumber & 0xff) // in case Ultralight EV1 0..2, NTAG21x 2
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Read Counter failed with IOException: " + e.getMessage());
            lastExceptionString = "Read Counter failed with IOException: " + e.getMessage();
        }
        return null;
    }

    /**
     * Read the 32 bytes long Electronic Signature of the tag that is generated on the tag UID
     * and the PRIVATE key owned by NXP. The verification of the signature requires the PUBLIC
     * key and the usage of an ECC Signature validation, this is protected under NDA and not
     * shown in this library.
     * This is available on NTAG21x or MIFARE Ultralight EV1 tags.
     *
     * @param nfcA
     * @return
     */
    public static byte[] readSignature(NfcA nfcA) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return null;
        }
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0x3C, // Read Signature command
                    (byte) 0x00 // Address is FRU, internally fixed to 0x00h
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Read Signature failed with IOException: " + e.getMessage());
            lastExceptionString = "Read Signature failed with IOException: " + e.getMessage();
        }
        return null;
    }

    /**
     * Set the ASCII Mirror feature of an NTAG21x tag. If both mirrorUidAscii and mirrorNfcCounter are
     * false the mirroring will be disabled
     * @param nfcA
     * @param startConfigurationPages
     * @param mirrorUidAscii
     * @param mirrorNfcCounter
     * @param startPage needs to be > 3 and < last user memory page - <data for mirroring>
     * @param startByteInPage needs to be in range 0 .. 3
     * @return
     *
     * Note on <data for mirroring> As the UID data is returned in Hex encoding we need more space:
     *         UID mirroring:           14 bytes (4 pages)
     *         NFC Counter mirroring:   6 bytes (2 pages)
     *         UID + Counter mirroring: 21 bytes (6 pages, 14 for UID, 6 for Counter + 1 for separation
     *         The separation character will be 0x78h = 'x'
     *
     * Note on sanity check for startPage: the minimum is 4 and the maximum that gets checked is page 221
     *         so there is enough space to mirror the UID and NFC Counter. This maximum is for an
     *         NTAG216 tag, if you use such a value for a startPage on an NTAG213 or NTAG215 it will fail !
     */
    public static boolean setAsciiMirroring(NfcA nfcA, int startConfigurationPages, boolean mirrorUidAscii, boolean mirrorNfcCounter, int startPage, int startByteInPage) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return false;
        }
        // skip checks when no mirroring is requested
        if ((!mirrorUidAscii) && (!mirrorNfcCounter)) {
            startByteInPage = 0;
            startPage = 0;
        } else {
            if ((startByteInPage < 0) || (startByteInPage > 3)) {
                Log.e(TAG, "startByteInPage < 0 or startByteInPage > 3, aborted");
                lastExceptionString = "startByteInPage < 0 or startByteInPage > 3, aborted";
                return false;
            }
            if ((startPage < 4) || (startPage > 221)) {
                Log.e(TAG, "startPage < 4 or startPage > 221, aborted");
                lastExceptionString = "startPage < 4 or startPage > 221, aborted";
                return false;
            }
        }
        /*
        Workflow
        1 Read the Configuration Pages 0 & 1
        2 Change the settings for mirroring depending on parameters
        3 write the changed Configuration Pages 0 + 1 to the tag
         */
        // step 1
        byte[] configurationPages = readConfigurationPages(nfcA, startConfigurationPages);
        if ((configurationPages == null) || (configurationPages.length != 8)) {
            Log.e(TAG, "Error on reading the Configuration pages, aborted. " + lastExceptionString);
            lastExceptionString = "Error on reading the Configuration pages, aborted. " + lastExceptionString;
            return false;
        }
        // step 2
        ConfigurationPages cp = new ConfigurationPages(ConfigurationPages.TagType.NTAG21x, configurationPages);
        if (!cp.isValid()) {
            Log.e(TAG, "ConfigurationPages are invalid, aborted");
            lastExceptionString = "ConfigurationPages are invalid, aborted";
            return false;
        }
        cp.setAsciiMirroring(mirrorUidAscii, mirrorNfcCounter, startPage, startByteInPage);

/*
// this code is here due to wrong writings in the past !
        // one time setting for enabling Strong Modulation
        byte c0Byte0 = cp.getC0Byte0();
        c0Byte0 = setBitInByte(c0Byte0, 2);
        cp.setC0Byte0(c0Byte0);
        // disable any CFGLOCK setting in ACCESS byte ... if avoided or not...
        byte c1Byte0 = cp.getC1Byte0();
        c1Byte0 = unsetBitInByte(c1Byte0, 6);
        cp.setC1Byte0(c1Byte0);
        // set AUTH0 to outside page frame to disable a setting
        cp.setC0Byte3((byte) 0xFF);
        //
        cp.buildConfigurationPages01();
*/

        // step 3
        byte[] configPage0 = cp.getConfigurationPage0();
        byte[] configPage1 = cp.getConfigurationPage1();
        byte[] writeConfig0Response = writePage(nfcA, startConfigurationPages, configPage0);
        if (checkResponse(writeConfig0Response[0])){
            Log.d(TAG, "Write Configuration Page 0 SUCCESS");
        } else {
            Log.e(TAG, "Write Configuration Page 0 FAILURE");
            lastExceptionString = "Write Configuration Page 0 FAILURE " + lastExceptionString;
            return false;
        }
        byte[] writeConfig1Response = writePage(nfcA, startConfigurationPages + 1, configPage1);
        if (checkResponse(writeConfig1Response[0])){
            Log.d(TAG, "Write Configuration Page 1 SUCCESS");
            return true;
        } else {
            Log.e(TAG, "Write Configuration Page 1 FAILURE");
            lastExceptionString = "Write Configuration Page 1 FAILURE " + lastExceptionString;
            return false;
        }
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
        } else if (tagResponse == NAK_INVALID_AUTHENTICATION_COUNTER_OVERFLOW) {
            return "NAK INVALID AUTHENTICATION COUNTER OVERFLOW";
        } else if (tagResponse == NAK_EEPROM_WRITE_ERROR) {
            return "NAK EEPROM WRITE ERROR";
        } else if (tagResponse == NAK_IOEXCEPTION_ERROR) {
            return "NAK IOEXCEPTION ERROR";
        } else {
            return "NAK UNKNOWN ERROR";
        }
    }

    public static void reconnect(NfcA nfcA) {
        Log.d(TAG, "Reconnect to NfcA class is best practise after (Tag Lost) exceptions.");
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return;
        }
        // this is just an advice - if an error occurs - close the connection and reconnect the tag
        // https://stackoverflow.com/a/37047375/8166854
        try {
            nfcA.close();
            Log.d(TAG, "Close NfcA");
        } catch (Exception e) {
            Log.e(TAG, "Exception on Close NfcA: " + e.getMessage());
        }
        try {
            Log.d(TAG, "Reconnect NfcA");
            nfcA.connect();
        } catch (Exception e) {
            Log.e(TAG, "Exception on Reconnect NfcA: " + e.getMessage());
        }
    }
    // todo exclude from final release

    /**
     * Note on working with changing the configuration settings: it is good practise to follow this
     * workflow:
     * 1: read the content of the configuration pages
     * 2: change just the bits or bytes you want to change
     * 3: write the configuration pages to the tag
     *
     * Please note: if the configuration pages are auth protected (for writing or writing&reading)
     * you need to run a successful authentication before you are going to change the content of the
     * tag. It is useful to analyze the AUTH0 byte and the ACCESS byte to be for sure.
     */

    /**
     * Reads the two configuration pages on an NTAG21x or MIFARE Ultralight EV1 tag.
     * The response is trimmed to the first 2 pages (8 bytes) instead of the full content of
     * 4 pages.
     *
     * @param nfcA
     * @param startConfigurationPages
     * @return 8 bytes for 2 bytes long content of configuration page 0 and 1
     */
    public static byte[] readConfigurationPages(NfcA nfcA, int startConfigurationPages) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return null;
        }
        byte[] response = readPage(nfcA, startConfigurationPages);
        if (response == null) {
            Log.e(TAG, "readConfigurationPages starting with page " + startConfigurationPages + " failed with IOException: " + lastExceptionString);
            lastExceptionString = "readConfigurationPages starting with page " + startConfigurationPages + " failed with IOException: " + lastExceptionString;
            return null;
        }
        if (response.length != 16) {
            Log.e(TAG, "readConfigurationPages starting with page " + startConfigurationPages + " does not return 16 bytes = 4 pages of data, aborted. Length of response: " + response.length);
            lastExceptionString = "readConfigurationPages starting with page " + startConfigurationPages + " does not return 16 bytes = 4 pages of data, aborted. Length of response: " + response.length;
            return null;
        }
        System.out.println(printData("*** readConfPagesResponse", Arrays.copyOf(response, 8)));
        return Arrays.copyOf(response, 8);

        /**
         * NTAG21x
         * first (= startConfigurationPages) configuration page
         * byte 0: MIRROR byte
         * byte 1: RFUI
         * byte 2: MIRROR PAGE
         * byte 3: AUTH0
         * second (= startConfigurationPages + 1) configuration page
         * byte 4: ACCESS
         * byte 5: RFUI
         * byte 6: RFUI
         * byte 7: RFUI
         *
         * MIFARE Ultralight EV1
         * first (= startConfigurationPages) configuration page
         * byte 0: MOD
         * byte 1: RFUI
         * byte 2: RFUI
         * byte 3: AUTH0
         * second (= startConfigurationPages + 1) configuration page
         * byte 4: ACCESS
         * byte 5: VCTID
         * byte 6: RFUI
         * byte 7: RFUI
         *
         * AUTH0 defines the page address from which the password verification is required. Valid
         * address range for byte AUTH0 is from 00h to FFh.
         * If AUTH0 is set to a page address which is higher than the last page from the user
         * configuration, the password protection is effectively disabled.
         *
         * ACCESS defines some settings, defined on bit basis
         * Bit 7: PROT: One bit inside the ACCESS byte defining the memory protection
         *              0b ... write access is protected by the password verification
         *              1b ... read and write access is protected by the password verification
         * Bit 6: CFGLCK: Write locking bit for the user configuration
         *                0b ... user configuration open to write access
         *                1b ... user configuration permanently locked against write access, except
         *                       PWD and PACK
         *        Remark: The CFGLCK bit activates the permanent write protection of the first two
         *        configuration pages. The write lock is only activated after a power cycle of
         *        NTAG21x. If write protection is enabled, each write attempt leads to a NAK response.
         * Bit 5: RFUI
         * Bit 4: NFC_CNT_EN: NFC counter configuration
         *                    0b ... NFC counter disabled
         *                    1b ... NFC counter enabled
         *        If the NFC counter is enabled, the NFC counter will be automatically increased at
         *        the first READ or FAST_READ command after a power on reset
         * Bit 3: NFC_CNT_PWD_PROT: NFC counter password protection
         *                          0b ... NFC counter not protected
         *                          1b ... NFC counter password protection enabled
         *        If the NFC counter password protection is enabled, the NFC tag will only respond
         *        to a READ_CNT command with the NFC counter value after a valid password verification
         * Bit 2: AUTHLIM
         * Bit 1: AUTHLIM
         * Bit 0: AUTHLIM
         *        Limitation of negative password verification attempts
         *        000b ... limiting of negative password verification attempts disabled
         *        001b-111b ... maximum number of negative password verification attempts
         * Note on bits 4 (NFC_CNT_EN) and 3 (NFC_CNT_PWD_PROT): they are available on an NTAG21x ONLY
         */
    }

    /**
     * @param startConfigurationPages
     * @param configurationPagesData8Bytes
     * @return false in case of any errors
     */
    public static boolean analyzeConfigurationPages(Enum TagType, int startConfigurationPages, int lastPageOnTag, byte[] configurationPagesData8Bytes) {
        if ((configurationPagesData8Bytes == null) || (configurationPagesData8Bytes.length != 8)) {
            Log.e(TAG, "analyzeConfigurationPages failed as configurationPagesData8Bytes is NULL or not of length 8, aborted");
            lastExceptionString = "analyzeConfigurationPages failed as configurationPagesData8Bytes is NULL or not of length 8, aborted";
            return false;
        }
        System.out.println("********* start analyze tag configuration *********");
        ConfigurationPages cp = new ConfigurationPages(TagType, configurationPagesData8Bytes);

        System.out.println("=== AUTH0 byte ===");
        byte authStartPageByte = cp.getC0Byte3();
        System.out.println("authStartPageByte: " + byteToHex(authStartPageByte));
        int authStartPage;
        if (authStartPageByte == (byte) 0xff) {
            authStartPage = 255;
        } else {
            authStartPage = (int) cp.getC0Byte3();
        }
        System.out.println("authStartPage: " + authStartPage);
        System.out.println("startConfigurationPage: " + startConfigurationPages);
        System.out.println("Last page on tag: " + lastPageOnTag);
        // if authStartPage is > lastPageOnTag the tag is not auth protected
        if (authStartPage > lastPageOnTag) {
            System.out.println("tag is NOT auth protected");
        } else {
            System.out.println("tag is auth protected starting from page " + authStartPage);
        }

        System.out.println("=== ACCESS byte ===");
        byte accessByte = cp.getC1Byte0();
        System.out.println("accessByte: " + byteToHex(accessByte));

        /**
         * NTAG21x
         * first (= startConfigurationPages) configuration page
         * byte 0: MIRROR byte
         * byte 1: RFUI
         * byte 2: MIRROR PAGE
         * byte 3: AUTH0
         * second (= startConfigurationPages + 1) configuration page
         * byte 4: ACCESS
         * byte 5: RFUI
         * byte 6: RFUI
         * byte 7: RFUI
         *
         * MIFARE Ultralight EV1
         * first (= startConfigurationPages) configuration page
         * byte 0: MOD
         * byte 1: RFUI
         * byte 2: RFUI
         * byte 3: AUTH0
         * second (= startConfigurationPages + 1) configuration page
         * byte 4: ACCESS
         * byte 5: VCTID
         * byte 6: RFUI
         * byte 7: RFUI
         *
         * AUTH0 defines the page address from which the password verification is required. Valid
         * address range for byte AUTH0 is from 00h to FFh.
         * If AUTH0 is set to a page address which is higher than the last page from the user
         * configuration, the password protection is effectively disabled.
         *
         * ACCESS defines some settings, defined on bit basis
         * Bit 7: PROT: One bit inside the ACCESS byte defining the memory protection
         *              0b ... write access is protected by the password verification
         *              1b ... read and write access is protected by the password verification
         * Bit 6: CFGLCK: Write locking bit for the user configuration
         *                0b ... user configuration open to write access
         *                1b ... user configuration permanently locked against write access, except
         *                       PWD and PACK
         *        Remark: The CFGLCK bit activates the permanent write protection of the first two
         *        configuration pages. The write lock is only activated after a power cycle of
         *        NTAG21x. If write protection is enabled, each write attempt leads to a NAK response.
         * Bit 5: RFUI
         * Bit 4: NFC_CNT_EN: NFC counter configuration
         *                    0b ... NFC counter disabled
         *                    1b ... NFC counter enabled
         *        If the NFC counter is enabled, the NFC counter will be automatically increased at
         *        the first READ or FAST_READ command after a power on reset
         * Bit 3: NFC_CNT_PWD_PROT: NFC counter password protection
         *                          0b ... NFC counter not protected
         *                          1b ... NFC counter password protection enabled
         *        If the NFC counter password protection is enabled, the NFC tag will only respond
         *        to a READ_CNT command with the NFC counter value after a valid password verification
         * Bit 2: AUTHLIM
         * Bit 1: AUTHLIM
         * Bit 0: AUTHLIM
         *        Limitation of negative password verification attempts
         *        000b ... limiting of negative password verification attempts disabled
         *        001b-111b ... maximum number of negative password verification attempts
         * Note on bits 4 (NFC_CNT_EN) and 3 (NFC_CNT_PWD_PROT): they are available on an NTAG21x ONLY
         */

        boolean protEnabled = testBit(accessByte, 7);
        System.out.println("The PROT bit 7 is enabled : " + protEnabled + " < true means: auth protection - if set - is read & write >");

        boolean cfglckEnabled = testBit(accessByte, 6);
        System.out.println("The CFGLCK bit 6 is enabled : " + cfglckEnabled + " < true means: user configuration is LOCKED to write access >");

        if (TagType == ConfigurationPages.TagType.NTAG21x) {
            boolean nfc_cnt_enEnabled = testBit(accessByte, 4);
            System.out.println("The NFC_CNT_EN bit 5 is enabled : " + nfc_cnt_enEnabled + " < true means: the NFC Read Counter IS enabled >");
            boolean nfc_cnt_pwd_protEnabled = testBit(accessByte, 3);
            System.out.println("The NFC_CNT_PWD_PROT 4 bit is enabled : " + nfc_cnt_pwd_protEnabled + " < true means: the NFC Read Counter READ command is returning data after a positive authentication >");
        } else {
            System.out.println("The NFC_CNT_EN bit 5 is not available on MIFARE Ultralight EV1");
            System.out.println("The NFC_CNT_PWD_PROT bit 4 is not available on MIFARE Ultralight EV1");
        }

        boolean authLim2 = testBit(accessByte, 2);
        boolean authLim1 = testBit(accessByte, 1);
        boolean authLim0 = testBit(accessByte, 0);
        System.out.println("AuthLim 2: " + authLim2 + " AuthLim 1: " + authLim1 + " AuthLim0: " + authLim0);
        int authLimInt = 0;
        if (authLim0) authLimInt += 1;
        if (authLim1) authLimInt += 2;
        if (authLim2) authLimInt += 4;
        System.out.println("AuthLim is: " + authLimInt);


        System.out.println("********* end analyze tag configuration *********");
        return true;
    }

    public static boolean authenticatePassword(NfcA nfcA, byte[] password, byte[] pack) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return false;
        }
        if ((password == null) || (password.length != 4)) {
            Log.e(TAG, "password is NULL or not of length 4, aborted");
            lastExceptionString = "password is NULL or not of length 4, aborted";
            return false;
        }
        if ((pack == null) || (pack.length != 2)) {
            Log.e(TAG, "pack is NULL or not of length 2, aborted");
            lastExceptionString = "pack is NULL or not of length 2, aborted";
            return false;
        }
        Log.d(TAG, printData("password", password) + " || " + printData("pack", pack));
        byte[] response;
        try {
            byte[] command = new byte[]{
                    (byte) 0x1B, // Password Authentication command
                    password[0],
                    password[1],
                    password[2],
                    password[3]
            };
            response = nfcA.transceive(command); // response should be 16 bytes = 4 pages
            Log.d(TAG, printData("response", response));
            if (response == null) {
                Log.e(TAG, "Error in authenticatePassword, aborted");
                // either communication to the tag was lost or a NAK was received
                lastExceptionString = "Error in authenticatePassword, aborted";
                return false;
            } else if ((response.length == 1) && ((response[0] & 0x00A) != 0x00A)) {
                // NAK response according to Digital Protocol/T2TOP
                // Log and return
                Log.e(TAG, "Error in authenticatePassword, aborted");
                lastExceptionString = "Error in authenticatePassword, aborted";
                return false;
            } else {
                // success: response contains (P)ACK or actual data
            }
        } catch (TagLostException e) {
            // Log and return
            Log.e(TAG, "TagLostException: " + e.getMessage());
            lastExceptionString = "TagLostException: " + e.getMessage();
            return false;
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            lastExceptionString = "IOException: " + e.getMessage();
            return false;
        }
        if (Arrays.equals(response, pack)) {
            Log.d(TAG, "The response EQUALS to PACK, authenticated");
            return true;
        } else {
            Log.d(TAG, "The response is DIFFERENT to PACK, NOT authenticated");
            lastExceptionString = "The response is DIFFERENT to PACK, NOT authenticated";
            return false;
        }
    }

    public static boolean changePasswordPack (NfcA nfcA, int passwordPageNumber, byte[] oldPassword4Bytes, byte[] oldPack2Bytes, byte[] newPassword4Bytes, byte[] newPack2Bytes) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return false;
        }
        if ((oldPassword4Bytes == null) || (oldPassword4Bytes.length != 4)) {
            Log.e(TAG, "oldPassword is NULL or not of length 4, aborted");
            lastExceptionString = "oldPassword is NULL or not of length 4, aborted";
            return false;
        }
        if ((oldPack2Bytes == null) || (oldPack2Bytes.length != 2)) {
            Log.e(TAG, "oldPACK is NULL or not of length 2, aborted");
            lastExceptionString = "oldPACK is NULL or not of length 2, aborted";
            return false;
        }
        if ((newPassword4Bytes == null) || (newPassword4Bytes.length != 4)) {
            Log.e(TAG, "newPassword is NULL or not of length 4, aborted");
            lastExceptionString = "newdPassword is NULL or not of length 4, aborted";
            return false;
        }
        if ((newPack2Bytes == null) || (newPack2Bytes.length != 2)) {
            Log.e(TAG, "newPACK is NULL or not of length 2, aborted");
            lastExceptionString = "newPACK is NULL or not of length 2, aborted";
            return false;
        }
        /*
        Workflow
        1 authenticate with old password and old pack
        2 write new password to page
        3 write new pack to page
        4 authenticate with new password and new pack
         */
        // step 1
        boolean oldPasswordAuthentication = authenticatePassword(nfcA, oldPassword4Bytes, oldPack2Bytes);
        if (!oldPasswordAuthentication) {
            Log.e(TAG, "changePassword step 1 authenticate with old password and pack failed, aborted");
            lastExceptionString = "changePassword step 1 authenticate with old password and PACK failed, aborted";
            return false;
        }
        // step 2
        byte[] writeNewPasswordResponse = writePage(nfcA, passwordPageNumber, newPassword4Bytes);
        if (writeNewPasswordResponse[0] != ACK) {
            Log.e(TAG, "changePassword step 2 write new password failed, aborted");
            lastExceptionString = "changePassword step 2 write new password failed, aborted";
            return false;
        }
        // step 3
        byte[] newPack = new byte[4]; // we do need a 4 bytes long array, but the new pack is only 2 bytes long
        System.arraycopy(newPack2Bytes, 0, newPack, 0, 2);
        byte[] writeNewPackResponse = writePage(nfcA, (passwordPageNumber + 1), newPack);
        if (writeNewPackResponse[0] != ACK) {
            Log.e(TAG, "changePassword step 3 write new PACK failed, aborted");
            lastExceptionString = "changePassword step 3 write new PACK failed, aborted";
            return false;
        }
        // step 4
        boolean newPasswordAuthentication = authenticatePassword(nfcA, newPassword4Bytes, newPack2Bytes);
        if (!newPasswordAuthentication) {
            Log.e(TAG, "changePassword step 4 authenticate with new password and PACK failed, aborted");
            lastExceptionString = "changePassword step 4 authenticate with new password and PACK failed, aborted";
            return false;
        }
        Log.d(TAG, "changePassword: SUCCESS");
        return true;
    }


    // todo exclude for final release

    // verify the NTAG21x Elliptic Curve Signature
    final static String publicKeyNxpX = "494E1A386D3D3CFE3DC10E5DE68A499B";
    final static String publicKeyNxpY = "1C202DB5B132393E89ED19FE5BE8BC61";
    final static String publicKeyNxp = "04494E1A386D3D3CFE3DC10E5DE68A499B1C202DB5B132393E89ED19FE5BE8BC61";

    public static boolean verifyNtag21xOriginalitySignature(byte[] tagUid, byte[] ntag21xSignature) {
        // now we are going to verify
        boolean signatureVerfied = false;
        // get the public key
        try {
            signatureVerfied = checkEcdsaSignature(publicKeyNxp, ntag21xSignature, tagUid);
        } catch (NoSuchAlgorithmException e) {
            lastExceptionString = "verifyNtag21xOriginalitySignature NoSuchAlgorithmException, aborted" + " " + e.getMessage();
            e.printStackTrace();
        }
        return signatureVerfied;
    }

    // NDA
    // START code from NXP's AN11350 document (NTAG21x Originality Signature Validation)
    public static boolean checkEcdsaSignature(final String ecPubKey, final byte[] signature, final byte[] data) throws NoSuchAlgorithmException {
        final ECPublicKeySpec ecPubKeySpec = getEcPubKey(ecPubKey, getEcSecp128r1());
        return checkEcdsaSignature(ecPubKeySpec, signature, data);
    }

    public static boolean checkEcdsaSignature(final ECPublicKeySpec ecPubKey, final byte[] signature, final byte[] data)
            throws NoSuchAlgorithmException {
        KeyFactory keyFac = null;
        try {
            keyFac = KeyFactory.getInstance("EC");
        } catch (final NoSuchAlgorithmException e1) {
            keyFac = KeyFactory.getInstance("ECDSA");
        }

        if (keyFac != null) {
            try {
                final PublicKey publicKey = keyFac.generatePublic(ecPubKey);
                final Signature dsa = Signature.getInstance("NONEwithECDSA");
                dsa.initVerify(publicKey);
                dsa.update(data);
                return dsa.verify(derEncodeSignature(signature));
            } catch (final SignatureException | InvalidKeySpecException | InvalidKeyException e) {
                Log.e(TAG, "Exceptions when verifying the signature: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return false;
    }

    public static ECPublicKeySpec getEcPubKey(final String key, final
    ECParameterSpec
            curve) {
        if (key == null || key.length() != 2 * 33 || !key.startsWith("04")) {
            return null;
        }
        final String keyX = key.substring(2 * 1, 2 * 17);
        final String keyY = key.substring(2 * 17, 2 * 33);
        final BigInteger affineX = new BigInteger(keyX, 16);
        final BigInteger affineY = new BigInteger(keyY, 16);
        final ECPoint w = new ECPoint(affineX, affineY);
        return new ECPublicKeySpec(w, curve);
    }

    public static ECParameterSpec getEcSecp128r1() {
        // EC definition of "secp128r1":
        final BigInteger p = new
                BigInteger("fffffffdffffffffffffffffffffffff", 16);
        final ECFieldFp field = new ECFieldFp(p);

        final BigInteger a = new
                BigInteger("fffffffdfffffffffffffffffffffffc", 16);
        final BigInteger b = new
                BigInteger("e87579c11079f43dd824993c2cee5ed3", 16);
        final EllipticCurve curve = new EllipticCurve(field, a, b);

        final BigInteger genX = new
                BigInteger("161ff7528b899b2d0c28607ca52c5b86", 16);
        final BigInteger genY = new
                BigInteger("cf5ac8395bafeb13c02da292dded7a83", 16);
        final ECPoint generator = new ECPoint(genX, genY);

        final BigInteger order = new
                BigInteger("fffffffe0000000075a30d1b9038a115", 16);
        final int cofactor = 1;
        return new ECParameterSpec(curve, generator, order, cofactor);
    }

    public static byte[] derEncodeSignature(final byte[] signature) {
        // split into r and s
        final byte[] r = Arrays.copyOfRange(signature, 0, 16);
        final byte[] s = Arrays.copyOfRange(signature, 16, 32);
        int rLen = r.length;
        int sLen = s.length;
        if ((r[0] & 0x80) != 0) {
            rLen++;
        }
        if ((s[0] & 0x80) != 0) {
            sLen++;
        }
        final byte[] encodedSig = new byte[rLen + sLen + 6]; // 6 T and L bytes
        encodedSig[0] = 0x30; // SEQUENCE
        encodedSig[1] = (byte) (4 + rLen + sLen);
        encodedSig[2] = 0x02; // INTEGER
        encodedSig[3] = (byte) rLen;
        encodedSig[4 + rLen] = 0x02; // INTEGER
        encodedSig[4 + rLen + 1] = (byte) sLen;

        // copy in r and s
        encodedSig[4] = 0;
        encodedSig[4 + rLen + 2] = 0;
        System.arraycopy(r, 0, encodedSig, 4 + rLen - r.length, r.length);
        System.arraycopy(s, 0, encodedSig, 4 + rLen + 2 + sLen - s.length,
                s.length);

        return encodedSig;
    }
    // END code from NXP's AN11350 document (NTAG21x Originality Signature Validation)

}


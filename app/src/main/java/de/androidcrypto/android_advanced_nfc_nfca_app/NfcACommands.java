package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.concatenateByteArrays;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.intFrom3ByteArrayLsb;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;

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

    public static final byte ACK = 0x0A; // this is the default response defined by NXP
    // Remark: Any 4-bit response different from Ah shall be interpreted as NAK
    public static final byte NAK_INVALID_ARGUMENT = 0x00; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_PARITY_CRC_ERROR = 0x01; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_INVALID_AUTHENTICATION_COUNTER_OVERFLOW = 0x04; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_EEPROM_WRITE_ERROR = 0x05; // this is the response defined by NXP for NTAG21x tags
    public static final byte NAK_IOEXCEPTION_ERROR = (byte) 0xFF; // this is the response defined by me

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
            lastExceptionString = "readPage for page " + pageNumber + " failed with IOException: " + e.getMessage();
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
     * This is an unofficial command or better helper method. It reads the content of the tag,
     * beginning with page 00 up to page <numberOfPages>, so in total <numberOfPages> + 1 pages.
     * It uses the fastRead method of this library, in case of any error the method will return NULL.
     * @param nfcA
     * @param numberOfPages
     * @return
     */
    public static byte[] readFullTag(NfcA nfcA, int maxTransceiveLength, int numberOfPages) {
        // don't extend the maxTransceiveLength as it might returns strange data
        // simple calculation including some protocol header bytes
        int maxFastReadPages = ((maxTransceiveLength - 16) / 4);
        //System.out.println("maxFastReadPages: " + maxFastReadPages);
        // so run this way to read the complete content of the tag
        byte[] completeContentFastRead = new byte[0];
        int pagesReadSoFar = 0;
        boolean fastReadSuccess = true;
        while (pagesReadSoFar < numberOfPages) {
            // if we can't read the remaining data in a 'full' read we are just reading the remaining data
            if ((numberOfPages - pagesReadSoFar) < maxFastReadPages) {
                maxFastReadPages = numberOfPages - pagesReadSoFar + 1;
                //System.out.println("Corrected maxFastReadPages to: " + maxFastReadPages);
            }
            //System.out.println("fastReadContent from " + pagesReadSoFar + " to " + (pagesReadSoFar + maxFastReadPages - 1));
            byte[] contentRead = fastReadPage(nfcA, pagesReadSoFar, (pagesReadSoFar + maxFastReadPages - 1));
            //System.out.println(printData("contentRead", contentRead));
            //System.out.println("contentRead length: " + contentRead.length);
            //System.out.println("(maxFastReadPages * ti.bytesPerPage): " + (maxFastReadPages * ti.bytesPerPage));
            // did we receive all data ?
            if ((contentRead != null) && (contentRead.length == (maxFastReadPages * 4))) {
                // we received the complete content
                completeContentFastRead = concatenateByteArrays(completeContentFastRead, contentRead);
                pagesReadSoFar += maxFastReadPages;
            } else {
                fastReadSuccess = false;
                pagesReadSoFar = numberOfPages; // end reading
            }
        }
        if (!fastReadSuccess) {
            Log.e(TAG, "Error while reading the content of the tag, e.g. some parts of the tag might be read protected");
            return null;
        } else {
            return completeContentFastRead;
        }
    }

    /**
     * Write data to one page. The data to write need to be exactly 4 bytes long. The page number needs
     * be in the range of the tag memory.
     * There is just a limitation on writing to page numbers below 4 - that is restricted. You can still
     * write to the areas at the end of the tag that are sensitive too (e.g. Dynamic Lock Bytes on an
     * NTAG21x tag).
     *
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
        // this check avoids to write to pages 0, 1, 2 and 3 as that are blocked pages or One Time Programmable areas
        if (pageNumber < 4) {
            Log.e(TAG, "writePage pageNumber is < 4, aborted");
            lastExceptionString = "writePage pageNumber is < 4 (avoid writing to OTP areas), aborted";
            return new byte[]{NAK_INVALID_ARGUMENT};
        }
        // there is no check on upper limit - this is tag specific
        // This method also does not prevent against writing in sensitive areas below user memory
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
     *
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
        // this check avoids to write to pages 0, 1, 2 and 3 as that are blocked pages or One Time Programmable areas
        if (startPageNumber < 4) {
            Log.e(TAG, "writePage startPageNumber is < 4, aborted");
            lastExceptionString = "writePage startPageNumber is < 4 (avoid writing to OTP areas), aborted";
            return false;
        }
        // This method also does not prevent against writing in sensitive areas below user memory
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
            pageIndex++;
            remainingBytes = remainingBytes - 4;
        }
        return true;
    }

    /**
     * Returns the version data of the tag that can be of dirrent length, depending on the tag type.
     * Modern tags respond with about >20 bytes, an NTAG21x or MIFARE Ultralight responds 8 bytes.
     *
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
     *
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
                    (byte) (counterNumber & 0xff) // in case of Ultralight EV1 0..2, NTAG21x 2
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Read Counter failed with IOException: " + e.getMessage());
            lastExceptionString = "Read Counter failed with IOException: " + e.getMessage();
        }
        return null;
    }

    /**
     * Increases one of the counters on a MIFARE Ultralight EV1 tag by 1.
     * @param nfcA
     * @param counterNumber
     * @return ACK or NAK
     */
    public static byte[] increaseCounterByOne(NfcA nfcA, int counterNumber) {
        // sanity checks
        if ((nfcA == null) || (!nfcA.isConnected())) {
            Log.e(TAG, "nfcA is NULL or not connected, aborted");
            lastExceptionString = "nfcA is NULL or not connected, aborted";
            return new byte[]{NAK_INVALID_ARGUMENT};
        }
        if ((counterNumber < 0) || (counterNumber > 2)) {
            Log.e(TAG, "The counterNumber is out of range 0..2, aborted");
            lastExceptionString = "The counterNumber is out of range 0..2, aborted";
            return new byte[]{NAK_INVALID_ARGUMENT};
        }
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0xA5, // Increase Counter command
                    (byte) (counterNumber & 0xff),
                    (byte) 0x01, // LSB order
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00, // this byte is ignored
            });
            return response; // should the ACK status byte
        } catch (IOException e) {
            Log.e(TAG, "IOException when reading a counter: " + e.getMessage());
            lastExceptionString = "Increase Counter failed with IOException: " + e.getMessage();
            return new byte[]{NAK_IOEXCEPTION_ERROR};
        }
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
                    (byte) 0x00 // Address is RFUI, internally fixed to 0x00h
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Read Signature failed with IOException: " + e.getMessage());
            lastExceptionString = "Read Signature failed with IOException: " + e.getMessage();
        }
        return null;
    }

    // helper methods

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

}


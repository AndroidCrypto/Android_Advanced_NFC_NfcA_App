package de.androidcrypto.android_advanced_nfc_nfca_app;

import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.concatenateByteArrays;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.hexStringToByteArray;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.printData;
import static de.androidcrypto.android_advanced_nfc_nfca_app.Utils.testBit;

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
    public static byte[] fastReadPage(NfcA nfcA, int pageNumberStart, int pageNumberEnd) {
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

    public static byte[] writePage(NfcA nfcA, int pageNumber, byte[] pageData4Byte) {
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

    public static byte[] readSignature(NfcA nfcA) {
        byte[] response = null;
        try {
            response = nfcA.transceive(new byte[]{
                    (byte) 0x3C,  // Read Signature command
                    (byte) 0x00 // Address is FRU, internally fixed to 0x00h
            });
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Read Signature failed with IOException: " + e.getMessage());
            lastExceptionString = "Read Signature failed with IOException: " + e.getMessage();
        }
        return null;
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


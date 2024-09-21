package de.androidcrypto.android_advanced_nfc_nfca_app;

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
}

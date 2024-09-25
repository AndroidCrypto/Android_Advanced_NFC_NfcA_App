# Android Advanced NFC NfcA App

## data sheets of the described NFC tags

- NXP NTAG21x tags (NTAG213, NTAG215 and NTAG216): https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf
- NTAG 21x features and hints (AN13089): https://www.puntoflotante.net/AN13089.pdf
- NTAG21x Originality Signature Validation (AN11350): no direct download, available under Non Disclosure Agreement only
- NTAG 424 DNA and NTAG 424 DNA TagTamper features and hints (AN12196): https://www.nxp.com/docs/en/application-note/AN12196.pdf 
Note: this document explains on pages 50 to 51 the Elliptic Curve signature verification process
- NXP Ultralight C (MF0ICU2): https://www.nxp.com/docs/en/data-sheet/MF0ICU2.pdf
- NXP Ultralight EV1 (MF0ULX1): https://www.nxp.com/docs/en/data-sheet/MF0ULX1.pdf
- NXP MIFARE DESFire EV3 short data sheet (MF3D_H_X3_SDS): https://www.nxp.com/docs/en/data-sheet/MF3D_H_X3_SDS.pdf
- Philips mifare DESFire MF3 IC D40 (M075031, outdated but newer DESFire tags are backwards compatible): https://neteril.org/files/M075031_desfire.pdf
- MIFARE type identification procedure (AN10833): https://www.nxp.com/docs/en/application-note/AN10833.pdf

## NTAG21x command overview

Using the NTAG21x data sheet, the command overview is starting from page 32 onwards. As most of the 
commands are already done by Android's OS I'm focusing on the user commands we are going to run. The 
following descriptions are copy & pasted from the data sheet, all copyrights are going to NXP Semiconductors:

### Get Version Command 0x60h

The GET_VERSION command is used to retrieve information on the NTAG family, the product version, storage 
size and other product data required to identify the specific NTAG21x.

This command is also available on other NTAG products to have a common way of identifying products 
across platforms and evolution steps. The GET_VERSION command has no arguments and replies the version 
information for the specific NTAG21x type.

**Response of the tag:**

The tag is responding an eight bytes long array with 8 data fields:
- byte 00: a fixed, static header '0x00h'
- byte 01: vendor ID of the manufacturer of the tag. A tag from NXP Semiconductors has '0x04h' as identifier
- byte 02: product type of the tag. The NTAG21x family gives '0x04h'
- byte 03: product subtype of the tag. The value is depending on the sensitivity of the tag. A "card type" 
tag has 50pF gives the value '0x02h'
- byte 04: major product version: For NTAG21x tags it is '0x01h'
- byte 05: minor product version: For NTAG21x tags it is '0x01h' meaning Version 0
- byte 06: storage size: see explanation below. An NTAG213 gives '0x0Fh', the NTAG215 returns '0x11h' 
and the NTAG216 is returning '0x13h'
- byte 07: protocol type: An NTAG21x tag returns '0x03h' as the tag is ISO/IEC 14443-3 compliant.

Storage size explanations:

The most significant 7 bits of the storage size byte are interpreted as a unsigned integer value n. 
As a result, it codes the total available user memory size as 2n. If the least significant bit is 0b, 
the user memory size is exactly 2n. If the least significant bit is 1b, the user memory size is between 
2n and 2n+1.

The user memory for NTAG213 is 144 bytes. This memory size is between 128bytes and 256 bytes. Therefore, 
the most significant 7 bits of the value 0Fh, are interpreted as 7d and the least significant bit is 1b.

The user memory for NTAG215 is 504 bytes. This memory size is between 256 bytes and 512 bytes. Therefore, 
the most significant 7 bits of the value 11h, are interpreted as 8d and the least significant bit is 1b.

The user memory for NTAG216 is 888 bytes. This memory size is between 512 bytes and 1024 bytes. Therefore, 
the most significant 7 bits of the value 13h, are interpreted as 9d and the least significant bit is 1b.

Example response: *0x0004040201001303h* gives this information:

```plaintext
Version data dump (8 bytes)
fixedHeader: 0
hardwareVendorId: 4
hardwareType: 4
hardwareSubtype: 2
Identification: NTAG 2xx on MIFARE native IC
hardwareVersionMajor: 1
hardwareVersionMinor: 0
hardwareStorageSize: 19
hardwareProtocol: 3
Storage size: >512 bytes
```

### Read Command 0x30h

The READ command requires a start page address, and **returns the 16 bytes of four NTAG21x pages**. For example, 
if address (Addr) is 03h then pages 03h, 04h, 05h, 06h are returned. Special conditions apply if the READ 
command address is near the end of the accessible memory area. The special conditions also apply if at 
least part of the addressed pages is within a password protected area.

In the initial state of NTAG21x, all memory pages are allowed as Addr parameter to the READ command.
- page address 00h to 2Ch for NTAG213
- page address 00h to 86h for NTAG215
- page address 00h to E6h for NTAG216

Addressing a memory page beyond the limits above results in a NAK response from NTAG21x.

A roll-over mechanism is implemented to continue reading from page 00h once the end of the accessible 
memory is reached. Reading from address 2Ah on a NTAG213 results in pages 2Ah, 2Bh, 2Ch and 00h being 
returned.

The following conditions apply if part of the memory is password protected for read access:

If NTAG21x is in the ACTIVE state:

– addressing a page which is equal or higher than AUTH0 results in a NAK response
– addressing a page lower than AUTH0 results in data being returned with the roll-over mechanism 
occurring just before the AUTH0 defined page. For example, if you read protect the tag from page 04 
onwards and you read the page 02, you receive the data as follows: <page02> <page03> <page00> <page01>. 

If NTAG21x is in the AUTHENTICATED state:

– the READ command behaves like on a NTAG21x without access protection

Remark: PWD ("Password") and PACK values can never be read out of the memory. When reading from the 
pages holding those two values, all *00h* bytes are replied to the NFC device instead.

### Fast Read Command 0x3Ah

The FAST_READ command requires a start page address and an end page address and returns the all 
n*4 bytes of the addressed pages. For example if the start address is 03h and the end address is 07h 
then pages 03h, 04h, 05h, 06h and 07h are returned. If the addressed page is outside of accessible 
area, NTAG21x replies a NAK.

In the initial state of NTAG21x, all memory pages are allowed as StartAddr parameter to the FAST_READ command.
- page address 00h to 2Ch for NTAG213
- page address 00h to 86h for NTAG215
- page address 00h to E6h for NTAG216

- Addressing a memory page beyond the limits above results in a NAK response from NTAG21x.

The EndAddr parameter must be equal to or higher than the StartAddr.
The following conditions apply if part of the memory is password protected for read access:
- if NTAG21x is in the ACTIVE state

 –> if any requested page address is equal or higher than AUTH0 a NAK is replied
 
- if NTAG21x is in the AUTHENTICATED state
  
  –> the FAST_READ command behaves like on a NTAG21x without access protection

**Remark**: PWD and PACK values can never be read out of the memory. When reading from the pages 
holding those two values, all 00h bytes are replied to the NFC device instead.

**Remark**: The FAST_READ command is able to read out the whole memory with one command. Nevertheless, 
**receive buffer of the NFC device must be able to handle the requested amount of data** as there is no 
chaining possibility.

You retrieve the "receive buffer size" of your device with the simple NfcA call:
`int maxTransceiveLength = nfcA.getMaxTransceiveLength();`

For e.g., my Samsung device gives me a size 253 for the buffer, so I'm been able to **fastread around 60 pages** 
in one run (60 pages * 4 bytes each = 240 bytes, this includes some protocol overhead bytes). Please 
be aware that each device may have a different buffer size and you cannot rely on a static value !

### Write Command 0xA2h



### Compatibility Write Command 0xA0h


### Read Counter Command 0x39h

**Description for MIFARE Ultralight EV1:**

The READ_CNT command is used to read out the current value of one of the 3 one-way counters of the 
MF0ULx1 (Ultralight EV1). The command has a single argument specifying the counter number and returns 
the 24-bit counter value of the corresponding counter. The counters are always readable, independent 
on the password protection settings.

**Description for NTAG21:**

The READ_CNT command is used to read out the current value of the NFC one-way counter of the NTAG213, 
NTAG215 and NTAG216. The command has a single argument specifying the counter number and returns the 
24-bit counter value of the corresponding counter. If the NFC_CNT_PWD_PROT bit is set to 1b the counter 
is password protected and can only be read with the READ_CNT command after a previous valid password 
authentication (see Section 10.7).

The following conditions apply if the NFC counter is password protected:
- if NTAG21x is in the ACTIVE state: Response to the READ_CNT command results in a NAK response
- if NTAG21x is in the AUTHENTICATED state: Response to the READ_CNT command is the current counter value plus CRC

NTAG21x features a NFC counter function. This function enables NTAG21x to automatically increase the 
24 bit counter value, triggered by the first valid
- READ command or
- FAST-READ command
after the NTAG21x tag is powered by an RF field.

Once the NFC counter has reached the maximum value of FF FF FF hex, the NFC counter value will not 
change any more. The NFC counter is enabled or disabled with the NFC_CNT_EN bit (see Section 8.5.7). 
The actual NFC counter value can be read with
• READ_CNT command or
• NFC counter mirror feature

The reading of the NFC counter (by READ_CNT command or with the NFC counter mirror) can also be protected 
with the password authentication. The NFC counter password protection is enabled or disabled with the 
NFC_CNT_PWD_PROT bit (see Section 8.5.7).

You can use the same command for both tag types, and in case of an NTAG21x you need to use the counter 
address 0x02h.

### Read Signature Command: 

*The READ_SIG command returns an IC specific, 32-byte ECC signature, to verify NXP Semiconductors as 
the silicon vendor. The signature is programmed at chip production and cannot be changed afterwards.*

*Details on how to check the signature value are provided in the following Application note
AN11350 NTAG21x Originality Signature Validation — Application note, BU-ID Document number 2604.*

*It is foreseen to offer an online and offline way to verify originality of NTAG21x.*

Unfortunately, the named document is available under a "Non Disclosure Agreement" only, sorry. But 
there is a light at the end of the NDA tunnel (https://community.nxp.com/t5/NFC/How-to-check-NXP-signature-on-NTAG413/m-p/878885?commentID=1119360&et=watches.email.thread#comment-1119360):

*... from now I think that also you should look this application note it explains in a better way how 
to check orginality signature in both asymmetric and symmetric, it is the same for 413. The new product 
NTAG 424 offers the same features as 413 and more. Please check section 8.2 ...*

NTAG 424 DNA and NTAG 424 DNA TagTamper features and hints (AN12196): https://www.nxp.com/docs/en/application-note/AN12196.pdf. 
Note: this document explains on pages 50 to 51 the Elliptic Curve signature verification process

Note: The NTAG 424 document shows **how to run the verification steps** but the NXP's Public Key is wrong !

```plaintext
UID length: 7 data: 04BE7982355B80
readSignatureResponse length: 32 data: F2DE84A291222F6A04F663D48104D1F523DA00B9A951CC6126CE1BAA8A9E6A50
Result of Originality Signature verification: true

Fake NTAG213 tag:
UID length: 7 data: 1D424AB9950000
readSignatureResponse length: 32 data: 1D424A9DB99500001D424A9DB99500001D424A9DB99500001D424A9DB9950000
Result of Originality Signature verification: false

Second Fake NTAG213 tag:
UID length: 7 data: 1DAC2BB9950000
readSignatureResponse length: 32 data: 1DAC2B12B99500001DAC2B12B99500001DAC2B12B99500001DAC2B12B9950000
UID length: 7 data:                    1DAC2BB9950000
                                                       1DAC2BB9950000
                                                                       1DAC2BB9950000
                                                                                       1DAC2BB9950000                        
Result of Originality Signature verification: false
```



### Log file of reading pages 0-3, 4-7 and 8-11 with Read AuthProtection starting page 05 and without authentication

```plaintext
--------------------
Read pages from page 00
data from pages 0, 1, 2 and 3: 1D424A9DB99500002CA30000E1101200
BJ���????,�????�??
--------------------
Read pages from page 04
data from pages 4, 5, 6 and 7: 416E64721D424A9DB99500002CA30000
AndrBJ���????,�????
--------------------
Read pages from page 08
Could not read the content of the tag, maybe it is read protected ?
Exception from operation: readPage for page 8 failed with IOException: Transceive failed
--------------------



--------------------
Read pages from page 04
data from pages 4, 5, 6 and 7: 416E64721D424A9DB99500002CA30000
AndrBJ���????,�????
--------------------
                                    
This is the content of page 00 .. 03:
```plaintext
--------------------
Read pages from page 00
data from pages 0, 1, 2 and 3: 1D424A9DB99500002CA30000E1101200

BJ���????,�????�??
--------------------

This data was presented by the tag: 416E6472 1D424A9D B9950000 2CA30000
                                    page 04  page 05  page 06  page 07

This data was presented by the tag: 1D424A9D B9950000 2CA30000 E1101200
                                    page 00  page 01  page 02  page 03
                                    
As the tag is read protected from page 6 onwards it responds pages 04 + 05 
with the real content and the content of "pages 06 + 07" is the data from 
pages 00 + 01 due to the "roll over" management.                                    

--------------------
Read pages from page 08
Could not read the content of the tag, maybe it is read protected ?
Exception from operation: readPage for page 8 failed with IOException: Transceive failed
--------------------
on page 8 readPage failed with IOException: Transceive failed                  
```

### Log file of reading pages 0-3, 4-7 and 8-11 with Read AuthProtection starting page 05 and successful authentication

```plaintext
--------------------
Read pages from page 00
data from pages 0, 1, 2 and 3: 1D424A9DB99500002CA30000E1101200
BJ���????,�????�??
--------------------
Read pages from page 04
data from pages 4, 5, 6 and 7: 416E64726F696443727970746F204E46
AndroidCrypto NF
--------------------
Read pages from page 08
data from pages 8, 9, 10 and 11: 43204E666341205475746F7269616C00
C NfcA Tutorial??
--------------------
```

### Log file fastReading page 00-15

```plaintext
--------------------
FastRead pages from page 00 to 15
content pages 00-15
length: 64 data: 1D424A9DB99500002CA30000E1101200416E64726F696443727970746F204E4643204E666341205475746F7269616C2020202020202020202020202020202000
--------------------
ASCII content
BJ���????,�????�??AndroidCrypto NFC NfcA Tutorial                ??
--------------------
```

### Log file fastReading full tag

```plaintext
--------------------
FastRead pages from page 00-end
Full tag content length: 180 data: 1D424A9DB99500002CA30000E1101200416E64726F696443727970746F204E4643204E666341205475746F7269616C20202020202020202020202020202020006765745F726567332E7068703F756964314434323441423939353030303031266D61633D3337316634366263FE000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000BDC2001405800000000000000000000000
--------------------
ASCII content
BJ���????,�????�??AndroidCrypto NFC NfcA Tutorial                ??get_reg3.php?uid1D424AB99500001&mac=371f46bc�????????????????????????????????????????????????????????????????????????????????????????????????????????????��??�??????????????????????
--------------------
```

### NTAG ACK and NAK responses


### ATQA and SAK responses


### ASCII mirror function (NTAG21x only)

NTAG21x features a ASCII mirror function. This function enables NTAG21x to virtually mirror
- 7 byte UID (see Section 8.7.1) or
- 3 byte NFC counter value (see Section 8.7.2) or
- both, 7 byte UID and 3 byte NFC counter value with a separation byte (see Section 8.7.3)
into the physical memory of the IC in ASCII code. On the READ or FAST READ command to the involved 
user memory pages, NTAG21x will respond with the virtual memory content of the UID and/or NFC 
counter value in ASCII code.

The required length of the reserved physical memory for the mirror functions is specified in Table 12. 
If the ASCII mirror exceeds the user memory area, the data will not be mirrored.

```plaintext
ASCII mirror       Required number of bytes in the physical memory
UID mirror         14 bytes
NFC counter        6 bytes
UID + NFC counter  21 bytes (14 bytes for UID + 1 byte separation + 6 bytes NFC counter value)
Table 12. Required memory space for ASCII mirror 
```

The position within the user memory where the mirroring of the UID and/or NFC counter shall start is 
defined by the MIRROR_PAGE and MIRROR_BYTE values. The MIRROR_PAGE value defines the page where the 
ASCII mirror shall start and the MIRROR_BYTE value defines the starting byte within the defined page.

The ASCII mirror function is enabled with a MIRROR_PAGE value >03h.

The MIRROR_CONF bits (see Table 9 and Table 11) define if ASCII mirror shall be enabled for the UID 
and/or NFC counter. If both, the UID and NFC counter, are enabled for the ASCII mirror, the UID and 
the NFC counter bytes are separated automatically with an “x” character (78h ASCII code).

```plaintext
Table 9. MIRROR configuration byte (Configuration Page 0, byte 0)
Bit Explanation Default
7:  MIRROR_CONF 00b     Defines which ASCII mirror shall be used, if the ASCII mirror is enabled by 
                        a valid the MIRROR_PAGE byte
                        00b : no ASCII mirror
                        01b : UID ASCII mirror
                        10b : NFC counter ASCII mirror
                        11b : UID and NFC counter ASCII mirror
6:  MIRROR_CONF 
5:  MIRROR_BYTE 00b     The 2 bits define the byte position within the page defined by the MIRROR_PAGE 
4:  MIRROR_BYTE         byte (beginning of ASCII mirror) (0, 1, 2 or 3 dec)
3:  RFUI        -
2:  STRG_MOD_EN 1b      STRG MOD_EN defines the modulation mode
                        0b : strong modulation mode disabled
                        1b : strong modulation mode enabled
1:  RFUI        -
0:  RFUI        -

MIRROR_PAGE (Configuration Page 0, byte 2):
Default value: 00h : MIRROR_Page defines the page for the beginning of the ASCII mirroring.
                     A value >03h enables the ASCII mirror feature
```

Bote: If you set the NFC Read Counter mirror in MIRROR byte (Bit 7, MIRROR_CONF) you need to enable the NFC 
Read Counter in the ACCESS byte (Bit 4, NFC_CNT_EN) or this value is not mirrored in the user memory ! 


### Signature

```plaintext
Fudan NTAG213 1 UID 1D424AB9950000
readSignatureResponse length: 32 data: 1D424A9DB99500001D424A9DB99500001D424A9DB99500001D424A9DB9950000
Fudan NTAG213 2 UID 1DAC2BB9950000
readSignatureResponse length: 32 data: 1DAC2B12B99500001DAC2B12B99500001DAC2B12B99500001DAC2B12B9950000
```

## NTAG213 complete Log File

```plaintext
==============================
Android Advanced NFC NfcA App
NFC tag detected
Tag UID of the discovered tag
UID length: 7 data: 1DAC2BB9950000
------------------------------
The TechList contains 1 entry/ies:
Entry 0: android.nfc.tech.NfcA
------------------------------
TAG: Tech [android.nfc.tech.NfcA]
------------------------------
-= NfcA Technology data =-
ATQA: 4400
SAK: 00
maxTransceiveLength: 253
------------------------------
Connected to the tag using NfcA technology
==============================
==== Tasks Overview ====
= Get Version           true
= Read Pages 0..3       true
= Read Pages 4..7       true
= FastRead Pages 00-12  true
= Write Page 04         true
= Wr. Bulk Data Page 05 true
= Read Counter          true
= Increase Counter 0    false
= Read Signature        true
= FastRead compl.Tag    true
==== Tasks Overview End ====
==============================
GET VERSION data
Run the GetVersion command and tries to identify the tag
Get Version data: 0004040201000F03
------------------------------
Analyze the get version data
Result of tag identification: true
Version data dump (8 bytes)
fixedHeader: 0
hardwareVendorId: 4
hardwareType: 4
hardwareSubtype: 2
Identification: NTAG_21x on MIFARE native IC
hardwareVersionMajor: 1
hardwareVersionMinor: 0
hardwareStorageSize: 15
hardwareProtocol: 3
Free User Storage size: >128 bytes
*** dump ended ***
Tag is of type NTAG213 with 144 bytes user memory
==============================
Read pages from page 00
Uses the READ command for accessing the content of the pages 0, 1, 2 and 3
data from pages 0, 1, 2 and 3: 1DAC2B12B99500002CA30000E1101200
ASCII: �+��????,�????�??
==============================
Read pages from page 04
Uses the READ command for accessing the content of the pages 4, 5, 6 and 7
data from pages 4, 5, 6 and 7: 31313533416E64726F69644372797074
ASCII: 1153AndroidCrypt
==============================
FastRead pages from page 00 to 12
Uses the FastRead command to read the content from pages 0 up to 12, in total 52 bytes.
content pages 00-12 length: 52 
data: 1DAC2B12B99500002CA30000E110120031313533416E64726F696443727970746F204E4643204E666341205475746F7269616C00
------------------------------
ASCII: 
�+��????,�????�??1153AndroidCrypto NFC NfcA Tutorial??
==============================
Write on page 04
Uses the WRITE command to write a 4 bytes long array to page 4
dataToWrite on page 04 length: 4 data: 31313535
writeToPage 04 response length: 1 data: 0A
Check writeResponse: true
Check writeResponse: ACK
==============================
Write bulk data on pages 05 ff
Uses the WRITEBULKDATA method to write 31 bytes to the tag.
writeBulkDataToPage 05 success: true
==============================
Read the Counter 2
Uses the ReadCnt command to get value of the counter 2. On an NTAG21x with fabric settings this will fail as the counter is not enabled by default.
readCounter 2 Response length: 0 data: IS NULL
readCounter 2 Response: -1
As value of -1 can indicate that the Read Counter is not enabled
------------------------------
Read Counter 0 + 1 is restricted to MIFARE Ultralight EV1 tags, skipped
==============================
Read the Signature
Uses the ReadSig command and gets the 32 bytes long digital signature of the tag.
readSignatureResponse length: 32 data: 1DAC2B12B99500001DAC2B12B99500001DAC2B12B99500001DAC2B12B9950000
For verification the signature please read the docs.
==============================
FastRead pages from page 00-end
Uses the FastRead command to read the full content of the tag.
Full tag content length: 180 data: 1DAC2B12B99500002CA30000E110120031313535416E64726F696443727970746F204E4643204E666341205475746F7269616C00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000BD040000FF000000000000000000000000
------------------------------
ASCII:
�+��????,�????�??1155AndroidCrypto NFC NfcA Tutorial????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????�????�????????????????????????
 
== Processing Ended ==
==============================
```

## MIFARE Ultralight C complete Log File
```plaintext
==============================
Android Advanced NFC NfcA App
NFC tag detected
Tag UID of the discovered tag
UID length: 7 data: 041F80AA987880
------------------------------
The TechList contains 3 entry/ies:
Entry 0: android.nfc.tech.NfcA
Entry 1: android.nfc.tech.MifareUltralight
Entry 2: android.nfc.tech.NdefFormatable
------------------------------
TAG: Tech [android.nfc.tech.NfcA, android.nfc.tech.MifareUltralight, android.nfc.tech.NdefFormatable]
------------------------------
-= NfcA Technology data =-
ATQA: 4400
SAK: 00
maxTransceiveLength: 253
------------------------------
Connected to the tag using NfcA technology
==============================
==== Tasks Overview ====
= Get Version           true
= Read Pages 0..3       true
= Read Pages 4..7       true
= FastRead Pages 00-12  true
= Write Page 04         true
= Wr. Bulk Data Page 05 true
= Read Counter          true
= Increase Counter 0    false
= Read Signature        true
= FastRead compl.Tag    true
==== Tasks Overview End ====
==============================
GET VERSION data
Run the GetVersion command and tries to identify the tag
Could not read the version of the tag, maybe it is read protected or does not provide a Get Version command ?
Exception from operation: Get Version failed with IOException: Transceive failed
------------------------------
Analyzing of the get version data skipped, using ATQA & SAK for tag identification
Tag is probably of type Ultralight C with 144 bytes user memory
==============================
Read pages from page 00
Uses the READ command for accessing the content of the pages 0, 1, 2 and 3
data from pages 0, 1, 2 and 3: 041F8013AA987880CA48000000000000
ASCII: ���x��H????????????
==============================
Read pages from page 04
Uses the READ command for accessing the content of the pages 4, 5, 6 and 7
data from pages 4, 5, 6 and 7: 31323135416E64726F69644372797074
ASCII: 1215AndroidCrypt
==============================
FastRead Page is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped
==============================
Write on page 04
Uses the WRITE command to write a 4 bytes long array to page 4
dataToWrite on page 04 length: 4 data: 31323137
writeToPage 04 response length: 1 data: 0A
Check writeResponse: true
Check writeResponse: ACK
==============================
Write bulk data on pages 05 ff
Uses the WRITEBULKDATA method to write 31 bytes to the tag.
writeBulkDataToPage 05 success: true
==============================
Read Counter is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped
==============================
Read Signature is restricted to NTAG21x and MIFARE Ultralight EV1 tags, skipped
==============================
FastRead of the complete tag content skipped, tag has no FAST READ command
 
== Processing Ended ==
==============================
```

## Credit Card complete Log File
```plaintext
==============================
Android Advanced NFC NfcA App
NFC tag detected
Tag UID of the discovered tag
UID length: 4 data: 020155A0
------------------------------
The TechList contains 2 entry/ies:
Entry 0: android.nfc.tech.IsoDep
Entry 1: android.nfc.tech.NfcA
------------------------------
TAG: Tech [android.nfc.tech.IsoDep, android.nfc.tech.NfcA]
------------------------------
-= NfcA Technology data =-
ATQA: 0400
SAK: 20
maxTransceiveLength: 253
------------------------------
Connected to the tag using NfcA technology
==============================
==== Tasks Overview ====
= Get Version           true
= Read Pages 0..3       true
= Read Pages 4..7       true
= FastRead Pages 00-12  true
= Write Page 04         true
= Wr. Bulk Data Page 05 true
= Read Counter          true
= Increase Counter 0    false
= Read Signature        true
= FastRead compl.Tag    true
==== Tasks Overview End ====
==============================
GET VERSION data
Run the GetVersion command and tries to identify the tag
Could not read the version of the tag, maybe it is read protected or does not provide a Get Version command ?
Exception from operation: Get Version failed with IOException: Tag was lost.
------------------------------
Analyzing of the get version data skipped, using ATQA & SAK for tag identification
Tag is probably of type Assumed Credit Card with 0 bytes user memory
==============================
This tag is not of type NTAG21x, MIFARE Ultralight EV or MIFARE Ultralight C. The further tasks are skipped
 
== Processing Ended ==
==============================
```

# OLD DESCRIPTION

This is a simple app showing how to detect and read some data from an NFC tag tapped to the Android's NFC reader.

As there are a lot of questions on Stackoverflow.com that use an **Intent-based** NFC detection system I'm showing here how to use the more 
modern **Reader Mode** for NFC communication.

This is from an answer by *[Andrew](https://stackoverflow.com/users/2373819/andrew)* regarding the two modes:

*Also note that using enableForegroundDispatch is actually not the best way to use NFC. Using enableReaderMode is a newer and much better API 
to use. NFC.enableReaderMode does not use Intent's and gives you more control, it is easy to do NFC operations in a background Thread (which 
is recommended), for writing to NFC Tag's it is much more reliable and leads to less errors.*

There are 4 simples steps to **implement the Reader mode**:

1) in `AndroidManifest.xml` add one line: `<uses-permission android:name="android.permission.NFC" />`
2) in your activity or fragment expand your class definition by `implements NfcAdapter.ReaderCallback`
3) create an `onTagDiscovered` method where all the work with the tag is done.
4) create an `onResume` method to define the technologies and settings for the Reader Mode:

```plaintext
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
```
The flags are responsible for defining the NFC classes the NFC reader should detect. If you e.g. delete 
the line `NfcAdapter.FLAG_READER_NFC_A` your app will not detect any NFC tags using the NfcA technology.  

The last flag `NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS` is useful for a better user experience. Using 
the **Intent based** mode a system sound will appear when the NFC tag is detected **at the beginning**. 
This causes some uses to move the NFC tag out of the reader field and you receive a "Tag Lost Exception". 
When using the **Reader Mode** the flag prohibits the device to give any feedback to the user. In my app 
I'm running a short *beep* **at the end** or the reading process, signalizing that everything is done. 

Note: **the `onTagDetected` method is not running on the User Interface (UI) thread**, so you are not allowed to write directly to any UI elements like 
e.g. TextViews or Toasts - you need to encapsulate them in a `run onUiTHread` construct. This method is running in an background thread:
```plaintext
runOnUiThread(() -> {
   Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
});
```

## Steps to read an NFC tag using Reader Mode

Here are some commands to get the first information's about the tag:

`byte[] tagUid = tag.getId();`: receive the tag Unique Identifier (the length depending on tag type).
`String[] techlist = tag.getTechList();`: this is very important as the **NFC tag informs us about the NFC technologies 
it is been able to communicate** with us (e.g. *android.nfc.tech.NfcA*).

Now it is time to **assign the received tag to an NFC technology class**, e.g. to the NDEF class. It is 
important to double check that the ndef variable is not NULL to avoid any errors. If e.g. the ndef-var is not 
null you can **connect** to the NFC tag. Always surround the tag operations with a *try.. catch* clause.

### Example workflow for an NfcA type tag

From the tag I'm getting the *ATQA* and *SAK* value - they are required to identify an NfcA tag on the first level. 
The *maxTransceiveLength* is important when trying to read data from tag - if the data length including some 
protocol bytes is extending this maximum you will receive an error from your device, as the maximum is the 
size of your device's NFC reader unit.

The next steps would be to send commands to the tag using the `transceive` method. I don't show any code for this 
within the app as commands are tag specific.

Please don't forget to close the NfcA class after your work is done. 

```plaintext
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
```

### Example workflow for an NDEF Message

This example has a very limited NDEF workflow and just print out the raw NDEF data. Usually you will divide 
the NDEF Message in separate NDEF records and work with the data depending on the NDEF Record type (not shown 
in this app).

Please don't forget to close the technology after reading is done.

```plaintext
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
```

## Screen after reading a tag with an NDEF message

![Screen of the Main](screenshot/small/app_home_01.png)

## Example outputs for some tag types

Below you find outputs for some tags with different technologies involved:

### Example for an NTAG216 tag with NfcA technology:
```plaintext
NFC tag detected
Tag UID length: 7 UID: 04be7982355b80
--------------------
The TechList contains 3 entry/ies:
Entry 0: android.nfc.tech.NfcA
Entry 1: android.nfc.tech.MifareUltralight
Entry 2: android.nfc.tech.NdefFormatable
--------------------
TAG: Tech [android.nfc.tech.NfcA, android.nfc.tech.MifareUltralight, android.nfc.tech.NdefFormatable]
--------------------
-= NfcA Technology =-
ATQA: 4400
SAK: 00
maxTransceiveLength: 253
--------------------
Connected to the tag using NfcA technology
--------------------
This tag is NOT supporting the NfcV class
--------------------
This tag is NOT supporting the NDEF class
--------------------
```

### Example for an NTAG216 tag containing an NDEF message:
```plaintext
NFC tag detected
Tag UID length: 7 UID: 04be7982355b80
--------------------
The TechList contains 3 entry/ies:
Entry 0: android.nfc.tech.NfcA
Entry 1: android.nfc.tech.MifareUltralight
Entry 2: android.nfc.tech.Ndef
--------------------
TAG: Tech [android.nfc.tech.NfcA, android.nfc.tech.MifareUltralight, android.nfc.tech.Ndef]
--------------------
-= NfcA Technology =-
ATQA: 4400
SAK: 00
maxTransceiveLength: 253
--------------------
Connected to the tag using NfcA technology
--------------------
This tag is NOT supporting the NfcV class
--------------------
Connected to the tag using NDEF technology
--------------------
NDEF message: NdefMessage [NdefRecord tnf=1 type=54 payload=02656E416E64726F696443727970746F]
NDEF message: d101105402656e416e64726f696443727970746f
NDEF message: enAndroidCrypto
```

### Example for a MIFARE Classic tag:
```plaintext
NFC tag detected
Tag UID length: 4 UID: 641a35cf
--------------------
The TechList contains 3 entry/ies:
Entry 0: android.nfc.tech.NfcA
Entry 1: android.nfc.tech.MifareClassic
Entry 2: android.nfc.tech.NdefFormatable
--------------------
TAG: Tech [android.nfc.tech.NfcA, android.nfc.tech.MifareClassic, android.nfc.tech.NdefFormatable]
--------------------
-= NfcA Technology =-
ATQA: 0400
SAK: 08
maxTransceiveLength: 253
--------------------
Connected to the tag using NfcA technology
--------------------
This tag is NOT supporting the NfcV class
--------------------
This tag is NOT supporting the NDEF class
--------------------
```

### Example for an NFC enabled Credit Card:
```plaintext
NFC tag detected
Tag UID length: 4 UID: b58fcc6d
--------------------
The TechList contains 2 entry/ies:
Entry 0: android.nfc.tech.IsoDep
Entry 1: android.nfc.tech.NfcA
--------------------
TAG: Tech [android.nfc.tech.IsoDep, android.nfc.tech.NfcA]
--------------------
-= NfcA Technology =-
ATQA: 0400
SAK: 20
maxTransceiveLength: 253
--------------------
Connected to the tag using NfcA technology
--------------------
This tag is NOT supporting the NfcV class
--------------------
This tag is NOT supporting the NDEF class
--------------------
```

### Example for an ICODE SLIX tag with NfcV technology:
```plaintext
NFC tag detected
Tag UID length: 8 UID: 18958608530104e0
--------------------
The TechList contains 2 entry/ies:
Entry 0: android.nfc.tech.NfcV
Entry 1: android.nfc.tech.NdefFormatable
--------------------
TAG: Tech [android.nfc.tech.NfcV, android.nfc.tech.NdefFormatable]
--------------------
This tag is NOT supporting the NfcA class
--------------------
-= NfcV Technology =-
DsfId: 00
maxTransceiveLength: 253
--------------------
Connected to the tag using NfcV technology
--------------------
This tag is NOT supporting the NDEF class
--------------------
```

```plaintext
/*
Get Version data: 040101010016050401010104160500046D759AA47780B90C224D703722
DESFire EV1 2K with 2048 bytes user memory
hardwareVendorId: 4
hardwareType: 1
hardwareSubtype: 1
Identification: MIFARE_DESFire on MIFARE native IC
hardwareVersionMajor: 1
hardwareVersionMinor: 0
hardwareStorageSize: 22
hardwareProtocol: 5
 */
/*
// Get Version data: 04010112001605040101020116050004464BDAD37580CF5B9665003521
DESFire EV2 2K with 2048 bytes user memory
hardwareVendorId: 4
hardwareType: 1
hardwareSubtype: 1
Identification: MIFARE_DESFire on MIFARE native IC
hardwareVersionMajor: 18
hardwareVersionMinor: 0
hardwareStorageSize: 22
hardwareProtocol: 5
*/
/*
Get Version data: 040101120018050401010201180500041858FA991190CF6C145D801222
DESFire EV2 4K with 4048 bytes user memory
hardwareVendorId: 4
hardwareType: 1
hardwareSubtype: 1
Identification: MIFARE_DESFire on MIFARE native IC
hardwareVersionMajor: 18
hardwareVersionMinor: 0
hardwareStorageSize: 24
hardwareProtocol: 5
*/
/*
Get Version data: 040101330016050401010300160500045E083250149020466430304822
DESFire EV3 2K with 2048 bytes user memory
hardwareVendorId: 4
hardwareType: 1
hardwareSubtype: 1
Identification: MIFARE_DESFire on MIFARE native IC
hardwareVersionMajor: 51
hardwareVersionMinor: 0
hardwareStorageSize: 22
hardwareProtocol: 5
*/
```


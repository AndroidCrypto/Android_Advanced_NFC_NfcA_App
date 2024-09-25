# Android Advanced NFC NfcA App

This app will explain in detail how the communication with an NFC tag works when using the **NfcA technology**. 
The repository is the accompanying resource for a tutorial on medium.com available here: soon... 

Please be aware that this is an **advanced tutorial**, meaning I do not explain the details of Near Field 
Communication (NFC). This is done in the tutorial "**How to use NFC Reader Mode in Android to connect to NFC tags (Java)**". 
There is as well a GitHub repository available: https://github.com/AndroidCrypto/AndroidBasicNfcReader.

## data sheets of the described NFC tags
For my work I used some well known and available on the market NFC tags. It is very important to understand 
the technical fetures of each tag you want to work with, for that reason this are the links to the data sheets. 
I included the last available versions in the "doc" subfolder of this repository.

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

**Storage size explanations**:

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

For e.g., my Samsung device gives me a size 253 for the buffer, so I'm being able to **fast read around 60 pages** 
in one run (60 pages * 4 bytes each = 240 bytes, this includes some protocol overhead bytes). Please 
be aware that each device may have a different buffer size and you cannot rely on a static value I using !

### Write Command 0xA2h

The WRITE command requires a block address, and writes 4 bytes of data into the addressed NTAG21x page.

In the initial state of NTAG21x, the following memory pages are valid Addr parameters to the WRITE command.
- page address 02h to 2Ch for NTAG213
- page address 02h to 86h for NTAG215
- page address 02h to E6h for NTAG216

- Addressing a memory page beyond the limits above results in a NAK response from NTAG21x.
Pages which are locked against writing cannot be reprogrammed using any write command. The locking 
mechanisms include static and dynamic lock bits as well as the locking of the configuration pages.

The following conditions apply if part of the memory is password protected for write access:
- if NTAG21x is in the ACTIVE state: writing to a page which address is equal or higher than AUTH0 results in a NAK response
- if NTAG21x is in the AUTHENTICATED state: the WRITE command behaves like on a NTAG21x without access protection NTAG21x features tearing protected write operations to specific memory content. The following pages are protected against tearing events during a WRITE operation:
- page 02h containing static lock bits
- page 03h containing CC bits
- page 28h containing the additional dynamic lock bits for the NTAG213
- page 82h containing the additional dynamic lock bits for the NTAG215
- page E2h containing the additional dynamic lock bits for the NTAG216

### Compatibility Write Command 0xA0h

As this command is for usage with older infrastructure I don't explain it any further:

*The COMPATIBILITY_WRITE command is implemented to guarantee interoperability with the established 
MIFARE Classic PCD infrastructure, in case of coexistence of ticketing and NFC applications.*

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

Note: The NTAG 424 document shows **how to run the verification steps** but the NXP's Public Key is 
wrong for usage with NTAG21x or Ultralight EV1 tags !

```plaintext
NTAG216 tag from NXP:
UID length: 7 data: 04BE7982355B80
readSignatureResponse length: 32 data: F2DE84A291222F6A04F663D48104D1F523DA00B9A951CC6126CE1BAA8A9E6A50

Fake NTAG213 tag:
UID length: 7 data: 1D424AB9950000
readSignatureResponse length: 32 data: 1D424A9DB99500001D424A9DB99500001D424A9DB99500001D424A9DB9950000

Second Fake NTAG213 tag:
UID length: 7 data: 1DAC2BB9950000
readSignatureResponse length: 32 data: 1DAC2B12B99500001DAC2B12B99500001DAC2B12B99500001DAC2B12B9950000
UID length: 7 data:                    1DAC2BB9950000
                                                       1DAC2BB9950000
                                                                       1DAC2BB9950000
                                                                                       1DAC2BB9950000                        
```

### Signature

```plaintext
Fudan NTAG213 1 UID 1D424AB9950000
readSignatureResponse length: 32 data: 1D424A9DB99500001D424A9DB99500001D424A9DB99500001D424A9DB9950000
Fudan NTAG213 2 UID 1DAC2BB9950000
readSignatureResponse length: 32 data: 1DAC2B12B99500001DAC2B12B99500001DAC2B12B99500001DAC2B12B9950000
```

## Screen after reading a tag

![Screen of the Main](screenshot/small/app_home_01.png)

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

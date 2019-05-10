/*
 * Copyright (c) 2019, Sergey Stolyarov <sergei@regolit.com>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.List;
import java.util.ArrayList;
import javax.smartcardio.*;
import java.util.Arrays;
import java.util.HashMap;

public class Example {
    public static void main(String[] args) {
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();

            if (terminals.size() == 0) {
                throw new Util.TerminalNotFoundException();
            }

            // get first terminal
            CardTerminal terminal = terminals.get(0);

            System.out.printf("Using terminal %s%n", terminal.toString());

            // wait for card, indefinitely until card appears
            terminal.waitForCardPresent(0);

            // establish a connection to the card using autoselected protocol
            Card card = terminal.connect("*");

            // obtain logical channel
            CardChannel channel = card.getBasicChannel();

            ResponseAPDU answer;
            int sw;

            // Select PSE first
            //                                   CLA  INS  P1 P2   Lc  DATA
            byte[] selectPSECommand = Util.toByteArray("00   A4   04 00   0E  31 50 41 59 2E 53 59 53 2E 44 44 46 30 31");
            answer = channel.transmit(new CommandAPDU(selectPSECommand));
            sw = answer.getSW();
            byte[] aid = null;
            if (sw == 0x9000) {
                System.out.println("Found PSE, extract AID");
                byte[] PSEData = answer.getData();
                aid = getAIDFromPSEFCI(channel, PSEData);
            } else if (sw == 0x6A82) {
                // guess AID
                System.out.println("PSE not found, guessing AID");
                aid = guessAID(channel);
            } else {
                card.disconnect(false);
                throw new Util.CardOperationFailedException(String.format("PSE retrieval failed: 0x%04X", answer.getSW()));
            }

            if (aid == null) {
                System.out.println("No payment AIDs found.");
                card.disconnect(false);
                return;
            } else {
                System.out.printf("Found payment system: %s%n", Util.hexify(aid));
            }

            // Select application with name "aid"
            //                                       CLS INS P1 P2  Le
            byte[] selectCommand = Util.toByteArray("00  A4  04 00  07  00 00 00 00 00 00 00");
            for (int i=0; i<7; i++) {
                selectCommand[5+i] = aid[i];
            }

            answer = channel.transmit(new CommandAPDU(selectCommand));
            if (answer.getSW() != 0x9000) {
                card.disconnect(false);
                throw new Util.CardOperationFailedException("No EMV app found.");
            }

            byte[] data = answer.getData();
            byte[] pdolData = null;
            try {
                BerTlv fciTlv = BerTlv.parseBytes(data);
                BerTlv piTlv = fciTlv.getPart("A5");
                BerTlv labelTlv = piTlv.getPart("50");
                System.out.printf("Application name: %s%n", Util.bytesToString(labelTlv.getValue()));
                BerTlv langTlv = piTlv.getPart("5F 2D");
                if (langTlv != null) {
                    System.out.printf("Language preference: %s%n", Util.bytesToString(langTlv.getValue()));
                }
                BerTlv pdolTlv = piTlv.getPart("9F 38");
                if (pdolTlv != null) {
                    pdolData = pdolTlv.getValue();
                }
            } catch (BerTlv.ConstraintException e) {
                card.disconnect(false);
                throw new Util.CardOperationFailedException("Failed to parse SELECT response");
            } catch (BerTlv.ParsingException e) {
                card.disconnect(false);
                throw new Util.CardOperationFailedException("Failed to parse SELECT response");
            }
            
            // Start financial transaction
            // prepare dolData
            byte[] dolData = Util.toByteArray("83 00");
            if (pdolData != null) {
                // parse pdol data and extract total fields length
                // ignore tags
                boolean lengthByte = false;
                int totalLength = 0;
                for (byte b : pdolData) {
                    if (lengthByte) {
                        int x = b;
                        if (x < 0) {
                            x += 256;
                        }
                        totalLength += x;
                        lengthByte = false;
                        continue;
                    }
                    if ((b & 0x1F) != 0x1F) {
                        // ^^^^^^ last five bits of "b" are not all 1s, so this byte is last one
                        // in tag block, so consider next byte as field length
                        lengthByte = true;
                    }
                }
                byte[] t = new byte[totalLength];
                dolData[1] = (byte)totalLength;  // remember, dolData = "83 00"
                dolData = Util.concatArrays(dolData, t);
            }

            // Send command "GET PROCESSING OPTIONS"
            byte[] gpoCommand = Util.toByteArray("80 A8 00 00 00");
            gpoCommand[4] = (byte)dolData.length;
            gpoCommand = Util.concatArrays(gpoCommand, dolData);
            gpoCommand = Util.concatArrays(gpoCommand, Util.toByteArray("00"));
            answer = channel.transmit(new CommandAPDU(gpoCommand));
            if (answer.getSW() != 0x9000) {
                card.disconnect(false);
                throw new Util.CardOperationFailedException(String.format("GET PROCESSING OPTIONS failed: %04X%n", answer.getSW()));
            }

            data = answer.getData();
            byte[] aipData = null;
            byte[] aflData = null;
            try {
                BerTlv gpoTlv = BerTlv.parseBytes(data);
                if (gpoTlv.tagEquals("77")) {
                    aipData = gpoTlv.getPart("82").getValue();
                    aflData = gpoTlv.getPart("94").getValue();
                } else if (gpoTlv.tagEquals("80")) {
                    byte[] gpoData = gpoTlv.getValue();
                    aipData = Util.copyArray(gpoData, 0, 2);
                    aflData = Util.copyArray(gpoData, 2, gpoData.length-2);
                } else {
                    throw new Util.CardOperationFailedException("Unknown response from GET PROCESSING OPTIONS command");
                }
            } catch (BerTlv.ConstraintException e) {
                throw new Util.CardOperationFailedException("Failed to decode response from GET PROCESSING OPTIONS command");
            } catch (BerTlv.ParsingException e) {
                throw new Util.CardOperationFailedException("Failed to parse response from GET PROCESSING OPTIONS command");
            }

            System.out.println("> Application Interchange Profile");
            System.out.printf("  SDA supported: %s%n", (aipData[0] & 0x40)==0 ? "no" : "yes");
            System.out.printf("  DDA supported: %s%n", (aipData[0] & 0x20)==0 ? "no" : "yes");
            System.out.printf("  Cardholder verification is supported: %s%n", (aipData[0] & 0x10)==0 ? "no" : "yes");
            System.out.printf("  Terminal risk management is to be performed: %s%n", (aipData[0] & 0x8)==0 ? "no" : "yes");
            System.out.printf("  Issuer authentication is supported: %s%n", (aipData[0] & 0x4)==0 ? "no" : "yes");
            System.out.printf("  CDA supported: %s%n", (aipData[0] & 0x1)==0 ? "no" : "yes");

            // now read AFL points to
            ArrayList<BerTlv> readObjects = new ArrayList<BerTlv>(10);
            int aflPartsCount = aflData.length / 4;
            for (int i=0; i<aflPartsCount; i++) {
                int startByte = i*4;

                byte sfi = (byte)(aflData[startByte] >> 3);
                int firstSfiRec = aflData[startByte + 1];
                int lastSfiRec = aflData[startByte + 2];
                int offlineAuthRecNumber = aflData[startByte + 3];  // we don't use it

                //                                           CLA INS P1 P2 Le
                byte[] readRecordCommand = Util.toByteArray("00  B2  00 00 00");
                for (int j=firstSfiRec; j<=lastSfiRec; j++) {
                    // set Le=0
                    readRecordCommand[4] = 0;
                    // set P1
                    readRecordCommand[2] = (byte)j;
                    byte p2 = (byte)((sfi << 3) | 4);
                    readRecordCommand[3] = p2;
                    answer = channel.transmit(new CommandAPDU(readRecordCommand));
                    if (answer.getSW1() == 0x6C) {
                        // set new Le and repeat
                        readRecordCommand[4] = (byte)answer.getSW2();
                        answer = channel.transmit(new CommandAPDU(readRecordCommand));
                    }
                    if (answer.getSW() != 0x9000) {
                        // real terminal must terminate transaction if any read fails
                        System.out.printf("Failed to read record %d from SFI=%d", j, sfi);
                        continue;
                    }
                    byte[] recordData = answer.getData();
                    try {
                        BerTlv recordTlv = BerTlv.parseBytes(recordData);
                        if (!recordTlv.tagEquals("70")) {
                            continue;
                        }
                        try {
                            for (BerTlv p : recordTlv.getParts()) {
                                readObjects.add(p);
                            }
                        } catch (BerTlv.ConstraintException e) {
                            System.out.println("BER-TLV error");
                        }

                    } catch (BerTlv.ParsingException e) {
                        System.out.printf("Failed to parse data: %s%n%s%n", e, Util.hexify(recordData));
                    }
                }
            }

            System.out.println("> AFL Data");
            HashMap<String, String> mappedValues = Util.mapDataObjects(readObjects);

            for (BerTlv b : readObjects) {
                String tagString = Util.hexify(b.getTag());
                System.out.printf("  %s%n", mappedValues.get(tagString));
            }

            card.disconnect(false);

        } catch (Util.CardOperationFailedException e) {
            System.out.printf("Card operation failed: %s%n", e.toString());
        } catch (Util.TerminalNotFoundException e) {
            System.out.println("No connected terminals.");
        } catch (CardException e) {
            System.out.println("CardException: " + e.toString());
        }
    }


    private final static byte[] getAIDFromPSEFCI(CardChannel channel, byte[] data)
        throws Util.CardOperationFailedException, CardException
    {
        try {
            BerTlv root = BerTlv.parseBytes(data);

            // pi means "proprietary information"
            BerTlv piTlv = root.getPart("A5");
            if (piTlv == null) {
                throw new Util.CardOperationFailedException("Cannot find EMV block in PSE FCI");
            }

            // piTlv now contains data specified in EMV_v4.3 book 1 spec,
            // section "11.3.4 Data Field Returned in the Response Message"
            BerTlv sfiTlv = piTlv.getPart("88");
            if (sfiTlv == null) {
                throw new Util.CardOperationFailedException("Cannot find SFI block in PSE FCI");
            }

            byte[] defSfiData = sfiTlv.getValue();
            int sfi = defSfiData[0];

            ResponseAPDU answer;

            // READ RECORD, see ISO/IEC 7816-4, section "7.3.3 READ RECORD (S) command"
            //                                           CLA INS P1 P2  Le
            byte[] readRecordCommand = Util.toByteArray("00  B2  00 00  00");
            // read single record specified in P1 from EF with short EF identifier sfi
            byte p2 = (byte)((sfi << 3) | 4);
            readRecordCommand[3] = p2;

            ArrayList<byte[]> aids = new ArrayList<byte[]>();

            byte recordNumber = 1;
            byte expectedLength = 0;
            while (true) {
                readRecordCommand[2] = recordNumber;
                readRecordCommand[4] = expectedLength;
                answer = channel.transmit(new CommandAPDU(readRecordCommand));
                if (answer.getSW1() == 0x6C) {
                    expectedLength = (byte)answer.getSW2();
                    continue;
                }
                if (answer.getSW() != 0x9000) {
                    break;
                }

                byte[] record = answer.getData();
                if (record.length != 0) {
                    BerTlv psd = BerTlv.parseBytes(record);
                    // psd must have tag "70"
                    // see EMV_v4.3 book 1, section "12.2.3 Coding of a Payment System Directory"
                    if (!psd.tagEquals("70")) {
                        throw new Util.CardOperationFailedException("Cannot find PSD record");
                    }
                    for (BerTlv p : psd.getParts()) {
                        if (p.tagEquals("61")) {
                            BerTlv aidTlv = p.getPart("4F");
                            aids.add(aidTlv.getValue());
                        }
                    }
                }
                recordNumber++;
            }
            if (aids.size() > 0) {
                return aids.get(0);
            } else {
                return null;
            }
        } catch (BerTlv.ConstraintException e) {
            throw new Util.CardOperationFailedException("Failed to parse PSE FCI data: BerTlv encoding error");
        } catch (BerTlv.ParsingException e) {
            throw new Util.CardOperationFailedException("Failed to parse PSE FCI data");
        }
    }

    private final static byte[] guessAID(CardChannel channel)
        throws CardException
    {
        ArrayList<byte[]> candidateAIDs = new ArrayList<byte[]>(5);

        candidateAIDs.add(Util.toByteArray("A0 00 00 00 03 20 10"));  // Visa Electron
        candidateAIDs.add(Util.toByteArray("A0 00 00 00 03 10 10"));  // Visa Classic
        candidateAIDs.add(Util.toByteArray("A0 00 00 00 04 10 10"));  // Mastercard

        //                                          CLS INS P1 P2  Le
        byte[] selectADFCommand = Util.toByteArray("00  A4  04 00  07  00 00 00 00 00 00 00");
        ResponseAPDU answer;
        byte[] foundAID = null;

        for (byte[] aid : candidateAIDs) {
            // copy AID to command array
            for (int i=0; i<7; i++) {
                selectADFCommand[5+i] = aid[i];
            }
            answer = channel.transmit(new CommandAPDU(selectADFCommand));
            if (answer.getSW() == 0x9000) {
                foundAID = aid;
                break;
            }
        }

        return foundAID;
    }
}
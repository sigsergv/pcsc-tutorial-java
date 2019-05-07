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
                // take AID from FCI
                byte[] PSEData = answer.getData();
                aid = getAIDFromPSEFCI(channel, PSEData);
            } else if (sw == 0x6A82) {
                // guess AID
                aid = guessAID(channel);
            } else {
                throw new Util.CardOperationFailedException(String.format("PSE retrieval failed: 0x%04X", answer.getSW()));
            }

            if (aid == null) {
                System.out.println("PSE not found.");
            } else {
                System.out.printf("Found payment system: %s%n", Util.hexify(aid));
            }

            // disconnect card
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

            // piTlv now contains data specified in EMV book 1 spec,
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
                    // see EMV book 1, section "12.2.3 Coding of a Payment System Directory"
                    if (!java.util.Arrays.equals(psd.getTag(), Util.toByteArray("70"))) {
                        throw new Util.CardOperationFailedException("Cannot find PSD record");
                    }
                    for (BerTlv p : psd.getParts()) {
                        if (java.util.Arrays.equals(p.getTag(), Util.toByteArray("61"))) {
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

    private final static byte[] guessAID(CardChannel channel) {
        return null;
    }
}
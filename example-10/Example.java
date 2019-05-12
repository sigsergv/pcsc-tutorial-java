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
            // System.out.printf("ATR: %s%n", Util.hexify(card.getATR().getBytes()));

            parseAndPrintATR(card.getATR().getBytes());

            // obtain logical channel
            // CardChannel channel = card.getBasicChannel();

            card.disconnect(false);

        // } catch (Util.CardOperationFailedException e) {
        //     System.out.printf("Card operation failed: %s%n", e.toString());
        } catch (Util.TerminalNotFoundException e) {
            System.out.println("No connected terminals.");
        } catch (CardException e) {
            System.out.println("CardException: " + e.toString());
        }
    }

    private static class ATRParsingException extends Exception {
        public ATRParsingException(String message) {
            super(message);
        }
    }

    private static class InterfaceBytes {
        public final Byte TA;
        public final Byte TB;
        public final Byte TC;
        public final Byte TD;
        public final int T;

        public InterfaceBytes(Byte TA, Byte TB, Byte TC, Byte TD, int T) {
            this.TA = TA;
            this.TB = TB;
            this.TC = TC;
            this.TD = TD;
            this.T = T;
        }
    };

    private static void parseAndPrintATR(byte[] bytes) {
        try {
            // first extract data
            if (bytes.length < 2) {
                throw new ATRParsingException("ATR bytes array is too short");
            }

            // check TS byte
            if (bytes[0] != 0x3B && bytes[0] != 0x3F) {
                throw new ATRParsingException("Byte TS is incorrect.");
            }

            ArrayList<InterfaceBytes> allInterfaceBytes = new ArrayList<InterfaceBytes>(33);
            int historicalBytesLength = 0;
            byte[] historicalBytes = new byte[0];

            // check format byte T0
            byte T0 = bytes[1];
            int Y = (T0 >> 4) & 0xF;
            historicalBytesLength = T0 & 0xF;

            // read interface bytes
            int p = 2;
            int T = 0;

            while (true) {
                Byte TA = null;
                Byte TB = null;
                Byte TC = null;
                Byte TD = null;

                // check is next byte is TAi
                if ((Y & 1) != 0) {
                    TA = bytes[p];
                    p++;
                }
                // check is next byte is TBi
                if ((Y & 2) != 0) {
                    TB = bytes[p];
                    p++;
                }
                // check is next byte is TCi
                if ((Y & 4) != 0) {
                    TC = bytes[p];
                    p++;
                }
                // check is next byte is TDi
                if ((Y & 8) != 0) {
                    TD = bytes[p];
                    p++;
                }
                allInterfaceBytes.add(new InterfaceBytes(TA, TB, TC, TD, T));

                if (TD == null) {
                    break;
                }
                T = TD & 0xF;
                Y = (TD >> 4) & 0xF;
            }

            // copy historical bytes
            historicalBytes = Util.copyArray(bytes, p, historicalBytesLength);

            // read and check TCK (if present)

            // now print data
            System.out.printf("ATR: %s%n", Util.hexify(bytes));

            System.out.println("Interface bytes:");
            p = 1;
            for (InterfaceBytes tb : allInterfaceBytes) {
                if (tb.TA != null) {
                    System.out.printf(" TA%d = %02X (T = %d)%n", p, tb.TA, tb.T);
                }
                if (tb.TB != null) {
                    System.out.printf(" TB%d = %02X (T = %d)%n", p, tb.TB, tb.T);
                }
                if (tb.TC != null) {
                    System.out.printf(" TC%d = %02X (T = %d)%n", p, tb.TC, tb.T);
                }
                if (tb.TD != null) {
                    System.out.printf(" TD%d = %02X (T = %d)%n", p, tb.TD, tb.T);
                }
                p++;
            }

            System.out.printf("Historical bytes length (K): %d%n", historicalBytesLength);
            System.out.printf("Historical bytes (raw): %s%n", Util.hexify(historicalBytes));


            if (historicalBytes[0] == (byte)0x80) {
                // parse all as COMPACT-TLV objects
                int limit = historicalBytes.length;
                p = 1;
                while (true) {
                    if (p == limit) {
                        break;
                    }
                    if (p > limit) {
                        throw new ATRParsingException("Incorrect historical bytes structure.");
                    }
                    int objLen = historicalBytes[p] & 0xF;
                    int objTag = ((historicalBytes[p] >> 4) & 0xF) + 0x40;
                    byte[] objData = Util.copyArray(historicalBytes, p+1, objLen);
                    System.out.printf("  TAG: %02X; DATA: %s%n", objTag, Util.hexify(objData));
                    p += objLen + 1;
                }
            } else if (historicalBytes[0] == 0x0) {
                // parse all as COMPACT-TLV except last three bytes
                int limit = historicalBytes.length - 3;
                p = 1;
                while (true) {
                    if (p == limit) {
                        break;
                    }
                    if (p > limit) {
                        throw new ATRParsingException("Incorrect historical bytes structure.");
                    }
                    int objLen = historicalBytes[p] & 0xF;
                    int objTag = ((historicalBytes[p] >> 4) & 0xF) + 0x40;
                    byte[] objData = Util.copyArray(historicalBytes, p+1, objLen);
                    System.out.printf("  TAG: %02X; DATA: %s%n", objTag, Util.hexify(objData));
                    p += objLen + 1;
                }
                System.out.printf("  Status indicator bytes: %s%n", Util.hexify(Util.copyArray(historicalBytes, historicalBytes.length - 3, 3)));
            } else if (historicalBytes[0] == 0x10) {
                // ???
            } else {
                // show as is
                // try to parse according to ISO 7816-10 ?
                 System.out.println("Proprietary historical bytes structure.");
            }

        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            System.out.println("Failed to parse ATR: internal structure is broken");
        } catch (ATRParsingException e) {
            System.out.printf("Failed to parse ATR: %s%n", e.toString());
        }
    }
}
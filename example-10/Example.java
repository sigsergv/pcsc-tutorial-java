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
            var factory = TerminalFactory.getDefault();
            var terminals = factory.terminals().list();

            if (terminals.size() == 0) {
                throw new Util.TerminalNotFoundException();
            }

            // get first terminal
            var terminal = terminals.get(0);

            System.out.printf("Using terminal %s%n", terminal.toString());

            // wait for card, indefinitely until card appears
            terminal.waitForCardPresent(0);

            // establish a connection to the card using autoselected protocol
            var card = terminal.connect("*");
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

            var allInterfaceBytes = new ArrayList<InterfaceBytes>(33);
            int historicalBytesLength = 0;
            var historicalBytes = new byte[0];

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
            for (var tb : allInterfaceBytes) {
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
                var limit = historicalBytes.length;
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
                    var objData = Util.copyArray(historicalBytes, p+1, objLen);
                    printHistoricalBytesValue(objTag, objData);
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
                    var objData = Util.copyArray(historicalBytes, p+1, objLen);
                    printHistoricalBytesValue(objTag, objData);
                    p += objLen + 1;
                }
                System.out.println("  Status indicator bytes:");
                for (String x: getStatusIndicatorBytes(Util.copyArray(historicalBytes, historicalBytes.length - 3, 3))) {
                    System.out.printf("    %s%n", x);
                }
                // System.out.printf("  Status indicator bytes: %s%n", Util.hexify(Util.copyArray(historicalBytes, historicalBytes.length - 3, 3)));
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

    private static void printHistoricalBytesValue(int tag, byte[] value) {
        System.out.printf("  TAG: %02X; DATA: %s%n", tag, Util.hexify(value));

        // print additional details
        byte b;
        String s = "";
        switch (tag) {
        case 0x41:
            System.out.println("    Country code");
            break;
        case 0x42:
            break;
        case 0x43:
            b = value[0];
            System.out.println("    Card service data:");
            System.out.printf("      Application selection by full DF name: %s%n", intToBoolString(b & 0x80));
            System.out.printf("      Application selection by partial DF name: %s%n", intToBoolString(b & 0x40));
            System.out.printf("      BER-TLV data objects in EF.DIR: %s%n", intToBoolString(b & 0x20));
            System.out.printf("      BER-TLV data objects in EF.ATR: %s%n", intToBoolString(b & 0x10));
            switch ((b >> 1) & 0x7) {
            case 0x4:
                s = "by the READ BINARY command (transparent structure)";
                break;
            case 0:
                s = "by the READ RECORD (S) command (record structure)";
                break;
            case 0x2:
                s = "by the GET DATA command (TLV structure)";
                break;
            } 
            System.out.printf("      EF.DIR and EF.ATR access services: %s%n", s);
            if ((b & 1) == 0) {
                System.out.println("      Card with MF");
            } else {
                System.out.println("      Card without MF");
            }
            break;
        case 0x44:
            System.out.println("    Initial access data");
            break;
        case 0x45:
            System.out.println("    Card issuer's data");
            break;
        case 0x46:
            System.out.println("    Pre-issuing data");
            break;
        case 0x47:
            System.out.println("    Card capabilities");
            for (String x: getCapabilities(value)) {
                System.out.printf("      %s%n", x);
            }
            break;
        case 0x48:
            System.out.println("    Status information:");
            for (String x: getStatusIndicatorBytes(value)) {
                System.out.printf("      %s%n", x);
            }
            break;
        case 0x4D:
            System.out.println("    Extended header list");
            break;
        case 0x4F:
            System.out.println("    Application identifier");
            break;
        }
    }

    private static String intToBoolString(int i) {
        if (i == 0) {
            return "no";
        } else {
            return "yes";
        }
    }

    private static String[] getStatusIndicatorBytes(byte[] value) {
        ArrayList<String> items = new ArrayList<String>(2);

        if (value.length == 1 || value.length == 3) {
            byte b = value[0];
            String s = "";
            if (b == 0) {
                s = "No information given";
            } else if (b == 1) {
                s = "Creation state";
            } else if (b == 3) {
                s = "Initialisation state";
            } else if ((b & 0x5) == 0x5) {
                s = "Operational state (activated)";
            } else if ((b & 0x5) == 0x4) {
                s = "Operational state (deactivated)";
            } else if ((b & 0xC) == 0xC) {
                s = "Termination state";
            } else if ((b & 0xF0) != 0) {
                s = "Proprietary";
            }
            items.add(String.format("LCS (life cycle status): %s", s));
        }
        if (value.length == 2 || value.length == 3) {
            items.add(String.format("Status word: %s", Util.hexify(Util.copyArray(value, value.length-2, 2))));
        }
        return items.toArray(new String[0]);
    }

    private static String[] getCapabilities(byte[] value) {
        var items = new ArrayList<String>(5);
        String s;
        byte b;

        if (value.length >= 1) {
            b = value[0];
            ArrayList<String> sub = new ArrayList<String>(5);
            if ((b & 0x80) != 0) {
                sub.add("by full DF name");
            }
            if ((b & 0x40) != 0) {
                sub.add("by partial DF name");
            }
            if ((b & 0x20) != 0) {
                sub.add("by path");
            }
            if ((b & 0x10) != 0) {
                sub.add("by file identifier");
            }
            if ((b & 0x8) != 0) {
                sub.add("Implicit DF selection");
            }
            s = String.format(s = "DF selection: %s", String.join(", ", sub));
            items.add(s);

            items.add(String.format("Short EF identifier supported: %s", intToBoolString(b & 0x4)));
            items.add(String.format("Record number supported: %s", intToBoolString(b & 0x2)));
            items.add(String.format("Record identifier supported: %s", intToBoolString(b & 0x1)));
        }

        if (value.length >= 2) {
            b = value[1];
            items.add(String.format("EFs of TLV structure supported: %s", intToBoolString(b & 0x80)));

            s = "Behaviour of write functions: ";
            switch ((b >> 5) & 0x3) {
            case 0:
                s += "One-time write";
                break;
            case 1:
                s += "Proprietary";
                break;
            case 2:
                s += "Write OR";
                break;
            case 3:
                s += "Write AND";
                break;
            }
            items.add(s);

            s = "Value 'FF' for the first byte of BER-TLV tag fields: ";
            if ((b & 0x10) == 0x10) {
                s += "valid";
            } else {
                s += "invalid";
            }
            items.add(s);

            b = (byte)(b & 0xF);
            items.add(String.format("Data unit size in quartets: %d", b));
        }

        if (value.length >= 3) {
            b = value[2];
            items.add(String.format("Commands chaining: %s", intToBoolString(b & 0x80)));
            items.add(String.format("Extended Lc and Le fields: %s", intToBoolString(b & 0x40)));

            s = "Logical channel number assignment: ";

            switch ((b >> 3) & 0x3) {
            case 0x0:
                s += "No logical channel";
                break;
            case 0x2:
                s += "by the card";
                break;
            case 0x3:
                s += "by the interface device";
                break;
            }
            items.add(s);

            s = String.format("Maximum number of logical channels: %d", 
                4*((b>>2)&1) + 2*((b>>1)&1) + (b&1) + 1);
            items.add(s);
        }
        return items.toArray(new String[0]);
    }


}
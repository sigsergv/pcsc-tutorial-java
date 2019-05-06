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
            if (sw == 0x9000) {
                // take AID from data
                byte[] PSEData = Util.responseDataOnly(answer.getBytes());
                System.out.printf("PSE: %s%n", Util.hexify(PSEData));
            } else if (sw == 0x6A82) {
                // guess AID
            } else {
                throw new Util.CardOperationFailedException(String.format("PSE retrieval failed: 0x%04X", answer.getSW()));
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
}
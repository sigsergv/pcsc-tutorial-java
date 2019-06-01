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

            // obtain logical channel
            var channel = card.getBasicChannel();

            System.out.println("Type quit or exit to stop the program.");

            // start infinite loop
            while (true) {
                System.out.print("C-APDU> ");
                var rawAPDU = System.console().readLine();

                if (rawAPDU.equals("quit") || rawAPDU.equals("exit")) {
                    break;
                }

                byte[] apduBytes = null;
                try {
                    apduBytes = Util.toByteArray(rawAPDU);
                } catch (Util.ByteStringParseException e) {
                    System.out.printf("ERROR: Incorrect input string%n");
                }

                if (apduBytes == null) {
                    continue;
                }

                if (apduBytes.length < 4) {
                    System.out.printf("ERROR: apdu must be at least 4 bytes long%n");
                    continue;
                }

                System.out.printf(">>> %s%n", Util.hexify(apduBytes));
                var apdu = new CommandAPDU(apduBytes);

                try {
                    var answer = channel.transmit(apdu);
                    System.out.printf("<<< %s%n", Util.hexify(answer.getBytes()));
                } catch (CardException e) {
                    System.out.printf("CARD EXCEPTION: %s%n", e.toString());
                    continue;
                }
            }

            // disconnect card
            card.disconnect(false);

        } catch (Util.TerminalNotFoundException e) {
            System.out.println("No connected terminals.");
        } catch (CardException e) {
            System.out.println("CardException: " + e.toString());
        }
    }
}
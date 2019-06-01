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


import javax.smartcardio.*;
import java.util.ArrayList;


class Example {
    public static class TerminalNotFoundException extends Exception {}

    public static void main(String[] args) {
        try {
            var factory = TerminalFactory.getDefault();
            var  terminals = factory.terminals().list();

            if (terminals.size() == 0) {
                throw new TerminalNotFoundException();
            }

            // get first terminal
            var terminal = terminals.get(0);

            System.out.printf("Using terminal %s%n", terminal.toString());

            // wait for card, indefinitely until card appears
            terminal.waitForCardPresent(0);

            // establish a connection to the card using any available protocol ("*")
            var card = terminal.connect("*");

            // read protocol and card ATR
            System.out.printf("  Card protocol: %s%n", card.getProtocol());
            System.out.printf("  Card ATR: %s%n", hexify(card.getATR().getBytes()));

            // disconnect card
            card.disconnect(true);

        } catch (TerminalNotFoundException e) {
            System.out.println("No connected terminals.");
        } catch (CardException e) {
            System.out.println("CardException: " + e.toString());
        }
    }

    public static String hexify(byte[] bytes) {
        var bytesStrings = new ArrayList<String>(bytes.length);
        for (var b : bytes) {
            bytesStrings.add(String.format("%02X", b));
        }
        return String.join(" ", bytesStrings);
    }
}
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

// This class Issues a new card:
//   * scan provided card
//   * if it matches requirements then initialize with a new keys and data
//   * if it doesn't then print error message explaining why
//   * quit
class IssueCard {

    public static void main(String[] args) {
        // load project configuration data
        Util.Config config = Util.loadConfig();

        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();

            if (terminals.size() == 0) {
                throw new Util.TerminalNotFoundException();
            }

            CardTerminal terminal = terminals.get(0);

            System.out.printf("Issue a new Balance Card%n========================%n");

            if (!terminal.isCardPresent()) {
                System.out.println("Please place a new non-initialized card on the terminal.");
            }
            terminal.waitForCardPresent(0);

            System.out.printf("Checking card sector %d... ", config.sector);
            Card card = terminal.connect("T=1");
            CardChannel channel = card.getBasicChannel();

            byte[] authenticateCommand = Util.toByteArray("FF 86 00 00 05 01 00 00 00 00");
            byte[] readBinaryCommand = Util.toByteArray("FF B0 00 00 10");
            byte[] updateBinaryCommand = Util.toByteArray("FF D6 00 00 10 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
            byte firstBlock = (byte)(config.sector * 4);
            byte[] data;
            byte[] command;

            ResponseAPDU answer;

            // load Key A to cell 00
            byte[] loadKeysCommand = Util.toByteArray("FF 82 00 00 06 " + config.initial_key_a);
            answer = channel.transmit(new CommandAPDU(loadKeysCommand));
            if (answer.getSW() != 0x9000) {
                card.disconnect(false);
                throw new Util.CardCheckFailedException("Failed to load Key A into terminal.");
            }

            // check Key A only: authenticate for the trailer block
            authenticateCommand[7] = (byte)(firstBlock+3);
            authenticateCommand[8] = 0x60;
            answer = channel.transmit(new CommandAPDU(authenticateCommand));
            if (answer.getSW() != 0x9000) {
                card.disconnect(false);
                throw new Util.CardCheckFailedException("Key A doesn't match.");
            }

            // read trailer block data
            readBinaryCommand[3] = (byte)(firstBlock+3);
            answer = channel.transmit(new CommandAPDU(readBinaryCommand));
            if (answer.getSW() != 0x9000) {
                card.disconnect(false);
                throw new Util.CardCheckFailedException("Failed to read block with Key A.");
            }
            // read trailer and check that we should be able to set both keys and change access 
            readBinaryCommand[3] = (byte)(firstBlock+3);
            answer = channel.transmit(new CommandAPDU(readBinaryCommand));
            if (answer.getSW() != 0x9000) {
                card.disconnect(false);
                throw new Util.CardCheckFailedException("Failed to read trailer with Key A.");
            }
            byte[] trailerData = answer.getData();
            String[] accessBits = Util.decodeAccessBits(trailerData[6], trailerData[7], trailerData[8]);
            if (!accessBits[3].equals("001")) {
                card.disconnect(false);
                throw new Util.CardCheckFailedException("Access condition bits don't match.");
            }
            System.out.printf("success%n");

            // write data
            System.out.printf("Writing data... ");
            // store empty balance to first block
            data = new byte[16];

            // create APDU by cloning updateBinaryCommand template, specify target
            // block address and copy data block
            command = updateBinaryCommand.clone();
            command[3] = firstBlock;
            for (int i=0; i<16; i++) {
                command[5+i] = data[i];
            }
            answer = channel.transmit(new CommandAPDU(command));
            if (answer.getSW() != 0x9000) {
                card.disconnect(false);
                throw new Util.CardUpdateFailedException("Failed to update data block.");
            }

            // create trailer data
            data = new byte[16];
            // fill with new Key A and Key B
            byte[] prodKeyA = Util.toByteArray(config.prod_key_a);
            byte[] prodKeyB = Util.toByteArray(config.prod_key_b);
            for (int i=0; i<6; i++) {
                data[i] = prodKeyA[i];
                data[10+i] = prodKeyB[i];
            }
            // force set user byte to 0xFF
            data[9] = (byte)0xFF;

            // calculate access conditions bytes
            String[] accessConditionBits = {"000", "111", "111", "001"};
            byte[] ac = Util.encodeAccessBits(accessConditionBits);
            data[6] = ac[0];
            data[7] = ac[1];
            data[8] = ac[2];

            // create APDU by cloning updateBinaryCommand template, specify target
            // block address (trailer) and copy data block
            command = updateBinaryCommand.clone();
            command[3] = (byte)(firstBlock+3);
            for (int i=0; i<16; i++) {
                command[5+i] = data[i];
            }
            answer = channel.transmit(new CommandAPDU(command));
            if (answer.getSW() != 0x9000) {
                card.disconnect(false);
                throw new Util.CardUpdateFailedException("Failed to update data block.");
            }
            System.out.printf("success%n");

            card.disconnect(false);
        } catch (Util.TerminalNotFoundException e) {
            System.out.println("No connected terminals.");
            System.exit(2);
        } catch (Util.CardCheckFailedException e) {
            System.out.printf("failed%n");
            System.out.printf("Error: %s%n", e.getMessage());
        } catch (Util.CardUpdateFailedException e) {
            System.out.printf("failed%n");
            System.out.printf("Error: %s%n", e.getMessage());
        } catch (CardException e) {
            System.out.println("CardException: " + e.toString());
            System.exit(2);
        }
    }
}
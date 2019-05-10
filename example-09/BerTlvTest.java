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


class BerTlvTest {
    public static void main(String[] args) {
        // testing method

        byte[] data;

        System.out.println("--------------------");
        System.out.println("Test 1");
        data = Util.toByteArray("6F");
        try {
            BerTlv d = BerTlv.parseBytes(data);
            System.out.println("FAILED");
        } catch (BerTlv.ParsingException e) {
            System.out.printf("PASSED: Parse failed: %s%n", e.getMessage());
        }

        System.out.println("--------------------");
        System.out.println("Test 2");
        data = Util.toByteArray("9F 38 01 91");
        try {
            BerTlv d = BerTlv.parseBytes(data);
            System.out.println(d);
            System.out.println("PASSED");
        } catch (BerTlv.ParsingException e) {
            System.out.printf("Parse failed: %s%n", e.getMessage());
        }

        System.out.println("--------------------");
        System.out.println("Test 3");
        data = Util.toByteArray("9F B8 D3 71 01 59");
        try {
            BerTlv d = BerTlv.parseBytes(data);
            System.out.println(d);
            System.out.println("PASSED");
        } catch (BerTlv.ParsingException e) {
            System.out.printf("Parse failed: %s%n", e.getMessage());
        }

        System.out.println("--------------------");
        System.out.println("Test 4");
        data = Util.toByteArray("6F 28 84 0E 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31 A5 16 88 01 01 5F 2D 08 72 75 65 6E 66 72 64 65 BF 0C 05 9F 4D 02 0B 0A");
        try {
            BerTlv d = BerTlv.parseBytes(data);
            System.out.println(d);
            System.out.println("PASSED");
        } catch (BerTlv.ParsingException e) {
            System.out.printf("Parse failed: %s%n", e.getMessage());
        }

        System.out.println("--------------------");
        System.out.println("Test 5");
        data = Util.toByteArray("70 81 93 90 81 90 1D 6B AA 1D 94 B6 2B 5E 88 A9 22 49 BF B5 3C CA A8 30 6E 99 6C C9 F1 DB BB E3 1B EE 04 70 37 00 FC 44 A8 B6 B3 57 45 51 F9 E6 A0 A3 1F 9A D6 DC EB F5 A6 17 4A 65 7F 2C 43 F7 CE 44 7C D5 71 82 8D 0D 14 E1 F9 5C 09 E9 29 93 3F C7 2B DD F7 8C 6E BD 18 1F 49 6D EF 78 C9 E4 88 BF 62 F7 3F B2 28 CE 4A 9F 28 F6 13 1E BA 0D 40 B9 77 35 58 A5 EE 13 BB 3B 76 0D B6 31 52 68 4E 8D CC B8 9D 06 80 2F 0E 1A BC 23 22 B5 FA 51 C6 07 96 40 F4 A4");
        try {
            BerTlv d = BerTlv.parseBytes(data);
            System.out.println(d);
            System.out.println("PASSED");
        } catch (BerTlv.ParsingException e) {
            System.out.printf("Parse failed: %s%n", e.getMessage());
        }

        // System.out.println("Test 5");
        // // 0xE329=58153 bytes
        // data = Util.toByteArray("50 82 E3 29 00 00");
        // try {
        //     BerTlv d = BerTlv.parseBytes(data);
        //     System.out.println("PASSED");
        // } catch (BerTlv.ParsingException e) {
        //     System.out.printf("Parse failed: %s%n", e.getMessage());
        // }
    }

}
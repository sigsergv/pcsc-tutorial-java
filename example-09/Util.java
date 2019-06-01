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

import static java.util.Arrays.copyOfRange;
import static java.lang.Math.max;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

// local utility class
class Util {
    public static class TerminalNotFoundException extends Exception {}

    static class CardOperationFailedException extends Exception{
        public CardOperationFailedException(String message) {
            super(message);
        }
    }
    public static String hexify(byte[] bytes) {
        var bytesStrings = new ArrayList<String>(bytes.length);
        for (var b : bytes) {
            bytesStrings.add(String.format("%02X", b));
        }
        return String.join(" ", bytesStrings);
    }

    public static byte[] toByteArray(String s) {
        var len = s.length();
        var buf = new byte[len/2];
        int bufLen = 0;
        int i = 0;
        
        while (i < len) {
            var c1 = s.charAt(i);
            i++;
            if (c1 == ' ') {
                continue;
            }
            var c2 = s.charAt(i);
            i++;

            byte d = (byte)((Character.digit(c1, 16) << 4) + (Character.digit(c2, 16)));
            buf[bufLen] = d;
            ++bufLen;
        }

        return copyOfRange(buf, 0, bufLen);
    }

    public static String bytesToString(byte[] bytes) {
        try {
            return new String(bytes, "ISO-8859-1");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    // // convert HCD to list of integers
    // // see EMV_4.3, section "4.3 Data Element Format Conventions"
    // public static int hcdBytesToInt() {
    // }

    // Convert HCD (Hex Coded Decimal) byte to Decimal
    public static int hcdByteToInt(byte b) {
        int lb = b & 0xF;
        int hb = (b >> 4) & 0xF;
        return hb * 10 + lb;
    }

    // convert YYMMDD BCD to YYYY-MM-DD
    public static String bytesToDate(byte[] bytes) {
        String res = "";
        return String.format("%04d-%02d-%02d", 2000+hcdByteToInt(bytes[0]), hcdByteToInt(bytes[1]), hcdByteToInt(bytes[2]));
    }

    public static byte[] concatArrays(byte[] a, byte[] b) {
        var buffer = new byte[a.length + b.length];
        System.arraycopy(a, 0, buffer, 0, a.length);
        System.arraycopy(b, 0, buffer, a.length, b.length);
        return buffer;
    }

    public static byte[] copyArray(byte[] buffer, int from, int length) {
        var res = new byte[length];
        System.arraycopy(buffer, from, res, 0, length);
        return res;
    }

    public static HashMap<String, String> mapDataObjects(List<BerTlv> objects) {
        var res = new HashMap<String, String>();

        for (var b : objects) {
            var tagBytes = b.getTag();
            var tagString = hexify(tagBytes);

            // convert tag to int value
            int tag = 0;
            for (int i=0; i<tagBytes.length; i++) {
                int x = tagBytes[i];
                if (x < 0) {
                    x += 256;
                }
                tag = tag*256 + x;
            }

            String name = null;
            var value = b.getValue();
            String displayValue = Util.hexify(value);

            switch (tag) {
            case 0x56:
                name = "Track 1 Data";
                break;
            case 0x57:
                name = "Track 2 Equivalent Data";
                break;
            case 0x5A:
                name = "Application Primary Account Number (PAN)";
                break;
            case 0x5F20:
                name = "Cardholder Name";
                displayValue = bytesToString(value);
                break;
            case 0x5F24:
                name = "Application Expiration Date";
                displayValue = bytesToDate(value);
                break;
            case 0x5F25:
                name = "Application Effective Date";
                displayValue = bytesToDate(value);
                break;
            case 0x5F28:
                name = "Issuer Country Code";
                // displayValue = bytesToString(value);
                break;
            case 0x5F30:
                name = "Service Code";
                break;
            case 0x5F34:
                name = "Application Primary Account Number (PAN) Sequence Number";
                break;
            case 0x8C:
                name = "Card Risk Management Data Object List 1 (CDOL1)";
                break;
            case 0x8D:
                name = "Card Risk Management Data Object List 2 (CDOL2)";
                break;
            case 0x8E:
                name = "Cardholder Verification Method (CVM) List";
                break;
            case 0x8F:
                name = "Certification Authority Public Key Index";
                break;
            case 0x90:
                name = "Issuer Public Key Certificate";
                break;
            case 0x92:
                name = "Issuer Public Key Remainder";
                break;
            case 0x93:
                name = "Signed Static Application Data";
                break;
            case 0x9F07:
                name = "Application Usage Control";
                break;
            case 0x9F08:
                name = "Application Version Number";
                break;
            case 0x9F0D:
                name = "Issuer Action Code - Default";
                break;
            case 0x9F0E:
                name = "Issuer Action Code - Denial";
                break;
            case 0x9F0F:
                name = "Issuer Action Code - Online";
                break;
            case 0x9F1F:
                name = "Track 1 Discretionary Data";
                break;
            case 0x9F32:
                name = "Issuer Public Key Exponent";
                break;
            case 0x9F42:
                name = "Application Currency Code";
                break;
            case 0x9F44:
                name = "Application Currency Exponent";
                break;
            case 0x9F46:
                name = "ICC Public Key Certificate";
                break;
            case 0x9F47:
                name = "ICC Public Key Exponent";
                break;
            case 0x9F48:
                name = "ICC Public Key Remainder";
                break;
            case 0x9F49:
                name = "Dynamic Data Authentication Data Object List (DDOL)";
                break;
            case 0x9F4A:
                name = "Static Data Authentication Tag List";
                break;
            case 0x9F62:
                name = "PCVC3 (Track1)";
                break;
            case 0x9F63:
                name = "PUNATC (Track1)";
                break;
            case 0x9F64:
                name = "NATC (Track1)";
                break;
            case 0x9F65:
                name = "PCVC3 (Track2)";
                break;
            case 0x9F66:
                name = "Terminal Transaction Qualifiers (TTQ)";
                break;
            case 0x9F67:
                name = "NATC (Track2)";
                break;
            case 0x9F68:
                name = "Card Additional Processes";
                break;
            case 0x9F6B:
                name = "Track 2 Data/Card CVM Limit";
                break;
            case 0x9F6C:
                name = "Card Transaction Qualifiers (CTQ)";
                break;
            default:
                name = hexify(tagBytes);
            }

            res.put(tagString, String.format("%s: %s", name, displayValue));
        }

        return res;
    }

}
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


class BerTlv {
    public enum Encoding {
        PRIMITIVE,
        CONSTRUCTED
    }

    public enum Class {
        UNIVERSAL,
        APPLICATION,
        PRIVATE,
        CONTEXT_SPECIFIC
    }

    public static class ParsingException extends Exception {
        public ParsingException(String message) {
            super(message);
        }
    }

    private final byte[] tag;
    private final Encoding tagEncoding;
    private final Class tagClass;
    // private final int length;

    // primitive value
    private byte[] value;

    // constructed value parts
    private final List<BerTlv> parts;


    // constructed value constructor
    public BerTlv(byte[] tag, List<BerTlv> parts)
        throws ParsingException
    {
        this.tag = tag;
        this.parts = parts;
        this.value = null;

        this.tagEncoding = Encoding.CONSTRUCTED;
        this.tagClass = getClassFromTag(tag);
        if (getEncodingFromTag(tag) != this.tagEncoding) {
            throw new ParsingException("Incorrect tag encoding");
        }
    }


    // primitive value constructor
    public BerTlv(byte[] tag, byte[] value)
        throws ParsingException
    {
        this.tag = tag;
        this.parts = null;
        this.value = value;

        this.tagEncoding = Encoding.PRIMITIVE;
        this.tagClass = getClassFromTag(tag);
        if (getEncodingFromTag(tag) != this.tagEncoding) {
            throw new ParsingException("Incorrect tag encoding");
        }
    }

    /**
     * Parse bytes into ONE BerTlv object ignoring remaining data if there are any.
     * @param  bytes            [description]
     * @return                  [description]
     * @throws ParsingException [description]
     */
    public static BerTlv parseBytes(byte[] bytes)
        throws ParsingException
    {
        Pair p = parseChunk(bytes);
        return p.value;
    }

    private static Pair parseChunk(byte[] bytes)
        throws ParsingException
    {
        // "bytes" MUST BE at least 2 bytes length
        if (bytes.length < 2) {
            throw new ParsingException("Bytes array is too short");
        }

        try {
            int p = 0;

            // extract tag bytes
            byte[] tagBytes;
            int v = bytes[0] & 0x1F;
            if (v == 0x1F) {
                // xxx1 1111, i.e. tag continues in later bytes
                while (true) {
                    p++;
                    if (((bytes[p] >> 7) & 1) == 0) {
                        break;
                    }
                }
            }
            tagBytes = copy(bytes, 0, p+1);

            // extract length bytes and length
            p++;
            byte[] lengthBytes = new byte[4];
            int lengthBytesLen = 1;

            v = (bytes[p] >> 7) & 1;
            if (v == 0) {
                lengthBytes[0] = (byte)(bytes[p] & 0x7F);
            } else {
                lengthBytesLen = bytes[p] & 0x7F;
                if (lengthBytesLen > 4) {
                    throw new ParsingException("Length value is too large");
                }
                for (int i=0; i<lengthBytesLen; i++) {
                    lengthBytes[i] = bytes[p+i+1];
                }
            }
            int length = 0;
            for (int i=0; i<lengthBytesLen; i++) {
                int x = lengthBytes[i];
                if (x < 0) {
                    x += 256;
                }
                length = length*256 + x;
            }
            p += lengthBytesLen;

            BerTlv t = null;
            if (((bytes[0] >> 5) & 1) == 1) {
                // CONSTRUCTED
                // parse chunks of data block until it depletes
                ArrayList<BerTlv> parts = new ArrayList<BerTlv>(5);
                byte[] remains = copy(bytes, p, length);
                while (true) {
                    Pair chunk = parseChunk(remains);
                    parts.add(chunk.value);
                    if (remains.length == chunk.size) {
                        break;
                    }
                    remains = copy(remains, chunk.size, remains.length-chunk.size);
                }

                t = new BerTlv(tagBytes, parts);
            } else {
                // PRIMITIVE
                t = new BerTlv(tagBytes, copy(bytes, p, length));
            }
            Pair pair = new Pair(p+length, t);
            return pair;

        } catch (ArrayIndexOutOfBoundsException e) {
            throw new ParsingException("Premature end of bytes");
        }
    }


    public String toString() {
        String s;

        if (tagEncoding == Encoding.PRIMITIVE) {
            s = String.format("{tag: %s enc: %s value: %s}", 
                Util.hexify(tag), 
                tagEncoding,
                Util.hexify(value));
        } else {
            s = String.format("{tag: %s enc: %s parts: %d}", 
                Util.hexify(tag), 
                tagEncoding,
                parts.size());
        }
        return s;
    }

    public static void main(String[] args) {
        // testing method

        byte[] data;

        System.out.println("Test 1");
        data = Util.toByteArray("6F");
        try {
            BerTlv d = parseBytes(data);
            System.out.println("FAILED");
        } catch (ParsingException e) {
            System.out.printf("PASSED: Parse failed: %s%n", e.getMessage());
        }

        System.out.println("Test 2");
        data = Util.toByteArray("9F 38 01 91");
        try {
            BerTlv d = parseBytes(data);
            System.out.printf("PASSED, d=%s%n", d);
        } catch (ParsingException e) {
            System.out.printf("Parse failed: %s%n", e.getMessage());
        }

        System.out.println("Test 3");
        data = Util.toByteArray("9F B8 D3 71 01 59");
        try {
            BerTlv d = parseBytes(data);
            System.out.printf("PASSED, d=%s%n", d);
        } catch (ParsingException e) {
            System.out.printf("Parse failed: %s%n", e.getMessage());
        }

        System.out.println("Test 4");
        data = Util.toByteArray("6F 28 84 0E 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31 A5 16 88 01 01 5F 2D 08 72 75 65 6E 66 72 64 65 BF 0C 05 9F 4D 02 0B 0A");
        try {
            BerTlv d = parseBytes(data);
            System.out.printf("PASSED, d=%s%n", d);
        } catch (ParsingException e) {
            System.out.printf("Parse failed: %s%n", e.getMessage());
        }

        // System.out.println("Test 5");
        // // 0xE329=58153 bytes
        // data = Util.toByteArray("50 82 E3 29 00 00");
        // try {
        //     BerTlv d = parseBytes(data);
        //     System.out.println("PASSED");
        // } catch (ParsingException e) {
        //     System.out.printf("Parse failed: %s%n", e.getMessage());
        // }

    }

    private static class Pair {
        public BerTlv value;
        public int size;
        public Pair(int size, BerTlv value) {
            this.size = size;
            this.value = value;
        }
    }

    private static byte[] copy(byte[] buffer, int from, int length) {
        byte[] res = new byte[length];
        System.arraycopy(buffer, from, res, 0, length);
        return res;
    }

    private static Encoding getEncodingFromTag(byte[] tag) {
        if ((tag[0] >> 5 & 1) == 1) {
            return Encoding.CONSTRUCTED;
        } else {
            return Encoding.PRIMITIVE;
        }
    }

    private static Class getClassFromTag(byte[] tag) {

        switch ((tag[0] >> 6) & 3) {
            case 0: return Class.UNIVERSAL;
            case 1: return Class.APPLICATION;
            case 2: return Class.PRIVATE;
            default: return Class.CONTEXT_SPECIFIC;
        }
    }

}
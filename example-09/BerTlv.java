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

    public static class ConstraintException extends Exception {
        public ConstraintException(String message) {
            super(message);
        }
    }

    private final byte[] tag;
    private final Encoding tagEncoding;
    private final Class tagClass;

    // primitive value
    private final byte[] value;

    // constructed value parts
    private final List<BerTlv> parts;


    // constructed value constructor
    public BerTlv(byte[] tag, List<BerTlv> parts)
        throws ConstraintException
    {
        this.tag = tag;
        this.parts = parts;
        this.value = null;

        this.tagEncoding = Encoding.CONSTRUCTED;
        this.tagClass = getClassFromTag(tag);
        if (getEncodingFromTag(tag) != this.tagEncoding) {
            throw new ConstraintException("Incorrect tag encoding");
        }
    }


    // primitive value constructor
    public BerTlv(byte[] tag, byte[] value)
        throws ConstraintException
    {
        this.tag = tag;
        this.parts = null;
        this.value = value;

        this.tagEncoding = Encoding.PRIMITIVE;
        this.tagClass = getClassFromTag(tag);
        if (getEncodingFromTag(tag) != this.tagEncoding) {
            throw new ConstraintException("Incorrect tag encoding");
        }
    }

    public byte[] getTag() {
        return tag;
    }

    public byte[] getValue()
        throws ConstraintException
    {
        if (this.tagEncoding == Encoding.CONSTRUCTED) {
            throw new ConstraintException("Incorrect tag encoding for getValue method");
        }
        return value;
    }


    /**
     * Add new BerTlv part to constructed
     * 
     * @param  part [description]
     * @return      [description]
     */
    public BerTlv addPart(BerTlv part)
        throws ConstraintException
    {
        return this;
    }


    /**
     * Parse bytes into ONE BerTlv object ignoring remaining data if there are any.
     * 
     * @param  bytes            bytes array to parse
     * @return                  parsed BerTlv object, remaining bytes are ignored
     * @throws ParsingException 
     */
    public static BerTlv parseBytes(byte[] bytes)
        throws ParsingException
    {
        Pair p = parseChunk(bytes);
        return p.value;
    }

    /**
     * Get first part tagged with tag that has binary representation tagBytesRepr
     * @param  tagBytesRepr        [description]
     * @return                     [description]
     * @throws ConstraintException [description]
     */
    public BerTlv getPart(String tagBytesRepr)
        throws ConstraintException
    {
        return getPart(Util.toByteArray(tagBytesRepr));
    }

    /**
     * Get first part tagged with tag tag.
     * @param  tag                 [description]
     * @return                     [description]
     * @throws ConstraintException [description]
     */
    public BerTlv getPart(byte[] tag)
        throws ConstraintException
    {
        if (this.tagEncoding != Encoding.CONSTRUCTED) {
            throw new ConstraintException("Only CONSTRUCTED objects have parts.");
        }
        BerTlv part = null;
        for (BerTlv p : parts) {
            if (java.util.Arrays.equals(p.getTag(), tag)) {
                part = p;
                break;
            }
        }
        return part;
    }


    /**
     * Get all parts
     * @return [description]
     */
    public BerTlv[] getParts()
        throws ConstraintException
    {
        if (this.tagEncoding != Encoding.CONSTRUCTED) {
            throw new ConstraintException("Only CONSTRUCTED objects have parts.");
        }
        BerTlv[] res = new BerTlv[parts.size()];
        return parts.toArray(res);
    }


    /**
     * Parse one chunk of continuous data.
     * 
     * @param  bytes            bytes array to parse
     * @return                  Pair structure that contains size of processed data and resulting BerTlv object
     * @throws ParsingException
     */
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
                    // we use this code because all Java types are signed
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

        } catch (ConstraintException e) {
            throw new ParsingException("Inconsistent data");
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new ParsingException("Premature end of bytes");
        }
    }

    /**
     * Recursively prints annotated object content.
     * 
     * @return [description]
     */
    public String toString() {
        String s;

        if (tagEncoding == Encoding.PRIMITIVE) {
            s = String.format("TAG:   %s(PRIMITIVE)%nVALUE: %s", 
                Util.hexify(tag), 
                Util.hexify(value));
        } else {
            // get representations of parts and indent them
            ArrayList<String> partStrings = new ArrayList<String>(parts.size());
            for (BerTlv p : parts) {
                partStrings.add(p.toString().replaceAll("(?m)^", "  "));
            }
            String partStringsJoined = String.join("\n", partStrings);

            s = String.format("TAG:   %s(CONSTRUCTED)%n%s", 
                Util.hexify(tag), 
                partStringsJoined);
        }
        return s;
    }

    /**
     * We use this class internally to return both BerTlv object and consumed bytes array size.
     */
    private static class Pair {
        public final BerTlv value;
        public final int size;
        public Pair(int size, BerTlv value) {
            this.size = size;
            this.value = value;
        }
    }

    /**
     * Local method to copy subarray into a new array.
     * 
     * @param  buffer [description]
     * @param  from   [description]
     * @param  length [description]
     * @return        [description]
     */
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
package com.dn15.websocket.util;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocketapi.CloseReason.CloseCodes;

public class CharsetStringConverter {

    public static CodingErrorAction codingErrorAction = CodingErrorAction.REPORT;

    /*
     * @return UTF-8 encoding in bytes
     */
    public static byte[] utf8Bytes(String s) {
        try {
            return s.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * @return ASCII encoding in bytes
     */
    public static byte[] asciiBytes(String s) {
        try {
            return s.getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String stringAscii(byte[] bytes) {
        return stringAscii(bytes, 0, bytes.length);
    }

    public static String stringAscii(byte[] bytes, int offset, int length) {
        try {
            return new String(bytes, offset, length, "ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String stringUtf8(byte[] bytes) throws WsIOException {
        return stringUtf8(ByteBuffer.wrap(bytes));
    }

    public static String stringUtf8(ByteBuffer bytes) throws WsIOException {
        CharsetDecoder decode = Charset.forName("UTF8").newDecoder();
        decode.onMalformedInput(codingErrorAction);
        decode.onUnmappableCharacter(codingErrorAction);
        // decode.replaceWith( "X" );
        String s;
        try {
            bytes.mark();
            s = decode.decode(bytes).toString();
            bytes.reset();
        } catch (CharacterCodingException e) {
            throw new WsIOException(CloseCodes.NOT_CONSISTENT, e.getMessage());
        }
        return s;
    }

    public static void main(String[] args) throws WsIOException {
        stringUtf8(utf8Bytes("\0"));
        stringAscii(asciiBytes("\0"));
    }

}

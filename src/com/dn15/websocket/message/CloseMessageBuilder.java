package com.dn15.websocket.message;

import java.nio.ByteBuffer;

import com.dn15.websocket.exception.InvalidFrameException;
import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocket.util.CharsetStringConverter;
import com.dn15.websocketapi.CloseReason.CloseCode;
import com.dn15.websocketapi.CloseReason.CloseCodes;

public class CloseMessageBuilder extends MessageImpl implements CloseMessage {
    static final ByteBuffer emptybytebuffer = ByteBuffer.allocate(0);

    private CloseCode code;
    private String reasonPhrase;

    public CloseMessageBuilder() {
        super(Opcode.CLOSING);
        setFin(true);
    }

    public CloseMessageBuilder(CloseCode code) throws WsIOException {
        super(Opcode.CLOSING);
        setFin(true);
        setCodeAndMessage(code, "");
    }

    public CloseMessageBuilder(CloseCode code, String m) throws WsIOException {
        super(Opcode.CLOSING);
        setFin(true);
        setCodeAndMessage(code, m);
    }

    private void setCodeAndMessage(CloseCode code, String m) throws WsIOException {
        if (m == null) {
            m = "";
        }
        // CloseFrame.TLS_ERROR is not allowed to be transfered over the wire
        if (code == CloseCodes.TLS_HANDSHAKE_FAILURE) {
            code = CloseCodes.NO_STATUS_CODE;
            m = "";
        }
        if (code == CloseCodes.NO_STATUS_CODE) {
            if (0 < m.length()) {
                throw new InvalidFrameException("A close frame must have a closecode if it has a reason");
            }
            return;// empty payload
        }

        byte[] by = CharsetStringConverter.utf8Bytes(m);
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(code.getCode());
        buf.position(2);
        ByteBuffer pay = ByteBuffer.allocate(2 + by.length);
        pay.put(buf);
        pay.put(by);
        pay.rewind();
        setPayload(pay);
    }

    private void initCloseCode() throws WsIOException {
        code = CloseCodes.NO_STATUS_CODE;
        ByteBuffer payload = super.getPayloadData();
        payload.mark();
        if (payload.remaining() >= 2) {
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.position(2);
            bb.putShort(payload.getShort());
            bb.position(0);
            code = CloseCodes.getCloseCode(bb.getInt());

            if (code == CloseCodes.CLOSED_ABNORMALLY || code == CloseCodes.TLS_HANDSHAKE_FAILURE || code == CloseCodes.NO_STATUS_CODE
 || code.getCode() > 4999 || code.getCode() < 1000
                    || code.getCode() == 1004) {
                throw new InvalidFrameException("closecode must not be sent over the wire: " + code);
            }
        }
        payload.reset();
    }

    @Override
    public CloseCode getCloseCode() {
        return code;
    }

    private void initMessage() throws WsIOException {
        if (code == CloseCodes.NO_STATUS_CODE) {
            reasonPhrase = CharsetStringConverter.stringUtf8(super.getPayloadData());
        } else {
            ByteBuffer b = super.getPayloadData();
            int mark = b.position();// because stringUtf8 also creates a mark
            try {
                b.position(b.position() + 2);
                reasonPhrase = CharsetStringConverter.stringUtf8(b);
            } catch (IllegalArgumentException e) {
                throw new InvalidFrameException(e.getMessage());
            } finally {
                b.position(mark);
            }
        }
    }

    @Override
    public String getMessage() {
        return reasonPhrase;
    }

    @Override
    public String toString() {
        return super.toString() + "code: " + code;
    }

    @Override
    public void setPayload(ByteBuffer payload) throws WsIOException {
        super.setPayload(payload);
        initCloseCode();
        initMessage();
    }

    @Override
    public ByteBuffer getPayloadData() {
        if (code == CloseCodes.NO_STATUS_CODE)
            return emptybytebuffer;
        return super.getPayloadData();
    }

}

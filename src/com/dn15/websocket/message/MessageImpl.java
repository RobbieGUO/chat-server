package com.dn15.websocket.message;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocket.util.CharsetStringConverter;

public class MessageImpl implements Message {
    protected static byte[] emptyarray = {};
    protected boolean fin;
    protected Opcode optcode;
    private ByteBuffer unmaskedpayload;
    protected boolean transferemasked;

    public MessageImpl() {
    }

    public MessageImpl(Opcode op) {
        this.optcode = op;
        unmaskedpayload = ByteBuffer.wrap(emptyarray);
    }

    /**
     * Helper constructor which helps to create "echo" frames. The new object
     * will use the same underlying payload data.
     **/
    public MessageImpl(Message f) {
        fin = f.isFin();
        optcode = f.getOpcode();
        unmaskedpayload = f.getPayloadData();
        transferemasked = f.getTransfereMasked();
    }

    @Override
    public boolean isFin() {
        return fin;
    }

    @Override
    public Opcode getOpcode() {
        return optcode;
    }

    @Override
    public boolean getTransfereMasked() {
        return transferemasked;
    }

    @Override
    public ByteBuffer getPayloadData() {
        return unmaskedpayload;
    }

    @Override
    public void setFin(boolean fin) {
        this.fin = fin;
    }

    @Override
    public void setOptcode(Opcode optcode) {
        this.optcode = optcode;
    }

    @Override
    public void setPayload(ByteBuffer payload) throws WsIOException {
        unmaskedpayload = payload;
    }

    @Override
    public void setTransferemasked(boolean transferemasked) {
        this.transferemasked = transferemasked;
    }

    @Override
    public void append(Message nextframe) throws WsIOException {
        ByteBuffer b = nextframe.getPayloadData();
        if (unmaskedpayload == null) {
            unmaskedpayload = ByteBuffer.allocate(b.remaining());
            b.mark();
            unmaskedpayload.put(b);
            b.reset();
        } else {
            b.mark();
            unmaskedpayload.position(unmaskedpayload.limit());
            unmaskedpayload.limit(unmaskedpayload.capacity());

            if (b.remaining() > unmaskedpayload.remaining()) {
                ByteBuffer tmp = ByteBuffer.allocate(b.remaining() + unmaskedpayload.capacity());
                unmaskedpayload.flip();
                tmp.put(unmaskedpayload);
                tmp.put(b);
                unmaskedpayload = tmp;

            } else {
                unmaskedpayload.put(b);
            }
            unmaskedpayload.rewind();
            b.reset();
        }
        fin = nextframe.isFin();
    }

    @Override
    public String toString() {
        return "Framedata{ optcode:" + getOpcode() + ", fin:" + isFin() + ", payloadlength:[pos:"
                + unmaskedpayload.position() + ", len:" + unmaskedpayload.remaining() + "], payload:"
                + Arrays.toString(CharsetStringConverter.utf8Bytes(new String(unmaskedpayload.array()))) + "}";
    }

}

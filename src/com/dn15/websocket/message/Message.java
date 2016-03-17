package com.dn15.websocket.message;

import java.nio.ByteBuffer;

import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocket.message.Message;

public interface Message {
    public enum Opcode {
        CONTINUOUS, TEXT, BINARY, PING, PONG, CLOSING
    }

    public boolean isFin();

    public boolean getTransfereMasked();

    public Opcode getOpcode();

    public ByteBuffer getPayloadData();// TODO the separation of the application
                                       // data and the extension data is yet to
                                       // be done

    public abstract void append(Message nextframe) throws WsIOException;

    public abstract void setFin(boolean fin);

    public abstract void setOptcode(Opcode optcode);

    public abstract void setPayload(ByteBuffer payload) throws WsIOException;

    public abstract void setTransferemasked(boolean transferemasked);

}
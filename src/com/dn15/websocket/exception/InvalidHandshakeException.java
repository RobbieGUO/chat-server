package com.dn15.websocket.exception;

import com.dn15.websocketapi.CloseReason.CloseCodes;

public class InvalidHandshakeException extends WsIOException {

    /**
     * Serializable
     */
    private static final long serialVersionUID = -1426533877490484964L;

    public InvalidHandshakeException() {
        super(CloseCodes.PROTOCOL_ERROR);
    }

    public InvalidHandshakeException(String arg0, Throwable arg1) {
        super(CloseCodes.PROTOCOL_ERROR, arg0, arg1);
    }

    public InvalidHandshakeException(String arg0) {
        super(CloseCodes.PROTOCOL_ERROR, arg0);
    }

    public InvalidHandshakeException(Throwable arg0) {
        super(CloseCodes.PROTOCOL_ERROR, arg0);
    }

}

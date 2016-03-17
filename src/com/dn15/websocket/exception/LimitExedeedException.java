package com.dn15.websocket.exception;


import com.dn15.websocketapi.CloseReason.CloseCodes;

public class LimitExedeedException extends WsIOException {

    /**
     * Serializable
     */
    private static final long serialVersionUID = 6908339749836826785L;

    public LimitExedeedException() {
        super(CloseCodes.TOO_BIG);
    }

    public LimitExedeedException(String s) {
        super(CloseCodes.TOO_BIG, s);
    }

}

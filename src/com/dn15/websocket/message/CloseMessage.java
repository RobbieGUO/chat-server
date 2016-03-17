package com.dn15.websocket.message;

import com.dn15.websocketapi.CloseReason.CloseCode;

public interface CloseMessage extends Message {
    public CloseCode getCloseCode() throws java.io.InvalidObjectException;

    public String getMessage() throws java.io.InvalidObjectException;
}

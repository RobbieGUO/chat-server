package com.dn15.websocket.message;

public interface ServerHandshake extends Handshake {
    public short getHttpStatus();

    public String getHttpStatusMessage();

    public void setHttpStatus(short status);

    public void setHttpStatusMessage(String message);
}

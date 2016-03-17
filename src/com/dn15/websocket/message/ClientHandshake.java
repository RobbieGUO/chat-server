package com.dn15.websocket.message;

public interface ClientHandshake extends Handshake {
    /**
     * returns the HTTP Request-URI as defined by
     * http://tools.ietf.org/html/rfc2616#section-5.1.2
     */
    public String getResourceDescriptor();

    public void setResourceDescriptor(String resourceDescriptor);
}

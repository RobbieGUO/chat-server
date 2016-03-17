package com.dn15.websocket.message;

import java.util.Iterator;

public interface Handshake {
    public Iterator<String> iterateHttpFields();

    public String getFieldValue(String name);

    public boolean hasFieldValue(String name);

    public byte[] getContent();

    public abstract void setContent(byte[] content);

    public abstract void put(String name, String value);
}

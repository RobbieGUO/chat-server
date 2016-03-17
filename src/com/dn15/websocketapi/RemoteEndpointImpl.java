/**
 * 
 */
package com.dn15.websocketapi;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;

/**
 * @author TonyHong
 * 
 */
public abstract class RemoteEndpointImpl implements RemoteEndpoint {

    /**
     * 
     */
    public RemoteEndpointImpl() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void setBatchingAllowed(boolean allowed) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean getBatchingAllowed() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void flushBatch() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
        // TODO Auto-generated method stub

    }

    @Override
    public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
        // TODO Auto-generated method stub

    }

    @Override
    public void sendText(String text) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public Writer getSendWriter() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

}

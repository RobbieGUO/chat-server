/**
 * 
 */
package com.dn15.websocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;

import com.dn15.websocket.message.Message;
import com.dn15.websocketapi.CloseReason.CloseCode;

/**
 * @author TonyHong
 * 
 */
public interface Session {
    public enum Role {
        CLIENT, SERVER
    }

    public enum READYSTATE {
        NOT_YET_CONNECTED, CONNECTING, OPEN, CLOSING, CLOSED;
    }

    public static final int DEFAULT_PORT = 80;

    public void close(CloseCode code);

    /**
     * This will close the connection immediately without a proper close
     * handshake. The code and the message therefore won't be transfered over
     * the wire also they will be forwarded to onClose/onWebsocketClose.
     **/
    public abstract void closeConnection(CloseCode code, String message);

    /**
     * Send Text data to the other end.
     * 
     * @throws IllegalArgumentException
     * @throws NotYetConnectedException
     */
    public abstract void send( String text ) throws NotYetConnectedException;

    /**
     * Send Binary data (plain bytes) to the other end.
     * 
     * @throws IllegalArgumentException
     * @throws NotYetConnectedException
     */
    public abstract void send( ByteBuffer bytes ) throws IllegalArgumentException , NotYetConnectedException;

    public abstract void send( byte[] bytes ) throws IllegalArgumentException , NotYetConnectedException;

    
    /**
     * Send Text data to the other end.
     * 
     * @throws IllegalArgumentException
     * @throws NotYetConnectedException
     */
    public abstract void sendMessage(Message message) throws IllegalArgumentException, NotYetConnectedException;

    public abstract boolean hasBufferedData();

    /**
     * @returns never returns null
     */
    public abstract InetSocketAddress getRemoteSocketAddress();

    /**
     * @returns never returns null
     */
    public abstract InetSocketAddress getLocalSocketAddress();

    public abstract boolean isConnecting();

    public abstract boolean isOpen();

    public abstract boolean isClosing();

    /**
     * Returns whether the close handshake has been completed and the socket is
     * closed.
     */
    public abstract boolean isClosed();

    /**
     * Retrieve the WebSocket 'readyState'. This represents the state of the
     * connection. It returns a numerical value, as per W3C WebSockets specs.
     * 
     * @return Returns '0 = CONNECTING', '1 = OPEN', '2 = CLOSING' or '3 =
     *         CLOSED'
     */
    public abstract READYSTATE getReadyState();

    /**
     * Returns the HTTP Request-URI as defined by
     * http://tools.ietf.org/html/rfc2616#section-5.1.2<br>
     * If the opening handshake has not yet happened it will return null.
     **/
    public abstract String getResourceDescriptor();



}

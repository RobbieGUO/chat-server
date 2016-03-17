package com.dn15.websocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocket.message.ClientHandshake;
import com.dn15.websocket.message.Handshake;
import com.dn15.websocket.message.Message;
import com.dn15.websocketapi.DecodeException;
import com.dn15.websocketapi.Endpoint;
import com.dn15.websocketapi.CloseReason.CloseCode;
import com.dn15.websocketapi.CloseReason.CloseCodes;

/**
 * Implemented by <tt>WebSocketClient</tt> and <tt>WebSocketServer</tt>. The
 * methods within are called by <tt>Session</tt>. Almost every method takes a
 * first parameter conn which represents the source of the respective event.
 */
public abstract class ServerEndpoint extends Endpoint {

    /**
     * Called on the server side when the socket connection is first
     * established, and the Session handshake has been received. This method
     * allows to deny connections based on the received handshake.<br>
     * By default this method only requires protocol compliance.
     * 
     * @param conn
     *            The Session related to this event
     * @param draft
     *            The protocol draft the client uses to connect
     * @param request
     *            The opening http message send by the client. Can be used to
     *            access additional fields like cookies.
     * @return Returns an incomplete handshake containing all optional fields
     * @throws WsIOException
     *             Throwing this exception will cause this handshake to be
     *             rejected
     */
    public abstract Handshake onHandshakeReceived(Session conn, WebSocketProtocol draft, Handshake request)
            throws DecodeException;

    /**
     * Called when an entire text frame has been received. Do whatever you want
     * here...
     * 
     * @param conn
     *            The <tt>Session</tt> instance this event is occurring on.
     * @param message
     *            The UTF-8 decoded message that was received.
     */
    public abstract void onMessage(Session conn, String message);

    /**
     * Called when an entire binary frame has been received. Do whatever you
     * want here...
     * 
     * @param conn
     *            The <tt>Session</tt> instance this event is occurring on.
     * @param blob
     *            The binary message that was received.
     */
    public abstract void onMessage(Session conn, ByteBuffer blob);

    /**
     * Called after <tt>Session#close</tt> is explicity called, or when the
     * other end of the Session connection is closed.
     * 
     * @param conn
     *            The <tt>Session</tt> instance this event is occuring on.
     */

    public abstract void onCloseInitiated(Session ws, CloseCode code, String reason);

    /** called as soon as no further frames are accepted */
    public abstract void onClosing(Session ws, CloseCode code, String reason, boolean remote);

    /** send when this peer sends a close handshake */


    /**
     * Called a ping frame has been received. This method must send a
     * corresponding pong by itself.
     * 
     * @param f
     *            The ping frame. Control frames may contain payload.
     */
    public abstract void onPing(Session conn, Message f);

    /**
     * Called when a pong frame is received.
     **/
    public abstract void onPong(Session conn, Message f);

    /**
     * This method is used to inform the selector thread that there is data
     * queued to be written to the socket.
     */
    public abstract void onWriteDemand(Session conn);

    public abstract InetSocketAddress getLocalSocketAddress(Session conn);

    public abstract InetSocketAddress getRemoteSocketAddress(Session conn);

    public void onHandshakeSent(WebSocketImpl webSocketImpl, ClientHandshake handshakerequest) throws WsIOException {
        // TODO Auto-generated method stub

    }
}

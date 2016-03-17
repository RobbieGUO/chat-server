/**
 * 
 */
package com.dn15.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.dn15.websocket.WebSocketProtocol.CloseHandshakeType;
import com.dn15.websocket.WebSocketProtocol.HandshakeState;
import com.dn15.websocket.WebSocketServer.WebSocketWorker;
import com.dn15.websocket.exception.IncompleteHandshakeException;
import com.dn15.websocket.exception.InvalidHandshakeException;
import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocket.message.ClientHandshake;
import com.dn15.websocket.message.CloseMessageBuilder;
import com.dn15.websocket.message.Handshake;
import com.dn15.websocket.message.Message;
import com.dn15.websocket.message.ServerHandshake;
import com.dn15.websocket.message.Message.Opcode;
import com.dn15.websocket.util.CharsetStringConverter;
import com.dn15.websocket.util.Log;
import com.dn15.websocketapi.CloseReason;
import com.dn15.websocketapi.Endpoint;
import com.dn15.websocketapi.CloseReason.CloseCode;
import com.dn15.websocketapi.CloseReason.CloseCodes;

/**
 * @author TonyHong
 * 
 */
public class WebSocketImpl implements Session {

    public static boolean DEBUG = false;

    public static int RCV_BUF_SIZE = 8192;

    public SelectionKey key;

    /**
     * the possibly wrapped channel object whose selection is controlled by
     * {@link #key}
     */
    public ByteChannel channel;
    /**
     * Queue of buffers that need to be sent to the client.
     */
    public final BlockingQueue<ByteBuffer> outQueue;
    /**
     * Queue of buffers that need to be processed
     */
    public final BlockingQueue<ByteBuffer> inQueue;

    /**
     * Helper variable meant to store the thread which ( exclusively ) triggers
     * this objects decode method.
     **/
    public volatile WebSocketWorker workerThread; // TODO reset worker?

    /** When true no further frames may be submitted to be sent */
    private volatile boolean flushandclosestate = false;

    private READYSTATE readystate = READYSTATE.NOT_YET_CONNECTED;

    /**
     * The listener to notify of WebSocket events.
     */
    private final ServerEndpoint wsl;

    private WebSocketProtocol protocol = new Draft_75();

    private Role role;

    private Opcode current_continuous_frame_opcode = null;

    /** the bytes of an incomplete received handshake */
    private ByteBuffer tmpHandshakeBytes = ByteBuffer.allocate(0);

    /** stores the handshake sent by this websocket ( Role.CLIENT only ) */
    private ClientHandshake handshakerequest = null;

    // private ClientHandshake handshakerequest = null;

    private String closemessage = null;
    private CloseCode closecode = null;
    private Boolean closedremotely = null;

    private String resourceDescriptor = null;

    /**
     * 
     */
    public WebSocketImpl(Endpoint listener, Socket s) {
        this(listener);
        this.role = Role.SERVER;
    }

    public WebSocketImpl(Endpoint listener) {
        if (listener == null || role == Role.SERVER)// socket can be null
                                                    // because we want do be
                                                    // able to create the object
                                                    // without already having a
                                                    // bound channel
            throw new IllegalArgumentException("parameters must not be null");
        this.outQueue = new LinkedBlockingQueue<ByteBuffer>();
        inQueue = new LinkedBlockingQueue<ByteBuffer>();
        this.wsl = (ServerEndpoint) listener;
        this.role = Role.CLIENT;
    }

    public void decode(ByteBuffer socketBuffer) throws IllegalArgumentException, NotYetConnectedException {
        assert (socketBuffer.hasRemaining());

        if (readystate != READYSTATE.NOT_YET_CONNECTED) {
            decodeMessage(socketBuffer);
            ;
        } else {
            if (decodeHandshake(socketBuffer)) {
                assert (tmpHandshakeBytes.hasRemaining() != socketBuffer.hasRemaining() || !socketBuffer.hasRemaining());

                if (socketBuffer.hasRemaining()) {
                    decodeMessage(socketBuffer);
                } else if (tmpHandshakeBytes.hasRemaining()) {
                    decodeMessage(tmpHandshakeBytes);
                }
            }
        }
        assert (isClosing() || isFlushAndClose() || !socketBuffer.hasRemaining());

    }

    private void decodeMessage(ByteBuffer socketBuffer) {

        List<Message> frames;
        try {
            frames = protocol.translateFrame(socketBuffer);
            for (Message f : frames) {
                Opcode curop = f.getOpcode();

                if (curop == Opcode.CLOSING) {
                    CloseCode code = CloseCodes.NO_STATUS_CODE;
                    String reason = "";
                    if (f instanceof CloseReason) {
                        CloseReason cf = (CloseReason) f;
                        code = cf.getCloseCode();
                        reason = cf.getMessage();
                    }
                    if (readystate == READYSTATE.CLOSING) {
                        // complete the close handshake by disconnecting
                        closeConnection(code, reason, true);
                    } else {
                        // echo close handshake
                        if (protocol.getCloseHandshakeType() == CloseHandshakeType.TWOWAY)
                            close(code, reason, true);
                        else
                            flushAndClose(code, reason, false);
                    }
                    continue;
                } else if (curop == Opcode.PING) {
                    wsl.onPing(this, f);
                    continue;
                } else if (curop == Opcode.PONG) {
                    wsl.onPong(this, f);
                    continue;
                } else if (current_continuous_frame_opcode != null) {
                    throw new WsIOException(CloseCodes.PROTOCOL_ERROR, "Continuous frame sequence not completed.");
                } else if (curop == Opcode.TEXT) {
                    try {
                        wsl.onMessage(this, CharsetStringConverter.stringUtf8(f.getPayloadData()));
                    } catch (Exception e) {
                        wsl.onError(this, e);
                    }
                } else if (curop == Opcode.BINARY) {
                    try {
                        wsl.onMessage(this, f.getPayloadData());
                    } catch (RuntimeException e) {
                        wsl.onError(this, e);
                    }
                } else {
                    throw new WsIOException(CloseCodes.PROTOCOL_ERROR, "non control or continious frame expected");
                }
            }
        } catch (WsIOException e1) {
            wsl.onError(this, e1);
            close(e1);
            return;
        }
    }

    private boolean decodeHandshake(ByteBuffer socketBufferNew) {
        Log.i("decoding handshake...");
        ByteBuffer socketBuffer;
        if (tmpHandshakeBytes.capacity() == 0) {
            socketBuffer = socketBufferNew;
        } else {
            if (tmpHandshakeBytes.remaining() < socketBufferNew.remaining()) {
                ByteBuffer buf = ByteBuffer.allocate(tmpHandshakeBytes.capacity() + socketBufferNew.remaining());
                tmpHandshakeBytes.flip();
                buf.put(tmpHandshakeBytes);
                tmpHandshakeBytes = buf;
            }

            tmpHandshakeBytes.put(socketBufferNew);
            tmpHandshakeBytes.flip();
            socketBuffer = tmpHandshakeBytes;
        }
        socketBuffer.mark();
        try {
            HandshakeState handshakestate = null;

            try {
                if (role == Role.SERVER) {
                    if (protocol == null) {
                        Log.i("protocol is null!");

                        /*
                         * for (WebSocketProtocol d : knownDrafts) { d =
                         * d.copyInstance(); try { d.setParseMode(role);
                         * socketBuffer.reset(); Handshake tmphandshake =
                         * d.translateHandshake(socketBuffer); if (tmphandshake
                         * instanceof ClientHandshake == false) {
                         * flushAndClose(CloseCodes.PROTOCOL_ERROR,
                         * "wrong http function", false); return false; }
                         * ClientHandshake handshake = (ClientHandshake)
                         * tmphandshake; handshakestate =
                         * d.acceptHandshakeAsServer(handshake); if
                         * (handshakestate == HandshakeState.MATCHED) {
                         * resourceDescriptor =
                         * handshake.getResourceDescriptor(); ServerHandshake
                         * response; try { response = (ServerHandshake)
                         * wsl.onHandshakeReceived(this, d, handshake); } catch
                         * (WsIOException e) { flushAndClose(e.getCloseCode(),
                         * e.getMessage(), false); return false; } catch
                         * (RuntimeException e) { wsl.onError(this, e);
                         * flushAndClose(CloseCodes.NEVER_CONNECTED,
                         * e.getMessage(), false); return false; }
                         * write(d.createHandshake
                         * (d.postProcessHandshakeResponse(handshake, response),
                         * role)); protocol = d; open(handshake); return true; }
                         * } catch (InvalidHandshakeException e) { // go on with
                         * an other protocol } } if (protocol == null) {
                         * close(CloseCodes.PROTOCOL_ERROR,
                         * "no protocol matches"); } return false;
                         */
                    } else {
                        // special case for multiple step handshakes
                        Handshake tmphandshake = protocol.translateHandshake(socketBuffer);

                        if (tmphandshake instanceof ClientHandshake == false) {
                            flushAndClose(CloseCodes.PROTOCOL_ERROR, "wrong http function", false);
                            return false;
                        }
                        ClientHandshake handshake = (ClientHandshake) tmphandshake;
                        handshakestate = protocol.acceptHandshake(handshake);

                        if (handshakestate == HandshakeState.MATCHED) {
                            open(handshake);
                            return true;
                        } else {
                            close(CloseCodes.PROTOCOL_ERROR, "the handshake did finaly not match");
                        }
                        return false;
                    }
                } else if (role == Role.CLIENT) {
                    /*
                     * 
                     * protocol.setParseMode(role); Handshake tmphandshake =
                     * protocol.translateHandshake(socketBuffer); if
                     * (tmphandshake instanceof ServerHandshake == false) {
                     * flushAndClose(CloseCodes.PROTOCOL_ERROR,
                     * "wrong http function", false); return false; } Handshake
                     * handshake = (ServerHandshake) tmphandshake;
                     * handshakestate =
                     * protocol.acceptHandshakeAsClient(handshakerequest,
                     * handshake); if (handshakestate == HandshakeState.MATCHED)
                     * { try { wsl.onHandshakeReceived(this, handshakerequest,
                     * handshake); } catch (WsIOException e) {
                     * flushAndClose(e.getCloseCode(), e.getMessage(), false);
                     * return false; } catch (RuntimeException e) {
                     * wsl.onError(this, e);
                     * flushAndClose(CloseCodes.NEVER_CONNECTED, e.getMessage(),
                     * false); return false; } open(handshake); return true; }
                     * else { close(CloseCodes.PROTOCOL_ERROR, "protocol " +
                     * protocol + " refuses handshake"); }
                     */
                }
            } catch (InvalidHandshakeException e) {
                close(e);
            }
        } catch (IncompleteHandshakeException e) {
            if (tmpHandshakeBytes.capacity() == 0) {
                socketBuffer.reset();
                int newsize = e.getPreferedSize();
                if (newsize == 0) {
                    newsize = socketBuffer.capacity() + 16;
                } else {
                    assert (e.getPreferedSize() >= socketBuffer.remaining());
                }
                tmpHandshakeBytes = ByteBuffer.allocate(newsize);

                tmpHandshakeBytes.put(socketBufferNew);
                // tmpHandshakeBytes.flip();
            } else {
                tmpHandshakeBytes.position(tmpHandshakeBytes.limit());
                tmpHandshakeBytes.limit(tmpHandshakeBytes.capacity());
            }
        }
        return false;
    }

    private void close(CloseCode code, String message, boolean remote) {
        Log.i("Start to close");
        if (readystate != READYSTATE.CLOSING && readystate != READYSTATE.CLOSED) {
            if (readystate == READYSTATE.OPEN) {
                if (code == CloseCodes.CLOSED_ABNORMALLY) {
                    assert (remote == false);
                    readystate = READYSTATE.CLOSING;
                    flushAndClose(code, message, false);
                    return;
                }
                if (protocol.getCloseHandshakeType() != CloseHandshakeType.NONE) {
                    try {
                        if (!remote) {
                            try {
                                wsl.onCloseInitiated(this, code, message);
                            } catch (RuntimeException e) {
                                wsl.onError(this, e);
                            }
                        }
                        sendMessage(new CloseMessageBuilder(code, message));
                    } catch (Exception e) {
                        wsl.onError(this, e);
                        flushAndClose(CloseCodes.CLOSED_ABNORMALLY, "generated frame is invalid", false);
                    }
                }
                flushAndClose(code, message, remote);
            } else {
                flushAndClose(CloseCodes.NEVER_CONNECTED, message, false);
            }
            if (code == CloseCodes.PROTOCOL_ERROR)// this endpoint found a
                                                  // PROTOCOL_ERROR
                flushAndClose(code, message, remote);
            readystate = READYSTATE.CLOSING;
            tmpHandshakeBytes = null;
            return;
        }
    }

    public void close(CloseCode code, String message) {
        close(code, message, false);
    }

    @Override
    public void close(CloseCode code) {
        close(code, "", false);
    }

    public void close() {
        close(CloseCodes.NORMAL_CLOSURE);
    }

    public void close(WsIOException e) {
        close(e.getCloseCode(), e.getMessage(), false);
    }

    /**
     * 
     * @param remote
     *            Indicates who "generated" <code>code</code>.<br>
     *            <code>true</code> means that this endpoint received the
     *            <code>code</code> from the other endpoint.<br>
     *            false means this endpoint decided to send the given code,<br>
     *            <code>remote</code> may also be true if this endpoint started
     *            the closing handshake since the other endpoint may not simply
     *            echo the <code>code</code> but close the connection the same
     *            time this endpoint does do but with an other <code>code</code>
     *            . <br>
     **/

    protected synchronized void closeConnection(CloseCode code, String message, boolean remote) {
        Log.i("Closing connection...");
        if (readystate == READYSTATE.CLOSED) {
            return;
        }

        if (key != null) {
            // key.attach( null ); //see issue #114
            key.cancel();
        }
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                wsl.onError(this, e);
            }
        }
        try {
            this.wsl.onClose(this, message);
        } catch (RuntimeException e) {
            wsl.onError(this, e);
        }
        if (protocol != null)
            protocol.reset();
        handshakerequest = null;

        readystate = READYSTATE.CLOSED;
        this.outQueue.clear();
    }

    protected void closeConnection(CloseCode code, boolean remote) {
        closeConnection(code, "", remote);
    }

    public void closeConnection() {
        if (closedremotely == null) {
            throw new IllegalStateException("this method must be used in conjuction with flushAndClose");
        }
        closeConnection(closecode, closemessage, closedremotely);
    }

    public void closeConnection(CloseCode code, String message) {
        closeConnection(code, message, false);
    }

    protected synchronized void flushAndClose(CloseCode code, String message, boolean remote) {
        Log.i("Start to flush and close...");
        if (flushandclosestate) {
            return;
        }
        closecode = code;
        closemessage = message;
        closedremotely = remote;

        flushandclosestate = true;

        wsl.onWriteDemand(this); // ensures that all outgoing frames are flushed
                                 // before closing the connection
        try {
            wsl.onClosing(this, code, message, remote);
        } catch (RuntimeException e) {
            wsl.onError(this, e);
        }
        if (protocol != null)
            protocol.reset();
        handshakerequest = null;
    }

    /**
     * Send Text data to the other end.
     * 
     * @throws IllegalArgumentException
     * @throws NotYetConnectedException
     */
    public void send(String text) throws RuntimeException {
        if (text == null)
            throw new IllegalArgumentException("Cannot send 'null' data to a WebSocketImpl.");
        sendMessages(protocol.createFrames(text, role == Role.CLIENT));
    }

    /**
     * Send Binary data (plain bytes) to the other end.
     * 
     * @throws IllegalArgumentException
     * @throws NotYetConnectedException
     */
    public void send(ByteBuffer bytes) throws IllegalArgumentException, RuntimeException {
        if (bytes == null)
            throw new IllegalArgumentException("Cannot send 'null' data to a WebSocketImpl.");
        sendMessages(protocol.createFrames(bytes, role == Role.CLIENT));
    }

    public void send(byte[] bytes) throws IllegalArgumentException, RuntimeException {
        send(ByteBuffer.wrap(bytes));
    }

    private void sendMessages(Collection<Message> frames) {
        if (!isOpen())
            throw new RuntimeException();
        for (Message f : frames) {
            sendMessage(f);
        }
    }

    @Override
    public void sendMessage(Message framedata) {
        write(protocol.createBinaryFrame(framedata));
    }

    @Override
    public boolean hasBufferedData() {
        return !this.outQueue.isEmpty();
    }

    public void startHandshake(ClientHandshake handshakedata) throws InvalidHandshakeException {
        assert (readystate != READYSTATE.CONNECTING) : "shall only be called once";

        // Store the Handshake Request we are about to send
        this.handshakerequest = protocol.postProcessHandshakeRequest(handshakedata);

        resourceDescriptor = handshakedata.getResourceDescriptor();
        assert (resourceDescriptor != null);

        // Notify Listener
        try {
            wsl.onHandshakeSent(this, this.handshakerequest);
        } catch (WsIOException e) {
            // Stop if the client code throws an exception
            throw new InvalidHandshakeException("Handshake data rejected by client.");
        } catch (RuntimeException e) {
            wsl.onError(this, e);
            throw new InvalidHandshakeException("rejected because of" + e);
        }

        // Send
        write(protocol.createHandshake(this.handshakerequest, role));
    }

    private void write(ByteBuffer buf) {
        Log.i("write(" + buf.remaining() + "): {"
                + (buf.remaining() > 1000 ? "too big to display" : new String(buf.array())) + "}");

        outQueue.add(buf);
        /*
         * try { outQueue.put( buf ); } catch ( InterruptedException e ) {
         * write( buf ); Thread.currentThread().interrupt(); // keep the
         * interrupted status e.printStackTrace(); }
         */
        wsl.onWriteDemand(this);
    }

    private void write(List<ByteBuffer> bufs) {
        for (ByteBuffer b : bufs) {
            write(b);
        }
    }

    private void open(ClientHandshake d) {
        Log.i("open using draft: " + protocol.getClass().getSimpleName());
        readystate = READYSTATE.OPEN;
        try {
            handshakerequest = d;
            wsl.onOpen(this);
        } catch (RuntimeException e) {
            wsl.onError(this, e);
        }
    }

    public void eot() {

        if (getReadyState() == READYSTATE.NOT_YET_CONNECTED) {
            closeConnection(CloseCodes.NEVER_CONNECTED, true);
        } else if (flushandclosestate) {
            closeConnection(closecode, closemessage, closedremotely);
        } else if (protocol.getCloseHandshakeType() == CloseHandshakeType.NONE) {
            closeConnection(CloseCodes.NORMAL_CLOSURE, true);
        } else if (protocol.getCloseHandshakeType() == CloseHandshakeType.ONEWAY) {
            if (role == Role.SERVER)
                closeConnection(CloseCodes.CLOSED_ABNORMALLY, true);
            else
                closeConnection(CloseCodes.NORMAL_CLOSURE, true);
        } else {
            closeConnection(CloseCodes.CLOSED_ABNORMALLY, true);
        }

    }

    @Override
    public InetSocketAddress getRemoteSocketAddress() {
        return wsl.getRemoteSocketAddress(this);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return wsl.getLocalSocketAddress(this);
    }

    @Override
    public boolean isConnecting() {
        assert (flushandclosestate ? readystate == READYSTATE.CONNECTING : true);
        return readystate == READYSTATE.CONNECTING; // ifflushandclosestate
    }

    @Override
    public boolean isOpen() {
        assert (readystate == READYSTATE.OPEN ? !flushandclosestate : true);
        return readystate == READYSTATE.OPEN;
    }

    @Override
    public boolean isClosing() {
        return readystate == READYSTATE.CLOSING;
    }

    @Override
    public boolean isClosed() {
        return readystate == READYSTATE.CLOSED;
    }

    @Override
    public READYSTATE getReadyState() {
        return readystate;
    }

    @Override
    public String getResourceDescriptor() {
        return resourceDescriptor;
    }

    public boolean isFlushAndClose() {
        return flushandclosestate;
    }

    public Role getRole() {
        return null;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return super.toString(); // its nice to be able to set breakpoints here
    }

    public WebSocketProtocol getDraft() {
        return protocol;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
    }

}

package com.dn15.websocket;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.dn15.websocket.Session.Role;
import com.dn15.websocket.exception.IncompleteHandshakeException;
import com.dn15.websocket.exception.InvalidHandshakeException;
import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocket.message.ClientHandshake;
import com.dn15.websocket.message.Handshake;
import com.dn15.websocket.message.HandshakeImplClient;
import com.dn15.websocket.message.HandshakeImplServer;
import com.dn15.websocket.message.Message;
import com.dn15.websocket.message.MessageImpl;
import com.dn15.websocket.message.ServerHandshake;
import com.dn15.websocket.message.Message.Opcode;
import com.dn15.websocket.util.CharsetStringConverter;
import com.dn15.websocketapi.CloseReason.CloseCodes;

/**
 * Base class for everything of a websocket specification which is not common
 * such as the way the handshake is read or frames are transfered.
 **/
public abstract class WebSocketProtocol {

    public enum HandshakeState {
        /** Handshake matched this Draft successfully */
        MATCHED,
        /** Handshake is does not match this Draft */
        NOT_MATCHED
    }

    public enum CloseHandshakeType {
        NONE, ONEWAY, TWOWAY
    }

    public static int MAX_FAME_SIZE = 1000 * 1;
    public static int INITIAL_FAMESIZE = 64;

    public static final byte[] FLASH_POLICY_REQUEST = CharsetStringConverter.utf8Bytes("<policy-file-request/>\0");

    /**
     * In some cases the handshake will be parsed different depending on whether
     */
    protected Role role = null;

    protected Opcode continuousFrameType = null;

    public static ByteBuffer readLine(ByteBuffer buf) {
        ByteBuffer sbuf = ByteBuffer.allocate(buf.remaining());
        byte prev = '0';
        byte cur = '0';
        while (buf.hasRemaining()) {
            prev = cur;
            cur = buf.get();
            sbuf.put(cur);
            if (prev == (byte) '\r' && cur == (byte) '\n') {
                sbuf.limit(sbuf.position() - 2);
                sbuf.position(0);
                return sbuf;

            }
        }
        // ensure that there wont be any bytes skipped
        buf.position(buf.position() - sbuf.position());
        return null;
    }

    public static String readStringLine(ByteBuffer buf) {
        ByteBuffer b = readLine(buf);
        return b == null ? null : CharsetStringConverter.stringAscii(b.array(), 0, b.limit());
    }

    public static Handshake translateHandshakeHttp(ByteBuffer buf, Role role) throws InvalidHandshakeException,
            IncompleteHandshakeException {
        Handshake handshake;

        String line = readStringLine(buf);
        if (line == null)
            throw new IncompleteHandshakeException(buf.capacity() + 128);

        String[] firstLineTokens = line.split(" ", 3);// eg. HTTP/1.1 101
                                                      // Switching the Protocols
        if (firstLineTokens.length != 3) {
            throw new InvalidHandshakeException();
        }

        // translating/parsing the request from the CLIENT
        HandshakeImplClient clienthandshake = new HandshakeImplClient();
        clienthandshake.setResourceDescriptor(firstLineTokens[1]);
        handshake = clienthandshake;

        line = readStringLine(buf);
        while (line != null && line.length() > 0) {
            String[] pair = line.split(":", 2);
            if (pair.length != 2)
                throw new InvalidHandshakeException("not an http header");
            handshake.put(pair[0], pair[1].replaceFirst("^ +", ""));
            line = readStringLine(buf);
        }
        if (line == null)
            throw new IncompleteHandshakeException();
        return handshake;
    }

    public abstract HandshakeState acceptHandshake(ClientHandshake request, ServerHandshake response)
            throws InvalidHandshakeException;

    public abstract HandshakeState acceptHandshake(ClientHandshake handshakedata) throws InvalidHandshakeException;

    protected boolean basicAccept(Handshake handshakedata) {
        return handshakedata.getFieldValue("Upgrade").equalsIgnoreCase("websocket")
                && handshakedata.getFieldValue("Connection").toLowerCase(Locale.ENGLISH).contains("upgrade");
    }

    public abstract ByteBuffer createBinaryFrame(Message framedata); // TODO
                                                                     // Allow to
                                                                     // send
                                                                     // data on
                                                                     // the base
                                                                     // of an
                                                                     // Iterator
                                                                     // or
                                                                     // InputStream

    public abstract List<Message> createFrames(ByteBuffer binary, boolean mask);

    public abstract List<Message> createFrames(String text, boolean mask);

    public List<Message> continuousFrame(Opcode op, ByteBuffer buffer, boolean fin) {
        if (op != Opcode.BINARY && op != Opcode.TEXT && op != Opcode.TEXT) {
            throw new IllegalArgumentException("Only Opcode.BINARY or  Opcode.TEXT are allowed");
        }

        if (continuousFrameType != null) {
            continuousFrameType = Opcode.CONTINUOUS;
        } else {
            continuousFrameType = op;
        }

        Message bui = new MessageImpl(continuousFrameType);
        try {
            bui.setPayload(buffer);
        } catch (WsIOException e) {
            throw new RuntimeException(e); // can only happen when one builds
                                           // close frames(Opcode.Close)
        }
        bui.setFin(fin);
        if (fin) {
            continuousFrameType = null;
        } else {
            continuousFrameType = op;
        }
        return Collections.singletonList((Message) bui);
    }

    public abstract void reset();

    public List<ByteBuffer> createHandshake(Handshake handshakedata, Role ownrole) {
        return createHandshake(handshakedata, ownrole, true);
    }

    public List<ByteBuffer> createHandshake(Handshake handshakedata, Role ownrole, boolean withcontent) {
        StringBuilder bui = new StringBuilder(100);
        if (handshakedata instanceof HandshakeImplClient) {
            bui.append("GET ");
            bui.append(((HandshakeImplClient) handshakedata).getResourceDescriptor());
            bui.append(" HTTP/1.1");
        } else if (handshakedata instanceof HandshakeImplServer) {
            bui.append("HTTP/1.1 101 " + ((HandshakeImplServer) handshakedata).getHttpStatusMessage());
        } else {
            throw new RuntimeException("unknow role");
        }
        bui.append("\r\n");
        Iterator<String> it = handshakedata.iterateHttpFields();
        while (it.hasNext()) {
            String fieldname = it.next();
            String fieldvalue = handshakedata.getFieldValue(fieldname);
            bui.append(fieldname);
            bui.append(": ");
            bui.append(fieldvalue);
            bui.append("\r\n");
        }
        bui.append("\r\n");
        byte[] httpheader = CharsetStringConverter.asciiBytes(bui.toString());

        byte[] content = withcontent ? handshakedata.getContent() : null;
        ByteBuffer bytebuffer = ByteBuffer.allocate((content == null ? 0 : content.length) + httpheader.length);
        bytebuffer.put(httpheader);
        if (content != null)
            bytebuffer.put(content);
        bytebuffer.flip();
        return Collections.singletonList(bytebuffer);
    }

    /**
     * As client
     * 
     * @param request
     * @param response
     * @return
     * @throws InvalidHandshakeException
     */
    public abstract ClientHandshake postProcessHandshakeRequest(ClientHandshake request)
            throws InvalidHandshakeException;

    /**
     * As server
     * 
     * @param request
     * @param response
     * @return
     * @throws InvalidHandshakeException
     */
    public abstract ServerHandshake postProcessHandshakeResponse(ClientHandshake request, ServerHandshake response)
            throws InvalidHandshakeException;

    public abstract List<Message> translateFrame(ByteBuffer buffer) throws WsIOException;

    public abstract CloseHandshakeType getCloseHandshakeType();

    /**
     * Drafts must only be by one websocket at all. To prevent drafts to be used
     * more than once the Websocket implementation should call this method in
     * order to create a new usable version of a given draft instance.<br>
     * The copy can be safely used in conjunction with a new websocket
     * connection.
     * */
    public abstract WebSocketProtocol copyInstance();

    public Handshake translateHandshake(ByteBuffer buf) throws InvalidHandshakeException {
        return translateHandshakeHttp(buf, role);
    }

    public int checkAlloc(int bytecount) throws WsIOException {
        if (bytecount < 0)
            throw new WsIOException(CloseCodes.PROTOCOL_ERROR, "Negative count");
        return bytecount;
    }

    public void setParseMode(Role role) {
        this.role = role;
    }

    public Role getRole() {
        return role;
    }

}

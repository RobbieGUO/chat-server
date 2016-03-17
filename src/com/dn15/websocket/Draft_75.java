package com.dn15.websocket;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.dn15.websocket.exception.InvalidFrameException;
import com.dn15.websocket.exception.InvalidHandshakeException;
import com.dn15.websocket.exception.LimitExedeedException;
import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocket.message.ClientHandshake;
import com.dn15.websocket.message.Message;
import com.dn15.websocket.message.MessageImpl;
import com.dn15.websocket.message.ServerHandshake;
import com.dn15.websocket.message.Message.Opcode;
import com.dn15.websocket.util.CharsetStringConverter;
import com.dn15.websocketapi.CloseReason.CloseCodes;

public class Draft_75 extends WebSocketProtocol {

    /**
     * The byte representing CR, or Carriage Return, or \r
     */
    public static final byte CR = (byte) 0x0D;
    /**
     * The byte representing LF, or Line Feed, or \n
     */
    public static final byte LF = (byte) 0x0A;
    /**
     * The byte representing the beginning of a WebSocket text frame.
     */
    public static final byte START_OF_FRAME = (byte) 0x00;
    /**
     * The byte representing the end of a WebSocket text frame.
     */
    public static final byte END_OF_FRAME = (byte) 0xFF;

    /** Is only used to detect protocol violations */
    protected boolean readingState = false;

    protected List<Message> readyframes = new LinkedList<Message>();
    protected ByteBuffer currentFrame;

    private final Random reuseableRandom = new Random();

    @Override
    public HandshakeState acceptHandshake(ClientHandshake request, ServerHandshake response) {
        return request.getFieldValue("WebSocket-Origin").equals(response.getFieldValue("Origin"))
                && basicAccept(response) ? HandshakeState.MATCHED : HandshakeState.NOT_MATCHED;
    }

    @Override
    public HandshakeState acceptHandshake(ClientHandshake handshakedata) {
        if (handshakedata.hasFieldValue("Origin") && basicAccept(handshakedata)) {
            return HandshakeState.MATCHED;
        }
        return HandshakeState.NOT_MATCHED;
    }

    @Override
    public ByteBuffer createBinaryFrame(Message framedata) {
        if (framedata.getOpcode() != Opcode.TEXT) {
            throw new RuntimeException("only text frames supported");
        }

        ByteBuffer pay = framedata.getPayloadData();
        ByteBuffer b = ByteBuffer.allocate(pay.remaining() + 2);
        b.put(START_OF_FRAME);
        pay.mark();
        b.put(pay);
        pay.reset();
        b.put(END_OF_FRAME);
        b.flip();
        return b;
    }

    @Override
    public List<Message> createFrames(ByteBuffer binary, boolean mask) {
        throw new RuntimeException("not yet implemented");
    }

    @Override
    public List<Message> createFrames(String text, boolean mask) {
        Message frame = new MessageImpl();
        try {
            frame.setPayload(ByteBuffer.wrap(CharsetStringConverter.utf8Bytes(text)));
        } catch (WsIOException e) {
            throw new RuntimeException(e);
        }
        frame.setFin(true);
        frame.setOptcode(Opcode.TEXT);
        frame.setTransferemasked(mask);
        return Collections.singletonList((Message) frame);
    }

    @Override
    public ClientHandshake postProcessHandshakeRequest(ClientHandshake request)
            throws InvalidHandshakeException {
        request.put("Upgrade", "WebSocket");
        request.put("Connection", "Upgrade");
        if (!request.hasFieldValue("Origin")) {
            request.put("Origin", "random" + reuseableRandom.nextInt());
        }

        return request;
    }

    @Override
    public ServerHandshake postProcessHandshakeResponse(ClientHandshake request, ServerHandshake response)
            throws InvalidHandshakeException {
        response.setHttpStatusMessage("Web Socket Protocol Handshake");
        response.put("Upgrade", "WebSocket");
        response.put("Connection", request.getFieldValue("Connection")); // to
                                                                         // respond
                                                                         // to a
                                                                         // Connection
                                                                         // keep
                                                                         // alive
        response.put("WebSocket-Origin", request.getFieldValue("Origin"));
        String location = "ws://" + request.getFieldValue("Host") + request.getResourceDescriptor();
        response.put("WebSocket-Location", location);
        // TODO handle Sec-WebSocket-Protocol and Set-Cookie
        return response;
    }

    protected List<Message> translateRegularFrame(ByteBuffer buffer) throws WsIOException {

        while (buffer.hasRemaining()) {
            byte newestByte = buffer.get();
            if (newestByte == START_OF_FRAME) { // Beginning of Frame
                if (readingState)
                    throw new InvalidFrameException("unexpected START_OF_FRAME");
                readingState = true;
            } else if (newestByte == END_OF_FRAME) { // End of Frame
                if (!readingState)
                    throw new InvalidFrameException("unexpected END_OF_FRAME");
                // currentFrame will be null if END_OF_FRAME was send directly
                // after
                // START_OF_FRAME, thus we will send 'null' as the sent message.
                if (this.currentFrame != null) {
                    currentFrame.flip();
                    MessageImpl curframe = new MessageImpl();
                    curframe.setPayload(currentFrame);
                    curframe.setFin(true);
                    curframe.setOptcode(Opcode.TEXT);
                    readyframes.add(curframe);
                    this.currentFrame = null;
                    buffer.mark();
                }
                readingState = false;
            } else if (readingState) { // Regular frame data, add to current
                                       // frame buffer //TODO This code is very
                                       // expensive and slow
                if (currentFrame == null) {
                    currentFrame = createBuffer();
                } else if (!currentFrame.hasRemaining()) {
                    currentFrame = increaseBuffer(currentFrame);
                }
                currentFrame.put(newestByte);
            } else {
                return null;
            }
        }

        // if no error occurred this block will be reached
        /*
         * if( readingState ) { checkAlloc(currentFrame.position()+1); }
         */

        List<Message> frames = readyframes;
        readyframes = new LinkedList<Message>();
        return frames;
    }

    @Override
    public List<Message> translateFrame(ByteBuffer buffer) throws WsIOException {
        List<Message> frames = translateRegularFrame(buffer);
        if (frames == null) {
            throw new WsIOException(CloseCodes.PROTOCOL_ERROR);
        }
        return frames;
    }

    @Override
    public void reset() {
        readingState = false;
        this.currentFrame = null;
    }

    @Override
    public CloseHandshakeType getCloseHandshakeType() {
        return CloseHandshakeType.NONE;
    }

    public ByteBuffer createBuffer() {
        return ByteBuffer.allocate(INITIAL_FAMESIZE);
    }

    public ByteBuffer increaseBuffer(ByteBuffer full) throws LimitExedeedException, WsIOException {
        full.flip();
        ByteBuffer newbuffer = ByteBuffer.allocate(checkAlloc(full.capacity() * 2));
        newbuffer.put(full);
        return newbuffer;
    }

    @Override
    public WebSocketProtocol copyInstance() {
        return new Draft_75();
    }
}

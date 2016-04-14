package com.dn15.websocket;

import java.math.BigInteger;
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
import com.dn15.websocket.message.CloseMessageBuilder;
import com.dn15.websocket.message.Message;
import com.dn15.websocket.message.MessageImpl;
import com.dn15.websocket.message.ServerHandshake;
import com.dn15.websocket.message.Message.Opcode;
import com.dn15.websocket.util.CharsetStringConverter;
//import com.dn15.websocket.util.Log;
import com.dn15.websocketapi.CloseReason.CloseCodes;

public abstract class WebSocketProtocolBase extends WebSocketProtocol {

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
    public static final byte START_OF_FRAME = (byte) 0x81;
    /**
     * The byte representing the end of a WebSocket text frame.
     */
    public static final byte END_OF_FRAME = (byte) 0xFF;

    /** Is only used to detect protocol violations */
    protected boolean readingState = false;

    protected List<Message> readyframes = new LinkedList<Message>();
    protected ByteBuffer currentFrame;

    protected byte[] thisMaskKey;

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

        ByteBuffer payLoad = framedata.getPayloadData();

        int payloadSize = payLoad.remaining();
        ByteBuffer b = ByteBuffer.allocate(payloadSize + 2);

        byte byte1 = START_OF_FRAME;
        b.put(byte1);
        byte byte2 = (byte) ((0 << 7) | payLoad.remaining());
        b.put(byte2);

        payLoad.mark();

        /**
         * for test
         */
        // Log.i("PayLoad Length: " + payLoad.remaining());
        // Log.i("Payload: " + new String(payLoad.array()));
        // Log.i("!!!!!!!!buffer length = " + Integer.toBinaryString(length));

        /**
         * mask key impl
         */
        // if (thisMaskKey == null) {
        // Random r = new Random();
        // thisMaskKey = new byte[4];
        // Log.i("KeyLength1: " + String.valueOf(thisMaskKey.length));
        // r.nextBytes(thisMaskKey);
        // Log.i("KeyLength2: " + String.valueOf(thisMaskKey.length));
        // for (byte bt : thisMaskKey) {
        // Log.i(String.valueOf(bt));
        // }
        // }
        // b.put(ByteBuffer.wrap(thisMaskKey));
        // for (int i = 0; i < payLoad.remaining(); i++) {
        // b.put((byte) (payLoad.get() ^ (byte) thisMaskKey[i % 4]));
        // }

        b.put(payLoad);

        payLoad.reset();

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
    public ClientHandshake postProcessHandshakeRequest(ClientHandshake request) throws InvalidHandshakeException {
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
        if (currentFrame == null) {
            currentFrame = createBuffer();
        }
        while (buffer.hasRemaining()) {
            byte newestByte = buffer.get();
            // frame buffer //TODO This code is very
            // expensive and slow
            if (!currentFrame.hasRemaining()) {
                currentFrame = increaseBuffer(currentFrame);
            }
            currentFrame.put(newestByte);
        }

        if (currentFrame != null) {
            currentFrame.flip();
            MessageImpl curframe = new MessageImpl();

            // curframe.setFin(true);
            // byte mask = (byte) 0x0F;
            // byte code = (byte) (currentFrame.get(0) & mask);
            // Message.Opcode opcode = Message.Opcode.getOpcode(code);
            // curframe.setOptcode(opcode);

            /**
             * frame data each line
             */
            // for (byte b : currentFrame.array())
            // Log.i(String.valueOf(b));
            // if (b != 0)
            // Log.i(Integer.toBinaryString(b));

            curframe = (MessageImpl) translateSingleFrame(currentFrame);

            /**
             * current frame
             */
            // Log.i("current: " + curframe.toString());
            
            readyframes.add(curframe);
            this.currentFrame = null;
            buffer.mark();
        } else
            return null;
        // if no error occurred this block will be reached
        /*
         * if( readingState ) { checkAlloc(currentFrame.position()+1); }
         */
        List<Message> frames = readyframes;
        readyframes = new LinkedList<Message>();
        return frames;
    }

    public Message translateSingleFrame(ByteBuffer buffer) throws WsIOException {
        int maxpacketsize = buffer.remaining();
        int realpacketsize = 2;

        if (maxpacketsize < realpacketsize)
            throw new WsIOException(CloseCodes.PROTOCOL_ERROR, "Imcomplete procotol...");
        byte b1 = buffer.get( /* 0 */);
        boolean FIN = b1 >> 8 != 0;
        byte rsv = (byte) ((b1 & ~(byte) 128) >> 4);
        if (rsv != 0)
            throw new InvalidFrameException("bad rsv " + rsv);
        byte b2 = buffer.get( /* 1 */);
        boolean MASK = (b2 & -128) != 0;
        byte[] maskskey = new byte[4];
        int payloadlength = (byte) (b2 & ~(byte) 128);
        Opcode optcode = Opcode.getOpcode(b1 & 15);

        if (!FIN) {
            if (optcode == Opcode.PING || optcode == Opcode.PONG || optcode == Opcode.CLOSING) {
                throw new InvalidFrameException("control frames may no be fragmented");
            }
        }

        if (payloadlength >= 0 && payloadlength <= 125) {
        } else {
            if (optcode == Opcode.PING || optcode == Opcode.PONG || optcode == Opcode.CLOSING) {
                throw new InvalidFrameException("more than 125 octets");
            }
            if (payloadlength == 126) {
                realpacketsize += 2; // additional length bytes
                if (maxpacketsize < realpacketsize)
                    throw new WsIOException(CloseCodes.PROTOCOL_ERROR, "Imcomplete procotol...");
                byte[] sizebytes = new byte[3];
                sizebytes[1] = buffer.get( /* 1 + 1 */);
                sizebytes[2] = buffer.get( /* 1 + 2 */);
                payloadlength = new BigInteger(sizebytes).intValue();
            } else {
                realpacketsize += 8; // additional length bytes
                if (maxpacketsize < realpacketsize)
                    throw new WsIOException(CloseCodes.PROTOCOL_ERROR, "Imcomplete procotol...");
                byte[] bytes = new byte[8];
                for (int i = 0; i < 8; i++) {
                    bytes[i] = buffer.get( /* 1 + i */);
                }
                long length = new BigInteger(bytes).longValue();
                if (length > Integer.MAX_VALUE) {
                    throw new WsIOException(CloseCodes.PROTOCOL_ERROR, "Payloadsize is to big...");
                } else {
                    payloadlength = (int) length;
                }
            }
        }

        // int maskskeystart = foff + realpacketsize;
        realpacketsize += (MASK ? 4 : 0);
        // int payloadstart = foff + realpacketsize;
        realpacketsize += payloadlength;

        if (maxpacketsize < realpacketsize)
            throw new WsIOException(CloseCodes.PROTOCOL_ERROR, "Imcomplete procotol...");
        ByteBuffer payload = ByteBuffer.allocate(checkAlloc(payloadlength));
        if (MASK) {
            buffer.get(maskskey);
            for (int i = 0; i < payloadlength; i++) {
                payload.put((byte) ((byte) buffer.get( /* payloadstart + i */) ^ (byte) maskskey[i % 4]));
            }
        } else {
            payload.put(buffer.array(), buffer.position(), payload.limit());
            buffer.position(buffer.position() + payload.limit());
        }

        Message frame;
        if (optcode == Opcode.CLOSING) {
            frame = new CloseMessageBuilder();
        } else {
            frame = new MessageImpl();
            frame.setFin(FIN);
            frame.setOptcode(optcode);
        }
        payload.flip();
        frame.setMaskKey(maskskey);
        thisMaskKey = maskskey;
        frame.setPayload(payload);
        return frame;
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
        return ByteBuffer.allocate(INITIAL_FRAMESIZE);
    }

    public ByteBuffer increaseBuffer(ByteBuffer full) throws LimitExedeedException, WsIOException {
        full.flip();
        ByteBuffer newbuffer = ByteBuffer.allocate(checkAlloc(full.capacity() * 2));
        newbuffer.put(full);
        return newbuffer;
    }
}

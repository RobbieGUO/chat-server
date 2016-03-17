package com.dn15.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.WritableByteChannel;

import com.dn15.websocket.Session.Role;

public class SocketChannelReader {

    public static boolean read(final ByteBuffer buf, WebSocketImpl ws, ByteChannel channel) throws IOException {
        buf.clear();
        int read = channel.read(buf);
        buf.flip();

        if (read == -1) {
            ws.eot();
            return false;
        }
        return read != 0;
    }

    /**
     * @see ByteChannel#readMore(ByteBuffer)
     * @return returns whether there is more data left which can be obtained via
     *         {@link #readMore(ByteBuffer, WebSocketImpl, ByteChannel)}
     **/
    public static boolean readMore(final ByteBuffer buf, WebSocketImpl ws, ByteChannel channel) throws IOException {
        buf.clear();
        int read = channel.read(buf);
        buf.flip();

        if (read == -1) {
            ws.eot();
            return false;
        }
        return read != 0;
    }

    public int write(ServerSocketChannel socketChannel, ByteBuffer src) throws IOException {
        int num = ((WritableByteChannel) socketChannel).write(src);
        return num;
    }

    /** Returns whether the whole outQueue has been flushed */
    public static boolean batch(WebSocketImpl ws, ByteChannel sockchannel) throws IOException {
        ByteBuffer buffer = ws.outQueue.peek();
        WritableByteChannel c = null;

        if (buffer == null) {
            //
        } else {
            do {// FIXME writing as much as possible is unfair!!
                /* int written = */
                sockchannel.write(buffer);
                if (buffer.remaining() > 0) {
                    return false;
                } else {
                    ws.outQueue.poll(); // Buffer finished. Remove it.
                    buffer = ws.outQueue.peek();
                }
            } while (buffer != null);
        }

        if (ws != null && ws.outQueue.isEmpty() && ws.isFlushAndClose() && ws.getRole() == Role.SERVER) {//
            synchronized (ws) {
                ws.closeConnection();
            }
        }
        return c == null;
    }
}

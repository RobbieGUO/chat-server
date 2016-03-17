package com.dn15.websocket;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.dn15.websocketapi.Endpoint;

public interface WebSocketFactory {

    /**
     * Allows to wrap the Socketchannel( key.channel() ) to insert a protocol layer( like ssl or proxy authentication) beyond the ws layer.
     * 
     * @param key
     *            a SelectionKey of an open SocketChannel.
     * @return The channel on which the read and write operations will be performed.<br>
     */
    public ByteChannel wrapChannel( SocketChannel channel, SelectionKey key ) throws IOException;

    public WebSocketImpl createWebSocket(Endpoint a, Socket s);
}

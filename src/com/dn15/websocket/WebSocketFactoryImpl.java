package com.dn15.websocket;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.dn15.websocketapi.Endpoint;


public class WebSocketFactoryImpl implements WebSocketFactory {

    @Override
    public WebSocketImpl createWebSocket(Endpoint a, Socket s) {
        return new WebSocketImpl(a, s);
    }

    @Override
    public SocketChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
        return (SocketChannel) channel;
    }

}

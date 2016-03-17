package com.dn15.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collection;

import com.dn15.websocket.Session;
import com.dn15.websocket.WebSocketImpl;
import com.dn15.websocket.WebSocketProtocol;
import com.dn15.websocket.WebSocketServer;
import com.dn15.websocket.exception.WsIOException;
import com.dn15.websocket.message.Handshake;
import com.dn15.websocket.message.Message;
import com.dn15.websocket.util.CharsetStringConverter;
import com.dn15.websocket.util.Log;
import com.dn15.websocketapi.DecodeException;
import com.dn15.websocketapi.CloseReason.CloseCode;

/**
 * A simple WebSocketServer implementation. Keeps track of a "chatroom".
 */
public class ChatServer extends WebSocketServer {

    public ChatServer(int port) throws UnknownHostException {
        super(new InetSocketAddress("localhost", port));
    }

    public ChatServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(Session conn) {
        this.sendToAll("new connection: " + conn.getResourceDescriptor());
        System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room!");
    }

    @Override
    public void onClose(Session conn, String closeReason) {
        this.sendToAll(conn + " has left the room!");
        System.out.println(conn + " has left the room!");
    }

    @Override
    public void onMessage(Session conn, String message) {
        this.sendToAll(message);
        System.out.println(conn + ": " + message);
    }

    public void onFragment(Session conn, Message fragment) {
        System.out.println("received fragment: " + fragment);
    }

    public static void main(String[] args) {
        WebSocketImpl.DEBUG = true;
        int port = 8887; // 843 flash policy port
        /*
         * try { port = Integer.parseInt(args[0]); } catch
         * (NumberFormatException ex) { ex.printStackTrace(); }
         */
        ChatServer s;
        try {
            s = new ChatServer(port);

            s.start();

            BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String in = sysin.readLine();
                if (in.equals("exit")) {
                    s.stop(0);
                    break;
                } else if (in.equals("restart")) {
                    s.stop(0);
                    s.start();
                    break;
                }
                s.sendToAll(in);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(Session conn, Throwable ex) {
        ex.printStackTrace();
        if (conn != null) {
            // some errors like port binding failed may not be assignable to a
            // specific websocket
        }
    }

    /**
     * Sends <var>text</var> to all currently connected Session clients.
     * 
     * @param text
     *            The String to send across the network.
     * @throws InterruptedException
     *             When socket related I/O errors occur.
     */
    public void sendToAll(String text) {
        Collection<Session> con = getSessions();
        if (con != null)
            synchronized (con) {
                for (Session c : con) {
                    c.send(text);
                }
            }
        else
            Log.i("No client!");
    }

    @Override
    public Handshake onHandshakeReceived(Session conn, WebSocketProtocol draft, Handshake request)
            throws DecodeException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onMessage(Session conn, ByteBuffer blob) {
        String message;
        try {
            message = CharsetStringConverter.stringUtf8(blob);
            this.sendToAll(message);
            System.out.println(conn + ": " + message);
        } catch (WsIOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void onCloseInitiated(Session ws, CloseCode code, String reason) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onClosing(Session ws, CloseCode code, String reason, boolean remote) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onPing(Session conn, Message f) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onPong(Session conn, Message f) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onWriteDemand(Session conn) {
        // TODO Auto-generated method stub
        
    }
}

package com.dn15.websocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.dn15.websocket.util.Log;
import com.dn15.websocketapi.CloseReason.CloseCodes;

public abstract class WebSocketServer extends ServerEndpoint implements Runnable {
    public static int DECODER_NUM = Runtime.getRuntime().availableProcessors() - 1;

    private final Collection<Session> sessions;

    private final InetSocketAddress address;

    private ServerSocketChannel serverChannel;

    private Selector selector;

    private Thread selectorThread;

    private List<WebSocketWorker> decoders;

    private List<WebSocketImpl> iqueue;

    private BlockingQueue<ByteBuffer> buffers;

    private int queueinvokes = 0;

    private volatile AtomicBoolean isClosed = new AtomicBoolean(false);

    private AtomicInteger queuesize = new AtomicInteger(0);

    private WebSocketFactory wsf = new WebSocketFactoryImpl();// TODO

    private BufferedReader stdIn;

    public WebSocketServer() {
        this(new InetSocketAddress(Session.DEFAULT_PORT), DECODER_NUM, null);
    }

    public WebSocketServer(InetSocketAddress ad) {
        this(ad, DECODER_NUM, null);
    }

    public WebSocketServer(InetSocketAddress ad, int dc) {
        this(ad, dc, null);
    }

    public WebSocketServer(InetSocketAddress ad, int decodercount, Collection<Session> sc) {
        if (ad == null || decodercount < 1)
            throw new IllegalArgumentException("address must not be null and you need at least 1 decoder");

        sessions = sc;
        address = ad;

        iqueue = new LinkedList<WebSocketImpl>();

        decoders = new ArrayList<WebSocketWorker>(decodercount);

        buffers = new LinkedBlockingQueue<ByteBuffer>();
        for (int i = 0; i < decodercount; i++) {
            WebSocketWorker ex = new WebSocketWorker();
            decoders.add(ex);
            ex.start();
        }
        stdIn = new BufferedReader(new InputStreamReader(System.in));

        if (sc == null) {
            sc = new ConcurrentLinkedQueue<Session>();
        }
    }

    @Override
    public abstract void onOpen(Session session);

    @Override
    public abstract void onClose(Session session, String closeReason);

    @Override
    public abstract void onError(Session session, Throwable thr);

    public abstract void onMessage(Session conn, String message);

    public void start() {
        if (selectorThread != null)
            throw new IllegalStateException(getClass().getName() + " can only be started once.");
        new Thread(this).start();
        Log.i("Server started on port: " + getPort());
    }

    public void stop(int timeout) throws InterruptedException {
        if (!isClosed.compareAndSet(false, true))
            return;

        List<Session> socketsToClose = null;

        // copy the connections in a list (prevent callback deadlocks)
        synchronized (sessions) {
            socketsToClose = new ArrayList<Session>(sessions);
        }

        for (Session ws : socketsToClose)
            ws.close(CloseCodes.GOING_AWAY);

        synchronized (this) {
            if (selectorThread != null)
                if (selectorThread != Thread.currentThread()) {
                    if (socketsToClose.size() > 0)
                        selectorThread.join(timeout);// isclosed will tell the
                                                     // selectorThread to go
                                                     // down after the last
                                                     // connection was closed
                    selectorThread.interrupt();// in case the selectorThread did
                                               // not terminate in time we send
                                               // the interrupt
                    selectorThread.join();
                }
        }
    }

    private void handleIOException(SelectionKey key, Session conn, IOException ex) {
        // onWebsocketError( conn, ex );// conn may be null here
        if (conn != null) {
            onError(conn, ex);
            conn.closeConnection(CloseCodes.CLOSED_ABNORMALLY, ex.getMessage());
        } else if (key != null) {
            SelectableChannel channel = key.channel();
            if (channel != null && channel.isOpen()) { // this could be the case
                                                       // if the IOException ex
                                                       // is a SSLException
                try {
                    channel.close();
                } catch (IOException e) {
                    // there is nothing that must be done here
                }
                System.out.println("Connection closed because of" + ex);
            }
        }
    }

    private void handleFatal(Session conn, Exception e) {
        onError(conn, e);
        try {
            stop(0);
        } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
            onError(null, e1);
        }
    }

    protected void allocateBuffers(Session c) throws InterruptedException {
        if (queuesize.get() >= 2 * decoders.size() + 1) {
            return;
        }
        queuesize.incrementAndGet();
        buffers.put(ByteBuffer.allocate(WebSocketImpl.RCV_BUF_SIZE));
    }

    private void pushBuffer(ByteBuffer buf) throws InterruptedException {
        if (buffers.size() > queuesize.intValue())
            return;
        buffers.put(buf);
        Log.i("Push buffer: " + buffers.size());
    }

    private void queue(WebSocketImpl ws) throws InterruptedException {
        if (ws.workerThread == null) {
            ws.workerThread = decoders.get(queueinvokes % decoders.size());
            queueinvokes++;
        }
        ws.workerThread.put(ws);
    }

    /**
     * Initialized function of ServerSocketChannel
     * 
     * @param address
     */
    private void init(InetSocketAddress address) {
        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(address.getPort()));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            handleFatal(null, e);
            return;
        }
    }

    /**
     * Listenning function
     */
    private void listen() {
        SelectionKey key = null;
        WebSocketImpl conn = null;
        try {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> i = keys.iterator();

            while (i.hasNext()) {
                key = i.next();

                if (!key.isValid())
                    continue;
                process(key, conn);
                i.remove();
            }

            while (!iqueue.isEmpty()) {
                conn = iqueue.remove(0);
                ByteChannel c = conn.channel;
                ByteBuffer buf = buffers.take();
                try {
                    if (SocketChannelReader.readMore(buf, conn, c))
                        iqueue.add(conn);
                    if (buf.hasRemaining()) {
                        conn.inQueue.put(buf);
                        queue(conn);
                    } else {
                        pushBuffer(buf);
                    }
                } catch (IOException e) {
                    pushBuffer(buf);
                    throw e;
                }

            }
        } catch (CancelledKeyException e) {
            // an other thread may cancel the key
        } catch (ClosedByInterruptException e) {
            return; // do the same stuff as when InterruptedException is
                    // thrown
        } catch (IOException ex) {
            if (key != null)
                key.cancel();
            handleIOException(key, conn, ex);
        } catch (InterruptedException e) {
            return;// FIXME controlled shutdown (e.g. take care of
                   // buffermanagement)
        }
    }

    /**
     * SelectionKey function
     * 
     * @param key
     * @param conn
     * @throws IOException
     * @throws InterruptedException
     */
    private void process(SelectionKey key, WebSocketImpl conn) throws IOException, InterruptedException {
        if (key.isAcceptable()) {
            Log.i("key acceptable...");

            ServerSocketChannel keyChannel = (ServerSocketChannel) key.channel();
            SocketChannel clientChannel = keyChannel.accept();
            clientChannel.configureBlocking(false);
            // send back message here
            clientChannel.register(selector, SelectionKey.OP_READ);

            WebSocketImpl w = wsf.createWebSocket(this, clientChannel.socket());
            w.key = clientChannel.register(selector, SelectionKey.OP_READ, w);
            w.channel = wsf.wrapChannel(clientChannel, w.key);
            allocateBuffers(conn);

        } else if (key.isReadable()) {
            Log.i("key readable...");

            conn = (WebSocketImpl) key.attachment();
            ByteBuffer buf = buffers.take();
            try {
                if (SocketChannelReader.read(buf, conn, conn.channel)) {
                    if (buf.hasRemaining()) {
                        conn.inQueue.put(buf);
                        queue(conn);
                        if (conn.channel instanceof ByteChannel) {
                            iqueue.add(conn);
                        }
                    } else
                        pushBuffer(buf);
                } else {
                    pushBuffer(buf);
                }
            } catch (IOException e) {
                pushBuffer(buf);
                throw e;
            }

            if (key.isWritable()) {
                Log.i("key writable...");

                conn = (WebSocketImpl) key.attachment();
                if (SocketChannelReader.batch(conn, conn.channel)) {
                    if (key.isValid())
                        key.interestOps(SelectionKey.OP_READ);
                }
            }
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            if (selectorThread != null)
                throw new IllegalStateException(getClass().getName() + " can only be started once.");
            selectorThread = Thread.currentThread();
            if (isClosed.get())
                return;
        }
        selectorThread.setName("WebsocketSelector" + selectorThread.getId());

        init(address);
        Log.i("Init complete...");

        try {
            while (!selectorThread.isInterrupted())
                listen();
        } catch (RuntimeException e) {
            // should hopefully never occur
            handleFatal(null, e);
        } finally {
            if (decoders != null) {
                for (WebSocketWorker w : decoders) {
                    w.interrupt();
                }
            }
            if (serverChannel != null) {
                try {
                    serverChannel.close();
                } catch (IOException e) {
                    onError(null, e);
                }
            }
        }
    }

    public Collection<Session> getSessions() {
        return sessions;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public int getPort() {
        return address.getPort();
    }

    private Socket getSocket(Session conn) {
        WebSocketImpl impl = (WebSocketImpl) conn;
        return ((SocketChannel) impl.key.channel()).socket();
    }

    public InetSocketAddress getLocalSocketAddress(Session conn) {
        return (InetSocketAddress) getSocket(conn).getLocalSocketAddress();
    }

    public InetSocketAddress getRemoteSocketAddress(Session conn) {
        return (InetSocketAddress) getSocket(conn).getRemoteSocketAddress();
    }

    public class WebSocketWorker extends Thread {

        private BlockingQueue<WebSocketImpl> iqueue;

        public WebSocketWorker() {
            Log.i("Worker " + getId() + "is on...");

            iqueue = new LinkedBlockingQueue<WebSocketImpl>();
            setName("WebSocketWorker-" + getId());
            setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    getDefaultUncaughtExceptionHandler().uncaughtException(t, e);
                }
            });
        }

        public void put(WebSocketImpl ws) throws InterruptedException {
            iqueue.put(ws);
        }

        @Override
        public void run() {
            Log.i("Worker " + getId() + " is working...");
            WebSocketImpl ws = null;
            try {
                while (true) {
                    ByteBuffer buf = null;
                    ws = iqueue.take();
                    buf = ws.inQueue.poll();
                    assert (ws != null && buf != null);
                    try {
                        Log.i("Worker " + getId() + " decode buffer...");
                        ws.decode(buf);
                    } catch (Exception e) {
                        System.err.println("Error while reading from remote connection: " + e);
                    } finally {
                        pushBuffer(buf);
                    }
                }
            } catch (InterruptedException e) {
            } catch (RuntimeException e) {
                handleFatal(ws, e);
            }
        }
    }
}

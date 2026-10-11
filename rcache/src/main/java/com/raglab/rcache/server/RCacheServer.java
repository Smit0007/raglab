package com.raglab.rcache.server;

import com.raglab.rcache.command.CommandDispatcher;
import com.raglab.rcache.protocol.RcpCodec;
import com.raglab.rcache.protocol.RcpFrame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Non-blocking Java NIO Selector-based TCP Server for RCache.
 * Listens on port 6380 (default) and executes incoming RCP commands asynchronously.
 */
public class RCacheServer implements Runnable, AutoCloseable {

    private final String bindAddress;
    private final int port;
    private final CommandDispatcher dispatcher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Thread workerThread;

    public RCacheServer(String bindAddress, int port, CommandDispatcher dispatcher) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.dispatcher = dispatcher;
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;

        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(new InetSocketAddress(bindAddress, port));
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.running.set(true);
        this.workerThread = new Thread(this, "rcache-nio-server");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    @Override
    public void run() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                if (selector.select(500) == 0) {
                    continue;
                }

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            } catch (ClosedSelectorException e) {
                break;
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("RCache NIO loop exception: " + e.getMessage());
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = ssc.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ, new ClientSession(clientChannel));
        }
    }

    private void handleRead(SelectionKey key) {
        ClientSession session = (ClientSession) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        try {
            int bytesRead = channel.read(session.readBuffer);
            if (bytesRead == -1) {
                key.cancel();
                channel.close();
                return;
            }

            if (bytesRead > 0) {
                session.processIncoming(dispatcher);
                if (session.hasPendingWrites()) {
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
            }
        } catch (IOException e) {
            key.cancel();
            try {
                channel.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleWrite(SelectionKey key) {
        ClientSession session = (ClientSession) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        try {
            session.flushWrites(channel);
            if (!session.hasPendingWrites()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            key.cancel();
            try {
                channel.close();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) return;

        if (selector != null) {
            try {
                selector.wakeup();
                selector.close();
            } catch (IOException ignored) {}
        }

        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (IOException ignored) {}
        }

        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    /**
     * Session state associated with an active TCP connection.
     */
    private static class ClientSession {
        final SocketChannel channel;
        final ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);
        final ConcurrentLinkedQueue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
        ByteBuffer currentWriteBuffer = null;

        ClientSession(SocketChannel channel) {
            this.channel = channel;
        }

        void processIncoming(CommandDispatcher dispatcher) {
            readBuffer.flip();

            while (readBuffer.hasRemaining()) {
                RcpFrame frame;
                try {
                    frame = RcpCodec.decode(readBuffer);
                } catch (Exception e) {
                    // Send error frame and close
                    byte[] errBytes = RcpCodec.encode(new RcpFrame.ErrorFrame("ERR " + e.getMessage()));
                    writeQueue.add(ByteBuffer.wrap(errBytes));
                    readBuffer.clear();
                    break;
                }

                if (frame == null) {
                    // Incomplete frame in buffer, wait for more bytes
                    break;
                }

                // Process decoded frame
                RcpFrame response = dispatcher.dispatch(frame);
                byte[] responseBytes = RcpCodec.encode(response);
                writeQueue.add(ByteBuffer.wrap(responseBytes));
            }

            readBuffer.compact();
        }

        boolean hasPendingWrites() {
            return (currentWriteBuffer != null && currentWriteBuffer.hasRemaining()) || !writeQueue.isEmpty();
        }

        void flushWrites(SocketChannel channel) throws IOException {
            while (hasPendingWrites()) {
                if (currentWriteBuffer == null || !currentWriteBuffer.hasRemaining()) {
                    currentWriteBuffer = writeQueue.poll();
                    if (currentWriteBuffer == null) break;
                }

                channel.write(currentWriteBuffer);
                if (currentWriteBuffer.hasRemaining()) {
                    // Socket send buffer full, stop and wait for OP_WRITE
                    break;
                }
            }
        }
    }
}


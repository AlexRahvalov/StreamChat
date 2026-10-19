package by.rahvalov.streamchat.client.tiktok;

import by.rahvalov.streamchat.StreamChatMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local HTTP CONNECT proxy. Java connects to one resolved address and waits
 * out the whole timeout when that address is dead. This proxy receives the
 * hostname and opens the first IPv4 address that accepts a connection.
 */
final class TikTokFailoverProxy {
    private static final int CONNECT_MILLIS = 2000;
    private static final Object START = new Object();
    private static volatile int port = -1;
    private static volatile ServerSocket server;

    private TikTokFailoverProxy() {
    }

    static int port() throws IOException {
        ServerSocket current = server;
        if (current != null && !current.isClosed() && port > 0) {
            return port;
        }
        synchronized (START) {
            current = server;
            if (current != null && !current.isClosed() && port > 0) {
                return port;
            }
            ServerSocket bound = new ServerSocket();
            bound.bind(new InetSocketAddress("127.0.0.1", 0));
            server = bound;
            port = bound.getLocalPort();
            Thread thread = new Thread(() -> acceptLoop(bound), "TikTokFailover-Proxy");
            thread.setDaemon(true);
            thread.start();
            StreamChatMod.LOGGER.info("TikTok failover proxy on 127.0.0.1:{}", port);
            return port;
        }
    }

    private static void acceptLoop(ServerSocket bound) {
        while (!bound.isClosed()) {
            try {
                Socket client = bound.accept();
                Thread worker = new Thread(() -> handle(client), "TikTokFailover-Conn");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (!bound.isClosed()) {
                    StreamChatMod.LOGGER.warn("TikTok failover proxy accept failed: {}", e.getMessage());
                }
                return;
            }
        }
    }

    private static void handle(Socket client) {
        try (client) {
            client.setSoTimeout(10_000);
            InputStream in = client.getInputStream();
            String request = readHeaders(in);
            String target = connectTarget(request);
            if (target == null) {
                write(client, "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n");
                return;
            }
            int colon = target.lastIndexOf(':');
            String host = colon > 0 ? target.substring(0, colon) : target;
            int remotePort = colon > 0 ? Integer.parseInt(target.substring(colon + 1)) : 443;
            Socket remote = open(host, remotePort);
            if (remote == null) {
                write(client, "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n");
                return;
            }
            client.setSoTimeout(0);
            write(client, "HTTP/1.1 200 Connection Established\r\n\r\n");
            try (remote) {
                Thread uplink = new Thread(() -> copy(in, remote), "TikTokFailover-Up");
                uplink.setDaemon(true);
                uplink.start();
                copy(remote.getInputStream(), client);
                uplink.join(1000);
            }
        } catch (Exception e) {
            StreamChatMod.LOGGER.debug("TikTok failover tunnel closed: {}", e.toString());
        }
    }

    private static String connectTarget(String request) {
        if (request == null || !request.regionMatches(true, 0, "CONNECT ", 0, "CONNECT ".length())) {
            return null;
        }
        int end = request.indexOf(' ', "CONNECT ".length());
        if (end < 0) {
            return null;
        }
        return request.substring("CONNECT ".length(), end).trim();
    }

    private static Socket open(String host, int port) {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            if (isIpv4Literal(host)) {
                addresses.add(InetAddress.getByName(host));
            } else {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (address instanceof Inet4Address) {
                        addresses.add(address);
                    }
                }
            }
        } catch (IOException e) {
            StreamChatMod.LOGGER.warn("TikTok DNS {} failed: {}", host, e.getMessage());
            return null;
        }
        Socket socket = connectAny(host, port, addresses);
        if (socket == null) {
            StreamChatMod.LOGGER.warn("No reachable IPv4 for {}:{}", host, port);
        }
        return socket;
    }

    private static boolean isIpv4Literal(String host) {
        int dots = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') {
                dots++;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return dots == 3;
    }

    private static Socket connectAny(String host, int port, List<InetAddress> addresses) {
        if (addresses.isEmpty()) {
            return null;
        }
        AtomicReference<Socket> winner = new AtomicReference<>();
        CountDownLatch pending = new CountDownLatch(addresses.size());
        for (InetAddress address : addresses) {
            Thread thread = new Thread(() -> {
                Socket socket = new Socket();
                try {
                    if (winner.get() != null) {
                        socket.close();
                        return;
                    }
                    socket.connect(new InetSocketAddress(address, port), CONNECT_MILLIS);
                    if (winner.compareAndSet(null, socket)) {
                        StreamChatMod.LOGGER.info("TikTok {} via {}", host, address.getHostAddress());
                    } else {
                        socket.close();
                    }
                } catch (IOException e) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                        // already closed
                    }
                } finally {
                    pending.countDown();
                }
            }, "TikTokFailover-Dial");
            thread.setDaemon(true);
            thread.start();
        }
        try {
            long end = System.currentTimeMillis() + CONNECT_MILLIS + 250L;
            while (winner.get() == null && System.currentTimeMillis() < end) {
                if (pending.await(50, TimeUnit.MILLISECONDS)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return winner.get();
    }

    private static String readHeaders(InputStream in) throws IOException {
        StringBuilder header = new StringBuilder();
        int matched = 0;
        int[] tail = {'\r', '\n', '\r', '\n'};
        while (header.length() < 8192) {
            int value = in.read();
            if (value < 0) {
                break;
            }
            header.append((char) value);
            matched = value == tail[matched] ? matched + 1 : (value == tail[0] ? 1 : 0);
            if (matched == tail.length) {
                break;
            }
        }
        return header.toString();
    }

    private static void write(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static void copy(InputStream in, Socket destination) {
        try {
            OutputStream out = destination.getOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
            // peer closed
        } finally {
            try {
                destination.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }
}

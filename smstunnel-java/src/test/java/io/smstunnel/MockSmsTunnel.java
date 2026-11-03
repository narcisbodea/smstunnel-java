package io.smstunnel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A local HTTP server (the JDK's own) that answers with queued responses and records every request. */
final class MockSmsTunnel implements AutoCloseable {

    static final class Recorded {
        final String method;
        final String path;
        final String query;
        final Map<String, String> headers;
        final String body;

        Recorded(String method, String path, String query, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        String header(String name) {
            return headers.get(name.toLowerCase());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> json() {
            return (Map<String, Object>) Json.parse(body);
        }
    }

    private static final class Reply {
        final int status;
        final String body;
        final Map<String, String> headers;
        final long delayMillis;

        Reply(int status, String body, Map<String, String> headers, long delayMillis) {
            this.status = status;
            this.body = body;
            this.headers = headers;
            this.delayMillis = delayMillis;
        }
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());

    MockSmsTunnel() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    MockSmsTunnel reply(int status, String body) {
        return reply(status, body, Collections.emptyMap(), 0);
    }

    MockSmsTunnel reply(int status, String body, Map<String, String> headers) {
        return reply(status, body, headers, 0);
    }

    synchronized MockSmsTunnel reply(int status, String body, Map<String, String> headers, long delayMillis) {
        replies.addLast(new Reply(status, body, headers, delayMillis));
        return this;
    }

    List<Recorded> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    Recorded lastRequest() {
        List<Recorded> all = requests();
        if (all.isEmpty()) {
            throw new AssertionError("no request was made");
        }
        return all.get(all.size() - 1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                exchange.getRequestURI().getRawQuery(), headers, body));

        Reply reply;
        synchronized (this) {
            reply = replies.pollFirst();
        }
        if (reply == null) {
            reply = new Reply(500, "{\"message\":\"no reply queued in the test\"}", Collections.emptyMap(), 0);
        }
        if (reply.delayMillis > 0) {
            try {
                Thread.sleep(reply.delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = reply.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        reply.headers.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        try {
            exchange.sendResponseHeaders(reply.status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        } catch (IOException clientGone) {
            // the client timed out and closed the connection
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}

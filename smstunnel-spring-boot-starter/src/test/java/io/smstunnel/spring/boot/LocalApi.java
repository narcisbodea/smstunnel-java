package io.smstunnel.spring.boot;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A local stand-in for the SMSTunnel API that accepts every text and remembers the last request. */
final class LocalApi implements AutoCloseable {

    private final HttpServer server;
    volatile String lastBody;
    final Map<String, String> lastHeaders = new ConcurrentHashMap<>();

    LocalApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                lastBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            lastHeaders.clear();
            exchange.getRequestHeaders().forEach((name, values) -> lastHeaders.put(name.toLowerCase(), values.get(0)));
            byte[] reply = "{\"success\":true,\"messageId\":\"m1\",\"data\":{\"messageId\":\"m1\",\"branded\":false}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(reply);
            }
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

package org.cloudServices.servers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.cloudServices.global.db.Memdb;
import org.cloudServices.infra.computeUnit.ComputeUnit;
import org.cloudServices.shared.interfaces.GatewayServerIntegration;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

public class GatewayServer {
    private static final int PORT = 8000;

    public static void start() {
        new Thread(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
                server.createContext("/", new ProxyHandler());

                server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
                server.start();
            } catch (IOException e) {
                System.err.println("Failed to start Ingress Gateway: " + e.getMessage());
            }
         }).start();
    }

    private static class ProxyHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestUri = exchange.getRequestURI();
            String path = requestUri.getRawPath();

            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            int firstSlash = path.indexOf('/');
            String instanceId = (firstSlash == -1) ? path : path.substring(0, firstSlash);
            String subpath = (firstSlash == -1) ? "/" : path.substring(firstSlash);

            if (instanceId.isBlank()) {
                String error = "400 Bad Request: Missing instance id in URL path (expected http://localhost:" + PORT + "/{instanceId}/[path])";
                sendResponse(exchange, 400, error);
                return;
            }

            // Resolve instance by ID or Name
            GatewayServerIntegration si = Memdb.getInstance(instanceId);

            if (si == null) {
                String notFound = "404 Not Found: No ComputeUnit found with ID or Name: " + instanceId;
                sendResponse(exchange, 404, notFound);
                return;
            }

            String rawQuery = requestUri.getRawQuery();
            String targetPath = subpath + (rawQuery != null ? "?" + rawQuery : "");

            // Pass the request directly to the instance
            si.dispatch(exchange, targetPath);
        }

        private static void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
            byte[] bytes = message.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}


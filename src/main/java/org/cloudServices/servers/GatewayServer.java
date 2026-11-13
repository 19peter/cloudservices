package org.cloudServices.servers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.cloudServices.global.db.Memdb;
import org.cloudServices.global.dns.Dns;
import org.cloudServices.shared.interfaces.GatewayServerIntegration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
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

            if (path == null || path.isBlank()) {
                path = "/";
            }

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

            GatewayServerIntegration si = Memdb.getInstance(instanceId);
            if (si == null) {
                String notFound = "404 Not Found: No ComputeUnit found with ID or Name: " + instanceId;
                sendResponse(exchange, 404, notFound);
                return;
            }

            if (!si.acquireSlot()) {
                sendResponse(exchange, 429, "429 Too Many Requests: ComputeUnit " + instanceId + " is unavailable or at capacity");
                return;
            }

            try {
                String backendUrl = Dns.resolve(instanceId);
                if (backendUrl == null || backendUrl.isBlank()) {
                    sendResponse(exchange, 502, "502 Bad Gateway: No backend address resolved for instance " + instanceId);
                    return;
                }

                String rawQuery = requestUri.getRawQuery();
                String targetPath = subpath + (rawQuery != null ? "?" + rawQuery : "");
                String targetUrl = backendUrl + (targetPath == null || targetPath.isBlank() ? "/" : targetPath);
                proxyHttp(exchange, targetUrl);
            } finally {
                si.releaseSlot();
            }
        }

        private static void proxyHttp(HttpExchange exchange, String targetUrlString) throws IOException {
            URI targetUri = URI.create(targetUrlString);
            HttpURLConnection backendConnection = (HttpURLConnection) targetUri.toURL().openConnection();
            backendConnection.setRequestMethod(exchange.getRequestMethod());
            backendConnection.setConnectTimeout(5000);
            backendConnection.setReadTimeout(10000);

            exchange.getRequestHeaders().forEach((header, values) -> {
                if (!"Host".equalsIgnoreCase(header)) {
                    for (String value : values) {
                        backendConnection.addRequestProperty(header, value);
                    }
                }
            });
            backendConnection.addRequestProperty("Host", targetUri.getHost() + (targetUri.getPort() > -1 ? ":" + targetUri.getPort() : ""));

            String method = exchange.getRequestMethod();
            if (method != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                    || "PATCH".equalsIgnoreCase(method) || exchange.getRequestBody().available() > 0)) {
                backendConnection.setDoOutput(true);
                try (InputStream requestBody = exchange.getRequestBody();
                     OutputStream backendOutput = backendConnection.getOutputStream()) {
                    requestBody.transferTo(backendOutput);
                }
            }

            int responseCode = backendConnection.getResponseCode();
            Map<String, java.util.List<String>> responseHeaders = backendConnection.getHeaderFields();
            responseHeaders.forEach((headerName, values) -> {
                if (headerName != null && !"Transfer-Encoding".equalsIgnoreCase(headerName)) {
                    for (String value : values) {
                        exchange.getResponseHeaders().add(headerName, value);
                    }
                }
            });

            exchange.sendResponseHeaders(responseCode, 0);
            try (InputStream backendInput = backendConnection.getInputStream();
                 OutputStream clientOutput = exchange.getResponseBody()) {
                backendInput.transferTo(clientOutput);
            } catch (IOException ignored) {
                try (InputStream backendError = backendConnection.getErrorStream();
                     OutputStream clientOutput = exchange.getResponseBody()) {
                    if (backendError != null) {
                        backendError.transferTo(clientOutput);
                    }
                }
            }
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


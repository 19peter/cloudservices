package example.staticapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public final class StaticApiServer {
    private static final Map<Route, Response> ROUTES = Map.of(
            new Route("GET", "/get"),
            new Response(200, "{\"message\":\"static GET response\",\"service\":\"static-api\"}"),
            new Route("GET", "/get/health"),
            new Response(200, "{\"status\":\"UP\"}"),
            new Route("GET", "/get/items"),
            new Response(200, "[{\"id\":1,\"name\":\"alpha\"},{\"id\":2,\"name\":\"beta\"}]"),
            new Route("GET", "/get/slow"),
            new Response(200, "{\"message\":\"static delayed GET response\"}"),
            new Route("POST", "/post"),
            new Response(200, "{\"message\":\"static POST response\",\"received\":true}"),
            new Route("POST", "/post/created"),
            new Response(201, "{\"id\":\"item-123\",\"status\":\"created\"}")
    );

    private StaticApiServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", StaticApiServer::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("Static API listening on 127.0.0.1:" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    private static int resolvePort(String[] args) {
        String portValue = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--server.port=")) {
                portValue = args[i].substring("--server.port=".length());
            } else if (args[i].equals("--server.port")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value after --server.port");
                }
                portValue = args[++i];
            }
        }

        if (portValue == null || portValue.isBlank()) {
            portValue = System.getenv("PORT");
        }
        if (portValue == null || portValue.isBlank()) {
            portValue = System.getenv("SERVER_PORT");
        }
        if (portValue == null || portValue.isBlank()) {
            portValue = "8080";
        }

        int port = Integer.parseInt(portValue);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535: " + port);
        }
        return port;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();
        Response response = ROUTES.get(new Route(method, path));

        if (response == null) {
            boolean knownPath = ROUTES.keySet().stream().anyMatch(route -> route.path().equals(path));
            if (knownPath) {
                exchange.getResponseHeaders().set("Allow", allowedMethods(path));
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            } else {
                sendJson(exchange, 404, "{\"error\":\"not found\"}");
            }
            return;
        }

        if (path.equals("/get/slow")) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendJson(exchange, 503, "{\"error\":\"request interrupted\"}");
                return;
            }
        }

        sendJson(exchange, response.status(), response.body());
    }

    private static String allowedMethods(String path) {
        return ROUTES.keySet().stream()
                .filter(route -> route.path().equals(path))
                .map(Route::method)
                .sorted()
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private record Route(String method, String path) {
    }

    private record Response(int status, String body) {
    }
}

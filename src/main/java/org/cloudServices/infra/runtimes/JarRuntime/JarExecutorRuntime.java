package org.cloudServices.infra.runtimes.JarRuntime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sun.net.httpserver.HttpExchange;
import org.cloudServices.global.port.PortAllocator;
import org.cloudServices.infra.runtimes.RuntimeInterface;
import org.cloudServices.infra.runtimes.Workload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JarExecutorRuntime implements RuntimeInterface {
    private static final Logger logger = LogManager.getLogger(JarExecutorRuntime.class);

    private Process process;
    private boolean booted = false;
    private JarWorkload workload;
    private ProcessBuilder processBuilder;
    private int allocatedPort = 0;

    public JarExecutorRuntime(JarWorkload workload) {
        this.workload = workload;
    }

    @Override
    public synchronized void boot() {
        if (booted) {
            logger.info("JarExecutorRuntime is already booted.");
            return;
        }

        if (this.workload == null) {
            throw new IllegalArgumentException("Workload cannot be null");
        }

        if (this.workload.jarPath() == null || this.workload.jarPath().isBlank()) {
            throw new IllegalArgumentException("Jar path must not be null or blank");
        }

        File jarFile = new File(this.workload.jarPath());
        if (!jarFile.exists()) {
            throw new IllegalArgumentException("Jar file not found at path: " + this.workload.jarPath());
        }

        if (process != null && process.isAlive()) {
            throw new IllegalStateException("A process is already running on this runtime (PID: " + process.pid() + ")");
        }

        if (this.allocatedPort <= 0) {
            this.allocatedPort = PortAllocator.allocate();
        }

        List<String> command = new ArrayList<>();
        command.add(resolveJavaBinary());

        if (this.workload.jvmOptions() != null && !this.workload.jvmOptions().isEmpty()) {
            command.addAll(this.workload.jvmOptions());
        }

        command.add("-jar");
        command.add(jarFile.getAbsolutePath());

        // Inject dynamic port argument to override hardcoded server.port in application.properties
        command.add("--server.port=" + this.allocatedPort);

        if (this.workload.args() != null && !this.workload.args().isEmpty()) {
            command.addAll(this.workload.args());
        }

        this.processBuilder = new ProcessBuilder(command);

        if (this.workload.environment() != null && !this.workload.environment().isEmpty()) {
            this.processBuilder.environment().putAll(this.workload.environment());
        }

        // Inject environment variables (supports Spring Boot SERVER_PORT and standard cloud PORT)
        this.processBuilder.environment().put("SERVER_PORT", String.valueOf(this.allocatedPort));
        this.processBuilder.environment().put("PORT", String.valueOf(this.allocatedPort));

        this.booted = true;
        logger.info("JarExecutorRuntime booted successfully on port: {}.", this.allocatedPort);
    }

    @Override
    public synchronized String submit() {
        if (this.workload == null) {
            throw new IllegalArgumentException("Workload cannot be null");
        }

        if (!booted) {
            boot();
        }

        if (process != null && process.isAlive()) {
            throw new IllegalStateException("A process is already running on this runtime (PID: " + process.pid() + ")");
        }

        try {
            this.process = processBuilder.start();
            long pid = process.pid();
            logger.info("Started JarWorkload process with PID: {} for jar: {}", pid, this.workload.jarPath());
            return String.valueOf(pid);
        } catch (IOException e) {
            logger.error("Failed to start JarWorkload process for jar: " + this.workload.jarPath(), e);
            throw new RuntimeException("Failed to start JarWorkload process: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            long pid = process.pid();
            logger.info("Terminating process with PID: {}", pid);
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    logger.warn("Process {} did not terminate within timeout; forcibly destroying.", pid);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for process {} to stop; destroying forcibly.", pid);
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            logger.info("Process {} terminated.", pid);
        } else {
            logger.info("No active process to stop.");
        }

        if (this.allocatedPort > 0) {
            PortAllocator.release(this.allocatedPort);
            this.allocatedPort = 0;
        }
    }

    public synchronized int getPort() {
        return allocatedPort;
    }

    public synchronized Process getProcess() {
        return process;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public synchronized boolean isBooted() {
        return booted;
    }

    public synchronized JarWorkload getWorkload() {
        return workload;
    }

    public synchronized void setWorkload(JarWorkload workload) {
        this.workload = workload;
        this.booted = false;
    }

    private String resolveJavaBinary() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path binDir = Path.of(javaHome, "bin");
            Path exePath = binDir.resolve("java.exe");
            if (Files.exists(exePath)) {
                return exePath.toString();
            }
            Path binPath = binDir.resolve("java");
            if (Files.exists(binPath)) {
                return binPath.toString();
            }
        }
        return "java";
    }

    /**
     * Forwards an incoming HTTP exchange directly to the workload process running on its allocated port.
     *
     * @param exchange the incoming HTTP exchange
     * @param subpath  the subpath and query parameters
     */
    public void forwardRequest(HttpExchange exchange, String subpath) {
        if (allocatedPort <= 0 || process == null || !process.isAlive()) {
            sendError(exchange, 502, "502 Bad Gateway: Workload process is not running or port is unallocated");
            return;
        }

        try {
            String targetPath = (subpath == null || subpath.isBlank()) ? "/" : (subpath.startsWith("/") ? subpath : "/" + subpath);
            String targetUrlStr = "http://127.0.0.1:" + allocatedPort + targetPath;
            URL targetUrl = URI.create(targetUrlStr).toURL();
            HttpURLConnection backendConn = (HttpURLConnection) targetUrl.openConnection();
            backendConn.setRequestMethod(exchange.getRequestMethod());

            // Copy incoming headers
            exchange.getRequestHeaders().forEach((k, v) -> {
                if (!k.equalsIgnoreCase("Host")) {
                    for (String val : v) {
                        backendConn.addRequestProperty(k, val);
                    }
                }
            });
            backendConn.addRequestProperty("Host", "127.0.0.1:" + allocatedPort);

            // Stream body to backend if present
            String method = exchange.getRequestMethod().toUpperCase();
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || exchange.getRequestBody().available() > 0) {
                backendConn.setDoOutput(true);
                try (InputStream is = exchange.getRequestBody();
                     OutputStream os = backendConn.getOutputStream()) {
                    is.transferTo(os);
                }
            }

            // Stream backend response back to client
            int responseCode = backendConn.getResponseCode();
            backendConn.getHeaderFields().forEach((headerName, headerVals) -> {
                if (headerName != null && !headerName.equalsIgnoreCase("Transfer-Encoding")) {
                    for (String val : headerVals) {
                        exchange.getResponseHeaders().add(headerName, val);
                    }
                }
            });

            exchange.sendResponseHeaders(responseCode, 0);
            try (InputStream is = backendConn.getInputStream();
                 OutputStream os = exchange.getResponseBody()) {
                is.transferTo(os);
            } catch (IOException err) {
                try (InputStream es = backendConn.getErrorStream();
                     OutputStream os = exchange.getResponseBody()) {
                    if (es != null) {
                        es.transferTo(os);
                    }
                }
            }
        } catch (Exception e) {
            sendError(exchange, 502, "502 Bad Gateway: Error connecting to workload process: " + e.getMessage());
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) {
        try {
            byte[] bytes = message.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException ignored) {}
    }
}

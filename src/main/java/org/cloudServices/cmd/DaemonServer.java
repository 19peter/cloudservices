package org.cloudServices.cmd;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;


public class DaemonServer {
    private static final int PORT = 9090;
    private static final Logger logger = LogManager.getLogger(DaemonServer.class);

    public static void start() {
        new Thread(() -> {
           try (ServerSocket serverSocket = new ServerSocket(PORT)) {
               while (true) {
                   try {
                       Socket clientSocket = serverSocket.accept();
                   } catch (IOException e) {
                       throw new RuntimeException(e);
                   }
               }
           } catch (IOException e) {
               throw new RuntimeException(e);
           }
        }).start();
    }

    private static void handleClient(Socket clientSocket) {
        new Thread(() -> {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            )
            {
                String inputLine = in.readLine();
                if (inputLine != null && !inputLine.trim().isEmpty()) {
                    String response = CommandDispatcher.dispatch(inputLine);
                    out.println(response);
                }
            } catch (Exception e) {
                logger.error("Error communicating with client", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (Exception _) {}
            }
        }).start();
    }
}

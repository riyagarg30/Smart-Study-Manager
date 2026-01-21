package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 5001;
    // Using a fixed thread pool to manage multiple client connections simultaneously
    private static ExecutorService pool = Executors.newFixedThreadPool(10); 

    public static void main(String[] args) {
        Database.createTables();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                // Handle each client connection in a new thread
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + PORT);
            e.printStackTrace();
        } finally {
            pool.shutdown();
            Database.close();
        }
    }
}
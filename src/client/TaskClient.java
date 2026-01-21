package client;

import java.io.*;
import java.net.Socket;

public class TaskClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public TaskClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    // Send a command to the server and return all response lines
    public String sendCommand(String command) {
        out.println(command);
        try {
            StringBuilder response = new StringBuilder();
            String line;
            // Read all lines until the buffer is empty
            while ((line = in.readLine()) != null) {
                response.append(line).append("\n");
                if (!in.ready()) break; 
            }
            return response.toString().trim();
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR: Could not read response from server";
        }
    }

    // Close connection
    public void close() {
        try {
            if(in != null) in.close();
            if(out != null) out.close();
            if(socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
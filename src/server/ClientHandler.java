package server;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                handleRequest(line);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + socket.getInetAddress());
        } finally {
            close();
        }
    }

    private void handleRequest(String request) {
        System.out.println("Received: " + request);
        
        int firstSemicolon = request.indexOf(";");
        String command = (firstSemicolon > 0) ? request.substring(0, firstSemicolon) : request;

        switch (command) {
            case "ADD":
                handleAdd(request);
                break;
                
            case "UPDATE":
                handleUpdate(request);
                break;
                
            case "DELETE":
                handleDelete(request);
                break;
                
            case "LIST":
                sendTaskList();
                break;
                
            case "LOG_SESSION":
                handleLogSession(request);
                break;
                
            case "COMPLETE":
                handleComplete(request);
                break;

            default:
                sendMessage("ERROR: Unknown command: " + command);
                break;
        }
    }
    
    private void handleAdd(String request) {
        // Enhanced ADD format: ADD;title;description;deadline;duration;priority;category
        String[] parts = request.split(";", 7);
        
        if (parts.length >= 4) {
            try {
                String title = parts[1].trim();
                String desc = parts[2].trim();
                String deadline = parts[3].trim();
                
                if (title.isEmpty() || desc.isEmpty() || deadline.isEmpty()) {
                    sendMessage("ERROR: Title, description, and deadline cannot be empty.");
                    return;
                }
                
                if (!deadline.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    sendMessage("ERROR: Invalid deadline format. Use YYYY-MM-DD.");
                    return;
                }
                
                int duration = 0;
                if (parts.length >= 5 && !parts[4].trim().isEmpty()) {
                    duration = Integer.parseInt(parts[4].trim());
                }
                
                TaskManager.Priority priority = TaskManager.Priority.MEDIUM;
                if (parts.length >= 6 && !parts[5].trim().isEmpty()) {
                    try {
                        priority = TaskManager.Priority.valueOf(parts[5].trim());
                    } catch (IllegalArgumentException e) {
                        priority = TaskManager.Priority.MEDIUM;
                    }
                }
                
                String category = "General";
                if (parts.length >= 7 && !parts[6].trim().isEmpty()) {
                    category = parts[6].trim();
                }
                
                TaskManager.addTask(title, desc, deadline, duration, priority, category);
                sendMessage("SUCCESS: Task added: " + title);
                sendTaskList();
            } catch (Exception e) {
                sendMessage("ERROR: Failed to add task: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            sendMessage("ERROR: ADD requires at least 3 arguments (title;description;deadline).");
        }
    }
    
    private void handleUpdate(String request) {
        // Enhanced UPDATE format: UPDATE;id;title;description;deadline;duration;priority;category
        String[] parts = request.split(";", 8);
        
        if (parts.length >= 6) {
            try {
                int taskId = Integer.parseInt(parts[1].trim());
                String title = parts[2].trim();
                String desc = parts[3].trim();
                String deadline = parts[4].trim();
                int duration = Integer.parseInt(parts[5].trim());
                
                if (title.isEmpty() || desc.isEmpty() || deadline.isEmpty()) {
                    sendMessage("ERROR: Title, description, and deadline cannot be empty.");
                    return;
                }
                
                if (!deadline.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    sendMessage("ERROR: Invalid deadline format. Use YYYY-MM-DD.");
                    return;
                }
                
                TaskManager.Priority priority = null;
                if (parts.length >= 7 && !parts[6].trim().isEmpty()) {
                    try {
                        priority = TaskManager.Priority.valueOf(parts[6].trim());
                    } catch (IllegalArgumentException e) {
                        // Keep priority null to not update it
                    }
                }
                
                String category = null;
                if (parts.length >= 8 && !parts[7].trim().isEmpty()) {
                    category = parts[7].trim();
                }
                
                TaskManager.updateTask(taskId, title, desc, deadline, duration, priority, null, category);
                sendMessage("SUCCESS: Task updated: " + title);
                sendTaskList();
            } catch (NumberFormatException e) {
                sendMessage("ERROR: Invalid Task ID or Duration: " + e.getMessage());
            } catch (Exception e) {
                sendMessage("ERROR: Failed to update task: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            sendMessage("ERROR: UPDATE requires at least 5 arguments (id;title;description;deadline;duration).");
        }
    }
    
    private void handleDelete(String request) {
        // DELETE format: DELETE;id
        String[] parts = request.split(";", 2);
        
        if (parts.length == 2) {
            try {
                int taskId = Integer.parseInt(parts[1].trim());
                TaskManager.deleteTask(taskId);
                sendMessage("SUCCESS: Task deleted (ID: " + taskId + ")");
                sendTaskList();
            } catch (NumberFormatException e) {
                sendMessage("ERROR: Invalid Task ID: " + e.getMessage());
            } catch (Exception e) {
                sendMessage("ERROR: Failed to delete task: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            sendMessage("ERROR: DELETE requires 1 argument (task id).");
        }
    }
    
    private void handleLogSession(String request) {
        // LOG_SESSION format: LOG_SESSION;taskId;durationSeconds
        String[] parts = request.split(";", 3);
        
        if (parts.length == 3) {
            try {
                int taskId = Integer.parseInt(parts[1].trim());
                int durationSeconds = Integer.parseInt(parts[2].trim());
                
                if (durationSeconds < 0) {
                    sendMessage("ERROR: Duration cannot be negative.");
                    return;
                }
                
                TimerManager.logSession(taskId, durationSeconds);
                sendMessage("SUCCESS: Session logged for Task ID " + taskId + " (" + formatDuration(durationSeconds) + ")");
            } catch (NumberFormatException e) {
                sendMessage("ERROR: Invalid Task ID or Duration: " + e.getMessage());
            } catch (Exception e) {
                sendMessage("ERROR: Failed to log session: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            sendMessage("ERROR: LOG_SESSION requires 2 arguments (task id;duration).");
        }
    }
    
    private void handleComplete(String request) {
        // COMPLETE format: COMPLETE;taskId
        String[] parts = request.split(";", 2);
        
        if (parts.length == 2) {
            try {
                int taskId = Integer.parseInt(parts[1].trim());
                TaskManager.markCompleted(taskId);
                sendMessage("SUCCESS: Task marked as completed (ID: " + taskId + ")");
                sendTaskList();
            } catch (NumberFormatException e) {
                sendMessage("ERROR: Invalid Task ID: " + e.getMessage());
            } catch (Exception e) {
                sendMessage("ERROR: Failed to mark task as complete: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            sendMessage("ERROR: COMPLETE requires 1 argument (task id).");
        }
    }
    
    private void sendTaskList() {
        List<String> tasks = TaskManager.listTasks();
        if (tasks.isEmpty()) {
            sendMessage("INFO: No tasks found.");
        } else {
            for (String task : tasks) {
                sendMessage(task);
            }
        }
    }

    public void sendMessage(String message) {
        try {
            writer.write(message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error sending message to client: " + e.getMessage());
        }
    }
    
    private String formatDuration(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) {
            return String.format("%dh %dm %ds", h, m, s);
        } else if (m > 0) {
            return String.format("%dm %ds", m, s);
        } else {
            return String.format("%ds", s);
        }
    }

    public void close() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
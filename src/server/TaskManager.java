package server;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class TaskManager {

    // Task priorities
    public enum Priority {
        LOW(1), MEDIUM(2), HIGH(3), URGENT(4);
        private final int level;
        Priority(int level) { this.level = level; }
        public int getLevel() { return level; }
    }

    // Task status
    public enum Status {
        NOT_STARTED, IN_PROGRESS, COMPLETED, OVERDUE
    }

    /**
     * Add a new task with validation and constraints
     */
    public static void addTask(String title, String desc, String deadline, int duration) {
        addTask(title, desc, deadline, duration, Priority.MEDIUM, null);
    }

    public static void addTask(String title, String desc, String deadline, int duration, 
                               Priority priority, String category) {
        // Validation constraints
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("Task title cannot exceed 200 characters");
        }
        if (desc != null && desc.length() > 1000) {
            throw new IllegalArgumentException("Task description cannot exceed 1000 characters");
        }
        if (duration < 0) {
            throw new IllegalArgumentException("Estimated duration cannot be negative");
        }
        if (duration > 24 * 3600) { // Max 24 hours
            throw new IllegalArgumentException("Estimated duration cannot exceed 24 hours");
        }

        // Validate deadline is not in the past
        try {
            LocalDate deadlineDate = LocalDate.parse(deadline);
            if (deadlineDate.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Deadline cannot be in the past");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid deadline format. Use YYYY-MM-DD");
        }

        String sql = "INSERT INTO tasks(title, description, deadline, estimated_duration, priority, status, category) " +
                     "VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement pstmt = Database.connect().prepareStatement(sql)) {
            pstmt.setString(1, title.trim());
            pstmt.setString(2, desc != null ? desc.trim() : "");
            pstmt.setString(3, deadline);
            pstmt.setInt(4, duration);
            pstmt.setString(5, priority.name());
            pstmt.setString(6, Status.NOT_STARTED.name());
            pstmt.setString(7, category != null ? category.trim() : "General");
            pstmt.executeUpdate();
            System.out.println("Task added: " + title);
        } catch (SQLException e) {
            System.err.println("Error adding task: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get total time spent on a task from sessions
     */
    private static int getTotalTimeSpent(int taskId) {
        String sql = "SELECT SUM(strftime('%s', end_time) - strftime('%s', start_time)) AS total_seconds " +
                     "FROM sessions WHERE task_id=?";
        try (PreparedStatement pstmt = Database.connect().prepareStatement(sql)) {
            pstmt.setInt(1, taskId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total_seconds");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Calculate days until deadline
     */
    private static long getDaysUntilDeadline(String deadline) {
        try {
            LocalDate deadlineDate = LocalDate.parse(deadline);
            return ChronoUnit.DAYS.between(LocalDate.now(), deadlineDate);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Auto-update task status based on deadline and completion
     */
    private static Status calculateStatus(String currentStatus, String deadline, int timeSpent, int estimatedDuration) {
        Status status = Status.valueOf(currentStatus);
        
        long daysUntil = getDaysUntilDeadline(deadline);
        
        // Check if overdue
        if (daysUntil < 0 && status != Status.COMPLETED) {
            return Status.OVERDUE;
        }
        
        // Check if completed (spent time >= estimated or manually marked)
        if (status == Status.COMPLETED) {
            return Status.COMPLETED;
        }
        
        // Check if in progress
        if (timeSpent > 0 && status == Status.NOT_STARTED) {
            return Status.IN_PROGRESS;
        }
        
        return status;
    }

    /**
     * List all tasks with enhanced information
     */
    public static List<String> listTasks() {
        List<String> tasks = new ArrayList<>();
        String sql = "SELECT id, title, description, deadline, estimated_duration, priority, status, category " +
                     "FROM tasks ORDER BY priority DESC, deadline ASC";
        
        try (Statement stmt = Database.connect().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while(rs.next()) {
                int taskId = rs.getInt("id");
                int timeSpent = getTotalTimeSpent(taskId);
                String deadline = rs.getString("deadline");
                int estimatedDuration = rs.getInt("estimated_duration");
                String currentStatus = rs.getString("status");
                
                // Auto-update status
                Status calculatedStatus = calculateStatus(currentStatus, deadline, timeSpent, estimatedDuration);
                if (!calculatedStatus.name().equals(currentStatus)) {
                    updateTaskStatus(taskId, calculatedStatus);
                }
                
                long daysUntil = getDaysUntilDeadline(deadline);
                int progressPercent = estimatedDuration > 0 ? 
                    Math.min(100, (timeSpent * 100) / estimatedDuration) : 0;

                // Format: ID|Title|Description|Deadline|TimeSpent|Priority|Status|Category|DaysUntil|Progress%
                String task = taskId + "|" +
                        rs.getString("title") + "|" +
                        rs.getString("description") + "|" +
                        deadline + "|" +
                        timeSpent + "|" +
                        rs.getString("priority") + "|" +
                        calculatedStatus.name() + "|" +
                        rs.getString("category") + "|" +
                        daysUntil + "|" +
                        progressPercent;
                tasks.add(task);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tasks;
    }

    /**
     * Update task status
     */
    public static void updateTaskStatus(int id, Status status) {
        String sql = "UPDATE tasks SET status=? WHERE id=?";
        try (PreparedStatement pstmt = Database.connect().prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Delete task with cascade (sessions will be deleted automatically)
     */
    public static void deleteTask(int id) {
        // Validation: Check if task exists
        String checkSql = "SELECT COUNT(*) FROM tasks WHERE id=?";
        try (PreparedStatement checkStmt = Database.connect().prepareStatement(checkSql)) {
            checkStmt.setInt(1, id);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                throw new IllegalArgumentException("Task with ID " + id + " does not exist");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        String sql = "DELETE FROM tasks WHERE id=?";
        try (PreparedStatement pstmt = Database.connect().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Task deleted: ID " + id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update task with validation
     */
    public static void updateTask(int id, String title, String desc, String deadline, int duration) {
        updateTask(id, title, desc, deadline, duration, null, null, null);
    }

    public static void updateTask(int id, String title, String desc, String deadline, int duration,
                                  Priority priority, Status status, String category) {
        // Validation constraints (same as add)
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("Task title cannot exceed 200 characters");
        }
        if (desc != null && desc.length() > 1000) {
            throw new IllegalArgumentException("Task description cannot exceed 1000 characters");
        }
        if (duration < 0) {
            throw new IllegalArgumentException("Estimated duration cannot be negative");
        }

        try {
            LocalDate.parse(deadline); // Validate format
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid deadline format. Use YYYY-MM-DD");
        }

        StringBuilder sql = new StringBuilder("UPDATE tasks SET title=?, description=?, deadline=?, estimated_duration=?");
        int paramIndex = 5;
        
        if (priority != null) {
            sql.append(", priority=?");
        }
        if (status != null) {
            sql.append(", status=?");
        }
        if (category != null) {
            sql.append(", category=?");
        }
        sql.append(" WHERE id=?");

        try (PreparedStatement pstmt = Database.connect().prepareStatement(sql.toString())) {
            pstmt.setString(1, title.trim());
            pstmt.setString(2, desc != null ? desc.trim() : "");
            pstmt.setString(3, deadline);
            pstmt.setInt(4, duration);
            
            if (priority != null) {
                pstmt.setString(paramIndex++, priority.name());
            }
            if (status != null) {
                pstmt.setString(paramIndex++, status.name());
            }
            if (category != null) {
                pstmt.setString(paramIndex++, category.trim());
            }
            pstmt.setInt(paramIndex, id);
            
            pstmt.executeUpdate();
            System.out.println("Task updated: ID " + id);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get tasks by category
     */
    public static List<String> getTasksByCategory(String category) {
        List<String> tasks = new ArrayList<>();
        String sql = "SELECT * FROM tasks WHERE category=? ORDER BY deadline ASC";
        
        try (PreparedStatement pstmt = Database.connect().prepareStatement(sql)) {
            pstmt.setString(1, category);
            ResultSet rs = pstmt.executeQuery();
            
            while(rs.next()) {
                int taskId = rs.getInt("id");
                int timeSpent = getTotalTimeSpent(taskId);
                tasks.add(taskId + "|" + rs.getString("title") + "|" + timeSpent);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tasks;
    }

    /**
     * Get overdue tasks
     */
    public static List<String> getOverdueTasks() {
        List<String> tasks = new ArrayList<>();
        String sql = "SELECT * FROM tasks WHERE status != 'COMPLETED' AND deadline < date('now') " +
                     "ORDER BY deadline ASC";
        
        try (Statement stmt = Database.connect().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while(rs.next()) {
                int taskId = rs.getInt("id");
                tasks.add(taskId + "|" + rs.getString("title") + "|" + rs.getString("deadline"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tasks;
    }

    /**
     * Mark task as completed
     */
    public static void markCompleted(int taskId) {
        updateTaskStatus(taskId, Status.COMPLETED);
        System.out.println("Task marked as completed: ID " + taskId);
    }

    /**
     * Get productivity statistics
     */
    public static String getStatistics() {
        StringBuilder stats = new StringBuilder();
        
        try (Statement stmt = Database.connect().createStatement()) {
            // Total tasks
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM tasks");
            if (rs.next()) {
                stats.append("Total Tasks: ").append(rs.getInt(1)).append("\n");
            }
            
            // Completed tasks
            rs = stmt.executeQuery("SELECT COUNT(*) FROM tasks WHERE status='COMPLETED'");
            if (rs.next()) {
                stats.append("Completed Tasks: ").append(rs.getInt(1)).append("\n");
            }
            
            // Total study time
            rs = stmt.executeQuery("SELECT SUM(strftime('%s', end_time) - strftime('%s', start_time)) FROM sessions");
            if (rs.next()) {
                int totalSeconds = rs.getInt(1);
                int hours = totalSeconds / 3600;
                stats.append("Total Study Time: ").append(hours).append(" hours\n");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return stats.toString();
    }
}
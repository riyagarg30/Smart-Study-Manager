package server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimerManager {

    // MODIFIED: Log session into database based on a duration in seconds reported by the client
    public static void logSession(int taskId, int durationSeconds) {
        String sql = "INSERT INTO sessions(task_id, start_time, end_time) VALUES(?,?,?)";

        // Calculate end and start times based on the duration
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusSeconds(durationSeconds);
        
        try (Connection conn = Database.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            pstmt.setInt(1, taskId);
            pstmt.setString(2, start.format(fmt));
            pstmt.setString(3, end.format(fmt));

            pstmt.executeUpdate();
            System.out.println("Session logged for Task ID " + taskId + ", duration: " + durationSeconds + "s");

        } catch (Exception e) {
            System.err.println("Error logging session: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
package server;

import java.sql.*;

public class Database {
    private static final String DB_URL = "jdbc:sqlite:database/study_scheduler.db";
    private static Connection conn = null;

    public static Connection connect() {
        try {
            Class.forName("org.sqlite.JDBC");

            if (conn == null || conn.isClosed()) {
                conn = DriverManager.getConnection(DB_URL);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
                System.out.println("Connected to SQLite database.");
            }
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found. Check your classpath.");
            e.printStackTrace();
        }
        return conn;
    }

    public static void createTables() {
        new java.io.File("database").mkdirs();

        // Check if tasks table exists and needs migration
        try {
            if (needsMigration()) {
                System.out.println("Migrating existing database to new schema...");
                migrateDatabase();
                System.out.println("Migration completed successfully!");
            }
        } catch (SQLException e) {
            System.err.println("Migration check failed: " + e.getMessage());
        }

        // Create tasks table with new fields
        String tasksTable = "CREATE TABLE IF NOT EXISTS tasks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "description TEXT,"
                + "deadline TEXT NOT NULL,"
                + "estimated_duration INTEGER DEFAULT 0,"
                + "priority TEXT DEFAULT 'MEDIUM',"
                + "status TEXT DEFAULT 'NOT_STARTED',"
                + "category TEXT DEFAULT 'General',"
                + "created_at TEXT DEFAULT (datetime('now', 'localtime')),"
                + "updated_at TEXT DEFAULT (datetime('now', 'localtime'))"
                + ");";

        // Sessions table
        String sessionsTable = "CREATE TABLE IF NOT EXISTS sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "task_id INTEGER NOT NULL,"
                + "start_time TEXT NOT NULL,"
                + "end_time TEXT NOT NULL,"
                + "notes TEXT,"
                + "session_type TEXT DEFAULT 'STUDY',"
                + "FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE"
                + ");";

        // Categories table
        String categoriesTable = "CREATE TABLE IF NOT EXISTS categories ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT UNIQUE NOT NULL,"
                + "color TEXT DEFAULT '#3498db',"
                + "description TEXT"
                + ");";

        // User preferences table
        String preferencesTable = "CREATE TABLE IF NOT EXISTS user_preferences ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "setting_key TEXT UNIQUE NOT NULL,"
                + "setting_value TEXT,"
                + "updated_at TEXT DEFAULT (datetime('now', 'localtime'))"
                + ");";

        // Indexes for performance
        String sessionsTaskIdIndex = "CREATE INDEX IF NOT EXISTS idx_sessions_task_id ON sessions(task_id);";
        String tasksDeadlineIndex = "CREATE INDEX IF NOT EXISTS idx_tasks_deadline ON tasks(deadline);";
        String tasksPriorityIndex = "CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);";
        String tasksStatusIndex = "CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);";
        String tasksCategoryIndex = "CREATE INDEX IF NOT EXISTS idx_tasks_category ON tasks(category);";

        // Trigger to update updated_at timestamp
        String updateTimestampTrigger = "CREATE TRIGGER IF NOT EXISTS update_task_timestamp "
                + "AFTER UPDATE ON tasks FOR EACH ROW "
                + "BEGIN "
                + "UPDATE tasks SET updated_at = datetime('now', 'localtime') WHERE id = NEW.id; "
                + "END;";

        try (Statement stmt = connect().createStatement()) {
            stmt.execute(tasksTable);
            stmt.execute(sessionsTable);
            stmt.execute(categoriesTable);
            stmt.execute(preferencesTable);
            stmt.execute(sessionsTaskIdIndex);
            stmt.execute(tasksDeadlineIndex);
            stmt.execute(tasksPriorityIndex);
            stmt.execute(tasksStatusIndex);
            stmt.execute(tasksCategoryIndex);
            stmt.execute(updateTimestampTrigger);
            
            // Insert default categories
            insertDefaultCategories();
            
            // Insert default preferences
            insertDefaultPreferences();
            
            System.out.println("Tables created successfully with constraints and indexes.");
        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean needsMigration() throws SQLException {
        // Check if tasks table exists and if it has the old schema
        String sql = "SELECT name FROM pragma_table_info('tasks') WHERE name='priority'";
        try (Statement stmt = connect().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // If priority column doesn't exist, we need migration
            return !rs.next();
        } catch (SQLException e) {
            // Table might not exist at all
            return false;
        }
    }

    private static void migrateDatabase() throws SQLException {
        try (Statement stmt = connect().createStatement()) {
            // Check if old table exists
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='tasks'");
            if (!rs.next()) {
                // No old table, nothing to migrate
                return;
            }

            // Rename old table
            stmt.execute("ALTER TABLE tasks RENAME TO tasks_old");

            // Create new table with updated schema
            String newTasksTable = "CREATE TABLE tasks ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "title TEXT NOT NULL,"
                    + "description TEXT,"
                    + "deadline TEXT NOT NULL,"
                    + "estimated_duration INTEGER DEFAULT 0,"
                    + "priority TEXT DEFAULT 'MEDIUM',"
                    + "status TEXT DEFAULT 'NOT_STARTED',"
                    + "category TEXT DEFAULT 'General',"
                    + "created_at TEXT DEFAULT (datetime('now', 'localtime')),"
                    + "updated_at TEXT DEFAULT (datetime('now', 'localtime'))"
                    + ")";
            stmt.execute(newTasksTable);

            // Copy data from old table to new table
            String copyData = "INSERT INTO tasks (id, title, description, deadline, estimated_duration, priority, status, category) "
                    + "SELECT id, title, description, deadline, "
                    + "COALESCE(estimated_duration, 0), "
                    + "'MEDIUM', "  // Default priority
                    + "'NOT_STARTED', "  // Default status
                    + "'General' "  // Default category
                    + "FROM tasks_old";
            stmt.execute(copyData);

            // Drop old table
            stmt.execute("DROP TABLE tasks_old");

            System.out.println("Successfully migrated " + stmt.getUpdateCount() + " tasks to new schema");
        }
    }

    private static void insertDefaultCategories() {
        String[] defaultCategories = {
            "General", "Math", "Science", "Programming", "Language", "History", "Art", "Other"
        };
        String[] colors = {
            "#95a5a6", "#e74c3c", "#3498db", "#9b59b6", "#1abc9c", "#f39c12", "#e91e63", "#34495e"
        };

        String sql = "INSERT OR IGNORE INTO categories(name, color) VALUES(?, ?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            for (int i = 0; i < defaultCategories.length; i++) {
                pstmt.setString(1, defaultCategories[i]);
                pstmt.setString(2, colors[i]);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error inserting default categories: " + e.getMessage());
        }
    }

    private static void insertDefaultPreferences() {
        String[][] defaultPrefs = {
            {"pomodoro_work_duration", "25"},
            {"pomodoro_break_duration", "5"},
            {"pomodoro_long_break_duration", "15"},
            {"daily_goal_minutes", "180"},
            {"notification_enabled", "true"},
            {"theme", "light"}
        };

        String sql = "INSERT OR IGNORE INTO user_preferences(setting_key, setting_value) VALUES(?, ?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            for (String[] pref : defaultPrefs) {
                pstmt.setString(1, pref[0]);
                pstmt.setString(2, pref[1]);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error inserting default preferences: " + e.getMessage());
        }
    }

    public static void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
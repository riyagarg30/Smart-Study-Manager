package client;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class Client {

	private static TaskClient client;
	private static DefaultTableModel taskTableModel;
	private static DefaultTableModel sessionTableModel;
	private static JTextArea logArea;
	private static JLabel statsLabel;
	private static JFrame mainFrame;

	// Maps to manage sessions
	private static final Map<Integer, javax.swing.Timer> sessionTimers = new HashMap<>();
	private static final Map<Integer, Integer> remainingTimeMap = new HashMap<>();
	private static final Map<Integer, Integer> cumulativeTimeMap = new HashMap<>();

	// Pomodoro timer
	private static javax.swing.Timer pomodoroTimer;
	private static int pomodoroSeconds = 0;
	private static boolean isPomodoroBreak = false;
	private static JLabel pomodoroLabel;

	// Notification tracking
	private static Set<Integer> notifiedTasks = new HashSet<>();

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			// Use default look and feel
		}

		try {
			client = new TaskClient("localhost", 5001);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null,
					"Could not connect to server. Ensure the server is running on port 5001.");
			return;
		}

		mainFrame = new JFrame("Smart Study Scheduler Pro");
		mainFrame.setSize(1400, 800);
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// ===== Menu Bar =====
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem exportCSV = new JMenuItem("Export Tasks to CSV");
		JMenuItem exitItem = new JMenuItem("Exit");
		fileMenu.add(exportCSV);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);

		JMenu viewMenu = new JMenu("View");
		JMenuItem showAnalytics = new JMenuItem("Analytics Dashboard");
		JMenuItem showPomodoro = new JMenuItem("Pomodoro Timer");
		viewMenu.add(showAnalytics);
		viewMenu.add(showPomodoro);

		menuBar.add(fileMenu);
		menuBar.add(viewMenu);
		mainFrame.setJMenuBar(menuBar);

		// ===== Top panel: Add Task with Enhanced Fields =====
		JPanel topPanel = new JPanel(new GridLayout(0, 2, 5, 5));
		topPanel.setBorder(BorderFactory.createTitledBorder("Add New Task"));

		JTextField titleField = new JTextField();
		JTextField descField = new JTextField();
		JTextField deadlineField = new JTextField();
		JTextField durationField = new JTextField("0");

		String[] priorities = { "LOW", "MEDIUM", "HIGH", "URGENT" };
		JComboBox<String> priorityCombo = new JComboBox<>(priorities);
		priorityCombo.setSelectedItem("MEDIUM");

		String[] categories = { "General", "Math", "Science", "Programming", "Language", "History", "Art", "Other" };
		JComboBox<String> categoryCombo = new JComboBox<>(categories);

		JButton addButton = new JButton("Add Task");
		addButton.setBackground(new Color(46, 204, 113));
		addButton.setForeground(Color.BLACK);
		addButton.setFont(new Font("Arial", Font.BOLD, 12));

		topPanel.add(new JLabel("Title:"));
		topPanel.add(titleField);
		topPanel.add(new JLabel("Description:"));
		topPanel.add(descField);
		topPanel.add(new JLabel("Deadline (YYYY-MM-DD):"));
		topPanel.add(deadlineField);
		topPanel.add(new JLabel("Est. Duration (minutes):"));
		topPanel.add(durationField);
		topPanel.add(new JLabel("Priority:"));
		topPanel.add(priorityCombo);
		topPanel.add(new JLabel("Category:"));
		topPanel.add(categoryCombo);
		topPanel.add(new JLabel());
		topPanel.add(addButton);

		// ===== Task Table with Enhanced Columns =====
		taskTableModel = new DefaultTableModel(new Object[] { "ID", "Title", "Description", "Deadline", "Time Spent",
				"Priority", "Status", "Category", "Days Left", "Progress %" }, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		JTable taskTable = new JTable(taskTableModel);
		taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		taskTable.setAutoCreateRowSorter(true);

		// Set column widths
		taskTable.getColumnModel().getColumn(0).setPreferredWidth(40);
		taskTable.getColumnModel().getColumn(1).setPreferredWidth(120);
		taskTable.getColumnModel().getColumn(2).setPreferredWidth(150);
		taskTable.getColumnModel().getColumn(3).setPreferredWidth(90);
		taskTable.getColumnModel().getColumn(4).setPreferredWidth(80);
		taskTable.getColumnModel().getColumn(5).setPreferredWidth(70);
		taskTable.getColumnModel().getColumn(6).setPreferredWidth(90);
		taskTable.getColumnModel().getColumn(7).setPreferredWidth(90);
		taskTable.getColumnModel().getColumn(8).setPreferredWidth(70);
		taskTable.getColumnModel().getColumn(9).setPreferredWidth(70);

		JScrollPane taskScroll = new JScrollPane(taskTable);
		taskScroll.setBorder(BorderFactory.createTitledBorder("Tasks"));

		// ===== Task Control Buttons =====
		JPanel taskControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton editTaskBtn = new JButton("Edit Task");
		JButton deleteTaskBtn = new JButton("Delete Task");
		JButton completeTaskBtn = new JButton("Mark Complete");
		JButton refreshBtn = new JButton("Refresh");

		editTaskBtn.setBackground(new Color(52, 152, 219));
		editTaskBtn.setForeground(Color.BLACK);
		deleteTaskBtn.setBackground(new Color(231, 76, 60));
		deleteTaskBtn.setForeground(Color.BLACK);
		completeTaskBtn.setBackground(new Color(46, 204, 113));
		completeTaskBtn.setForeground(Color.BLACK);
		refreshBtn.setBackground(new Color(149, 165, 166));
		refreshBtn.setForeground(Color.BLACK);

		taskControlPanel.add(editTaskBtn);
		taskControlPanel.add(deleteTaskBtn);
		taskControlPanel.add(completeTaskBtn);
		taskControlPanel.add(refreshBtn);

		// ===== Session Table =====
		sessionTableModel = new DefaultTableModel(
				new Object[] { "Task ID", "Title", "Start Time", "Elapsed Time", "Status" }, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		JTable sessionTable = new JTable(sessionTableModel);
		sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane sessionScroll = new JScrollPane(sessionTable);
		sessionScroll.setBorder(BorderFactory.createTitledBorder("Active Sessions"));
		sessionScroll.setPreferredSize(new Dimension(500, 0));

		// ===== Session Control Buttons =====
		JPanel sessionControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton startTimerBtn = new JButton("Start Session");
		JButton stopTimerBtn = new JButton("Stop & Log Session");

		startTimerBtn.setBackground(new Color(46, 204, 113));
		startTimerBtn.setForeground(Color.BLACK);
		stopTimerBtn.setBackground(new Color(230, 126, 34));
		stopTimerBtn.setForeground(Color.BLACK);

		sessionControlPanel.add(startTimerBtn);
		sessionControlPanel.add(stopTimerBtn);

		// ===== Stats Label =====
		statsLabel = new JLabel("Total Tasks: 0 | Active Sessions: 0");
		statsLabel.setFont(new Font("Arial", Font.BOLD, 12));
		statsLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

		// ===== Log Area =====
		logArea = new JTextArea(5, 30);
		logArea.setEditable(false);
		JScrollPane logScroll = new JScrollPane(logArea);
		logScroll.setBorder(BorderFactory.createTitledBorder("Activity Log"));

		// ===== Layout =====
		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.add(taskScroll, BorderLayout.CENTER);
		leftPanel.add(taskControlPanel, BorderLayout.SOUTH);

		JPanel rightPanel = new JPanel(new BorderLayout());
		rightPanel.add(sessionScroll, BorderLayout.CENTER);
		rightPanel.add(sessionControlPanel, BorderLayout.SOUTH);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
		splitPane.setDividerLocation(850);

		mainFrame.setLayout(new BorderLayout());
		mainFrame.add(topPanel, BorderLayout.NORTH);
		mainFrame.add(splitPane, BorderLayout.CENTER);

		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(statsLabel, BorderLayout.NORTH);
		bottomPanel.add(logScroll, BorderLayout.CENTER);
		mainFrame.add(bottomPanel, BorderLayout.SOUTH);

		mainFrame.setVisible(true);

		// ===== Load tasks =====
		sendCommand("LIST");

		// Start deadline notification checker
		startNotificationChecker();

		// ===== Menu Actions =====
		exportCSV.addActionListener(e -> exportToCSV());
		exitItem.addActionListener(e -> System.exit(0));
		showAnalytics.addActionListener(e -> showAnalyticsDashboard());
		showPomodoro.addActionListener(e -> showPomodoroTimer());

		// ===== Add Task Action =====
		addButton.addActionListener(e -> {
			String title = titleField.getText().trim();
			String desc = descField.getText().trim();
			String deadline = deadlineField.getText().trim();
			String durationStr = durationField.getText().trim();
			String priority = (String) priorityCombo.getSelectedItem();
			String category = (String) categoryCombo.getSelectedItem();

			if (title.isEmpty() || desc.isEmpty() || deadline.isEmpty()) {
				JOptionPane.showMessageDialog(mainFrame, "Title, description, and deadline are required.",
						"Input Error", JOptionPane.WARNING_MESSAGE);
				return;
			}
			if (!deadline.matches("\\d{4}-\\d{2}-\\d{2}")) {
				JOptionPane.showMessageDialog(mainFrame, "Deadline format must be YYYY-MM-DD.", "Format Error",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			int durationMinutes = 0;
			try {
				durationMinutes = Integer.parseInt(durationStr);
				if (durationMinutes < 0)
					throw new NumberFormatException();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(mainFrame, "Duration must be a positive number.", "Input Error",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			// Server expects seconds for duration
			int durationSeconds = durationMinutes * 60;

			// Enhanced ADD command with priority and category
			sendCommand("ADD;" + title + ";" + desc + ";" + deadline + ";" + durationSeconds + ";" + priority + ";"
					+ category);

			titleField.setText("");
			descField.setText("");
			deadlineField.setText("");
			durationField.setText("0");
			priorityCombo.setSelectedItem("MEDIUM");
			categoryCombo.setSelectedItem("General");

			logArea.append("[" + getCurrentTime() + "] Adding task: " + title + "\n");

			new Thread(() -> {
				try {
					Thread.sleep(200);
				} catch (InterruptedException ex) {
				}
				sendCommand("LIST");
			}).start();
		});

		// ===== Edit Task Action =====
		editTaskBtn.addActionListener(e -> {
			int selectedRow = taskTable.getSelectedRow();
			if (selectedRow == -1) {
				JOptionPane.showMessageDialog(mainFrame, "Select a task to edit.", "Selection Error",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			int taskId = Integer.parseInt(taskTableModel.getValueAt(selectedRow, 0).toString());
			String currentTitle = taskTableModel.getValueAt(selectedRow, 1).toString();
			String currentDesc = taskTableModel.getValueAt(selectedRow, 2).toString();
			String currentDeadline = taskTableModel.getValueAt(selectedRow, 3).toString();
			String currentPriority = taskTableModel.getValueAt(selectedRow, 5).toString();
			String currentCategory = taskTableModel.getValueAt(selectedRow, 7).toString();

			JTextField editTitle = new JTextField(currentTitle);
			JTextField editDesc = new JTextField(currentDesc);
			JTextField editDeadline = new JTextField(currentDeadline);
			JComboBox<String> editPriority = new JComboBox<>(priorities);
			editPriority.setSelectedItem(currentPriority);
			JComboBox<String> editCategory = new JComboBox<>(categories);
			editCategory.setSelectedItem(currentCategory);

			Object[] message = { "Title:", editTitle, "Description:", editDesc, "Deadline (YYYY-MM-DD):", editDeadline,
					"Priority:", editPriority, "Category:", editCategory };

			int option = JOptionPane.showConfirmDialog(mainFrame, message, "Edit Task", JOptionPane.OK_CANCEL_OPTION);
			if (option == JOptionPane.OK_OPTION) {
				String newTitle = editTitle.getText().trim();
				String newDesc = editDesc.getText().trim();
				String newDeadline = editDeadline.getText().trim();
				String newPriority = (String) editPriority.getSelectedItem();
				String newCategory = (String) editCategory.getSelectedItem();

				if (newTitle.isEmpty() || newDesc.isEmpty() || newDeadline.isEmpty()) {
					JOptionPane.showMessageDialog(mainFrame, "All fields must be filled.", "Input Error",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				if (!newDeadline.matches("\\d{4}-\\d{2}-\\d{2}")) {
					JOptionPane.showMessageDialog(mainFrame, "Deadline format must be YYYY-MM-DD.", "Format Error",
							JOptionPane.WARNING_MESSAGE);
					return;
				}

				sendCommand("UPDATE;" + taskId + ";" + newTitle + ";" + newDesc + ";" + newDeadline + ";0;"
						+ newPriority + ";" + newCategory);
				logArea.append("[" + getCurrentTime() + "] Updated task ID: " + taskId + "\n");

				new Thread(() -> {
					try {
						Thread.sleep(200);
					} catch (InterruptedException ex) {
					}
					sendCommand("LIST");
				}).start();
			}
		});

		// ===== Delete Task Action =====
		deleteTaskBtn.addActionListener(e -> {
			int selectedRow = taskTable.getSelectedRow();
			if (selectedRow == -1) {
				JOptionPane.showMessageDialog(mainFrame, "Select a task to delete.", "Selection Error",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			int taskId = Integer.parseInt(taskTableModel.getValueAt(selectedRow, 0).toString());
			String title = taskTableModel.getValueAt(selectedRow, 1).toString();

			int confirm = JOptionPane.showConfirmDialog(mainFrame,
					"Are you sure you want to delete task: " + title + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);

			if (confirm == JOptionPane.YES_OPTION) {
				javax.swing.Timer timer = sessionTimers.remove(taskId);
				if (timer != null) {
					timer.stop();
					remainingTimeMap.remove(taskId);
					cumulativeTimeMap.remove(taskId);
					for (int i = 0; i < sessionTableModel.getRowCount(); i++) {
						if ((int) sessionTableModel.getValueAt(i, 0) == taskId) {
							sessionTableModel.removeRow(i);
							break;
						}
					}
				}

				sendCommand("DELETE;" + taskId);
				logArea.append("[" + getCurrentTime() + "] Deleted task: " + title + "\n");

				new Thread(() -> {
					try {
						Thread.sleep(200);
					} catch (InterruptedException ex) {
					}
					sendCommand("LIST");
				}).start();
			}
		});

		// ===== Mark Complete Action =====
		completeTaskBtn.addActionListener(e -> {
			int selectedRow = taskTable.getSelectedRow();
			if (selectedRow == -1) {
				JOptionPane.showMessageDialog(mainFrame, "Select a task to mark as complete.", "Selection Error",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			int taskId = Integer.parseInt(taskTableModel.getValueAt(selectedRow, 0).toString());
			String title = taskTableModel.getValueAt(selectedRow, 1).toString();

			sendCommand("COMPLETE;" + taskId);
			logArea.append("[" + getCurrentTime() + "] Completed task: " + title + "\n");

			new Thread(() -> {
				try {
					Thread.sleep(200);
				} catch (InterruptedException ex) {
				}
				sendCommand("LIST");
			}).start();
		});

		// ===== Refresh Action =====
		refreshBtn.addActionListener(e -> {
			sendCommand("LIST");
			logArea.append("[" + getCurrentTime() + "] Refreshed task list\n");
		});

		// ===== Start Session Action =====
		startTimerBtn.addActionListener(e -> {
			int selectedRow = taskTable.getSelectedRow();
			if (selectedRow == -1) {
				JOptionPane.showMessageDialog(mainFrame, "Select a task first.", "Selection Error",
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			int taskId = Integer.parseInt(taskTableModel.getValueAt(selectedRow, 0).toString());
			if (sessionTimers.containsKey(taskId)) {
				JOptionPane.showMessageDialog(mainFrame, "Task already has an active session.", "Session Active",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			String title = taskTableModel.getValueAt(selectedRow, 1).toString();
			startSession(taskId, title, selectedRow);
			logArea.append("[" + getCurrentTime() + "] Started session for: " + title + "\n");
			updateStats();
		});

		// ===== Stop Session Action =====
		stopTimerBtn.addActionListener(e -> {
			int selectedRow = sessionTable.getSelectedRow();
			if (selectedRow == -1) {
				JOptionPane.showMessageDialog(mainFrame, "Select a session to stop and log.", "Selection Error",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			int taskId = (int) sessionTableModel.getValueAt(selectedRow, 0);
			String title = (String) sessionTableModel.getValueAt(selectedRow, 1);
			int elapsedSeconds = remainingTimeMap.getOrDefault(taskId, 0);

			int confirm = JOptionPane.showConfirmDialog(mainFrame,
					"Stop and log session for: " + title + "?\nElapsed time: " + formatTime(elapsedSeconds),
					"Confirm Stop", JOptionPane.YES_NO_OPTION);

			if (confirm == JOptionPane.YES_OPTION) {
				javax.swing.Timer timer = sessionTimers.remove(taskId);
				if (timer != null) {
					timer.stop();
				}

				remainingTimeMap.remove(taskId);
				cumulativeTimeMap.remove(taskId); // Clear cached time for this task
				sendCommand("LOG_SESSION;" + taskId + ";" + elapsedSeconds);
				sessionTableModel.removeRow(selectedRow);

				logArea.append("[" + getCurrentTime() + "] Logged session for: " + title + " ("
						+ formatTime(elapsedSeconds) + ")\n");
				updateStats();

				// Refresh task list to show updated time spent
				new Thread(() -> {
					try {
						Thread.sleep(200); // Small delay to ensure server processes the log
					} catch (InterruptedException ex) {
					}
					sendCommand("LIST");
				}).start();
			}
		});

		// ===== Double-click to Pause/Resume =====
		sessionTable.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				if (evt.getClickCount() == 2) {
					int selectedRow = sessionTable.getSelectedRow();
					if (selectedRow == -1)
						return;
					String status = (String) sessionTableModel.getValueAt(selectedRow, 4);
					String newStatus = status.equals("Running") ? "Paused" : "Running";
					sessionTableModel.setValueAt(newStatus, selectedRow, 4);

					String taskTitle = (String) sessionTableModel.getValueAt(selectedRow, 1);
					logArea.append(
							"[" + getCurrentTime() + "] Session " + newStatus.toLowerCase() + ": " + taskTitle + "\n");
				}
			}
		});

		// ===== Close window =====
		mainFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				boolean hasActiveSessions = !sessionTimers.isEmpty();

				if (hasActiveSessions) {
					int confirm = JOptionPane.showConfirmDialog(mainFrame,
							"You have active sessions. Stop and log them before closing?", "Active Sessions",
							JOptionPane.YES_NO_CANCEL_OPTION);

					if (confirm == JOptionPane.YES_OPTION) {
						for (Integer taskId : new HashSet<>(sessionTimers.keySet())) {
							javax.swing.Timer timer = sessionTimers.remove(taskId);
							if (timer != null)
								timer.stop();
							int elapsed = remainingTimeMap.getOrDefault(taskId, 0);
							sendCommand("LOG_SESSION;" + taskId + ";" + elapsed);
						}
					} else if (confirm == JOptionPane.CANCEL_OPTION) {
						return;
					}
				}

				for (javax.swing.Timer t : sessionTimers.values())
					t.stop();
				client.close();
			}
		});

		updateStats();
	}

	// ===== Send Command =====
	private static void sendCommand(String command) {
		new Thread(() -> {
			String response = client.sendCommand(command);
			SwingUtilities.invokeLater(() -> processServerMessage(response));
		}).start();
	}

	// ===== Process Server Message =====
	private static void processServerMessage(String msg) {
		if (msg.contains("|")) {
			updateTaskTableFromResponse(msg);
		} else {
			logArea.append(msg + "\n");
			logArea.setCaretPosition(logArea.getDocument().getLength());
		}
	}

	// ===== Update Task Table =====
	private static void updateTaskTableFromResponse(String response) {
		Set<Integer> activeTaskIds = new HashSet<>(sessionTimers.keySet());

		taskTableModel.setRowCount(0);
		String[] lines = response.split("\n");
		for (String line : lines) {
			// Skip non-task lines (like SUCCESS/INFO messages)
			if (!line.contains("|") || line.startsWith("SUCCESS") || line.startsWith("INFO")
					|| line.startsWith("ERROR")) {
				continue;
			}

			String[] parts = line.split(Pattern.quote("|"));

			if (parts.length >= 10) {
				try {
					int taskId = Integer.parseInt(parts[0].trim());
					int serverTotalTime = Integer.parseInt(parts[4].trim());

					if (!activeTaskIds.contains(taskId)) {
						cumulativeTimeMap.put(taskId, serverTotalTime);
					}

					int displayTime = cumulativeTimeMap.getOrDefault(taskId, serverTotalTime);

					taskTableModel.addRow(new Object[] { taskId, parts[1].trim(), parts[2].trim(), parts[3].trim(),
							formatTime(displayTime), parts[5].trim(), parts[6].trim(), parts[7].trim(), parts[8].trim(),
							parts[9].trim() + "%" });
				} catch (NumberFormatException e) {
					logArea.append("ERROR: Invalid number format in server task response: " + line + "\n");
				}
			} else if (!line.trim().isEmpty()) {
				logArea.append("INFO: " + line + "\n");
			}
		}
		updateStats();
		checkDeadlineNotifications();
	}

	// ===== Start Session =====
	private static void startSession(int taskId, String title, int taskRowIndex) {
		LocalDateTime startTime = LocalDateTime.now();
		Object[] row = { taskId, title, startTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")), "00:00", "Running" };
		sessionTableModel.addRow(row);

		remainingTimeMap.put(taskId, 0);

		javax.swing.Timer timer = new javax.swing.Timer(1000, null);
		timer.addActionListener(e -> {
			int rowIndex = -1;
			String status = "Running";

			for (int i = 0; i < sessionTableModel.getRowCount(); i++) {
				if ((int) sessionTableModel.getValueAt(i, 0) == taskId) {
					rowIndex = i;
					status = (String) sessionTableModel.getValueAt(i, 4);
					break;
				}
			}

			if (rowIndex == -1) {
				timer.stop();
				sessionTimers.remove(taskId);
				remainingTimeMap.remove(taskId);
				return;
			}

			if (status.equals("Paused"))
				return;

			int elapsed = remainingTimeMap.get(taskId);
			elapsed++;
			remainingTimeMap.put(taskId, elapsed);
			sessionTableModel.setValueAt(formatTime(elapsed), rowIndex, 3);

			int prevTotal = cumulativeTimeMap.getOrDefault(taskId, 0);
			prevTotal++;
			cumulativeTimeMap.put(taskId, prevTotal);

			int currentTaskRow = -1;
			for (int i = 0; i < taskTableModel.getRowCount(); i++) {
				if ((int) taskTableModel.getValueAt(i, 0) == taskId) {
					currentTaskRow = i;
					break;
				}
			}
			if (currentTaskRow != -1) {
				taskTableModel.setValueAt(formatTime(prevTotal), currentTaskRow, 4);
			}
		});
		timer.start();
		sessionTimers.put(taskId, timer);
	}

	// ===== Update Stats =====
	private static void updateStats() {
		int totalTasks = taskTableModel.getRowCount();
		int activeSessions = sessionTableModel.getRowCount();
		statsLabel.setText("Total Tasks: " + totalTasks + " | Active Sessions: " + activeSessions);
	}

	// ===== Format Time =====
	private static String formatTime(int totalSecs) {
		int h = totalSecs / 3600;
		int m = (totalSecs % 3600) / 60;
		int s = totalSecs % 60;
		if (h > 0)
			return String.format("%02d:%02d:%02d", h, m, s);
		else
			return String.format("%02d:%02d", m, s);
	}

	// ===== Get Current Time =====
	private static String getCurrentTime() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
	}

	// ===== Export to CSV =====
	private static void exportToCSV() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export Tasks to CSV");
		fileChooser.setSelectedFile(new java.io.File("tasks_export.csv"));

		int result = fileChooser.showSaveDialog(mainFrame);
		if (result == JFileChooser.APPROVE_OPTION) {
			try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
				// Write header
				writer.write("ID,Title,Description,Deadline,Time Spent,Priority,Status,Category,Days Left,Progress\n");

				// Write rows
				for (int i = 0; i < taskTableModel.getRowCount(); i++) {
					for (int j = 0; j < taskTableModel.getColumnCount(); j++) {
						String value = taskTableModel.getValueAt(i, j).toString();
						writer.write("\"" + value.replace("\"", "\"\"") + "\"");
						if (j < taskTableModel.getColumnCount() - 1) {
							writer.write(",");
						}
					}
					writer.write("\n");
				}

				JOptionPane.showMessageDialog(mainFrame, "Tasks exported successfully!", "Export Complete",
						JOptionPane.INFORMATION_MESSAGE);
				logArea.append("[" + getCurrentTime() + "] Exported tasks to CSV\n");
			} catch (Exception e) {
				JOptionPane.showMessageDialog(mainFrame, "Error exporting: " + e.getMessage(), "Export Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// ===== Analytics Dashboard =====
	private static void showAnalyticsDashboard() {
		JDialog analyticsDialog = new JDialog(mainFrame, "Analytics Dashboard", false);
		analyticsDialog.setSize(900, 600);
		analyticsDialog.setLocationRelativeTo(mainFrame);

		JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Stats panel
		JPanel statsPanel = new JPanel(new GridLayout(2, 4, 10, 10));
		statsPanel.setBorder(BorderFactory.createTitledBorder("Summary Statistics"));

		// Calculate stats
		int totalTasks = taskTableModel.getRowCount();
		int completedTasks = 0;
		int overdueTasks = 0;
		int totalTimeSpent = 0;
		Map<String, Integer> categoryTime = new HashMap<>();
		Map<String, Integer> priorityCount = new HashMap<>();

		for (int i = 0; i < taskTableModel.getRowCount(); i++) {
			String status = taskTableModel.getValueAt(i, 6).toString();
			String timeStr = taskTableModel.getValueAt(i, 4).toString();
			String category = taskTableModel.getValueAt(i, 7).toString();
			String priority = taskTableModel.getValueAt(i, 5).toString();

			if (status.equals("COMPLETED"))
				completedTasks++;
			if (status.equals("OVERDUE"))
				overdueTasks++;

			int seconds = parseTimeToSeconds(timeStr);
			totalTimeSpent += seconds;

			categoryTime.put(category, categoryTime.getOrDefault(category, 0) + seconds);
			priorityCount.put(priority, priorityCount.getOrDefault(priority, 0) + 1);
		}

		statsPanel.add(createStatLabel("Total Tasks", String.valueOf(totalTasks), new Color(52, 152, 219)));
		statsPanel.add(createStatLabel("Completed", String.valueOf(completedTasks), new Color(46, 204, 113)));
		statsPanel.add(createStatLabel("Overdue", String.valueOf(overdueTasks), new Color(231, 76, 60)));
		statsPanel.add(createStatLabel("Completion Rate",
				String.format("%.1f%%", totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 0),
				new Color(155, 89, 182)));
		statsPanel.add(createStatLabel("Total Study Time", formatTime(totalTimeSpent), new Color(26, 188, 156)));
		statsPanel.add(createStatLabel("Avg Time/Task", formatTime(totalTasks > 0 ? totalTimeSpent / totalTasks : 0),
				new Color(230, 126, 34)));
		statsPanel.add(createStatLabel("Active Sessions", String.valueOf(sessionTableModel.getRowCount()),
				new Color(241, 196, 15)));
		statsPanel.add(createStatLabel("Categories", String.valueOf(categoryTime.size()), new Color(52, 73, 94)));

		// Charts panel
		JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
		chartsPanel.setBorder(BorderFactory.createTitledBorder("Visual Analysis"));

		// Category chart
		JPanel categoryChart = createBarChart("Time by Category", categoryTime);
		chartsPanel.add(categoryChart);

		// Priority chart
		JPanel priorityChart = createPieChart("Tasks by Priority", priorityCount);
		chartsPanel.add(priorityChart);

		mainPanel.add(statsPanel, BorderLayout.NORTH);
		mainPanel.add(chartsPanel, BorderLayout.CENTER);

		JButton closeBtn = new JButton("Close");
		closeBtn.addActionListener(e -> analyticsDialog.dispose());
		JPanel buttonPanel = new JPanel();
		buttonPanel.add(closeBtn);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		analyticsDialog.add(mainPanel);
		analyticsDialog.setVisible(true);

		logArea.append("[" + getCurrentTime() + "] Opened Analytics Dashboard\n");
	}

	private static JPanel createStatLabel(String label, String value, Color color) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(color);
		panel.setBorder(BorderFactory.createLineBorder(color.darker(), 2));

		JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
		valueLabel.setFont(new Font("Arial", Font.BOLD, 24));
		valueLabel.setForeground(Color.BLACK);

		JLabel labelLabel = new JLabel(label, SwingConstants.CENTER);
		labelLabel.setFont(new Font("Arial", Font.PLAIN, 12));
		labelLabel.setForeground(Color.BLACK);

		panel.add(valueLabel, BorderLayout.CENTER);
		panel.add(labelLabel, BorderLayout.SOUTH);

		return panel;
	}

	private static JPanel createBarChart(String title, Map<String, Integer> data) {
		JPanel panel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g;
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				int width = getWidth();
				int height = getHeight();
				int margin = 40;
				int chartHeight = height - 2 * margin;
				int chartWidth = width - 2 * margin;

				// Draw title
				g2.setFont(new Font("Arial", Font.BOLD, 14));
				g2.drawString(title, width / 2 - g2.getFontMetrics().stringWidth(title) / 2, 20);

				if (data.isEmpty()) {
					g2.drawString("No data available", width / 2 - 50, height / 2);
					return;
				}

				int maxValue = data.values().stream().max(Integer::compare).orElse(1);
				int barWidth = chartWidth / data.size() - 10;
				int x = margin;

				Color[] colors = { new Color(52, 152, 219), new Color(46, 204, 113), new Color(155, 89, 182),
						new Color(241, 196, 15), new Color(230, 126, 34), new Color(231, 76, 60) };
				int colorIndex = 0;

				for (Map.Entry<String, Integer> entry : data.entrySet()) {
					int barHeight = (int) ((entry.getValue() / (double) maxValue) * chartHeight);
					int y = height - margin - barHeight;

					g2.setColor(colors[colorIndex % colors.length]);
					g2.fillRect(x, y, barWidth, barHeight);

					g2.setColor(Color.BLACK);
					g2.setFont(new Font("Arial", Font.PLAIN, 10));
					String timeStr = formatTime(entry.getValue());
					g2.drawString(timeStr, x, y - 5);

					// Draw category name
					g2.drawString(entry.getKey(), x, height - margin + 15);

					x += barWidth + 10;
					colorIndex++;
				}
			}
		};
		panel.setBackground(Color.WHITE);
		panel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		return panel;
	}

	private static JPanel createPieChart(String title, Map<String, Integer> data) {
		JPanel panel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g;
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				int width = getWidth();
				int height = getHeight();

				// Draw title
				g2.setFont(new Font("Arial", Font.BOLD, 14));
				g2.drawString(title, width / 2 - g2.getFontMetrics().stringWidth(title) / 2, 20);

				if (data.isEmpty()) {
					g2.drawString("No data available", width / 2 - 50, height / 2);
					return;
				}

				int total = data.values().stream().mapToInt(Integer::intValue).sum();
				int centerX = width / 2 - 50;
				int centerY = height / 2 + 20;
				int diameter = Math.min(width, height) - 100;

				Color[] colors = { new Color(231, 76, 60), new Color(230, 126, 34), new Color(241, 196, 15),
						new Color(46, 204, 113) };

				int startAngle = 0;
				int colorIndex = 0;
				int legendY = 50;

				for (Map.Entry<String, Integer> entry : data.entrySet()) {
					int arcAngle = (int) Math.round(entry.getValue() * 360.0 / total);

					g2.setColor(colors[colorIndex % colors.length]);
					g2.fillArc(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter, startAngle,
							arcAngle);

					// Draw legend
					g2.fillRect(width - 120, legendY, 15, 15);
					g2.setColor(Color.BLACK);
					g2.setFont(new Font("Arial", Font.PLAIN, 11));
					g2.drawString(entry.getKey() + " (" + entry.getValue() + ")", width - 100, legendY + 12);

					startAngle += arcAngle;
					colorIndex++;
					legendY += 25;
				}
			}
		};
		panel.setBackground(Color.WHITE);
		panel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		return panel;
	}

	private static int parseTimeToSeconds(String timeStr) {
		String[] parts = timeStr.split(":");
		try {
			if (parts.length == 3) {
				return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
			} else if (parts.length == 2) {
				return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
			}
		} catch (NumberFormatException e) {
			// Return 0 if parsing fails
		}
		return 0;
	}

	// ===== Pomodoro Timer =====
	private static void showPomodoroTimer() {
		JDialog pomodoroDialog = new JDialog(mainFrame, "Pomodoro Timer", false);
		pomodoroDialog.setSize(400, 300);
		pomodoroDialog.setLocationRelativeTo(mainFrame);

		JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

		// Timer display
		pomodoroLabel = new JLabel("25:00", SwingConstants.CENTER);
		pomodoroLabel.setFont(new Font("Arial", Font.BOLD, 48));
		pomodoroLabel.setForeground(new Color(52, 152, 219));

		JLabel statusLabel = new JLabel("Ready to start", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Arial", Font.PLAIN, 16));

		// Controls
		JPanel controlPanel = new JPanel(new FlowLayout());
		JButton startBtn = new JButton("Start");
		JButton pauseBtn = new JButton("Pause");
		JButton resetBtn = new JButton("Reset");
		JButton skipBtn = new JButton("Skip Break");

		startBtn.setBackground(new Color(46, 204, 113));
		startBtn.setForeground(Color.BLACK);
		pauseBtn.setBackground(new Color(230, 126, 34));
		pauseBtn.setForeground(Color.BLACK);
		resetBtn.setBackground(new Color(149, 165, 166));
		resetBtn.setForeground(Color.BLACK);
		skipBtn.setBackground(new Color(52, 152, 219));
		skipBtn.setForeground(Color.BLACK);
		skipBtn.setEnabled(false);

		pauseBtn.setEnabled(false);

		controlPanel.add(startBtn);
		controlPanel.add(pauseBtn);
		controlPanel.add(resetBtn);
		controlPanel.add(skipBtn);

		// Settings
		JPanel settingsPanel = new JPanel(new GridLayout(2, 2, 5, 5));
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
		JSpinner workSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 60, 1));
		JSpinner breakSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 30, 1));
		settingsPanel.add(new JLabel("Work (min):"));
		settingsPanel.add(workSpinner);
		settingsPanel.add(new JLabel("Break (min):"));
		settingsPanel.add(breakSpinner);

		mainPanel.add(pomodoroLabel, BorderLayout.NORTH);
		mainPanel.add(statusLabel, BorderLayout.CENTER);
		mainPanel.add(controlPanel, BorderLayout.SOUTH);

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(settingsPanel, BorderLayout.NORTH);
		topPanel.add(mainPanel, BorderLayout.CENTER);

		pomodoroDialog.add(topPanel);

		// Timer logic
		startBtn.addActionListener(e -> {
			if (pomodoroTimer == null || !pomodoroTimer.isRunning()) {
				int minutes = isPomodoroBreak ? (int) breakSpinner.getValue() : (int) workSpinner.getValue();
				if (pomodoroSeconds == 0) {
					pomodoroSeconds = minutes * 60;
				}

				statusLabel.setText(isPomodoroBreak ? "Break time!" : "Work time!");
				statusLabel.setForeground(isPomodoroBreak ? new Color(46, 204, 113) : new Color(52, 152, 219));

				pomodoroTimer = new javax.swing.Timer(1000, evt -> {
					pomodoroSeconds--;
					int mins = pomodoroSeconds / 60;
					int secs = pomodoroSeconds % 60;
					pomodoroLabel.setText(String.format("%02d:%02d", mins, secs));

					if (pomodoroSeconds <= 0) {
						pomodoroTimer.stop();

						if (!isPomodoroBreak) {
							JOptionPane.showMessageDialog(pomodoroDialog, "Work session complete! Time for a break.",
									"Pomodoro", JOptionPane.INFORMATION_MESSAGE);
							isPomodoroBreak = true;
							pomodoroSeconds = (int) breakSpinner.getValue() * 60;
							skipBtn.setEnabled(true);
						} else {
							JOptionPane.showMessageDialog(pomodoroDialog, "Break over! Ready for another work session?",
									"Pomodoro", JOptionPane.INFORMATION_MESSAGE);
							isPomodoroBreak = false;
							pomodoroSeconds = (int) workSpinner.getValue() * 60;
							skipBtn.setEnabled(false);
						}

						statusLabel.setText(isPomodoroBreak ? "Break time!" : "Work time!");
						statusLabel.setForeground(isPomodoroBreak ? new Color(46, 204, 113) : new Color(52, 152, 219));
						int newMins = pomodoroSeconds / 60;
						int newSecs = pomodoroSeconds % 60;
						pomodoroLabel.setText(String.format("%02d:%02d", newMins, newSecs));
					}
				});
				pomodoroTimer.start();
				startBtn.setEnabled(false);
				pauseBtn.setEnabled(true);
			}
		});

		pauseBtn.addActionListener(e -> {
			if (pomodoroTimer != null && pomodoroTimer.isRunning()) {
				pomodoroTimer.stop();
				startBtn.setEnabled(true);
				pauseBtn.setEnabled(false);
				statusLabel.setText("Paused");
			}
		});

		resetBtn.addActionListener(e -> {
			if (pomodoroTimer != null) {
				pomodoroTimer.stop();
			}
			isPomodoroBreak = false;
			pomodoroSeconds = (int) workSpinner.getValue() * 60;
			int resetMins = pomodoroSeconds / 60;
			pomodoroLabel.setText(String.format("%02d:00", resetMins));
			statusLabel.setText("Ready to start");
			statusLabel.setForeground(Color.BLACK);
			startBtn.setEnabled(true);
			pauseBtn.setEnabled(false);
			skipBtn.setEnabled(false);
		});

		skipBtn.addActionListener(e -> {
			if (isPomodoroBreak) {
				if (pomodoroTimer != null) {
					pomodoroTimer.stop();
				}
				isPomodoroBreak = false;
				pomodoroSeconds = (int) workSpinner.getValue() * 60;
				int skipMins = pomodoroSeconds / 60;
				pomodoroLabel.setText(String.format("%02d:00", skipMins));
				statusLabel.setText("Ready to start");
				statusLabel.setForeground(Color.BLACK);
				startBtn.setEnabled(true);
				pauseBtn.setEnabled(false);
				skipBtn.setEnabled(false);
			}
		});

		pomodoroDialog.setVisible(true);
		logArea.append("[" + getCurrentTime() + "] Opened Pomodoro Timer\n");
	}

	// ===== Deadline Notifications =====
	private static void startNotificationChecker() {
		javax.swing.Timer notificationTimer = new javax.swing.Timer(60000, e -> {
			checkDeadlineNotifications();
		});
		notificationTimer.start();
	}

	private static void checkDeadlineNotifications() {
		for (int i = 0; i < taskTableModel.getRowCount(); i++) {
			int taskId = Integer.parseInt(taskTableModel.getValueAt(i, 0).toString());
			String title = taskTableModel.getValueAt(i, 1).toString();
			String status = taskTableModel.getValueAt(i, 6).toString();
			String daysLeftStr = taskTableModel.getValueAt(i, 8).toString();

			if (status.equals("COMPLETED") || notifiedTasks.contains(taskId)) {
				continue;
			}

			try {
				int daysLeft = Integer.parseInt(daysLeftStr);

				if (daysLeft <= 1 && daysLeft >= 0) {
					notifiedTasks.add(taskId);
					SwingUtilities.invokeLater(() -> {
						JOptionPane.showMessageDialog(mainFrame,
								"Task \"" + title + "\" is due " + (daysLeft == 0 ? "today" : "tomorrow") + "!",
								"Deadline Alert", JOptionPane.WARNING_MESSAGE);
						logArea.append(
								"[" + getCurrentTime() + "] ALERT: Task \"" + title + "\" deadline approaching!\n");
					});
				}
			} catch (NumberFormatException ex) {
				// Ignore parsing errors
			}
		}
	}
}
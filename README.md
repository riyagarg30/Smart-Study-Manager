# Distributed Task and Study Session Management System

## Final Project Report
**Course:** CS9053 – Introduction to Java  
**Semester:** Fall 2025  
**Student:** Riya Garg  
**Java Version:** Java SE 1.8  

---

## Introduction

This project implements a distributed task and study-session management system using Java. The goal of the system is to allow users to manage tasks, track focused study sessions, and persist progress data while supporting multiple concurrent clients.

The application follows a client–server architecture, where a Java Swing client communicates with a multi-threaded server using a custom networking protocol. All data is stored persistently in a relational database.

---

## Application Features

### Task Management
- Create, update, delete, and complete tasks
- Task metadata includes deadlines, priority, duration, and category
- Automatic tracking of completed and overdue tasks

### Study Session and Time Tracking
- Pomodoro-style study session timers
- Start and stop sessions directly from the client
- Session durations are logged to the server and stored in the database
- Supports cumulative study time and progress per task

### Multi-Client Support
- Multiple clients can connect simultaneously
- Consistent shared view of tasks and session data
- Concurrent updates handled by the server

### Real-Time and Event-Driven Behavior
- Non-blocking, event-driven timers
- Real-time session tracking and deadline monitoring
- Responsive graphical user interface

### Networking and Persistence
- Custom socket-based communication protocol
- Server-side command parsing and validation
- Persistent storage using a relational database with integrity constraints

---

## Advanced Java Concepts Used

- **Multithreading and Concurrency**
  - Thread pool used on the server to handle multiple clients
  - Client-side timers and network communication run concurrently with the GUI

- **Custom Networking Protocol**
  - Raw TCP socket communication
  - Explicit command parsing and request handling

- **Database Engineering**
  - Relational schemas with foreign keys and indexing
  - Triggers and schema initialization for data integrity

- **Event-Driven Programming**
  - GUI events and timers managed without blocking execution

---

## How to Run the Application

### Requirements
- Java SE Development Kit 1.8
- SQLite JDBC Driver (`sqlite-jdbc-3.41.2.1.jar`)

### Start the Server
```bash
javac Server.java
java Server
```

### Start the Client
```bash
javac Client.java
java Client
```

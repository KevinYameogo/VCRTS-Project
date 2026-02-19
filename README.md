# VCRTS — Vehicular Cloud Real-Time System

A Java desktop application that simulates a **Vehicular Cloud Computing** environment, where parked or idle vehicles contribute their computing power to process jobs submitted by clients. The system is managed by a central **VC Controller** and communicates in real time via a multi-threaded TCP socket server.

---

## Features

- **MVC Architecture** — Clean separation between `Server` (model/persistence), `VCController` (controller/scheduler), and role-based Swing GUIs (views: Client, Owner, VC Controller)
- **Real-Time Notifications** — Persistent background socket client pushes live job/vehicle status updates to users without polling
- **Job Scheduling** — FIFO queue with redundancy-level support; jobs wait until enough vehicles are available
- **Checkpoint-Based Fault Tolerance** — If a vehicle departs mid-job, the system recovers from the latest checkpoint on a replacement vehicle, or re-queues the job
- **Role-Based Access** — Three distinct user roles (Client, Vehicle Owner, VC Controller) each with a dedicated GUI panel
- **MySQL Persistence** — All users, jobs, vehicles, and checkpoints are stored in a MySQL database via `DatabaseManager`
- **Billing Management** — Clients can add, update, and delete billing info (card details stored per user)
- **Password Management** — Users can generate a temporary password on first login and change it at any time

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Main.java                        │
│   Starts Server → VCController → NetworkServer → GUI    │
└───────────────┬─────────────────────────────────────────┘
                │
   ┌────────────▼────────────┐
   │       Server.java        │  ← In-memory cache + DB bridge
   │  (Requests, Notifications│
   │   Jobs, Vehicles, etc.)  │
   └────────────┬─────────────┘
                │
   ┌────────────▼────────────┐      ┌─────────────────────┐
   │    VCController.java     │◄────►│  DatabaseManager.java│
   │  (Scheduling, Approval,  │      │  (MySQL persistence) │
   │   Fault Recovery)        │      └─────────────────────┘
   └────────────┬─────────────┘
                │
   ┌────────────▼────────────┐
   │    NetworkServer.java    │  ← TCP socket server (port 12345)
   │  (Push notifications,    │    Spawns thread per client
   │   checkpoint messages)   │
   └─────────────────────────┘

Swing GUIs (all use CardLayout via LandingPage):
  ├── ClientGUI.java       — Job submission, billing, notifications
  ├── OwnerGUI.java        — Vehicle registration & history
  └── VCControllerGUI.java — Approve/reject requests, monitor jobs & vehicles
```

---

## Roles

| Role | Capabilities |
|---|---|
| **Client** | Submit jobs with duration, deadline & redundancy level; track job status; manage billing info; receive real-time notifications |
| **Vehicle Owner** | Register vehicles with compute specs; view vehicle history; receive approval/rejection notifications |
| **VC Controller** | Approve or reject job/vehicle requests; monitor all pending, in-progress & completed jobs; trigger checkpoints; view logs |

---

## Prerequisites

- **Java 11+**
- **Maven**
- **MySQL 8+**

---

## Database Setup

1. Create the database:
   ```sql
   CREATE DATABASE vcrts;
   ```

2. Create a `.env` file in the project root (same level as `pom.xml`):
   ```properties
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=vcrts
   DB_USER=root
   DB_PASSWORD=your_password

   # Default credentials for first-time login
   CLIENT_USERNAME=client
   CLIENT_PASSWORD=client123
   OWNER_USERNAME=owner
   OWNER_PASSWORD=owner123
   ```

> Tables (`users`, `jobs`, `vehicles`, `checkpoints`) are created automatically on first run.

---

## Running the Application

From the project root directory (where `pom.xml` is located):

```bash
mvn exec:java -Dexec.mainClass="Main"
```

Or set an alias for convenience:

```bash
alias runmain='mvn exec:java -Dexec.mainClass="Main"'
runmain
```

---

## First-Time Login

1. Select a role (Client or Owner) from the dropdown
2. Enter a username and click **"Generate Pass"** to create your account
3. Copy the generated password and use it to log in
4. You can change your password any time from the **"Change Password"** tab

The **VC Controller** role requires no login — it opens directly.

---

## Project Structure

```
VCRTS-Project/
├── src/main/java/
│   ├── Main.java                # Entry point
│   ├── LandingPage.java         # Role-selection & login screen
│   ├── Server.java              # Central in-memory state & DB bridge
│   ├── VCController.java        # Job/vehicle scheduling & fault recovery
│   ├── NetworkServer.java       # TCP socket server for push notifications
│   ├── DatabaseManager.java     # MySQL CRUD operations
│   ├── ClientGUI.java           # Client dashboard (jobs, billing, notifications)
│   ├── OwnerGUI.java            # Owner dashboard (vehicle registration)
│   ├── VCControllerGUI.java     # Controller dashboard (approvals, monitoring)
│   ├── Job.java                 # Job model
│   ├── Vehicle.java             # Vehicle model with checkpoint support
│   ├── Checkpoint.java          # Checkpoint model
│   ├── Request.java             # Request model (job & vehicle submissions)
│   ├── Client.java / Owner.java # User role models
│   └── UserStore.java           # Local file-based user persistence
├── .env                         # Environment variables (git-ignored)
├── pom.xml                      # Maven build config
└── README.md
```

---

## Tech Stack

| Technology | Usage |
|---|---|
| Java (Swing) | Desktop GUI — all role-based views |
| Maven | Build & dependency management |
| MySQL | Persistent storage for jobs, vehicles & users |
| TCP Sockets | Real-time push notification server |
| Multithreading | Background notification listener, per-client server threads |
| dotenv-java | Environment variable loading from `.env` |

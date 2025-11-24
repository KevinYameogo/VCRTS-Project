import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import io.github.cdimascio.dotenv.Dotenv;

public class DatabaseManager {

    private static DatabaseManager instance;
    private Connection connection;
    private String url;
    private String user;
    private String password;

    private DatabaseManager() {
        // Load credentials from .env or use defaults
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String host = dotenv.get("DB_HOST", "localhost");
        String port = dotenv.get("DB_PORT", "3306");
        String dbName = dotenv.get("DB_NAME", "vcrts");
        this.user = dotenv.get("DB_USER", "root");
        this.password = dotenv.get("DB_PASSWORD", "password");
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + dbName;

        connect();
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, user, password);
                System.out.println("Connected to database: " + url);
            }
        } catch (SQLException e) {
            System.err.println("Failed to connect to database: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Disconnected from database.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetDatabase() {
        if (connection == null) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS requests");
            stmt.execute("DROP TABLE IF EXISTS notifications");
            stmt.execute("DROP TABLE IF EXISTS jobs");
            stmt.execute("DROP TABLE IF EXISTS vehicles");
            stmt.execute("DROP TABLE IF EXISTS users");
            System.out.println("Database reset (tables dropped).");
            initializeDatabase(); // Re-create with new schema
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeDatabase() {
        if (connection == null) return;

        try (Statement stmt = connection.createStatement()) {
            // Users Table - Updated for structured billing
            String createUsers = "CREATE TABLE IF NOT EXISTS users (" +
                    "user_id VARCHAR(50) PRIMARY KEY, " +
                    "name VARCHAR(100), " +
                    "password VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "card_holder VARCHAR(100), " +
                    "card_number VARCHAR(20), " +
                    "cvc VARCHAR(5), " +
                    "expiry VARCHAR(10))";
            stmt.execute(createUsers);

            // Jobs Table - Added timestamp
            String createJobs = "CREATE TABLE IF NOT EXISTS jobs (" +
                    "job_id VARCHAR(50) PRIMARY KEY, " +
                    "client_id VARCHAR(50), " +
                    "username VARCHAR(50), " + 
                    "duration INT, " +
                    "deadline VARCHAR(50), " +
                    "redundancy INT, " +
                    "status VARCHAR(50), " +
                    "timestamp DATETIME, " +
                    "FOREIGN KEY (username) REFERENCES users(user_id))";
            stmt.execute(createJobs);

            // Vehicles Table - Added timestamp and state fields
            String createVehicles = "CREATE TABLE IF NOT EXISTS vehicles (" +
                    "vehicle_id VARCHAR(50) PRIMARY KEY, " +
                    "owner_id VARCHAR(50), " +
                    "username VARCHAR(50), " +
                    "license VARCHAR(20), " +
                    "state VARCHAR(20), " +
                    "make VARCHAR(50), " +
                    "model VARCHAR(50), " +
                    "year INT, " +
                    "departure_schedule VARCHAR(50), " +
                    "status VARCHAR(20), " +
                    "cpu_status VARCHAR(20), " +
                    "memory_status VARCHAR(20), " +
                    "current_job_id VARCHAR(50), " +
                    "timestamp DATETIME, " +
                    "FOREIGN KEY (username) REFERENCES users(user_id))";
            stmt.execute(createVehicles);
            
            // Notifications Table
            String createNotifications = "CREATE TABLE IF NOT EXISTS notifications (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_id VARCHAR(50), " +
                    "message TEXT, " +
                    "is_read BOOLEAN DEFAULT FALSE, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(user_id))";
            stmt.execute(createNotifications);
            
            // Requests Table (for Server State)
            String createRequests = "CREATE TABLE IF NOT EXISTS requests (" +
                    "request_id VARCHAR(50) PRIMARY KEY, " +
                    "sender_id VARCHAR(50), " +
                    "request_type VARCHAR(50), " +
                    "data BLOB, " + // Serialized Job or Vehicle object
                    "status VARCHAR(20), " +
                    "timestamp DATETIME, " +
                    "acknowledged BOOLEAN DEFAULT FALSE, " +
                    "decision_timestamp DATETIME)";
            stmt.execute(createRequests);

            // VC Controller Logs/Notifications Table
            String createControllerLogs = "CREATE TABLE IF NOT EXISTS controller_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "message TEXT, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";
            stmt.execute(createControllerLogs);

            System.out.println("Database initialized (tables checked/created).");

        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    // --- User Operations ---

    public void saveUser(User user) {
        if (connection == null) return;
        String sql = "INSERT INTO users (user_id, name, password, role, card_holder, card_number, cvc, expiry) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name = ?, password = ?, role = ?, card_holder = ?, card_number = ?, cvc = ?, expiry = ?";
        
        String cardHolder = "", cardNumber = "", cvc = "", expiry = "";
        if (user instanceof Client) {
            Client c = (Client) user;
            cardHolder = c.getCardHolder();
            cardNumber = c.getCardNumber();
            cvc = c.getCvc();
            expiry = c.getExpiry();
        } else if (user instanceof Owner) {
            Owner o = (Owner) user;
            cardHolder = o.getCardHolder();
            cardNumber = o.getCardNumber();
            cvc = o.getCvc();
            expiry = o.getExpiry();
        }

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, user.getUserID());
            pstmt.setString(2, user.getName());
            pstmt.setString(3, user.getPassword());
            pstmt.setString(4, user.getRole());
            pstmt.setString(5, cardHolder);
            pstmt.setString(6, cardNumber);
            pstmt.setString(7, cvc);
            pstmt.setString(8, expiry);
            
            pstmt.setString(9, user.getName());
            pstmt.setString(10, user.getPassword());
            pstmt.setString(11, user.getRole());
            pstmt.setString(12, cardHolder);
            pstmt.setString(13, cardNumber);
            pstmt.setString(14, cvc);
            pstmt.setString(15, expiry);
            
            pstmt.executeUpdate();
            System.out.println("User saved to DB: " + user.getUserID());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public User loadUser(String username) {
        if (connection == null) return null;
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String name = rs.getString("name");
                String password = rs.getString("password");
                String role = rs.getString("role");
                String cardHolder = rs.getString("card_holder");
                String cardNumber = rs.getString("card_number");
                String cvc = rs.getString("cvc");
                String expiry = rs.getString("expiry");
                
                if (role.equalsIgnoreCase("Client")) {
                    return new Client(username, name, password, cardHolder, cardNumber, cvc, expiry);
                } else if (role.equalsIgnoreCase("Owner")) {
                    return new Owner(username, name, password, cardHolder, cardNumber, cvc, expiry);
                } 
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // --- Job Operations ---

    public void saveJob(Job job, String clientEnteredId, String username) {
        if (connection == null) return;
        String sql = "INSERT INTO jobs (job_id, client_id, username, duration, deadline, redundancy, status, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE status = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, job.getJobID());
            pstmt.setString(2, clientEnteredId);
            pstmt.setString(3, username);
            pstmt.setInt(4, job.getDuration());
            pstmt.setString(5, job.getDeadline().toString());
            pstmt.setInt(6, job.getRedundancyLevel());
            pstmt.setString(7, job.getStatus());
            pstmt.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            
            pstmt.setString(9, job.getStatus());
            pstmt.executeUpdate();
            System.out.println("Job saved to DB: " + job.getJobID());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getJobStatus(String jobId) {
        if (connection == null) return "Unknown";
        String sql = "SELECT status FROM jobs WHERE job_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, jobId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("status");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Job not found";
    }

    public List<Job> getClientJobHistory(String username) {
        List<Job> jobs = new ArrayList<>();
        if (connection == null) return jobs;
        String sql = "SELECT * FROM jobs WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String jobId = rs.getString("job_id");
                int duration = rs.getInt("duration");
                int redundancy = rs.getInt("redundancy");
                String deadlineStr = rs.getString("deadline");
                String status = rs.getString("status");
                
                String clientId = rs.getString("client_id");
                
                LocalDateTime deadline = LocalDateTime.parse(deadlineStr);
                
                Job job = new Job(jobId, clientId, username, duration, redundancy, deadline);
                job.updateStatus(status);
                if (rs.getTimestamp("timestamp") != null) {
                    job.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                }
                jobs.add(job);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jobs;
    }

    // --- Vehicle Operations ---

    public void saveVehicle(Vehicle vehicle, String ownerEnteredId, String username) {
        if (connection == null) return;
        String sql = "INSERT INTO vehicles (vehicle_id, owner_id, username, license, state, make, model, year, departure_schedule, status, cpu_status, memory_status, current_job_id, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE departure_schedule = ?, status = ?, cpu_status = ?, memory_status = ?, current_job_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, vehicle.getVehicleID());
            pstmt.setString(2, ownerEnteredId);
            pstmt.setString(3, username);
            pstmt.setString(4, vehicle.getLicensePlate());
            pstmt.setString(5, vehicle.getLicenseState());
            pstmt.setString(6, vehicle.getMake());
            pstmt.setString(7, vehicle.getModel());
            pstmt.setInt(8, vehicle.getYear());
            pstmt.setString(9, vehicle.getDepartureSchedule().toString());
            pstmt.setString(10, vehicle.getStatus());
            pstmt.setString(11, vehicle.getCpuStatus());
            pstmt.setString(12, vehicle.getMemoryStatus());
            pstmt.setString(13, vehicle.getCurrentJobID());
            pstmt.setTimestamp(14, Timestamp.valueOf(LocalDateTime.now()));
            
            pstmt.setString(15, vehicle.getDepartureSchedule().toString());
            pstmt.setString(16, vehicle.getStatus());
            pstmt.setString(17, vehicle.getCpuStatus());
            pstmt.setString(18, vehicle.getMemoryStatus());
            pstmt.setString(19, vehicle.getCurrentJobID());
            
            pstmt.executeUpdate();
            System.out.println("Vehicle saved to DB: " + vehicle.getVehicleID());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Vehicle> getOwnerVehicleHistory(String username) {
        List<Vehicle> vehicles = new ArrayList<>();
        if (connection == null) return vehicles;
        String sql = "SELECT * FROM vehicles WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String license = rs.getString("license");
                String state = rs.getString("state");
                String make = rs.getString("make");
                String model = rs.getString("model");
                int year = rs.getInt("year");
                String departureStr = rs.getString("departure_schedule");
                
                String ownerId = rs.getString("owner_id");
                
                LocalDateTime departure = LocalDateTime.parse(departureStr);
                
                Vehicle vehicle = new Vehicle(ownerId, username, make, model, year, license, state, departure);
                if (rs.getTimestamp("timestamp") != null) {
                    vehicle.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                }
                vehicles.add(vehicle);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return vehicles;
    }

    public List<Vehicle> getAllVehicles() {
        List<Vehicle> vehicles = new ArrayList<>();
        if (connection == null) return vehicles;
        String sql = "SELECT * FROM vehicles";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String license = rs.getString("license");
                String state = rs.getString("state");
                String make = rs.getString("make");
                String model = rs.getString("model");
                int year = rs.getInt("year");
                String departureStr = rs.getString("departure_schedule");
                LocalDateTime departure = LocalDateTime.parse(departureStr);
                
                String status = rs.getString("status");
                String cpuStatus = rs.getString("cpu_status");
                String memStatus = rs.getString("memory_status");
                String currentJobId = rs.getString("current_job_id");
                
                Vehicle vehicle = new Vehicle(license, make, model, year, license, state, departure);
                if (status != null) {
                    vehicle.restoreState(status, cpuStatus, memStatus, currentJobId);
                }
                vehicles.add(vehicle);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return vehicles;
    }

    public List<Job> getAllJobs() {
        List<Job> jobs = new ArrayList<>();
        if (connection == null) return jobs;
        String sql = "SELECT * FROM jobs";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String jobId = rs.getString("job_id");
                int duration = rs.getInt("duration");
                int redundancy = rs.getInt("redundancy");
                String deadlineStr = rs.getString("deadline");
                String status = rs.getString("status");
                LocalDateTime deadline = LocalDateTime.parse(deadlineStr);
                
                Job job = new Job(jobId, duration, redundancy, deadline);
                job.updateStatus(status);
                jobs.add(job);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jobs;
    }

    public java.util.Map<String, String> getJobClientMap() {
        java.util.Map<String, String> map = new java.util.concurrent.ConcurrentHashMap<>();
        if (connection == null) return map;
        String sql = "SELECT job_id, client_id FROM jobs";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String cid = rs.getString("client_id");
                if (cid != null) map.put(rs.getString("job_id"), cid);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    public java.util.Map<String, String> getJobSenderMap() {
        java.util.Map<String, String> map = new java.util.concurrent.ConcurrentHashMap<>();
        if (connection == null) return map;
        String sql = "SELECT job_id, username FROM jobs";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String uname = rs.getString("username");
                if (uname != null) map.put(rs.getString("job_id"), uname);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    public java.util.Map<String, String> getVehicleSenderMap() {
        java.util.Map<String, String> map = new java.util.concurrent.ConcurrentHashMap<>();
        if (connection == null) return map;
        String sql = "SELECT vehicle_id, username FROM vehicles";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String uname = rs.getString("username");
                if (uname != null) map.put(rs.getString("vehicle_id"), uname);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    public java.util.Map<String, String> getVehicleOwnerIdMap() {
        java.util.Map<String, String> map = new java.util.concurrent.ConcurrentHashMap<>();
        if (connection == null) return map;
        String sql = "SELECT vehicle_id, owner_id FROM vehicles";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String oid = rs.getString("owner_id");
                if (oid != null) map.put(rs.getString("vehicle_id"), oid);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }
    
    // --- Notification Operations ---
    
    public void addNotification(String userId, String message) {
        if (connection == null) return;
        String sql = "INSERT INTO notifications (user_id, message) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public List<String> getNotifications(String userId) {
        List<String> notifications = new ArrayList<>();
        if (connection == null) return notifications;
        String sql = "SELECT message FROM notifications WHERE user_id = ? ORDER BY timestamp ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                notifications.add(rs.getString("message"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notifications;
    }
    
    public int getUnreadNotificationCount(String userId) {
        if (connection == null) return 0;
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    public void markNotificationsRead(String userId) {
        if (connection == null) return;
        String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void clearNotifications(String userId) {
        if (connection == null) return;
        String sql = "DELETE FROM notifications WHERE user_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // --- Request Operations (Server State) ---
    
    public void saveRequest(Request request) {
        if (connection == null) return;
        String sql = "INSERT INTO requests (request_id, sender_id, request_type, data, status, timestamp, acknowledged, decision_timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE status = ?, acknowledged = ?, decision_timestamp = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, request.getRequestID());
            pstmt.setString(2, request.getSenderID());
            pstmt.setString(3, request.getRequestType());
            
            // Serialize data object
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
            oos.writeObject(request.getData());
            pstmt.setBytes(4, baos.toByteArray());
            
            pstmt.setString(5, request.getStatus());
            pstmt.setTimestamp(6, Timestamp.valueOf(request.getTimestamp()));
            pstmt.setBoolean(7, request.isAcknowledged());
            
            if (request.getDecisionTimestamp() != null) {
                pstmt.setTimestamp(8, Timestamp.valueOf(request.getDecisionTimestamp()));
            } else {
                pstmt.setNull(8, Types.TIMESTAMP);
            }
            
            // Update params
            pstmt.setString(9, request.getStatus());
            pstmt.setBoolean(10, request.isAcknowledged());
            if (request.getDecisionTimestamp() != null) {
                pstmt.setTimestamp(11, Timestamp.valueOf(request.getDecisionTimestamp()));
            } else {
                pstmt.setNull(11, Types.TIMESTAMP);
            }
            
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public List<Request> getAllRequests() {
        List<Request> requests = new ArrayList<>();
        if (connection == null) return requests;
        String sql = "SELECT * FROM requests";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String reqId = rs.getString("request_id");
                String senderId = rs.getString("sender_id");
                String type = rs.getString("request_type");
                byte[] dataBytes = rs.getBytes("data");
                String status = rs.getString("status");
                Timestamp ts = rs.getTimestamp("timestamp");
                boolean ack = rs.getBoolean("acknowledged");
                Timestamp decisionTs = rs.getTimestamp("decision_timestamp");
                
                // Deserialize data
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(dataBytes);
                java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
                Object data = ois.readObject();
                
                Request req = new Request(reqId, senderId, type, data);
                // We need to restore the state of the request object manually or add setters to Request
                // For now, we'll assume Request is simple enough or we might need to refactor Request to allow setting status/timestamps
                // Actually, Request has methods like approve(), reject(), acknowledge() which change state.
                // But setting exact timestamps might require new setters.
                // Let's just set what we can.
                if ("Approved".equals(status)) req.approve();
                if ("Rejected".equals(status)) req.reject();
                if (ack) req.acknowledge();
                
                requests.add(req);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return requests;
    }

    // --- VC Controller Notification Operations ---

    public void saveVCNotification(String message) {
        if (connection == null) return;
        String sql = "INSERT INTO controller_logs (message) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getVCNotifications() {
        List<String> logs = new ArrayList<>();
        if (connection == null) return logs;
        String sql = "SELECT message FROM controller_logs ORDER BY timestamp ASC"; // Load in chronological order
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                logs.add(rs.getString("message"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }
}

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
            // Users Table
            String createUsers = "CREATE TABLE IF NOT EXISTS users (" +
                    "user_id VARCHAR(50) PRIMARY KEY, " +
                    "name VARCHAR(100), " +
                    "password VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "info VARCHAR(255))"; 
            stmt.execute(createUsers);

            // Jobs Table
            // client_id = Entered ID (from GUI)
            // username = Actual User Account ID (FK)
            String createJobs = "CREATE TABLE IF NOT EXISTS jobs (" +
                    "job_id VARCHAR(50) PRIMARY KEY, " +
                    "client_id VARCHAR(50), " +
                    "username VARCHAR(50), " + 
                    "duration INT, " +
                    "deadline VARCHAR(50), " +
                    "redundancy INT, " +
                    "status VARCHAR(50), " +
                    "FOREIGN KEY (username) REFERENCES users(user_id))";
            stmt.execute(createJobs);

            // Vehicles Table
            // owner_id = Entered ID (from GUI)
            // username = Actual User Account ID (FK)
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
                    "FOREIGN KEY (username) REFERENCES users(user_id))";
            stmt.execute(createVehicles);

            System.out.println("Database initialized (tables checked/created).");

        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    // --- User Operations ---

    public void saveUser(User user) {
        if (connection == null) return;
        String sql = "INSERT INTO users (user_id, name, password, role, info) VALUES (?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name = ?, password = ?, role = ?, info = ?";
        
        String info = "";
        if (user instanceof Client) {
            info = ((Client) user).getBillingInfo();
        } else if (user instanceof Owner) {
            info = ((Owner) user).getPaymentInfo();
        }

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, user.getUserID());
            pstmt.setString(2, user.getName());
            pstmt.setString(3, user.getPassword());
            pstmt.setString(4, user.getRole());
            pstmt.setString(5, info);
            
            pstmt.setString(6, user.getName());
            pstmt.setString(7, user.getPassword());
            pstmt.setString(8, user.getRole());
            pstmt.setString(9, info);
            
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
                String info = rs.getString("info");
                
                if (role.equalsIgnoreCase("Client")) {
                    return new Client(username, name, password, info);
                } else if (role.equalsIgnoreCase("Owner")) {
                    return new Owner(username, name, password, info);
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
        String sql = "INSERT INTO jobs (job_id, client_id, username, duration, deadline, redundancy, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE status = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, job.getJobID());
            pstmt.setString(2, clientEnteredId);
            pstmt.setString(3, username);
            pstmt.setInt(4, job.getDuration());
            pstmt.setString(5, job.getDeadline().toString());
            pstmt.setInt(6, job.getRedundancyLevel());
            pstmt.setString(7, job.getStatus());
            pstmt.setString(8, job.getStatus());
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
        // Filter by username (the account ID)
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

    // --- Vehicle Operations ---

    public void saveVehicle(Vehicle vehicle, String ownerEnteredId, String username) {
        if (connection == null) return;
        String sql = "INSERT INTO vehicles (vehicle_id, owner_id, username, license, state, make, model, year, departure_schedule) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE departure_schedule = ?";
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
            
            pstmt.setString(10, vehicle.getDepartureSchedule().toString());
            pstmt.executeUpdate();
            System.out.println("Vehicle saved to DB: " + vehicle.getVehicleID());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Vehicle> getOwnerVehicleHistory(String username) {
        List<Vehicle> vehicles = new ArrayList<>();
        if (connection == null) return vehicles;
        // Filter by username (the account ID)
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
                
                LocalDateTime departure = LocalDateTime.parse(departureStr);
                
                Vehicle vehicle = new Vehicle(license, make, model, year, license, state, departure);
                vehicles.add(vehicle);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return vehicles;
    }
}

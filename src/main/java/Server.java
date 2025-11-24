import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Server class with request tracking, pending requests management, and user notifications.
 * Thread-safe implementation using synchronized methods and concurrent collections.
 */
public class Server implements Serializable {
    private static final long serialVersionUID = 2L;
    // private static final String STATE_FILE = "server_state.dat"; // Removed

    private final String serverID;
    private final String ipAddress;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Server State components (made non-final for loading)
    private List<Job> storageArchive;
    private List<Checkpoint> checkpointRepo;
    private HashMap<String, Request> pendingRequests;
    private AtomicInteger requestCounter;
    private ConcurrentHashMap<String, List<String>> userNotifications;

    //concurrentHashMap for thread safety + concurrency
    // Map to link Job ID to the USER-ENTERED Client ID  - used for job display/CSV
    private ConcurrentHashMap<String, String> jobClientMap;

    // Map to link Job ID to the SENDER LOGIN ID - used for job history filtering
    private ConcurrentHashMap<String, String> jobSenderMap;

    // Map to link Vehicle ID to the SENDER LOGIN ID - used for vehicle history filtering
    private ConcurrentHashMap<String, String> vehicleSenderMap;

    // Map to link Vehicle ID to the USER-ENTERED Owner ID - used for vehicle display/CSV
    private ConcurrentHashMap<String, String> vehicleOwnerIdMap;

    // Map to hold active, persistent notification sockets (transient because sockets aren't serializable)
    private transient ConcurrentHashMap<String, ObjectOutputStream> activeNotificationClients;

    private List<Vehicle> registeredVehicles; // Store all approved vehicles

    private List<Job> approvedJobs; // Store all approved jobs (pending + in-progress)

    public Server(String serverID, String ipAddress) {
        this.serverID = serverID;
        this.ipAddress = ipAddress;

        // Initialize transient map here, outside of loadState()
        this.activeNotificationClients = new ConcurrentHashMap<>();

        this.registeredVehicles = new ArrayList<>();
       
        this.approvedJobs = new ArrayList<>();

        if (!loadState()) {
            this.storageArchive = new ArrayList<>();
            this.checkpointRepo = new ArrayList<>();
            this.pendingRequests = new HashMap<>();
            this.requestCounter = new AtomicInteger(1);
            this.userNotifications = new ConcurrentHashMap<>();
            this.jobClientMap = new ConcurrentHashMap<>();
            this.jobSenderMap = new ConcurrentHashMap<>();
            this.vehicleSenderMap = new ConcurrentHashMap<>();
            this.vehicleOwnerIdMap = new ConcurrentHashMap<>();
            System.out.println("Server initialized fresh.");
        } else {
            System.out.println("Server state successfully loaded.");
        }
    }

    // Override readObject to handle transient fields after deserialization
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.activeNotificationClients = new ConcurrentHashMap<>();
    }

    /** Saves the current state of critical server components. */
    /** Saves the current state of critical server components. */
    @Deprecated
    public synchronized void saveState() {
        // No-op: State is saved to DB immediately on change.
    }

    /** Loads the state from file on startup. */
    @SuppressWarnings("unchecked")
    /** Loads the state from DB on startup. */
    public boolean loadState() {
        reloadState();
        return true;
    }

    /**
     * Method to force reload the in-memory state from the DB.
     */
    public synchronized void reloadState() {
        DatabaseManager db = DatabaseManager.getInstance();
        
        // Load Requests
        this.pendingRequests = new HashMap<>();
        List<Request> allRequests = db.getAllRequests();
        int maxReqId = 0;
        for (Request r : allRequests) {
            if ("Pending".equals(r.getStatus())) {
                this.pendingRequests.put(r.getRequestID(), r);
            }
            // Try to parse ID to update counter if needed
            if (r.getRequestID().startsWith("REQ-")) {
                try {
                    int id = Integer.parseInt(r.getRequestID().substring(4));
                    if (id > maxReqId) maxReqId = id;
                } catch (NumberFormatException e) {}
            }
        }
        this.requestCounter = new AtomicInteger(maxReqId + 1);
        
        // Initialize userNotifications map
        this.userNotifications = new ConcurrentHashMap<>();

        // Load Vehicles and Jobs
        this.registeredVehicles = db.getAllVehicles();
        this.approvedJobs = db.getAllJobs(); 
        
        // Rebuild Maps
        this.jobClientMap = new ConcurrentHashMap<>(db.getJobClientMap());
        this.jobSenderMap = new ConcurrentHashMap<>(db.getJobSenderMap());
        this.vehicleSenderMap = new ConcurrentHashMap<>(db.getVehicleSenderMap());
        this.vehicleOwnerIdMap = new ConcurrentHashMap<>(db.getVehicleOwnerIdMap());
        
        // Storage Archive
        this.storageArchive = new ArrayList<>();
        for (Job j : this.approvedJobs) {
            if ("Completed".equals(j.getStatus())) {
                this.storageArchive.add(j);
            }
        }

        System.out.println("Server: State reloaded from DB.");
    }

    // request mangement
    /**
     * Creates a new request and stores it in pending requests.
     */
    public synchronized Request createRequest(String senderID, String requestType, Object data) {
        String requestID = "REQ-" + requestCounter.getAndIncrement();
        Request request = new Request(requestID, senderID, requestType, data);
        pendingRequests.put(requestID, request);

        if (requestType.equals("JOB_SUBMISSION") && data instanceof Job) {
            Job job = (Job) data;
            jobSenderMap.put(job.getJobID(), senderID);
            String clientEnteredID = job.getJobID().split("-")[0];
            jobClientMap.put(job.getJobID(), clientEnteredID);
        } else if (requestType.equals("VEHICLE_REGISTRATION") && data instanceof Vehicle) {
            Vehicle vehicle = (Vehicle) data;
            vehicleSenderMap.put(vehicle.getVehicleID(), senderID);
        }

        DatabaseManager.getInstance().saveRequest(request);
        System.out.println("Server: Created request " + requestID + " from " + senderID);
        return request;
    }

    /**
     * Retrieves a request by ID.
     */
    public Request getRequest(String requestID) {
        return pendingRequests.get(requestID);
    }

    /**
     * Gets all requests that are currently marked "Pending".
     */
    public synchronized List<Request> getPendingRequests() {
        reloadState();

        // Return a list of only the requests that are marked "Pending".
        return pendingRequests.values()
                .stream()
                .filter(r -> r.getStatus().equals("Pending"))
                .toList();
    }

    /**
     * Marks a request as approved and removes it from the pending list.
     */
    public synchronized boolean approveRequest(String requestID) {
        Request request = pendingRequests.get(requestID);
        if (request == null || !request.getStatus().equals("Pending")) {
            return false;
        }

        request.approve();
        pendingRequests.remove(requestID);

        DatabaseManager.getInstance().saveRequest(request);
        System.out.println("Server: Approved request " + requestID);
        return true;
    }

    /**
     * Rejects a request and removes it from the pending list.
     */
    public synchronized boolean rejectRequest(String requestID) {
        Request request = pendingRequests.get(requestID);
        if (request == null || !request.getStatus().equals("Pending")) {
            return false;
        }

        request.reject();
        pendingRequests.remove(requestID);

        DatabaseManager.getInstance().saveRequest(request);
        System.out.println("Server: Rejected request " + requestID);
        return true;
    }


    /**
     * Gets the user-entered Client ID for a given job.
     */
    public String getClientIDForJob(Job job) {
        if (job == null) return null;
        return jobClientMap.get(job.getJobID());
    }

    /**
     * Gets the Login ID that submitted a job.
     */
    public String getLoginIDForJob(Job job) {
        if (job == null) return null;
        return jobSenderMap.get(job.getJobID());
    }

    /**
     * Returns the SENDER LOGIN ID for vehicle history filtering (used by VCController).
     */
    public String getOwnerIDForVehicle(Vehicle vehicle) {
        if (vehicle == null) return null;
        return vehicleSenderMap.get(vehicle.getVehicleID());
    }

    /**
     * Returns the USER-ENTERED Owner ID for display/CSV purposes (used by OwnerGUI/VCControllerGUI).
     */
    public String getVehicleOwnerIDForDisplay(Vehicle vehicle) {
        if (vehicle == null) return null;
        return vehicleOwnerIdMap.get(vehicle.getVehicleID());
    }

    /**
     * Maps the user-entered Owner ID to the vehicle's license plate (used by OwnerGUI on submission).
     */
    public synchronized void mapVehicleOwnerIDForDisplay(String licensePlate, String ownerEnteredID) {
        vehicleOwnerIdMap.put(licensePlate, ownerEnteredID);
        // Note: This mapping is transient until vehicle is registered/saved to DB.
        System.out.println("Server: Mapped license " + licensePlate + " to entered Owner ID " + ownerEnteredID);
    }

    //


    public synchronized Job retrieveJob(String jobID) {
        if (jobID == null) {
            return null;
        }
        for (Job job : storageArchive) {
            if (jobID.equals(job.getJobID())) {
                return job;
            }
        }
        return null;
    }

    public synchronized void storeCheckpoint(Checkpoint checkpoint) {
        if (checkpoint != null) {
            this.checkpointRepo.add(checkpoint);
            // Checkpoints are transient for now, or we could add a DB table.
            // saveState(); 
            System.out.println("Server: Stored checkpoint " + checkpoint.getCheckpointID());
        }
    }

    public synchronized Checkpoint getLatestCheckpoint(String jobID) {
        // Assuming Checkpoint has getJobID() and getTimestamp()
        return checkpointRepo.stream()
                .filter(cp -> cp.getJobID().equals(jobID))
                .max(Comparator.comparing(Checkpoint::getTimestamp))
                .orElse(null);
    }


    /** Registers a client's active notification connection. */
    public synchronized void registerNotificationClient(String userID, ObjectOutputStream oos) {
        activeNotificationClients.put(userID, oos);
        System.out.println("Server: Registered active notifier for user: " + userID);

        // After registration, send any pending notifications immediately
        List<String> pending = userNotifications.getOrDefault(userID, new ArrayList<>());
        if (!pending.isEmpty()) {
            for (String msg : pending) {
                pushNotification(userID, msg);
            }
            pending.clear();
            // saveState(); // Not needed as pushNotification handles persistence logic if failed
        }
    }

    /** Deregisters a client's notification connection ( on logout or disconnect). */
    public synchronized void deregisterNotificationClient(String userID) {
        ObjectOutputStream oos = activeNotificationClients.remove(userID);
        if (oos != null) {
            try {
                oos.close();
                System.out.println("Server: Deregistered and closed notifier for user: " + userID);
            } catch (IOException e) {
                System.err.println("Error closing notifier stream for " + userID + ": " + e.getMessage());
            }
        }
    }

    /** Internal method to push notification over active socket. */
    private void pushNotification(String userID, String message) {
        ObjectOutputStream oos = activeNotificationClients.get(userID);
        if (oos != null) {
            try {
                oos.writeObject(message);
                oos.flush();
                System.out.println("Server PUSH: Notification sent to " + userID + ": " + message);
            } catch (IOException e) {
                System.err.println("Server PUSH failed for " + userID + ". Error: " + e.getMessage());
                // Socket broken, remove client connection
                deregisterNotificationClient(userID);
                // Fallback: queue the message back for the next poll/login
                userNotifications.computeIfAbsent(userID, k -> new ArrayList<>()).add(message);
                saveState();
            }
        } else {
            // If client not connected, use the persistent polling queue as a fallback
            // userNotifications.computeIfAbsent(userID, k -> new ArrayList<>()).add(message);
            // saveState();
            // DatabaseManager handles this.
            System.out.println("Server: Notification queued (no active client) for " + userID);
        }
    }

        /** 
     * Sends a notification with GUARANTEED queuing for reliability.
     * This ensures every notification is persisted and retrievable.
     */
    public synchronized void notifyUser(String userID, String message) {
        if (userID == null || message == null) {
            System.err.println("Server: Cannot notify - null userID or message");
            return;
        }
        
        // Format message with timestamp
        String timestamp = TS_FMT.format(LocalDateTime.now());
        String formattedMessage = "[" + timestamp + "] " + message;

        // Save to DB immediately
        DatabaseManager.getInstance().addNotification(userID, formattedMessage);
        System.out.println("Server: Notification saved for " + userID + ": " + formattedMessage);
        
        // Try to push if client is connected
        ObjectOutputStream oos = activeNotificationClients.get(userID);
        if (oos != null) {
            try {
                oos.writeObject(formattedMessage);
                oos.flush();
                System.out.println("Server PUSH: Live notification sent to " + userID);
            } catch (IOException e) {
                System.err.println("Server PUSH failed for " + userID);
                deregisterNotificationClient(userID);
            }
        }
    }

    /**
     * Retrieval for polling fallback (used on client connection to load queued notifications).
     */
    public synchronized List<String> getNotifications(String userID) {
        return DatabaseManager.getInstance().getNotifications(userID);
    }

    // Store registered vehicle:
    public synchronized void storeRegisteredVehicle(Vehicle vehicle) {
        if (vehicle != null && !registeredVehicles.contains(vehicle)) {
            registeredVehicles.add(vehicle);
            
            String ownerEnteredID = vehicle.getOwnerEnteredID();
            if (ownerEnteredID == null) ownerEnteredID = "UNKNOWN";
            
            String username = vehicle.getSenderID();
            if (username == null) username = "UNKNOWN";
            
            DatabaseManager.getInstance().saveVehicle(vehicle, ownerEnteredID, username);
            System.out.println("Server: Stored registered vehicle " + vehicle.getVehicleID() + " to DB.");
        }
    }

    // Get all registered vehicles:
    public synchronized List<Vehicle> getAllRegisteredVehicles() {
        return new ArrayList<>(registeredVehicles);
    }

    // Store approved job:
    public synchronized void storeApprovedJob(Job job) {
        if (job != null && !approvedJobs.contains(job)) {
            approvedJobs.add(job);
            
            String clientEnteredID = job.getClientEnteredID(); 
            if (clientEnteredID == null) clientEnteredID = "UNKNOWN";
            
            String username = job.getSenderID();
            if (username == null) username = "UNKNOWN";
            
            DatabaseManager.getInstance().saveJob(job, clientEnteredID, username);
            System.out.println("Server: Stored approved job " + job.getJobID() + " to DB.");
        }
    }

    // Get all approved jobs:
    public synchronized List<Job> getAllApprovedJobs() {
        return new ArrayList<>(approvedJobs);
    }
    
    // Store completed job:
    public synchronized void storeCompletedJob(Job job) {
        if (job != null) {
            storageArchive.add(job);
            
            String clientEnteredID = job.getClientEnteredID();
            if (clientEnteredID == null) clientEnteredID = "UNKNOWN";
            
            String username = job.getSenderID();
            if (username == null) username = "UNKNOWN";
            
            DatabaseManager.getInstance().saveJob(job, clientEnteredID, username);
            System.out.println("Server: Stored completed job " + job.getJobID() + " to DB.");
        }
    }
}
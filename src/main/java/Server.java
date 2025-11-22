import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.util.HashMap;

/**
 * Server class with request tracking, pending requests management, and user notifications.
 * Thread-safe implementation using synchronized methods and concurrent collections.
 */
public class Server implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final String STATE_FILE = "server_state.dat";

    private final String serverID;
    private final String ipAddress;

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
    public synchronized void saveState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(STATE_FILE))) {
            oos.writeObject(this.storageArchive);
            oos.writeObject(this.checkpointRepo);
            oos.writeObject(this.pendingRequests);
            oos.writeObject(this.requestCounter);
            oos.writeObject(this.userNotifications);
            oos.writeObject(this.jobClientMap);
            oos.writeObject(this.jobSenderMap);
            oos.writeObject(this.vehicleSenderMap);
            oos.writeObject(this.vehicleOwnerIdMap);
            oos.writeObject(this.registeredVehicles);
            oos.writeObject(this.approvedJobs); 
            System.out.println("Server state saved.");
        } catch (IOException e) {
            System.err.println("Error saving Server state: " + e.getMessage());
        }
    }

    /** Loads the state from file on startup. */
    @SuppressWarnings("unchecked")
    public boolean loadState() {
        File stateFile = new File(STATE_FILE);
        if (!stateFile.exists()) return false;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
            this.storageArchive = (List<Job>) ois.readObject();
            this.checkpointRepo = (List<Checkpoint>) ois.readObject();
            this.pendingRequests = (HashMap<String, Request>) ois.readObject();
            this.requestCounter = (AtomicInteger) ois.readObject();
            this.userNotifications = (ConcurrentHashMap<String, List<String>>) ois.readObject();

            this.jobClientMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.jobSenderMap = (ConcurrentHashMap<String, String>) ois.readObject();

            this.vehicleSenderMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.vehicleOwnerIdMap = (ConcurrentHashMap<String, String>) ois.readObject();

            this.registeredVehicles = (List<Vehicle>) ois.readObject(); 
            this.approvedJobs = (List<Job>) ois.readObject(); 

            return true;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading Server state: " + e.getMessage());
            stateFile.delete();
            return false;
        }
    }

    /**
     * Method to force reload the in-memory state from the latest saved file state.
     * Crucial for multi-instance synchronization.
     */
    @SuppressWarnings("unchecked")
    public synchronized void reloadState() {
        File stateFile = new File(STATE_FILE);
        if (!stateFile.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
            this.storageArchive = (List<Job>) ois.readObject();
            this.checkpointRepo = (List<Checkpoint>) ois.readObject();
            this.pendingRequests = (HashMap<String, Request>) ois.readObject();
            this.requestCounter = (AtomicInteger) ois.readObject();
            this.userNotifications = (ConcurrentHashMap<String, List<String>>) ois.readObject();

            this.jobClientMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.jobSenderMap = (ConcurrentHashMap<String, String>) ois.readObject();

            this.vehicleSenderMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.vehicleOwnerIdMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.registeredVehicles = (List<Vehicle>) ois.readObject(); // NEW
            this.approvedJobs = (List<Job>) ois.readObject(); // NEW
            
            System.out.println("Server: State reloaded from disk.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error reloading Server state: " + e.getMessage());
        }
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
            // Store the SENDER ID (login ID) for filtering job history.
            jobSenderMap.put(job.getJobID(), senderID);

            // Store the user-entered Client ID for display/CSV.
            String clientEnteredID = job.getJobID().split("-")[0];
            jobClientMap.put(job.getJobID(), clientEnteredID);

        } else if (requestType.equals("VEHICLE_REGISTRATION") && data instanceof Vehicle) {
            Vehicle vehicle = (Vehicle) data;
            // Store the SENDER ID (login ID) for filtering vehicle history.
            vehicleSenderMap.put(vehicle.getVehicleID(), senderID);
        }

        saveState();
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

        // Remove from the map once processed to prevent other VCController instances
        // from processing it (since it's no longer Pending).
        pendingRequests.remove(requestID);

        saveState();
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

        // Remove from the map once processed to prevent other VCController instances
        // from processing it (since it's no longer Pending).
        pendingRequests.remove(requestID);

        saveState();
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
        saveState();
        System.out.println("Server: Mapped license " + licensePlate + " to entered Owner ID " + ownerEnteredID);
    }

    //
    public synchronized void storeCompletedJob(Job job) {
        if (job != null) {
            storageArchive.add(job);
            saveState();
            // Update in Database
            String loginID = getLoginIDForJob(job);
            DatabaseManager.getInstance().saveJob(job, loginID);
            System.out.println("Server: Stored completed job " + job.getJobID() + " to DB.");
        }
    }

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
            saveState();
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
            saveState();
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
            userNotifications.computeIfAbsent(userID, k -> new ArrayList<>()).add(message);
            saveState();
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
        
        // STEP 1: ALWAYS add to queue first for guaranteed persistence
        // This ensures the notification survives even if push fails or client disconnects
        userNotifications.computeIfAbsent(userID, k -> new ArrayList<>()).add(message);
        System.out.println("Server: Notification queued for " + userID + ": " + message);
        
        // STEP 2: Immediately save state to persist the queue to disk
        saveState();
        
        // STEP 3: Try to push if client is connected (live notification)
        ObjectOutputStream oos = activeNotificationClients.get(userID);
        if (oos != null) {
            try {
                oos.writeObject(message);
                oos.flush();
                System.out.println("Server PUSH: Live notification sent to " + userID);
                
                // STEP 4: Remove from queue since push was successful
                // Client received it live, so they don't need to poll for it later
                List<String> notifications = userNotifications.get(userID);
                if (notifications != null && !notifications.isEmpty()) {
                    notifications.remove(notifications.size() - 1); // Remove the just-added message
                    saveState(); // Save again to persist the removal
                }
            } catch (IOException e) {
                System.err.println("Server PUSH failed for " + userID + ". Notification remains queued.");
                // Don't remove from queue - client will retrieve it on next login
                deregisterNotificationClient(userID);
            }
        } else {
            System.out.println("Server: Client " + userID + " not connected. Notification queued for retrieval.");
        }
    }

    /**
     * Retrieval for polling fallback (used on client connection to load queued notifications).
     */
    public synchronized List<String> getNotifications(String userID) {
        List<String> notifications = userNotifications.get(userID);
        if (notifications == null || notifications.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> copy = new ArrayList<>(notifications);
        notifications.clear();
        saveState();
        return copy;
    }

    // Store registered vehicle:
    public synchronized void storeRegisteredVehicle(Vehicle vehicle) {
        if (vehicle != null && !registeredVehicles.contains(vehicle)) {
            registeredVehicles.add(vehicle);
            saveState();
            // Save to Database
            String ownerID = getOwnerIDForVehicle(vehicle);
            DatabaseManager.getInstance().saveVehicle(vehicle, ownerID);
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
            saveState();
            // Save to Database
            String clientID = getClientIDForJob(job); // Use user-entered ID or login ID?
            // getClientIDForJob returns user-entered ID. getLoginIDForJob returns login ID.
            // DatabaseManager expects client_id (FK to users). Users table uses login ID (username).
            // So we MUST use getLoginIDForJob(job).
            String loginID = getLoginIDForJob(job);
            DatabaseManager.getInstance().saveJob(job, loginID);
            System.out.println("Server: Stored approved job " + job.getJobID() + " to DB.");
        }
    }

    // Get all approved jobs:
    public synchronized List<Job> getAllApprovedJobs() {
        return new ArrayList<>(approvedJobs);
    }
}
    
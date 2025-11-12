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

/**
 * Server class with request tracking, pending requests management, and user notifications.
 * Thread-safe implementation using synchronized methods and concurrent collections.
 * FIX: Implements createRequest, getRequest, approveRequest, rejectRequest, and all ID mapping getters/setters.
 */
public class Server implements Serializable { 
    private static final long serialVersionUID = 2L; 
    private static final String STATE_FILE = "server_state.dat";

    private final String serverID;
    private final String ipAddress;
    
    // Server State components (made non-final for loading)
    private List<Job> storageArchive;
    private List<Checkpoint> checkpointRepo;
    private ConcurrentHashMap<String, Request> pendingRequests;
    private AtomicInteger requestCounter;
    private ConcurrentHashMap<String, List<String>> userNotifications;
    
    // Map to link Job ID to the USER-ENTERED Client ID (or prefix) - used for job display/CSV
    private ConcurrentHashMap<String, String> jobClientMap;
    
    // Map to link Job ID to the SENDER LOGIN ID - used for job history filtering
    private ConcurrentHashMap<String, String> jobSenderMap;
    
    // Map to link Vehicle ID (License Plate) to the SENDER LOGIN ID - used for vehicle history filtering
    private ConcurrentHashMap<String, String> vehicleSenderMap;
    
    // FIX: Map to link Vehicle ID (License Plate) to the USER-ENTERED Owner ID - used for vehicle display/CSV
    private ConcurrentHashMap<String, String> vehicleOwnerIdMap; 

    // FIX: Map to hold active, persistent notification sockets (transient because sockets aren't serializable)
    private transient ConcurrentHashMap<String, ObjectOutputStream> activeNotificationClients; 

    public Server(String serverID, String ipAddress) {
        this.serverID = serverID;
        this.ipAddress = ipAddress;
        
        // Initialize transient map here, outside of loadState()
        this.activeNotificationClients = new ConcurrentHashMap<>(); 
        
        if (!loadState()) {
            this.storageArchive = new ArrayList<>();
            this.checkpointRepo = new ArrayList<>();
            this.pendingRequests = new ConcurrentHashMap<>();
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
            this.pendingRequests = (ConcurrentHashMap<String, Request>) ois.readObject();
            this.requestCounter = (AtomicInteger) ois.readObject();
            this.userNotifications = (ConcurrentHashMap<String, List<String>>) ois.readObject();
            
            this.jobClientMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.jobSenderMap = (ConcurrentHashMap<String, String>) ois.readObject();
            
            this.vehicleSenderMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.vehicleOwnerIdMap = (ConcurrentHashMap<String, String>) ois.readObject();
            
            return true;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading Server state: " + e.getMessage());
            stateFile.delete(); 
            return false;
        }
    }
    
    /** Method to force reload the in-memory state from the latest saved file state. */
    @SuppressWarnings("unchecked")
    public synchronized void reloadState() {
        File stateFile = new File(STATE_FILE);
        if (!stateFile.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
            this.storageArchive = (List<Job>) ois.readObject();
            this.checkpointRepo = (List<Checkpoint>) ois.readObject();
            this.pendingRequests = (ConcurrentHashMap<String, Request>) ois.readObject();
            this.requestCounter = (AtomicInteger) ois.readObject();
            this.userNotifications = (ConcurrentHashMap<String, List<String>>) ois.readObject(); 
            
            this.jobClientMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.jobSenderMap = (ConcurrentHashMap<String, String>) ois.readObject();
            
            this.vehicleSenderMap = (ConcurrentHashMap<String, String>) ois.readObject();
            this.vehicleOwnerIdMap = (ConcurrentHashMap<String, String>) ois.readObject(); 
            
            System.out.println("Server: State reloaded from disk.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error reloading Server state: " + e.getMessage());
        }
    }

    // =========================================================================
    //                            REQUEST MANAGEMENT
    // =========================================================================
    
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
            
            // Store the user-entered Client ID (prefix of job ID) for display/CSV.
            String clientEnteredID = job.getJobID().split("-")[0]; 
            jobClientMap.put(job.getJobID(), clientEnteredID);
            
        } else if (requestType.equals("VEHICLE_REGISTRATION") && data instanceof Vehicle) {
            Vehicle vehicle = (Vehicle) data;
            // Store the SENDER ID (login ID) for filtering vehicle history.
            // Assuming getVehicleID() returns the unique license plate.
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
     * Gets all pending requests (for display in VC Controller GUI).
     */
    public synchronized List<Request> getPendingRequests() {
        return new ArrayList<>(pendingRequests.values()
            .stream()
            .filter(r -> r.getStatus().equals("Pending"))
            .toList());
    }
    
    /**
     * Marks a request as approved.
     */
    public synchronized boolean approveRequest(String requestID) {
        Request request = pendingRequests.get(requestID);
        if (request == null || !request.getStatus().equals("Pending")) {
            return false;
        }
        
        request.approve();
        saveState();
        System.out.println("Server: Approved request " + requestID);
        return true;
    }
    
    /**
     * Rejects a request.
     */
    public synchronized boolean rejectRequest(String requestID) {
        Request request = pendingRequests.get(requestID);
        if (request == null || !request.getStatus().equals("Pending")) {
            return false;
        }
        
        request.reject();
        saveState();
        System.out.println("Server: Rejected request " + requestID);
        return true;
    }

    // =========================================================================
    //                           ID MAPPING & GETTERS
    // =========================================================================
    
    /**
     * Gets the user-entered Client ID (e.g., "CLIENT1") for a given job.
     */
    public String getClientIDForJob(Job job) {
        if (job == null) return null;
        return jobClientMap.get(job.getJobID());
    }
    
    /**
     * Gets the Login ID (e.g., "client_user") that submitted a job.
     */
    public String getLoginIDForJob(Job job) {
        if (job == null) return null;
        return jobSenderMap.get(job.getJobID());
    }
    
    /** * Returns the SENDER LOGIN ID for vehicle history filtering (used by VCController). 
     * Assumes getVehicleID() returns the unique key (license plate).
     */
    public String getOwnerIDForVehicle(Vehicle vehicle) {
        if (vehicle == null) return null;
        return vehicleSenderMap.get(vehicle.getVehicleID());
    }
    
    /** * Returns the USER-ENTERED Owner ID for display/CSV purposes (used by OwnerGUI/VCControllerGUI). 
     * Assumes getVehicleID() returns the unique key (license plate).
     */
    public String getVehicleOwnerIDForDisplay(Vehicle vehicle) {
        if (vehicle == null) return null;
        return vehicleOwnerIdMap.get(vehicle.getVehicleID());
    }
    
    /** * Maps the user-entered Owner ID to the vehicle's license plate (used by OwnerGUI on submission). 
     */
    public synchronized void mapVehicleOwnerIDForDisplay(String licensePlate, String ownerEnteredID) {
        vehicleOwnerIdMap.put(licensePlate, ownerEnteredID);
        saveState();
        System.out.println("Server: Mapped license " + licensePlate + " to entered Owner ID " + ownerEnteredID);
    }
    
    // =========================================================================
    //                         JOB/CHECKPOINT STORAGE
    // =========================================================================
    
    public synchronized void storeCompletedJob(Job job) {
        if (job != null) {
            storageArchive.add(job);
            saveState(); 
            System.out.println("Server: Stored completed job " + job.getJobID());
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

    public synchronized void storeCheckpoint(Checkpoint checkpoint){
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

    // =========================================================================
    //                             NOTIFICATION PUSH
    // =========================================================================

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

    /** Deregisters a client's notification connection (e.g., on logout or disconnect). */
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
            }
        } else {
            // If client not connected, use the persistent polling queue as a fallback
            userNotifications.computeIfAbsent(userID, k -> new ArrayList<>()).add(message);
            System.out.println("Server: Notification queued (no active client) for " + userID);
        }
    }
    
    /** Sends a notification. Tries push first, falls back to queue. */
    public synchronized void notifyUser(String userID, String message) {
        pushNotification(userID, message);
    }
    
    /** Retrieval for polling fallback (used on client connection to load queued notifications). */
    public synchronized List<String> getNotifications(String userID) {
        reloadState(); 
        List<String> notifications = userNotifications.get(userID);
        if (notifications == null || notifications.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> copy = new ArrayList<>(notifications);
        notifications.clear();
        saveState(); 
        return copy;
    }
}
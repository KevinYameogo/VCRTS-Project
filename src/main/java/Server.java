import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Server implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Server State components
    private List<Job> storageArchive;
    private List<Checkpoint> checkpointRepo;
    private HashMap<String, Request> pendingRequests;
    private List<Request> archivedRequests;
    private AtomicInteger requestCounter;
    
    // In-memory notification store: UserID -> List of Notification objects
    private ConcurrentHashMap<String, List<Notification>> notificationStore;
    
    // In-memory controller logs
    private List<String> controllerLogs;

    // Maps
    private ConcurrentHashMap<String, String> jobClientMap;
    private ConcurrentHashMap<String, String> jobSenderMap;
    private ConcurrentHashMap<String, String> vehicleSenderMap;
    private ConcurrentHashMap<String, String> vehicleOwnerIdMap;

    // Active notification sockets (Transient, Local to Host)
    private transient ConcurrentHashMap<String, ObjectOutputStream> activeNotificationClients;

    private List<Vehicle> registeredVehicles;
    private List<Job> approvedJobs;

    public Server() {
        this.activeNotificationClients = new ConcurrentHashMap<>();
        this.registeredVehicles = new ArrayList<>();
        this.approvedJobs = new ArrayList<>();

        if (!loadState()) {
            this.storageArchive = new ArrayList<>();
            this.checkpointRepo = new ArrayList<>();
            this.pendingRequests = new HashMap<>();
            this.archivedRequests = new ArrayList<>();
            this.requestCounter = new AtomicInteger(1);
            this.notificationStore = new ConcurrentHashMap<>();
            this.controllerLogs = new ArrayList<>();
            this.jobClientMap = new ConcurrentHashMap<>();
            this.jobSenderMap = new ConcurrentHashMap<>();
            this.vehicleSenderMap = new ConcurrentHashMap<>();
            this.vehicleOwnerIdMap = new ConcurrentHashMap<>();
            System.out.println("Server initialized fresh.");
        } else {
            System.out.println("Server loaded.");
        }
    }

    public boolean loadState() {
        reloadState();
        return true;
    }

    public synchronized void reloadState() {
        DatabaseManager db = DatabaseManager.getInstance();
        
        // In-memory only (Reset on restart)
        if (this.pendingRequests == null) this.pendingRequests = new HashMap<>();
        if (this.archivedRequests == null) this.archivedRequests = new ArrayList<>();
        if (this.requestCounter == null) this.requestCounter = new AtomicInteger(1);
        if (this.notificationStore == null) this.notificationStore = new ConcurrentHashMap<>();
        if (this.controllerLogs == null) this.controllerLogs = new ArrayList<>();

        // Persistent Data
        this.registeredVehicles = db.getAllVehicles();
        this.approvedJobs = db.getAllJobs(); 
        
        this.jobClientMap = new ConcurrentHashMap<>(db.getJobClientMap());
        this.jobSenderMap = new ConcurrentHashMap<>(db.getJobSenderMap());
        this.vehicleSenderMap = new ConcurrentHashMap<>(db.getVehicleSenderMap());
        this.vehicleOwnerIdMap = new ConcurrentHashMap<>(db.getVehicleOwnerIdMap());
        
        this.storageArchive = new ArrayList<>();
        for (Job j : this.approvedJobs) {
            if ("Completed".equals(j.getStatus())) {
                this.storageArchive.add(j);
            }
        }
        System.out.println("Server: State reloaded from DB.");
    }

    // --- Request Management ---

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

        System.out.println("Server: Created request " + requestID + " from " + senderID);
        return request;
    }

    public Request getRequest(String requestID) {
        return pendingRequests.get(requestID);
    }

    public synchronized List<Request> getPendingRequests() {
        return pendingRequests.values()
                .stream()
                .filter(r -> r.getStatus().equals("Pending"))
                .toList();
    }

    public synchronized List<Request> getAllRequests() {
        List<Request> all = new ArrayList<>(pendingRequests.values());
        all.addAll(archivedRequests);
        return all;
    }

    public synchronized boolean approveRequest(String requestID) {
        Request request = pendingRequests.get(requestID);
        if (request == null || !request.getStatus().equals("Pending")) {
            return false;
        }
        request.approve();
        pendingRequests.remove(requestID);
        archivedRequests.add(request);
        System.out.println("Server: Approved request " + requestID);
        return true;
    }

    public synchronized boolean rejectRequest(String requestID) {
        Request request = pendingRequests.get(requestID);
        if (request == null || !request.getStatus().equals("Pending")) {
            return false;
        }
        request.reject();
        pendingRequests.remove(requestID);
        archivedRequests.add(request);
        System.out.println("Server: Rejected request " + requestID);
        return true;
    }

    // --- Job & Vehicle Lookups ---

    public String getClientIDForJob(Job job) {
        if (job == null) return null;
        return jobClientMap.get(job.getJobID());
    }

    public String getLoginIDForJob(Job job) {
        if (job == null) return null;
        return jobSenderMap.get(job.getJobID());
    }

    public String getOwnerIDForVehicle(Vehicle vehicle) {
        if (vehicle == null) return null;
        return vehicleSenderMap.get(vehicle.getVehicleID());
    }

    public String getVehicleOwnerIDForDisplay(Vehicle vehicle) {
        if (vehicle == null) return null;
        return vehicleOwnerIdMap.get(vehicle.getVehicleID());
    }

    public synchronized void mapVehicleOwnerIDForDisplay(String licensePlate, String ownerEnteredID) {
        vehicleOwnerIdMap.put(licensePlate, ownerEnteredID);
        System.out.println("Server: Mapped license " + licensePlate + " to " + ownerEnteredID);
    }

    public synchronized Job retrieveJob(String jobID) {
        if (jobID == null) return null;
        for (Job job : storageArchive) {
            if (jobID.equals(job.getJobID())) return job;
        }
        return null;
    }

    // --- Persistence Wrappers ---

    public synchronized void storeRegisteredVehicle(Vehicle vehicle) {
        if (vehicle != null) {
            // Add to memory if not present
            if (!registeredVehicles.contains(vehicle)) {
                registeredVehicles.add(vehicle);
            }
            
            // save to DB
            String ownerEnteredID = vehicle.getOwnerEnteredID();
            if (ownerEnteredID == null) ownerEnteredID = "UNKNOWN";
            String username = vehicle.getSenderID();
            if (username == null) username = "UNKNOWN";
            DatabaseManager.getInstance().saveVehicle(vehicle, ownerEnteredID, username);
            System.out.println("Server: Stored vehicle " + vehicle.getVehicleID());
        }
    }

    public synchronized List<Vehicle> getAllRegisteredVehicles() {
        return new ArrayList<>(registeredVehicles);
    }

    public synchronized void storeApprovedJob(Job job) {
        if (job != null) {
             // Add to memory if not present
            if (!approvedJobs.contains(job)) {
                approvedJobs.add(job);
            }

            // save to DB 
            String clientEnteredID = job.getClientEnteredID(); 
            if (clientEnteredID == null) clientEnteredID = "UNKNOWN";
            String username = job.getSenderID();
            if (username == null) username = "UNKNOWN";
            DatabaseManager.getInstance().saveJob(job, clientEnteredID, username);
            System.out.println("Server: Stored approved job " + job.getJobID());
        }
    }

    public synchronized List<Job> getAllApprovedJobs() {
        return new ArrayList<>(approvedJobs);
    }

    public synchronized void storeCompletedJob(Job job) {
        if (job != null) {
            storageArchive.add(job);
            
            // Always save to DB
            String clientEnteredID = job.getClientEnteredID();
            if (clientEnteredID == null) clientEnteredID = "UNKNOWN";
            String username = job.getSenderID();
            if (username == null) username = "UNKNOWN";
            DatabaseManager.getInstance().saveJob(job, clientEnteredID, username);
            System.out.println("Server: Stored completed job " + job.getJobID());
        }
    }

    // --- Checkpoints ---

    public synchronized void storeCheckpoint(Checkpoint checkpoint) {
        if (checkpoint != null) {
            this.checkpointRepo.add(checkpoint);
            System.out.println("Server: Stored checkpoint " + checkpoint.getCheckpointID());
        }
    }

    public synchronized Checkpoint getLatestCheckpoint(String jobID) {
        return checkpointRepo.stream()
                .filter(cp -> cp.getJobID().equals(jobID))
                .max(Comparator.comparing(Checkpoint::getTimestamp))
                .orElse(null);
    }

    // --- Notifications ---

    public synchronized void registerNotificationClient(String userID, ObjectOutputStream oos) {
        activeNotificationClients.put(userID, oos);
        System.out.println("Server: Registered active notifier for user: " + userID);
    }

    public synchronized void deregisterNotificationClient(String userID) {
        ObjectOutputStream oos = activeNotificationClients.remove(userID);
        if (oos != null) {
            try {
                oos.close();
                System.out.println("Server: Deregistered notifier for user: " + userID);
            } catch (IOException e) {
                System.err.println("Error closing notifier stream: " + e.getMessage());
            }
        }
    }

    public synchronized void notifyUser(String userID, String message) {
        if (userID == null || message == null) return;
        
        String timestamp = TS_FMT.format(LocalDateTime.now());
        String formattedMessage = "[" + timestamp + "] " + message;

        addNotification(userID, formattedMessage);
        System.out.println("Server: Notification saved for " + userID);
        
        // Push if local client connected
        pushNotification(userID, formattedMessage);
    }

    private void pushNotification(String userID, String message) {
        ObjectOutputStream oos = activeNotificationClients.get(userID);
        if (oos != null) {
            try {
                oos.writeObject(message);
                oos.flush();
                System.out.println("Server PUSH: Sent to " + userID);
            } catch (IOException e) {
                System.err.println("Server PUSH failed for " + userID);
                deregisterNotificationClient(userID);
            }
        }
    }

    public synchronized void addNotification(String userID, String message) {
        notificationStore.computeIfAbsent(userID, k -> new ArrayList<>()).add(new Notification(message));
    }

    public synchronized List<String> getNotifications(String userID) {
        List<String> msgs = new ArrayList<>();
        List<Notification> notifs = notificationStore.get(userID);
        if (notifs != null) {
            for (Notification n : notifs) {
                msgs.add(n.message);
            }
        }
        return msgs;
    }

    public synchronized int getUnreadNotificationCount(String userID) {
        List<Notification> notifs = notificationStore.get(userID);
        if (notifs == null) return 0;
        return (int) notifs.stream().filter(n -> !n.isRead).count();
    }

    public synchronized void markNotificationsRead(String userID) {
        List<Notification> notifs = notificationStore.get(userID);
        if (notifs != null) {
            for (Notification n : notifs) {
                n.isRead = true;
            }
        }
    }

    public synchronized void clearNotifications(String userID) {
        notificationStore.remove(userID);
    }

    // --- Controller Logs ---

    public synchronized void logControllerMessage(String message) {
        controllerLogs.add(message);
    }

    public synchronized List<String> getControllerLogs() {
        return new ArrayList<>(controllerLogs);
    }

    public synchronized void clearControllerLogs() {
        controllerLogs.clear();
    }

    // Inner class for Notification
    private static class Notification implements Serializable {
        String message;
        boolean isRead;
        
        Notification(String message) {
            this.message = message;
            this.isRead = false;
        }
    }
}
import java.time.LocalDateTime;
import java.io.Serializable; 

public class Vehicle implements Serializable {
    private static final long serialVersionUID = 1L; 
    
    private final String vehicleID;         // SAME as licensePlate
    private final String make;
    private final String model;
    private final int year; 
    private String status;       
    private final LocalDateTime departureSchedule;
    private String cpuStatus;
    private String memoryStatus;
    
    private final String licensePlate; 
    private final String state;
    
    private String currentJobID; 

    private String ownerEnteredID;
    private String senderID; 

    //Constructor
    public Vehicle(String ownerEnteredID, String senderID, String make, String model, int year, String licensePlate, String state, LocalDateTime departureSchedule) {
        this.vehicleID = licensePlate + "-" + state; // Unique ID composition
        this.ownerEnteredID = ownerEnteredID;
        this.senderID = senderID;
        this.make = make;
        this.model = model;
        this.year = year;
        this.licensePlate = licensePlate; 
        this.state = state;
        this.departureSchedule = departureSchedule;
        this.status = "Available";
        this.cpuStatus = "Idle";
        this.memoryStatus = "Free";
        this.currentJobID = null; 
    }

    // Legacy constructor for backward compatibility
    public Vehicle(String licensePlate, String make, String model, int year, String licensePlateArg, String state, LocalDateTime departureSchedule) {
        this("UNKNOWN", "UNKNOWN", make, model, year, licensePlate, state, departureSchedule);
    }

    public void startExecution(String jobID) {
        this.currentJobID = jobID; 
        this.status = "Active";
        this.cpuStatus = "Busy";
        this.memoryStatus = "Used";
        System.out.println("Vehicle " + getSignature() + " started execution for Job " + this.currentJobID + ".");
    }

    public Checkpoint createCheckpoint() {
        if (currentJobID == null) {
            System.err.println("Error: Vehicle " + getSignature() + " tried to create a checkpoint without an assigned job.");
            return null; 
        }
        
        byte[] mockStateData = ("Vehicle running. Status: " + this.status + " for Job " + this.currentJobID).getBytes();
        String chkID = java.util.UUID.randomUUID().toString();
        
        Checkpoint checkpoint = new Checkpoint(chkID, this.currentJobID, LocalDateTime.now(), mockStateData, getVehicleID());

        System.out.println("Vehicle " + getSignature() + " generated checkpoint " + checkpoint.getCheckpointID());
        return checkpoint; 
    }

    public void loadFromCheckpoint(Checkpoint checkpoint) {
        this.currentJobID = checkpoint.getJobID(); 
        System.out.println("Vehicle " + getSignature() + " loading state from checkpoint " 
                           + checkpoint.getCheckpointID() + " for Job " + this.currentJobID);
        this.status = "Active (Restarted)";
    }

    public String reportStatus() {
        return "Status: " + this.status + 
               ", CPU: " + this.cpuStatus + 
               ", Memory: " + this.memoryStatus + 
               (currentJobID != null ? ", Job ID: " + currentJobID : "");
    }

    public void markAvailable() {
        this.status = "Available";
        this.cpuStatus = "Idle";
        this.memoryStatus = "Free";
        this.currentJobID = null; 
    }
  
    public String getSignature() {
        return this.licensePlate + this.state;
    }
    
    public String getVehicleID() {
        return licensePlate; 
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getMake() { 
        return make; 
    }

    public String getModel() { 
        return model; 
    }
    
    public String getLicenseState(){
        return state;
    }

    public LocalDateTime getDepartureSchedule() {
        return departureSchedule;
    }
    public int getYear() {    
        return year;
    }
    public void restoreState(String status, String cpuStatus, String memoryStatus, String currentJobID) {
        this.status = status;
        this.cpuStatus = cpuStatus;
        this.memoryStatus = memoryStatus;
        this.currentJobID = currentJobID;
    }

    public String getStatus() {
        return status;
    }

    public String getCpuStatus() {
        return cpuStatus;
    }

    public String getMemoryStatus() {
        return memoryStatus;
    }

    public String getCurrentJobID() {
        return currentJobID;
    }

    public String getOwnerEnteredID() {
        return ownerEnteredID;
    }

    public String getSenderID() {
        return senderID;
    }

    private LocalDateTime timestamp;

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

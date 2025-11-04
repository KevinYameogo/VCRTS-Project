// Vehicle.java (FINAL REVISED - ADDED Serializable)
import java.time.LocalDateTime;
// *** FIX 1: Add Java serialization import ***
import java.io.Serializable; 

// *** FIX 2: Implement Serializable ***
public class Vehicle implements Serializable {
    
    // *** FIX 3: Add serialVersionUID for stability ***
    private static final long serialVersionUID = 1L; 
    
    private final String vehicleID;
    private final String make;
    private final String model;
    private String status;         // e.g., "Available", "Active"
    private final LocalDateTime departureSchedule;
    private String cpuStatus;
    private String memoryStatus;
    
    private final String licensePlate; 
    private final String state;


    // Corrected Constructor: using 'licensePlate' not 'vehiclePlate'
    public Vehicle(String vehicleID, String make, String model, int year, String licensePlate, String state, LocalDateTime departureSchedule) {
        this.vehicleID = licensePlate; // *** FIX APPLIED HERE ***
        this.make = make;
        this.model = model;
        this.licensePlate = licensePlate; 
        this.state = state;
        this.departureSchedule = departureSchedule;
        this.status = "Available";
        this.cpuStatus = "Idle";
        this.memoryStatus = "Free";
    }

    // --- UML Methods (REWORKED to remove Job parameter/dependency) ---

    // *executeJob() - REWORKED: Marks vehicle active, VCController passes the job info.
    public void startExecution() {
        this.status = "Active";
        this.cpuStatus = "Busy";
        this.memoryStatus = "Used";
        System.out.println("Vehicle " + getSignature() + " started execution.");
    }

    // *createCheckpoint(): Checkpoint
    public Checkpoint createCheckpoint() {
        // MOCK: Generate some dummy state data.
        byte[] mockStateData = ("Vehicle running. Status: " + this.status).getBytes();
        String chkID = java.util.UUID.randomUUID().toString();
        
        // Pass the Vehicle's ID (licensePlate) to the Checkpoint
        Checkpoint checkpoint = new Checkpoint(chkID, LocalDateTime.now(), mockStateData, getVehicleID());

        System.out.println("Vehicle " + getSignature() + " generated checkpoint " + checkpoint.getCheckpointID());
        return checkpoint; 
    }

    // *loadFromCheckpoint(checkpoint: Checkpoint): void
    public void loadFromCheckpoint(Checkpoint checkpoint) {
        System.out.println("Vehicle " + getSignature() + " loading state from checkpoint " 
                           + checkpoint.getCheckpointID());
        this.status = "Active (Restarted)";
    }

    // *reportStatus(): String
    public String reportStatus() {
        return "Status: " + this.status + 
               ", CPU: " + this.cpuStatus + 
               ", Memory: " + this.memoryStatus;
    }

    // *eraseJobData() - REWORKED: Simple reset/mark available
    public void markAvailable() {
        this.status = "Available";
        this.cpuStatus = "Idle";
        this.memoryStatus = "Free";
    }

    // --- Getters/Helpers ---
    
    public String getSignature() {
        return this.licensePlate + this.state;
    }
    
    public String getVehicleID() {
        return licensePlate; // The unique identifier used by the VCController
    }
    
    public String getStatus() {
        return status;
    }
}
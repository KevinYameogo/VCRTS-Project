import java.io.Serializable;
import java.time.LocalDateTime;

// Job.java

public class Job implements Serializable {
    private static final long serialVersionUID = 1L; 
    
    // --- UML Attributes ---
    private final String jobID; // +jobID: String
    private String status;      // -status: String
    private final int redundancyLevel; // -redundancyLevel: int
    
    // -jobData: any (Using a String placeholder for simplicity)
    private String jobData; 
    
    // -computationResult: any (Using a String placeholder for simplicity)
    private String computationResult; 

    // --- Non-UML Attributes (Required by VCController/ClientGUI) ---
    private final int durationInHours;
    private final LocalDateTime deadline;
    
    // Simplified constructor (used when submitting a NEW job)
    public Job(String jobID, int durationInHours, int redundancyLevel, LocalDateTime deadline) {
        this.jobID = jobID;
        // Status is correctly initialized to Pending for NEW submissions
        this.status = "Pending"; 
        this.redundancyLevel = redundancyLevel;
        this.durationInHours = durationInHours;
        this.deadline = deadline;
        this.jobData = "Default Job Data"; // Initialize placeholder
        this.computationResult = "N/A";    // Initialize placeholder
    }
    
    // --- UML Methods (REQUIRED) ---

    // +updateStatus(newStatus: String): void
    // This is used by VCController when loading a job from persistence to set the stored status.
    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }

    // *setResult(result: any): void (Placeholder uses String)
    public void setResult(String result) {
        this.computationResult = result;
    }

    // *getStatus(): String
    public String getStatus() {
        return status;
    }
    
    // *getJobID(): String
    public String getJobID() { 
        return jobID; 
    }
    
    // *getRedundancyLevel(): int
    public int getRedundancyLevel() { 
        return redundancyLevel; 
    }
    
    // --- Non-UML Getters (Required by VCController for scheduling/GUI) ---
    
    // Used by VCController.calculateCompletionTimes()
    public int getDuration() { 
        return durationInHours; 
    } 
    
    // Used for display in logs/GUI lists
    @Override
    public String toString() {
        // Updated to explicitly show status when printing
        return jobID + " (" + status + ", " + durationInHours + " hrs)";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Job job = (Job) obj;
        return jobID.equals(job.jobID);
    }

    @Override
    public int hashCode() {
        return jobID.hashCode();
    }
}
import java.io.Serializable;
import java.time.LocalDateTime;

// Job.java
import java.time.LocalDateTime;

public class Job implements Serializable {
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
    
    // Simplified constructor to match ClientGUI submission logic
    public Job(String jobID, int durationInHours, int redundancyLevel, LocalDateTime deadline) {
        this.jobID = jobID;
        this.status = "Pending";
        this.redundancyLevel = redundancyLevel;
        this.durationInHours = durationInHours;
        this.deadline = deadline;
        this.jobData = "Default Job Data"; // Initialize placeholder
        this.computationResult = "N/A";    // Initialize placeholder
    }
    
    // --- UML Methods (REQUIRED) ---

    // +updateStatus(newStatus: String): void
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

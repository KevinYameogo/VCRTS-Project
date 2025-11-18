import java.io.Serializable;
import java.time.LocalDateTime;

public class Job implements Serializable {
    private static final long serialVersionUID = 1L; 
    
    
    private final String jobID; 
    private String status;     
    private final int redundancyLevel; 
    
    private String jobData; 
    
    private String computationResult; 

    private final int durationInHours;
    private final LocalDateTime deadline;
    
    // constructor 
    public Job(String jobID, int durationInHours, int redundancyLevel, LocalDateTime deadline) {
        this.jobID = jobID;
        this.status = "Pending"; 
        this.redundancyLevel = redundancyLevel;
        this.durationInHours = durationInHours;
        this.deadline = deadline;
        this.jobData = "Default Job Data"; //placeholder
        this.computationResult = "N/A";    //placeholder
    }
    
    // This is used by VCController when loading a job from persistence to set the stored status.
    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }

 
    public void setResult(String result) {
        this.computationResult = result;
    }

    
    public String getStatus() {
        return status;
    }
    
    public String getJobID() { 
        return jobID; 
    }
    

    public int getRedundancyLevel() { 
        return redundancyLevel; 
    }
    
    
   
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
    
    public LocalDateTime getDeadline(){
        return deadline;
    }
}
import java.time.LocalDateTime;
import java.util.Arrays;
import java.io.Serializable;
import java.util.UUID; 

public class Checkpoint implements Serializable {
    private final String checkpointID;
    private final String jobID; 
    private final LocalDateTime timestamp;
    private final byte[] stateData;
    private String vehicleID;

    private static final long serialVersionUID = 1L;

    
    public Checkpoint(byte[] stateData, String vehicleID, String jobID) { 
        this.checkpointID = UUID.randomUUID().toString();
        this.jobID = jobID; 
        this.timestamp = LocalDateTime.now();
        this.stateData = (stateData == null) ? new byte[0] : Arrays.copyOf(stateData, stateData.length);
        this.vehicleID = vehicleID; 
    }

   
    public Checkpoint(String checkpointID, String jobID, LocalDateTime timestamp, byte[] stateData, String vehicleID) {
        this.checkpointID = (checkpointID == null) ? UUID.randomUUID().toString() : checkpointID;
        this.jobID = jobID;
        this.timestamp = (timestamp == null) ? LocalDateTime.now() : timestamp;
        this.stateData = (stateData == null) ? new byte[0] : Arrays.copyOf(stateData, stateData.length);
        this.vehicleID = vehicleID;
    }

    
    public String getCheckpointID() {
        return checkpointID;
    }
    
    
    public String getJobID() {
        return jobID;
    }

  
    public byte[] getStateData() {
        return Arrays.copyOf(stateData, stateData.length);
    }

   
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getVehicleID(){
        return this.vehicleID;
    }
    
    @Override
    public String toString() {
        return "Checkpoint: " +
                "checkpointID = '" + checkpointID + "\'" +
                ", jobID = '" + jobID + "\'" + 
                ", vehicleID = '" + vehicleID + "\'" + 
                ", timestamp = " + getTimestamp() +
                ", stateDataLength = " + ((stateData == null) ? 0 : stateData.length) +
                '}';
    }
}
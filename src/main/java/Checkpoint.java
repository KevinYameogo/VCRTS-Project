import java.time.LocalDateTime;
import java.util.Arrays;
import java.io.Serializable;
import java.util.UUID; 
// *** FIX: Import ThreadLocalRandom if you intend to generate random IDs here, 
// but sticking to UUID as provided.

public class Checkpoint implements Serializable {
    private final String checkpointID;
    private final LocalDateTime timestamp;
    private final byte[] stateData;
    private String vehicleID;

    private static final long serialVersionUID = 1L;

    // *** FIX: Added vehicleID parameter to the simplified constructor ***
    // Creates new checkpoint with random ID, current timestamp, and provided state data
    public Checkpoint(byte[] stateData, String vehicleID) { // ADDED vehicleID
        this.checkpointID = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.stateData = (stateData == null) ? new byte[0] : Arrays.copyOf(stateData, stateData.length);
        this.vehicleID = vehicleID; // ASSIGN THE VEHICLE ID
    }

    // Creates checkpoint with all values specified (for the purpose of restoring previous checkpoints)
    public Checkpoint(String checkpointID, LocalDateTime timestamp, byte[] stateData,String vehicleID) {
        this.checkpointID = (checkpointID == null) ? UUID.randomUUID().toString() : checkpointID;
        this.timestamp = (timestamp == null) ? LocalDateTime.now() : timestamp;
        this.stateData = (stateData == null) ? new byte[0] : Arrays.copyOf(stateData, stateData.length);
        this.vehicleID = vehicleID;
    }
    // Getter method for checkpointID
    public String getCheckpointID() {
        return checkpointID;
    }

    // Getter method for stateData; returns a copy to maintain immutability
    public byte[] getStateData() {
        return Arrays.copyOf(stateData, stateData.length);
    }

    // Getter method for timestamp
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
                ", vehicleID = '" + vehicleID + "\'" + // Added vehicleID to toString for clarity
                ", timestamp = " + getTimestamp() +
                ", stateDataLength = " + ((stateData == null) ? 0 : stateData.length) +
                '}';
    }
}
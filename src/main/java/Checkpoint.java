import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID; // class to generate immutable + unique identifier


public class Checkpoint {
    private final String checkpointID;
    private final LocalDateTime timestamp;
    private final byte[] stateData;

    // Creates new checkpoint with random ID, current timestamp, and provided state data
    public Checkpoint(byte[] stateData) {
        this.checkpointID = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.stateData = (stateData == null) ? new byte[0] : Arrays.copyOf(stateData, stateData.length);
    }

    // Creates checkpoint with all values specified (for the purpose of restoring previous checkpoints)
    public Checkpoint(String checkpointID, LocalDateTime timestamp, byte[] stateData) {
        this.checkpointID = (checkpointID == null) ? UUID.randomUUID().toString() : checkpointID;
        this.timestamp = (timestamp == null) ? LocalDateTime.now() : timestamp;
        this.stateData = (stateData == null) ? new byte[0] : Arrays.copyOf(stateData, stateData.length);
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

    @Override
    public String toString() {
        return "Checkpoint: " +
                "checkpointID = '" + checkpointID + "\'" +
                ", timestamp = " + getTimestamp() +
                ", stateDataLength = " + ((stateData == null) ? 0 : stateData.length) +
                '}';
    }
}
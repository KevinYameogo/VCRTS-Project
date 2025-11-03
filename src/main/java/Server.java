import java.util.ArrayList;
import java.util.List;


public class Server {
    private final String serverID;
    private final String ipAddress;
    private final List<Job> storageArchive;
    private List<Checkpoint> checkpointRepo; //hold checkpoints

    public Server(String serverID, String ipAddress) {
        this.serverID = serverID;
        this.ipAddress = ipAddress;
        this.storageArchive = new ArrayList<>();
        this.checkpointRepo = new ArrayList<>();
    }

    // Getter methods for serverID and ipAddress
    public String getServerID() { 
        return serverID; 
    }
    public String getIpAddress() { 
        return ipAddress; 
    }

    // Stores job in storageArchive
    public synchronized void storeCompletedJob(Job job) {
        if (job != null) {
            storageArchive.add(job);
        }
    }

    // Retrieves specified job from storageArchive by jobID
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

    //Stores a received checkpoint into the server's repo list
    public void storeCheckpoint(Checkpoint checkpoint){
        this.checkpointRepo.add(checkpoint);
        System.out.println("Server stored checkpoint: " + checkpoint.getCheckpointID()); 
    }


}
import java.util.ArrayList;
import java.util.List;


public class Server {
    private final String serverID;
    private final String ipAddress;
    private final List<Job> storageArchive;

    public Server(String serverID, String ipAddress) {
        this.serverID = serverID;
        this.ipAddress = ipAddress;
        this.storageArchive = new ArrayList<>();
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
}
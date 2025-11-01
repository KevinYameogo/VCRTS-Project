// this is job constructor!

public class Job {
	
	private String clientId;
    private String duration;
    private String deadline;
    private String timestamp;
    

    public Job(String clientId, String duration, String deadline, String timestamp) {
        this.clientId = clientId;
        this.duration = duration;
        this.deadline = deadline;
        this.timestamp = timestamp;
    }
    

    // Getters
    public String getClientId() { return clientId; }
    public String getDuration() { return duration; }
    public String getDeadline() { return deadline; }
    public String getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "Client ID: " + clientId + "\n" +
               "Job Duration: " + duration + "\n" +
               "Job Deadline: " + deadline + "\n" +
               "Submitted At: " + timestamp;
    }

}

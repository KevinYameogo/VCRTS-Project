import java.io.Serializable; 
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a request from a Client or Vehicle Owner to the VC Controller.
 * Includes acknowledgment and decision tracking.
 */
public class Request implements Serializable { 
    private static final long serialVersionUID = 1L; // SerialVersionUID
    
    private final String requestID;
    private final String senderID;
    private final String requestType; // "JOB_SUBMISSION" or "VEHICLE_REGISTRATION"
    private final Object data; // Job or Vehicle object
    private String status; // "Pending", "Approved", "Rejected"
    private final LocalDateTime timestamp;
    private boolean acknowledged;
    private LocalDateTime decisionTimestamp;
    
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public Request(String requestID, String senderID, String requestType, Object data) {
        this.requestID = requestID;
        this.senderID = senderID;
        this.requestType = requestType;
        this.data = data;
        this.status = "Pending";
        this.timestamp = LocalDateTime.now();
        this.acknowledged = false;
    }
    
    // Getters
    public String getRequestID() {
        return requestID;
    }
    
    public String getSenderID() {
        return senderID;
    }
    
    public String getRequestType() {
        return requestType;
    }
    
    public Object getData() {
        return data;
    }
    
    public String getStatus() {
        return status;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public boolean isAcknowledged() {
        return acknowledged;
    }
    
    public LocalDateTime getDecisionTimestamp() {
        return decisionTimestamp;
    }
    
    // Setters and state changes
    public void acknowledge() {
        this.acknowledged = true;
        System.out.println("Request " + requestID + " acknowledged");
    }
    
    public void approve() {
        this.status = "Approved";
        this.decisionTimestamp = LocalDateTime.now();
    }
    
    public void reject() {
        this.status = "Rejected";
        this.decisionTimestamp = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Request{" +
               "ID='" + requestID + '\'' +
               ", sender='" + senderID + '\'' +
               ", type='" + requestType + '\'' +
               ", status='" + status + '\'' +
               ", time=" + timestamp.format(FMT) +
               '}';
    }
}
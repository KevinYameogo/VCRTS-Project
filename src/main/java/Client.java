// Client.java (FIXED: Added secureClientID and methods)
import java.io.Serializable;

// NOTE: Assuming Job class is available (from VCController context)
public class Client extends User implements Serializable { 
    private static final long serialVersionUID = 1L; 
    
    // The unique, unchangeable ID for client operations (random 6-char)
    private String secureClientID;
    
    // -billingInfo: String (Private, matching UML)
    private String billingInfo; 

    // Updated constructor to include password
    public Client(String userID, String name, String password, String billingInfo) { 
        super(userID, name, password); // Call new User constructor
        this.billingInfo = billingInfo;
        // The secureClientID will be initialized by the generator/setter upon first login/creation
        this.secureClientID = ""; 
    }
    
    // +submitJob(jobData: any, redundancy: int): Job (Placeholder implementation)
    // +checkJobStatus(jobID: String): String (Placeholder implementation)

    @Override
    public String getRole() {
        return "Client";
    }

    // Getters and Setters for the GUI to manage state
    public String getBillingInfo() {
        return billingInfo;
    }
    
    public void setBillingInfo(String billingInfo) {
        this.billingInfo = billingInfo;
    }
    
    // NEW: Getter for the secure, unique client ID
    public String getSecureClientID() {
        return secureClientID;
    }

    // NEW: Setter for the secure ID (used for first-time assignment)
    public void setSecureClientID(String secureClientID) {
        this.secureClientID = secureClientID;
    }
}
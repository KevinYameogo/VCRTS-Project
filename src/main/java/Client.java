// Client.java
import java.io.Serializable;

// NOTE: Assuming Job class is available (from VCController context)
public class Client extends User implements Serializable { // ADDED Serializable
    private static final long serialVersionUID = 1L; 
    
    // -billingInfo: String (Private, matching UML)
    private String billingInfo; 

    // Updated constructor to include password
    public Client(String userID, String name, String password, String billingInfo) { // ADDED password
        super(userID, name, password); // Call new User constructor
        this.billingInfo = billingInfo;
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
}
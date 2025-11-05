import java.io.Serializable;

public class Client extends User implements Serializable { 
    private static final long serialVersionUID = 1L; 
    
    // The unique, unchangeable ID for client operations 
    private String secureClientID;
    
    private String billingInfo; 

    public Client(String userID, String name, String password, String billingInfo) { 
        super(userID, name, password); // Call new User constructor
        this.billingInfo = billingInfo;
        this.secureClientID = ""; 
    }

    @Override
    public String getRole() {
        return "Client";
    }

    public String getBillingInfo() {
        return billingInfo;
    }
    
    public void setBillingInfo(String billingInfo) {
        this.billingInfo = billingInfo;
    }
    
    
    public String getSecureClientID() {
        return secureClientID;
    }

    
    public void setSecureClientID(String secureClientID) {
        this.secureClientID = secureClientID;
    }
}
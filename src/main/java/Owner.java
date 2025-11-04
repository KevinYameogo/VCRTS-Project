import java.io.Serializable;
import java.util.Random;

public class Owner extends User implements Serializable {
    private static final long serialVersionUID = 1L; 

    // The unique, unchangeable ID for vehicle registration (random 6-char)
    private String secureOwnerID; 
    
    // Payment info is part of the owner profile
    private String paymentInfo; 

    // Constructor for initial creation (secureOwnerID is generated later or pre-set)
    public Owner(String userID, String name, String password, String paymentInfo) {
        super(userID, name, password);
        this.paymentInfo = paymentInfo;
        // The secureOwnerID will be initialized by the generator/setter upon first login/creation
        this.secureOwnerID = ""; 
    }

    @Override
    public String getRole() {
        return "Owner";
    }

    public String getPaymentInfo() {
        return paymentInfo;
    }

    public void setPaymentInfo(String paymentInfo) {
        this.paymentInfo = paymentInfo;
    }

    public String getSecureOwnerID() {
        return secureOwnerID;
    }

    // NEW: Setter for the secure ID (used for first-time assignment)
    public void setSecureOwnerID(String secureOwnerID) {
        this.secureOwnerID = secureOwnerID;
    }
}
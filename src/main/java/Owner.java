import java.io.Serializable;
import java.util.Random;

public class Owner extends User implements Serializable {
    private static final long serialVersionUID = 1L; 

    private String secureOwnerID; 
    
    private String paymentInfo; 

    // Constructor
    public Owner(String userID, String name, String password, String paymentInfo) {
        super(userID, name, password);
        this.paymentInfo = paymentInfo;
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

    public void setSecureOwnerID(String secureOwnerID) {
        this.secureOwnerID = secureOwnerID;
    }
}
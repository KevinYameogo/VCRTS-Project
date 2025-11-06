import java.io.Serializable;

public class Client extends User implements Serializable { 
    private static final long serialVersionUID = 1L; 
    
    private String billingInfo; 

    public Client(String userID, String name, String password, String billingInfo) { 
        super(userID, name, password);
        this.billingInfo = billingInfo;
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
}
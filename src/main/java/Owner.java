import java.io.Serializable;

public class Owner extends User implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String paymentInfo;

    public Owner(String userID, String name, String password, String paymentInfo) {
        super(userID, name, password);
        this.paymentInfo = paymentInfo;
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
}
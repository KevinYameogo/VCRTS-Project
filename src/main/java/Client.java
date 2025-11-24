import java.io.Serializable;

public class Client extends User implements Serializable { 
    private static final long serialVersionUID = 1L; 
    
    private String cardHolder;
    private String cardNumber;
    private String cvc;
    private String expiry;

    public Client(String userID, String name, String password, String cardHolder, String cardNumber, String cvc, String expiry) { 
        super(userID, name, password);
        this.cardHolder = cardHolder;
        this.cardNumber = cardNumber;
        this.cvc = cvc;
        this.expiry = expiry;
    }

    @Override
    public String getRole() {
        return "Client";
    }

    public String getCardHolder() { return cardHolder; }
    public String getCardNumber() { return cardNumber; }
    public String getCvc() { return cvc; }
    public String getExpiry() { return expiry; }
    
    public void setBillingInfo(String cardHolder, String cardNumber, String cvc, String expiry) {
        this.cardHolder = cardHolder;
        this.cardNumber = cardNumber;
        this.cvc = cvc;
        this.expiry = expiry;
    }
}
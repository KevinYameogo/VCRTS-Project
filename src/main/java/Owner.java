// VehicleOwner.java
import java.io.Serializable;

// NOTE: Assuming Vehicle class is available
public class Owner extends User implements Serializable  { // ADDED Serializable
    private static final long serialVersionUID = 1L; 
    
    // -paymentInfo: String (Private, matching UML)
    private String paymentInfo; 

    // Updated constructor to include password
    public Owner(String userID, String name, String password, String paymentInfo) { // ADDED password
        super(userID, name, password); // Call new User constructor
        this.paymentInfo = paymentInfo;
    }
    
    // +registerVehicle(vehicle: Vehicle): void (Placeholder implementation)
    // +viewVehicleStatus(vehicleID: String): String (Placeholder implementation)

    @Override
    public String getRole() {
        // Renamed from "Owner" to "VehicleOwner" for class consistency
        return "Owner"; 
    }

    // Getters and Setters for the GUI to manage state
    public String getPaymentInfo() {
        return paymentInfo;
    }
    
    public void setPaymentInfo(String paymentInfo) {
        this.paymentInfo = paymentInfo;
    }
}
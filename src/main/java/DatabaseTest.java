import java.time.LocalDateTime;
import java.util.List;

public class DatabaseTest {
    public static void main(String[] args) {
        System.out.println("Starting Database Test...");

        DatabaseManager db = DatabaseManager.getInstance();
        
        // 1. Test Connection
        db.connect();
        
        // 2. Test User (Client)
        System.out.println("\n--- Testing User (Client) ---");
        Client client = new Client("testClient", "Test Client", "password123", "Visa 1234");
        db.saveUser(client);
        
        User loadedClient = db.loadUser("testClient");
        if (loadedClient != null && loadedClient instanceof Client) {
            System.out.println("SUCCESS: Loaded Client: " + loadedClient.getName() + ", Info: " + ((Client)loadedClient).getBillingInfo());
        } else {
            System.err.println("FAILURE: Could not load client.");
        }

        // 3. Test Job
        System.out.println("\n--- Testing Job ---");
        Job job = new Job("job-test-1", 5, 2, LocalDateTime.now().plusDays(1));
        db.saveJob(job, "testClient");
        
        String status = db.getJobStatus("job-test-1");
        System.out.println("Job Status: " + status);
        if ("Pending".equals(status)) {
             System.out.println("SUCCESS: Job saved with Pending status.");
        } else {
             System.err.println("FAILURE: Job status mismatch.");
        }
        
        // 4. Test Vehicle
        System.out.println("\n--- Testing Vehicle ---");
        Owner owner = new Owner("testOwner", "Test Owner", "pass456", "PayPal");
        db.saveUser(owner);
        
        Vehicle vehicle = new Vehicle("V123", "Toyota", "Camry", 2020, "V123", "CA", LocalDateTime.now().plusHours(2));
        db.saveVehicle(vehicle, "testOwner");
        
        List<Vehicle> history = db.getOwnerVehicleHistory("testOwner");
        if (!history.isEmpty() && history.get(0).getVehicleID().equals("V123")) {
            System.out.println("SUCCESS: Retrieved vehicle history.");
        } else {
            System.err.println("FAILURE: Vehicle history mismatch.");
        }

        System.out.println("\nDatabase Test Completed.");
        db.disconnect();
    }
}

import java.util.List;

public class DatabaseTest {
    public static void main(String[] args) {
        System.out.println("Starting Database Test...");
        
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect();
        
        // Test User Persistence
        System.out.println("\n--- Testing User Persistence ---");
        String testUser = "testUser" + System.currentTimeMillis();
        String testPass = "pass123";
        Client client = new Client(testUser, "Test Client", testPass, "Holder", "1234", "123", "12/25");
        
        System.out.println("Saving user: " + testUser);
        db.saveUser(client);
        
        System.out.println("Loading user: " + testUser);
        User loaded = db.loadUser(testUser);
        
        if (loaded != null) {
            System.out.println("SUCCESS: User loaded.");
            System.out.println("Name: " + loaded.getName());
            System.out.println("Pass: " + loaded.getPassword());
            if (loaded.getPassword().equals(testPass)) {
                System.out.println("Password match: YES");
            } else {
                System.out.println("Password match: NO");
            }
        } else {
            System.out.println("FAILURE: User not found.");
        }
        
        db.disconnect();
    }
}

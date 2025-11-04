import java.io.Serializable;

public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L; 

    // #userID: String (protected, matching UML)
    protected String userID; 
    // #name: String (protected, matching UML)
    protected String name;   
    // NEW: Protected password field
    protected String password; 

    // Constructor to initialize required properties, now including password
    public User(String userID, String name, String password) { // ADDED password
        this.userID = userID;
        this.name = name;
        this.password = password;
    }

    // +login(): boolean (Public, matching UML)
    public boolean login() {
        return true; 
    }

    // +logout(): void (Public, matching UML)
    public void logout() {
        System.out.println(name + " logged out.");
    }
    
    // Getters for common attributes
    public String getUserID() {
        return userID;
    }

    public String getName() {
        return name;
    }
    
    // NEW: Getter for password (used by FileBasedUserStore for comparison)
    public String getPassword() {
        return password;
    }
    
    // NEW: Setter for password (used when saving temporary or new passwords)
    public void setPassword(String password) {
        this.password = password;
    }
    
    // Abstract method to differentiate user-specific dashboards
    public abstract String getRole(); 
}
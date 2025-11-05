import java.io.Serializable;

public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L; 


    protected String userID; 
    protected String name;   
    protected String password; 

    // Constructor
    public User(String userID, String name, String password) { 
        this.userID = userID;
        this.name = name;
        this.password = password;
    }

  
    public boolean login() {
        return true; 
    }

  
    public void logout() {
        System.out.println(name + " logged out.");
    }
    
    
    public String getUserID() {
        return userID;
    }

    public String getName() {
        return name;
    }
    
   
    public String getPassword() {
        return password;
    }
    

    public void setPassword(String password) {
        this.password = password;
    }
    
    // Abstract method to differentiate user-specific dashboards
    public abstract String getRole(); 
}
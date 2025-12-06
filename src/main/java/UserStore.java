import javax.swing.JOptionPane;

// Handles saving and loading the User object via DatabaseManager
public class UserStore {
    
    /**
     * Saves the User object to the database.
     */
    public static void saveUser(User user) {
        if (user == null) return;
        try {
            DatabaseManager.getInstance().saveUser(user);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error saving user data to database.", "Persistence Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads the User object from the database and checks the input password.
     */
    public static User loadUser(String username, String role) {
        try {
            User loadedUser = DatabaseManager.getInstance().loadUser(username);
            if (loadedUser != null && loadedUser.getRole().equals(role)) {
                return loadedUser;
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error loading user data from database.", "Error", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }
}
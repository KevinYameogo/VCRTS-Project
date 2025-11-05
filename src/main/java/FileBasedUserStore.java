import java.io.*;
import javax.swing.JOptionPane;

// Handles saving and loading the User object (which includes the password)
public class FileBasedUserStore {
    
    // Naming convention for the user file: [username]_user.dat
    private static String getFileName(String username, String role) {
        // We use the username for the file name
        return username + "_user.dat"; 
    }

    /**
     * Saves the User object (including the current password) to a file.
     * Uses Java Object Serialization.
     */
    public static void saveUser(User user) {
        if (user == null) return;
        String fileName = getFileName(user.getUserID(), user.getRole());
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileName))) {
            oos.writeObject(user);
            System.out.println("User data (including password) saved to " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error saving user data.", "Persistence Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads the User object from the file and checks the input password.
     * Uses Java Object Deserialization.
     */
    public static User loadUser(String username, String role) {
        String fileName = getFileName(username, role);
        File userFile = new File(fileName);
        
        if (!userFile.exists()) {
            return null; 
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(userFile))) {
            Object obj = ois.readObject();
            if (obj instanceof User) {
                User loadedUser = (User) obj;
                if (loadedUser.getRole().equals(role)) {
                    return loadedUser;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            userFile.delete(); 
            JOptionPane.showMessageDialog(null, "Corrupt user file found. File deleted.", "Error", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }
}
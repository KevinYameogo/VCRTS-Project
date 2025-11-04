// LandingPage.java (FINAL FIX: Implements 'Generate Pass' on login view, handles Client/Owner secure IDs)
import javax.swing.*;
import java.awt.*;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.*;
import java.util.Random; 

// NOTE: Assumes all model classes (User, Client, Owner) and FileBasedUserStore, 
// and the new ClientIDGenerator/OwnerIDGenerator are available.

public class LandingPage extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainContentPanel; 
    private VCController controller;
    private JComboBox<String> roleComboBox;
    private JPanel loginPanel;
    private JLabel infoLabel;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton generatePassButton; 
    private JTextArea tempPassDisplayArea; 

    // Load .env variables outside the constructor for access
    // Note: The reliance on these is now minimized, primarily for the *very first* bootstapping login.
    Dotenv dotenv = Dotenv.load();

    private final String CLIENT_USERNAME = dotenv.get("CLIENT_USERNAME");
    private final String CLIENT_PASSWORD = dotenv.get("CLIENT_PASSWORD");
    private final String OWNER_USERNAME = dotenv.get("OWNER_USERNAME");
    private final String OWNER_PASSWORD = dotenv.get("OWNER_PASSWORD");
    

    public LandingPage(VCController controller) {
        setTitle("VCRTS");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);
        
        this.controller = controller;
        cardLayout = new CardLayout();
        mainContentPanel = new JPanel(cardLayout);
        JPanel loginViewPanel = createLoginPanel();
        mainContentPanel.add(loginViewPanel, "LOGIN");
        add(mainContentPanel);
        cardLayout.show(mainContentPanel, "LOGIN");
        setVisible(true);
    }

    private JPanel createLoginPanel() {
        // --- UI Setup ---
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(187, 213, 237));
        
        JLabel logoLabel = new JLabel("VCRTS", SwingConstants.CENTER);
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(logoLabel);
        
        JLabel fullNameLabel = new JLabel("Vehicular Cloud Real Time System", SwingConstants.CENTER);
        fullNameLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        fullNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(fullNameLabel);

        mainPanel.add(Box.createVerticalStrut(8));

        JLabel welcomeLabel = new JLabel("Manage vehicular cloud resources for clients and vehicle owners with live job tracking and reporting.", SwingConstants.CENTER);
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(welcomeLabel);

        mainPanel.add(Box.createVerticalStrut(10));

        infoLabel = new JLabel("Choose your role to continue.", SwingConstants.CENTER);
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        infoLabel.setForeground(Color.DARK_GRAY);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(infoLabel);

        String[] roles = {"Select role", "Client", "Owner"};
        roleComboBox = new JComboBox<>(roles);
        roleComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(roleComboBox);

        mainPanel.add(Box.createVerticalStrut(6));

        loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        loginPanel.setVisible(false);
        loginPanel.setBackground(new Color(187, 213, 237));

        JLabel usernameLabel = new JLabel("Username");
        usernameLabel.setForeground(Color.BLACK);
        usernameLabel.setFont(new Font("MONOSCAPED", Font.BOLD, 14));
        usernameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginPanel.add(usernameLabel);

        usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(300, 30));
        usernameField.setMaximumSize(new Dimension(300, 30));
        usernameField.setFont(new Font("SansSerif", Font.BOLD, 16));
        usernameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginPanel.add(usernameField);

        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setForeground(Color.BLACK);
        passwordLabel.setFont(new Font("MONOSCAPED", Font.BOLD, 14));
        passwordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginPanel.add(passwordLabel);

        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(300, 30));
        passwordField.setMaximumSize(new Dimension(300, 30));
        passwordField.setFont(new Font("SansSerif", Font.BOLD, 16));
        passwordField.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginPanel.add(passwordField);

        // NEW: Panel for login button and Generate Pass button
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonRow.setOpaque(false);
        JButton loginButton = new JButton("Login");
        generatePassButton = new JButton("Generate Pass"); 
        buttonRow.add(loginButton);
        buttonRow.add(generatePassButton); 
        
        // NEW: Text area for temporary password display
        tempPassDisplayArea = new JTextArea("Use this panel to generate your initial login password.");
        tempPassDisplayArea.setEditable(false);
        tempPassDisplayArea.setLineWrap(true);
        tempPassDisplayArea.setWrapStyleWord(true); 
        tempPassDisplayArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tempPassDisplayArea.setBackground(new Color(240, 248, 255));
        tempPassDisplayArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        tempPassDisplayArea.setPreferredSize(new Dimension(300, 50));
        tempPassDisplayArea.setMaximumSize(new Dimension(300, 50));
        tempPassDisplayArea.setAlignmentX(Component.CENTER_ALIGNMENT);


        loginPanel.add(Box.createVerticalStrut(7));
        loginPanel.add(Box.createVerticalStrut(7));
        loginPanel.add(Box.createVerticalStrut(10));
        loginPanel.add(buttonRow); 
        loginPanel.add(Box.createVerticalStrut(10));
        loginPanel.add(tempPassDisplayArea); 
        
        mainPanel.add(loginPanel);
        
        // --- Role Selection (Updates text guidance) ---
        roleComboBox.addActionListener(e -> {
            int index = roleComboBox.getSelectedIndex();
            usernameField.setText("");
            passwordField.setText("");
            
            if (index == 1) { // Client
                infoLabel.setText("Client view: Welcome! Login to continue.");
                loginPanel.setVisible(true);
                tempPassDisplayArea.setText("New Client? Enter a username and click 'Generate Pass'.");
            } else if (index == 2) { // Owner
                infoLabel.setText("Owner view: Welcome! Login to continue.");
                loginPanel.setVisible(true);
                tempPassDisplayArea.setText("New Owner? Enter a username and click 'Generate Pass'.");
            } else { // Select role
                infoLabel.setText("Choose your role to continue.");
                loginPanel.setVisible(false);
                tempPassDisplayArea.setText("Use this panel to generate your initial login password.");
            }
            tempPassDisplayArea.setBackground(new Color(240, 248, 255));
        });

        // NEW: Generate Pass Action
        generatePassButton.addActionListener(e -> generateAndStoreTempPass());

        
        // --- AUTHENTICATION LOGIC ---
        loginButton.addActionListener(ev -> {
            int index = roleComboBox.getSelectedIndex();
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            
            User authenticatedUser = null; 
            String role = (String) roleComboBox.getSelectedItem();

            Runnable onLogout = () -> {
                cardLayout.show(mainContentPanel, "LOGIN");
                passwordField.setText("");
            };

            if (index == 0) {
                 infoLabel.setText("Select a role to continue.");
                 return;
            }

            // Centralized authentication logic
            authenticatedUser = loadUserOrAuthenticate(username, password, role);
            
            // Process login result
            if (authenticatedUser != null) {
                
                if (authenticatedUser instanceof Client) {
                    ClientGUI clientPanel = new ClientGUI((Client) authenticatedUser, onLogout, this.controller);
                    mainContentPanel.add(clientPanel, "CLIENT_VIEW");
                    cardLayout.show(mainContentPanel, "CLIENT_VIEW");
                    
                } else if (authenticatedUser instanceof Owner) {
                    OwnerGUI ownerPanel = new OwnerGUI((Owner) authenticatedUser, onLogout, this.controller);
                    mainContentPanel.add(ownerPanel, "OWNER_VIEW");
                    cardLayout.show(mainContentPanel, "OWNER_VIEW");
                }
                
                // Success message
                infoLabel.setText("Login successful! Welcome, " + authenticatedUser.getName());
                
            } else {
                infoLabel.setText("Invalid username or password!");
            }
        });

        return mainPanel;
    }
    
    /**
     * Attempts to load existing user data from file and authenticate. 
     * If the file does not exist, it allows a one-time login via the hardcoded .env password.
     */
    private User loadUserOrAuthenticate(String username, String password, String role) {
        
        // 1. Try to load user from file (Persistent Login) - PRIMARY SOURCE OF TRUTH
        User userFromFile = FileBasedUserStore.loadUser(username, role);
        
        if (userFromFile != null) {
            // File Found: Authenticate against the file-stored password
            if (userFromFile.getPassword().equals(password)) {
                
                // NEW: FIX for Client and Owner - ensure secure ID is assigned upon file load
                if (userFromFile instanceof Owner) {
                    Owner owner = (Owner) userFromFile;
                    // Check if the secureOwnerID is null or empty
                    if (owner.getSecureOwnerID() == null || owner.getSecureOwnerID().isEmpty()) {
                        String newID = OwnerIDGenerator.generateRandomID();
                        owner.setSecureOwnerID(newID);
                        FileBasedUserStore.saveUser(owner); // Resave the object with the new ID
                        System.out.println("FIX: Assigned missing secureOwnerID to existing owner: " + newID);
                    }
                } else if (userFromFile instanceof Client) { // ADDED CLIENT ID CHECK
                    Client client = (Client) userFromFile;
                    // Check if the secureClientID is null or empty
                    if (client.getSecureClientID() == null || client.getSecureClientID().isEmpty()) {
                        String newID = ClientIDGenerator.generateRandomID();
                        client.setSecureClientID(newID);
                        FileBasedUserStore.saveUser(client); // Resave the object with the new ID
                        System.out.println("FIX: Assigned missing secureClientID to existing client: " + newID);
                    }
                }
                
                return userFromFile; // SUCCESS: Logged in with file-stored password
            }
            // File found, but password failed -> Fail login
            return null; 
        }
        
        // 2. File Not Found: ONE-TIME Initial Authentication using .env (Bootstrap)
        
        String targetPassword;
        if (role.equals("Client")) {
            targetPassword = CLIENT_PASSWORD;
        } else if (role.equals("Owner")) {
            targetPassword = OWNER_PASSWORD; 
        } else {
            return null;
        }

        if (password.equals(targetPassword)) {
            // SUCCESS: Initial login using the hardcoded password. 
            // Return a temporary object. The user must use 'Generate Pass' to persist this user.
             if (role.equals("Client")) {
                 // FIX: Client must have a new secureClientID generated upon successful bootstrap
                Client newClient = new Client(username, "Initial Client User", targetPassword, ""); 
                newClient.setSecureClientID(ClientIDGenerator.generateRandomID()); // Generate and set the ID
                return newClient;
            } else {
                // FIX: Owner must have a new secureOwnerID generated upon successful bootstrap
                Owner newOwner = new Owner(username, "Initial Vehicle Owner", targetPassword, ""); 
                newOwner.setSecureOwnerID(OwnerIDGenerator.generateRandomID()); // Generate and set the ID
                return newOwner;
            }
        }

        return null; // Authentication failed
    }

    /**
     * Generates a temporary password, creates the user object, and saves it to the file.
     * This establishes the user file as the source of truth, removing reliance on .env.
     */
    private void generateAndStoreTempPass() {
        String username = usernameField.getText().trim();
        String role = (String) roleComboBox.getSelectedItem();

        if (username.isEmpty() || role.equals("Select role")) {
            tempPassDisplayArea.setText("Error: Enter a username and select a role first.");
            tempPassDisplayArea.setBackground(new Color(255, 200, 200));
            return;
        }

        // 1. Check if user already exists based on the file.
        // If it exists, they should use the file-stored password.
        if (FileBasedUserStore.loadUser(username, role) != null) {
            tempPassDisplayArea.setText("Error: User file already exists. Please log in.");
            tempPassDisplayArea.setBackground(new Color(255, 200, 200));
            return;
        }
        
        String tempPassword = generateTempPassword(username);
        User newUser;
        String secureID = "";

        // Ensure the correct subclass is instantiated for saving and assign the secure ID
        if (role.equals("Client")) {
            Client newClient = new Client(username, "Client User", tempPassword, ""); 
            secureID = ClientIDGenerator.generateRandomID();
            newClient.setSecureClientID(secureID);
            newUser = newClient;
        } else {
            Owner newOwner = new Owner(username, "Vehicle Owner", tempPassword, ""); 
            secureID = OwnerIDGenerator.generateRandomID();
            newOwner.setSecureOwnerID(secureID);
            newUser = newOwner;
        }
        
        // 4. Save the new user object with the temporary password immediately (source of truth)
        FileBasedUserStore.saveUser(newUser);

        // 5. Update display area
        String secureIDInfo = "";
        if (newUser instanceof Owner) {
            secureIDInfo = "\nOwner ID: " + ((Owner)newUser).getSecureOwnerID();
        } else if (newUser instanceof Client) {
            secureIDInfo = "\nClient ID: " + ((Client)newUser).getSecureClientID();
        }

        tempPassDisplayArea.setBackground(new Color(200, 255, 200));
        tempPassDisplayArea.setText(
            "SUCCESS: New user file created for '" + username + "'!\n" +
            "Password: " + tempPassword + secureIDInfo + "\n" +
            "Enter this password above to log in."
        );
        infoLabel.setText("Temporary password generated. Log in above.");
        passwordField.setText("");
    }

    /**
     * Generates a temporary password string.
     */
    private String generateTempPassword(String username) {
        // Ensures the generated part is 3 digits (100-999)
        return username + (new Random().nextInt(900) + 100); 
    }
}

import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import io.github.cdimascio.dotenv.Dotenv;


public class LandingPage extends JFrame {
 
    private JComboBox<String> roleComboBox;
    private JPanel loginPanel;
    private JLabel infoLabel;
    Dotenv dotenv = Dotenv.load();

    

    // Client auth info
    private final String CLIENT_USERNAME = dotenv.get("CLIENT_USERNAME");
    private final String CLIENT_PASSWORD = dotenv.get("CLIENT_PASSWORD");
  
    // Owner auth info
     
    private final String OWNER_USERNAME = dotenv.get("OWNER_USERNAME"); 
    private final String OWNER_PASSWORD = dotenv.get("OWNER_PASSWORD");

    public LandingPage() {
        //main frame boiler
        setTitle("VCRTS");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);

        // Main panel with vertical BoxLayout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));// padding top,left,bottom, right

        mainPanel.setBackground(new Color(187, 213, 237));

        
        // Logo
        JLabel logoLabel = new JLabel("VCRTS", SwingConstants.CENTER);
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(logoLabel);

        // full name
        JLabel fullNameLabel = new JLabel("Vehicular Cloud Real Time System", SwingConstants.CENTER);
        fullNameLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        fullNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(fullNameLabel);

        // Spacing between component in our vertical container
        mainPanel.add(Box.createVerticalStrut(8));

        // Welcome message
        JLabel welcomeLabel = new JLabel("Manage vehicular cloud resources for clients and vehicle owners with live job tracking and reporting.", SwingConstants.CENTER);
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(welcomeLabel);

        // Spacing
        mainPanel.add(Box.createVerticalStrut(10));

        // Info label for role
        infoLabel = new JLabel("Choose your role to continue.", SwingConstants.CENTER);
        infoLabel.setFont(new Font("SansSerif",Font.BOLD,18));
        infoLabel.setForeground(Color.DARK_GRAY);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(infoLabel);

        //  dropdown menu for role
        String[] roles = {"Select role", "Client", "Owner"};
        roleComboBox = new JComboBox<>(roles);
        roleComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(roleComboBox);

        // Spacing
        mainPanel.add(Box.createVerticalStrut(6));

        // Panel for login fields
        loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        loginPanel.setVisible(false);
        loginPanel.setBackground(new Color(187, 213, 237));

        //username field label //princetaller
        JLabel usernameLabel = new JLabel("Username");
        usernameLabel.setForeground(Color.BLACK); 
        usernameLabel.setFont(new Font("MONOSCAPED", Font.BOLD, 14)); // font and size
        usernameLabel.setAlignmentX(Component.CENTER_ALIGNMENT); // center in BoxLayout
        loginPanel.add(usernameLabel);

        // Username input field
        JTextField usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(300, 30));
        usernameField.setMaximumSize(new Dimension(300, 30));
        usernameField.setFont(new Font("SansSerif",Font.BOLD,16));
        usernameField.setAlignmentX(Component.CENTER_ALIGNMENT);

        //Password field label //guy1234

        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setForeground(Color.BLACK);
        passwordLabel.setFont(new Font("MONOSCAPED", Font.BOLD, 14));
        passwordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginPanel.add(passwordLabel);

        // Password input field
        JPasswordField passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(300, 30));
        passwordField.setMaximumSize(new Dimension(300, 30));
        passwordField.setFont(new Font("SansSerif",Font.BOLD,16));
        passwordField.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Login button
        JButton loginButton = new JButton("Login");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add fields to loginPanel
        loginPanel.add(Box.createVerticalStrut(7));
        loginPanel.add(usernameLabel);
        loginPanel.add(usernameField);
        loginPanel.add(Box.createVerticalStrut(7));
        loginPanel.add(passwordLabel);
        loginPanel.add(passwordField);
        loginPanel.add(Box.createVerticalStrut(10));
        loginPanel.add(loginButton);

        // Add loginPanel to mainPanel
        mainPanel.add(loginPanel);

        // Role selection 
        roleComboBox.addActionListener(e -> {
          int index = roleComboBox.getSelectedIndex();
          if (index == 1) { // Client
              infoLabel.setText("Client view: Welcome! Login to continue.");
              loginPanel.setVisible(true);
          } else if (index == 2) { // Owner
              infoLabel.setText("Owner view: Welcome! Login to continue.");
              loginPanel.setVisible(true);
          } else { // Select role
              infoLabel.setText("Choose your role to continue.");
              loginPanel.setVisible(false);
          }
      });

        // show pages for each role(client or owner)
        loginButton.addActionListener(ev -> {
          int index = roleComboBox.getSelectedIndex();
          String username = usernameField.getText();
          String password = new String(passwordField.getPassword());
      
          if (index == 1) { // Client
      	    if (username.equals("princetaller") && password.equals("guy1234")) {
      	        this.setVisible(false);
      	        // Automatically open the VisJob page for the client
      	        new VisJob(username);
      	    } else {
      	        infoLabel.setText("Invalid username or password!");
      	    }
      	
          } else if (index == 2) { // Owner
              if (username.equals("owner") && password.equals("owner123")) {
                 this.setVisible(false);
                  //create a Owner.java class
                  //owner.rentCar(); // your owner logic here
                  Owner owner1 = new Owner(username);
              } else {
                  infoLabel.setText("Invalid username or password!");
              }
          } else {
              infoLabel.setText("Select a role to continue.");
          }
      });

        // Add mainPanel to frame
        add(mainPanel);
        setVisible(true);
    }
}

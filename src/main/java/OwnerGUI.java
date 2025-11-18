import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.List;
import java.util.ArrayList;
import java.net.Socket;

/**
 * OwnerGUI with persistent notification 
 */
public class OwnerGUI extends JPanel {

    private final Owner ownerUser;
    private final Runnable onLogout;
    private final VCController controller;
    private final Server server;

    // Request tracking
    private Map<String, String> submittedRequests;
    
    // Notification components
    private static NotificationClient backgroundNotificationClient; // STATIC for persistence
    private static Thread notificationThread; // STATIC for persistence
    private Timer statusRefreshTimer;

    private JTextField ownerIdField;
    private JSpinner departureMonthSpinner;
    private JSpinner departureDaySpinner;
    private JSpinner departureYearSpinner;
    private JSpinner departureHourSpinner;
    private JTextField makeField;
    private JTextField modelField;
    private JSpinner yearSpinner;
    private JTextField licenseField;
    private JComboBox<String> stateComboBox;

    private String paymentInfo; 
    private JButton addPaymentButton; 
    private JTable paymentTable;
    private DefaultTableModel paymentTableModel;
    private PaymentInfoDialog paymentDialog;
    
    private JPasswordField oldPassField;
    private JPasswordField newPassField;
    private JPasswordField confirmNewPassField;
    private JLabel passStatusLabel;

    private DefaultListModel<String> notificationListModel;
    private JList<String> notificationList;
    private JLabel badgeLabel;
    private int notificationCount = 0;
    private int notificationsTabIndex;

    private DefaultTableModel tableModel;
    private JTable table;

    private final String PAYMENT_FILE;
    private final String NOTIFICATION_FILE;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] STATES = {"", "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN",
            "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH",
            "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"};

    public OwnerGUI(Owner ownerUser, Runnable onLogout, VCController controller) { 
        this.ownerUser = ownerUser; 
        this.onLogout = onLogout;
        this.controller = controller;
        this.server = controller.getServer();
        this.submittedRequests = new HashMap<>();
        
        this.paymentInfo = ownerUser.getPaymentInfo();
        this.PAYMENT_FILE = ownerUser.getUserID() + "_" + ownerUser.getRole() + "_payment.dat";
        this.NOTIFICATION_FILE = ownerUser.getUserID() + "_" + ownerUser.getRole() + "_notifications.txt";

        this.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Register Vehicle", createRegisterVehiclePanel());
        tabs.addTab("Manage Payment", createPaymentPanel());
        tabs.addTab("Change Password", createPasswordPanel());
        
        JPanel notifPanel = createNotificationsPanel();
        notificationsTabIndex = tabs.getTabCount();
        tabs.addTab("Notifications", notifPanel);

        badgeLabel = new JLabel("0");
        badgeLabel.setOpaque(true);
        badgeLabel.setBackground(Color.RED);
        badgeLabel.setForeground(Color.WHITE);
        badgeLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        badgeLabel.setVerticalAlignment(SwingConstants.CENTER);
        badgeLabel.setPreferredSize(new Dimension(18, 18));
        badgeLabel.setVisible(false);
        badgeLabel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1, true));

        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        tabHeader.setOpaque(false);
        JLabel tabTitle = new JLabel("Notifications");
        tabHeader.add(tabTitle);
        tabHeader.add(badgeLabel);
        tabs.setTabComponentAt(notificationsTabIndex, tabHeader);

        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (tabs.getSelectedIndex() == notificationsTabIndex) {
                    resetNotificationBadge();
                } else if (tabs.getSelectedIndex() == 0) { 
                    loadVehiclesFromCentralStorage(); 
                }
            }
        });

        this.add(tabs, BorderLayout.CENTER);
        
        loadVehiclesFromCentralStorage(); 
        loadPaymentInfo();
        loadNotifications();  
        
        // Start persistent notification listener
        startNotificationClient();
    }

    // --- Notification Client (Persistent Across Sessions) ---

    /**
     * Starts a dedicated thread to connect to the NetworkNotificationServer 
     * and listen for pushed messages. Thread persists even after GUI logout.
     */
    private void startNotificationClient() {
        // Check if notification client is already running for this user
        if (backgroundNotificationClient != null && 
            backgroundNotificationClient.getUserID().equals(ownerUser.getUserID()) &&
            notificationThread != null && notificationThread.isAlive()) {
            System.out.println("OwnerGUI: Notification client already running for " + ownerUser.getUserID());
            
            // Reconnect GUI callbacks to the existing client
            backgroundNotificationClient.setNotificationCallback(this::addNotification);
            
            // Load any queued notifications from server
            checkServerNotifications();
            
            // Start status refresh timer
            startStatusRefreshTimer();
            return;
        }
        
        // Create new notification client
        backgroundNotificationClient = new NotificationClient(
            "127.0.0.1", 
            Main.NOTIFICATION_PORT, 
            ownerUser.getUserID(),
            this::addNotification
        );
        
        notificationThread = new Thread(backgroundNotificationClient);
        notificationThread.setDaemon(false); // NOT a daemon - stays alive after GUI closes
        notificationThread.start();
        
        // Load any queued notifications from server
        checkServerNotifications();
        
        // Start status refresh timer
        startStatusRefreshTimer();
    }
    
    /**
     * Starts timer for status checks/refreshes.
     */
    private void startStatusRefreshTimer() {
        if (statusRefreshTimer != null && statusRefreshTimer.isRunning()) {
            return;
        }
        
        this.statusRefreshTimer = new Timer(5000, e -> {
            checkRequestStatuses(); 
        });
        this.statusRefreshTimer.start();
    }
    
    /**
     * Notification Client (WebSocket-like) for continuous server push.
     * Callback interface to support background operation.
     */
    private static class NotificationClient implements Runnable {
        private final String serverAddress;
        private final int port;
        private final String userID;
        private NotificationCallback callback;
        private volatile boolean running = true;

        public NotificationClient(String serverAddress, int port, String userID, NotificationCallback callback) {
            this.serverAddress = serverAddress;
            this.port = port;
            this.userID = userID;
            this.callback = callback;
        }
        
        public String getUserID() {
            return userID;
        }
        
        public void setNotificationCallback(NotificationCallback callback) {
            this.callback = callback;
        }
        
        public void shutdown() {
            this.running = false;
        }

        @Override
        public void run() {
            while (running) {
                try (Socket socket = new Socket(serverAddress, port);
                     ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

                    System.out.println("NotificationClient (Owner): Connected for user " + userID);
                    
                    oos.writeObject(userID);
                    oos.flush();

                    while (running && !socket.isClosed()) {
                        try {
                            Object received = ois.readObject();
                            if (received instanceof String) {
                                String notification = (String) received;
                                
                                if (callback != null) {
                                    callback.onNotificationReceived(notification);
                                } else {
                                    saveNotificationToFile(notification);
                                }
                            }
                        } catch (EOFException e) {
                            break; 
                        } catch (IOException | ClassNotFoundException e) {
                            if (running) {
                                System.err.println("NotificationClient (Owner): Error receiving: " + e.getMessage());
                            }
                            break;
                        }
                    }

                } catch (IOException e) {
                    if (running) {
                        System.err.println("NotificationClient (Owner): Connection failed: " + e.getMessage());
                        System.out.println("NotificationClient (Owner): Retrying in 5 seconds...");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            System.out.println("NotificationClient (Owner): Shutdown for " + userID);
        }
        
        //
        private void saveNotificationToFile(String notification) {
            String filename = userID + "_owner_notifications.txt";
            String timestamp = TS_FMT.format(LocalDateTime.now());
            String formattedNotif = "[" + timestamp + "] " + notification;
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true))) {
                writer.write(formattedNotif);
                writer.newLine();
                System.out.println("NotificationClient (Owner): Saved to file: " + formattedNotif);
            } catch (IOException e) {
                System.err.println("NotificationClient (Owner): Error saving notification: " + e.getMessage());
            }
            
            // INCREMENT THE COUNT FILE
            String countFilename = filename + "_count.txt";
            try {
                int currentCount = 0;
                File countFile = new File(countFilename);
                if (countFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(countFile))) {
                        String line = reader.readLine();
                        if (line != null) {
                            currentCount = Integer.parseInt(line.trim());
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing count file, resetting to 0");
                    }
                }
                
                // Increment and save
                currentCount++;
                try (PrintWriter writer = new PrintWriter(new FileWriter(countFile, false))) {
                    writer.print(currentCount);
                    System.out.println("NotificationClient (Owner): Incremented count to " + currentCount);
                }
            } catch (IOException e) {
                System.err.println("NotificationClient (Owner): Error updating count: " + e.getMessage());
            }
        }
    }
    
    @FunctionalInterface
    private interface NotificationCallback {
        void onNotificationReceived(String notification);
    }
    
    private void checkServerNotifications() {
        List<String> serverNotifications = server.getNotifications(ownerUser.getUserID());
        for (String notification : serverNotifications) {
            addNotification(notification);
        }
    }
    
    private void checkRequestStatuses() {
        java.util.Iterator<Map.Entry<String, String>> iterator = submittedRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            String vehicleSignature = entry.getKey();
            String requestID = entry.getValue();
            
            Request request = server.getRequest(requestID);
            
            if (request == null || !request.getStatus().equals("Pending")) {
                Vehicle vehicle = (Vehicle) (request != null ? request.getData() : null);
                String vehicleID = (vehicle != null) ? vehicle.getVehicleID() : vehicleSignature;

                if (request != null && request.getStatus().equals("Approved")) {
                    addNotification("✓ Vehicle " + vehicleID + " has been APPROVED by VC Controller");
                } else if (request != null && request.getStatus().equals("Rejected")) {
                    addNotification("✗ Vehicle " + vehicleID + " has been REJECTED by VC Controller");
                    removeVehicleFromTable(vehicleSignature);
                }
                
                iterator.remove();
                loadVehiclesFromCentralStorage(); 
            }
        }
    }

    private void removeVehicleFromTable(String vehicleSignature) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String license = (String) tableModel.getValueAt(i, 5);
                String state = (String) tableModel.getValueAt(i, 6);
                
                if ((license + state).equals(vehicleSignature)) {
                    tableModel.removeRow(i);
                    break;
                }
            }
        });
    }
    
    private void updateNotificationBadge() {
        JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, this);
        if (tabs == null || tabs.getSelectedIndex() != notificationsTabIndex) {
             notificationCount++;
             badgeLabel.setText(String.valueOf(notificationCount));
             badgeLabel.setVisible(true);
        }
    }
    
    private void setNotificationCount(int count) {
        this.notificationCount = count;
        if (count > 0) {
            badgeLabel.setText(String.valueOf(count));
            badgeLabel.setVisible(true);
        } else {
            badgeLabel.setVisible(false);
        }
    }

    private JPanel createNotificationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(187, 213, 237));

        JLabel header = new JLabel("Notifications from VC Controller", SwingConstants.CENTER);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(header, BorderLayout.NORTH);

        notificationListModel = new DefaultListModel<>();
        notificationList = new JList<>(notificationListModel);
        notificationList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        notificationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(notificationList);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        JButton clearButton = new JButton("Clear All");
        clearButton.addActionListener(e -> {
            notificationListModel.clear();
            resetNotificationBadge();
            saveNotifications();
        });
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    public void addNotification(String message) {
        String timestamp = TS_FMT.format(LocalDateTime.now());
        String formattedMessage = "[" + timestamp + "] " + message;
        
        SwingUtilities.invokeLater(() -> {
            notificationListModel.addElement(formattedMessage);
            
            // Always increment badge for NEW notifications
            // Check if we're NOT on the notifications tab before incrementing
            JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, OwnerGUI.this);
            if (tabs == null || tabs.getSelectedIndex() != notificationsTabIndex) {
                notificationCount++;
                badgeLabel.setText(String.valueOf(notificationCount));
                badgeLabel.setVisible(true);
            }
            
            saveNotifications();

        });
    }

    // Also update the saveNotifications method to ensure count is always saved:

    private void saveNotifications() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(NOTIFICATION_FILE))) {
            for (int i = 0; i < notificationListModel.size(); i++) {
                writer.write(notificationListModel.getElementAt(i));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving notifications: " + e.getMessage());
        }
        
        // Always save the count separately
        try (PrintWriter countWriter = new PrintWriter(new FileWriter(NOTIFICATION_FILE + "_count.txt", false))) {
            countWriter.print(this.notificationCount);
            System.out.println("OwnerGUI: Saved notification count: " + this.notificationCount);
        } catch (IOException e) {
            System.err.println("Error saving notification count: " + e.getMessage());
        }
    }

    // And ensure resetNotificationBadge() saves the cleared state:

    private void resetNotificationBadge() {
        notificationCount = 0;
        badgeLabel.setVisible(false);
        // Save the reset count immediately
        try (PrintWriter countWriter = new PrintWriter(new FileWriter(NOTIFICATION_FILE + "_count.txt", false))) {
            countWriter.print(0);
            System.out.println("OwnerGUI: Reset notification count to 0");
        } catch (IOException e) {
            System.err.println("Error saving reset notification count: " + e.getMessage());
        }
    }

    private void loadNotifications() {
        File file = new File(NOTIFICATION_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                notificationListModel.addElement(line);
            }
        } catch (IOException e) {
            System.err.println("Error loading notifications: " + e.getMessage());
        }
        
        File countFile = new File(NOTIFICATION_FILE + "_count.txt");
        if (countFile.exists()) {
            try (BufferedReader countReader = new BufferedReader(new FileReader(countFile))) {
                String countLine = countReader.readLine();
                if (countLine != null) {
                    try {
                        int unseenCount = Integer.parseInt(countLine.trim());
                        setNotificationCount(unseenCount);
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing notification count.");
                    }
                }
            } catch (IOException e) {
                 System.err.println("Error loading notification count: " + e.getMessage());
            }
        }
    }
    
    private void loadVehiclesFromCentralStorage() {
        tableModel.setRowCount(0);
        server.reloadState();

        List<Vehicle> vehicleHistory = controller.getOwnerVehicleHistory(ownerUser.getUserID()); 
        
        for (Vehicle vehicle : vehicleHistory) {
             String ownerEnteredID = server.getVehicleOwnerIDForDisplay(vehicle);
             if (ownerEnteredID == null || ownerEnteredID.isEmpty()) {
                 System.err.println("Owner ID not found in server map for vehicle: " + vehicle.getVehicleID());
                 ownerEnteredID = ownerUser.getUserID(); 
             }
             
             tableModel.addRow(new Object[]{
                 TS_FMT.format(LocalDateTime.now()), 
                 ownerEnteredID,
                 vehicle.getMake(),
                 vehicle.getModel(),
                 vehicle.getYear(),
                 vehicle.getVehicleID(),
                 vehicle.getLicenseState(),
                 vehicle.getDepartureSchedule().toString()
             });
        }
        System.out.println("OwnerGUI: Loaded " + vehicleHistory.size() + " registered vehicles.");
    }

    private JPanel createRegisterVehiclePanel() {
        JPanel rootPanel = new JPanel();
        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        rootPanel.setBackground(new Color(187, 213, 237));

        JLabel header = new JLabel("Vehicle Registration  |  Welcome " + ownerUser.getName(), SwingConstants.CENTER);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        rootPanel.add(header);
        rootPanel.add(Box.createVerticalStrut(10));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        ownerIdField = new JTextField();
        ((AbstractDocument) ownerIdField.getDocument()).setDocumentFilter(new AlphanumericFilter(6));

        makeField = new JTextField();
        ((AbstractDocument) makeField.getDocument()).setDocumentFilter(new LettersOnlyFilter());

        modelField = new JTextField();
        ((AbstractDocument) modelField.getDocument()).setDocumentFilter(new LettersOnlyFilter());

        LocalDateTime now = LocalDateTime.now();
        yearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), 1980, now.getYear(), 1));
        yearSpinner.setPreferredSize(new Dimension(100, 28)); 

        licenseField = new JTextField();
        ((AbstractDocument) licenseField.getDocument()).setDocumentFilter(new LicensePlateFilter());
        licenseField.setPreferredSize(new Dimension(100, 28)); 

        stateComboBox = new JComboBox<>(STATES);

        departureMonthSpinner = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        departureDaySpinner = new JSpinner(new SpinnerNumberModel(now.getDayOfMonth(), 1, 31, 1));
        departureYearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), now.getYear(), now.getYear() + 5, 1));
        departureHourSpinner = new JSpinner(new SpinnerNumberModel(now.getHour(), 0, 23, 1));

        int r = 0; 

        gc.gridx = 0; gc.gridy = r; form.add(new JLabel("Owner ID(6 chars):"), gc); 
        gc.gridx = 1; gc.gridy = r++; form.add(ownerIdField, gc);

        gc.gridx = 0; gc.gridy = r; form.add(new JLabel("Vehicle Make:"), gc);
        gc.gridx = 1; gc.gridy = r++; form.add(makeField, gc);

        gc.gridx = 0; gc.gridy = r; form.add(new JLabel("Vehicle Model:"), gc);
        gc.gridx = 1; gc.gridy = r++; form.add(modelField, gc);

        JPanel yearPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        yearPanel.setOpaque(false);
        yearPanel.add(yearSpinner);
        gc.gridx = 0; gc.gridy = r; form.add(new JLabel("Vehicle Year:"), gc);
        gc.gridx = 1; gc.gridy = r++; form.add(yearPanel, gc);

        gc.gridx = 0; gc.gridy = r; form.add(new JLabel("License Plate:"), gc);
        gc.gridx = 1; gc.gridy = r; form.add(licenseField, gc);
        gc.gridx = 2; gc.gridy = r; form.add(new JLabel("State:"), gc);
        gc.gridx = 3; gc.gridy = r++; form.add(stateComboBox, gc);

        JPanel departurePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        departurePanel.setOpaque(false);
        departurePanel.add(new JLabel("M:"));
        departurePanel.add(departureMonthSpinner);
        departurePanel.add(new JLabel("D:"));
        departurePanel.add(departureDaySpinner);
        departurePanel.add(new JLabel("Y:"));
        departurePanel.add(departureYearSpinner);
        departurePanel.add(new JLabel("Hour (0-23):"));
        departurePanel.add(departureHourSpinner);

        gc.gridx = 0; gc.gridy = r; form.add(new JLabel("Departure Time:"), gc);
        gc.gridx = 1; gc.gridy = r++; form.add(departurePanel, gc);

        JButton registerButton = new JButton("Register Vehicle");
        JButton clearButton = new JButton("Clear form");
        JButton logoutButton = new JButton("Logout");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        buttons.setOpaque(false);
        buttons.add(registerButton);
        buttons.add(clearButton);
        buttons.add(logoutButton);

        gc.gridx = 0; gc.gridy = r; gc.gridwidth = 4; form.add(buttons, gc);

        rootPanel.add(form);
        rootPanel.add(Box.createVerticalStrut(12));

        tableModel = new DefaultTableModel(new Object[]{
                "Timestamp", "Owner ID", "Make", "Model", "Year", "License", "State", "Departure"
        }, 0);
        table = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(760, 260));
        rootPanel.add(tableScroll);

        registerButton.addActionListener(this::onRegister);
        clearButton.addActionListener(e -> clearForm());
        logoutButton.addActionListener(e -> {
            // Notification thread runs in background
            if (statusRefreshTimer != null) {
                statusRefreshTimer.stop();
            }
            
            // Disconnect GUI callback but keep socket alive
            if (backgroundNotificationClient != null) {
                backgroundNotificationClient.setNotificationCallback(null);
            }
            
            saveNotifications();
            onLogout.run();
        });
        
        return rootPanel;
    }

    private JPanel createPaymentPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(187, 213, 237));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        addPaymentButton = new JButton("Add Payment Info"); 
        addPaymentButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        addPaymentButton.addActionListener(this::onAddPaymentInfo);

        JButton deletePaymentButton = new JButton("Delete Info");
        deletePaymentButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        deletePaymentButton.setBackground(new Color(244, 67, 54));
        deletePaymentButton.setForeground(Color.BLACK);
        deletePaymentButton.addActionListener(this::onDeletePaymentInfo);

        topPanel.add(addPaymentButton);
        topPanel.add(deletePaymentButton);
        panel.add(topPanel, BorderLayout.NORTH);

        paymentTableModel = new DefaultTableModel(new Object[]{"Field", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        paymentTable = new JTable(paymentTableModel);
        paymentTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
        paymentTable.setRowHeight(20);
        paymentTable.setTableHeader(null);

        JScrollPane scrollPane = new JScrollPane(paymentTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createPasswordPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        panel.setBackground(new Color(187, 213, 237));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JLabel header = new JLabel("Change Your Login Password", SwingConstants.CENTER);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; panel.add(header, gbc);
        
        oldPassField = new JPasswordField(20);
        newPassField = new JPasswordField(20);
        confirmNewPassField = new JPasswordField(20);
        JButton changeButton = new JButton("Set New Password");
        changeButton.addActionListener(this::onChangePassword);
        
        passStatusLabel = new JLabel("Your password will be updated instantly.", SwingConstants.CENTER);
        passStatusLabel.setForeground(Color.BLUE);
        
        int r = 1;
        gbc.gridwidth = 1;
        
        gbc.gridx = 0; gbc.gridy = r; panel.add(new JLabel("Current Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = r++; panel.add(oldPassField, gbc);
        gbc.gridx = 0; gbc.gridy = r; panel.add(new JLabel("New Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = r++; panel.add(newPassField, gbc);
        gbc.gridx = 0; gbc.gridy = r; panel.add(new JLabel("Confirm New Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = r++; panel.add(confirmNewPassField, gbc);
        gbc.gridx = 0; gbc.gridy = r; gbc.gridwidth = 2; panel.add(Box.createVerticalStrut(10), gbc); r++;
        gbc.gridx = 0; gbc.gridy = r; gbc.gridwidth = 2; panel.add(changeButton, gbc); r++;
        gbc.gridx = 0; gbc.gridy = r; gbc.gridwidth = 2; panel.add(Box.createVerticalStrut(10), gbc); r++;
        gbc.gridx = 0; gbc.gridy = r; gbc.gridwidth = 2; panel.add(passStatusLabel, gbc); r++;

        return panel;
    }
    
    private void onChangePassword(ActionEvent e) {
        String currentPass = new String(oldPassField.getPassword());
        String newPass = new String(newPassField.getPassword());
        String confirmPass = new String(confirmNewPassField.getPassword());

        if (!currentPass.equals(ownerUser.getPassword())) {
            passStatusLabel.setForeground(Color.RED);
            passStatusLabel.setText("Error: Current Password is incorrect.");
            return;
        }

        if (newPass.length() < 6) {
            passStatusLabel.setForeground(Color.RED);
            passStatusLabel.setText("Error: New password must be at least 6 characters long.");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            passStatusLabel.setForeground(Color.RED);
            passStatusLabel.setText("Error: New Password and Confirmation do not match.");
            return;
        }

        ownerUser.setPassword(newPass);

        passStatusLabel.setForeground(new Color(34, 139, 34));
        passStatusLabel.setText("Password successfully updated! New password is now active.");
        
        oldPassField.setText("");
        newPassField.setText("");
        confirmNewPassField.setText("");
    }

    private void onAddPaymentInfo(ActionEvent e) {
        if (paymentDialog == null) {
            paymentDialog = new PaymentInfoDialog(SwingUtilities.getWindowAncestor(this));
        }
        paymentDialog.prefill(this.paymentInfo);
        paymentDialog.setVisible(true);

        if (paymentDialog.getSavedInfo() != null) {
            this.paymentInfo = paymentDialog.getSavedInfo();
            savePaymentInfo();
            loadPaymentInfo();
        }
    }

    private void onDeletePaymentInfo(ActionEvent e) {
        if (paymentInfo == null || paymentInfo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No payment info to delete.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete your payment info?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            this.paymentInfo = "";
            savePaymentInfo();
            loadPaymentInfo();
            JOptionPane.showMessageDialog(this, "Payment Info Deleted.");
        }
    }

    private void onRegister(ActionEvent e) {
        if (this.paymentInfo == null || this.paymentInfo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add payment info (Tab 2) before registering a vehicle.", "Payment Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String ownerId = ownerIdField.getText().trim();
        if (ownerId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an Owner ID.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String make = makeField.getText().trim();
        String model = modelField.getText().trim();
        int year = (Integer) yearSpinner.getValue();
        String license = licenseField.getText().trim();
        String state = String.valueOf(stateComboBox.getSelectedItem());

        if (make.isEmpty() || model.isEmpty() || license.isEmpty() || state.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all vehicle fields.");
            return;
        }

        if (controller.isVehicleInSystem(license, state)) {
            JOptionPane.showMessageDialog(this, "This vehicle (License + State) is already active or available in the system.", "Duplicate Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LocalDateTime departureTime;
        try {
            int depYear = (Integer) departureYearSpinner.getValue();
            int month = (Integer) departureMonthSpinner.getValue();
            int day = (Integer) departureDaySpinner.getValue();
            int hour = (Integer) departureHourSpinner.getValue();
            departureTime = LocalDateTime.of(depYear, month, day, hour, 0);

            if (departureTime.isBefore(LocalDateTime.now())) {
                JOptionPane.showMessageDialog(this, "Departure time must be in the future.");
                return;
            }
        } catch (DateTimeException ex) {
            JOptionPane.showMessageDialog(this, "Invalid date (e.g., Feb 30).");
            return;
        }

        String departureString = departureTime.toString();
        
        Vehicle newVehicle = new Vehicle(license, make, model, year, license, state, departureTime);
        
        Request request = server.createRequest(ownerUser.getUserID(), "VEHICLE_REGISTRATION", newVehicle);
        server.mapVehicleOwnerIDForDisplay(license, ownerId); 
        controller.processVehicleRequest(request);
        
        String vehicleSignature = license + state;
        submittedRequests.put(vehicleSignature, request.getRequestID());
        
        JOptionPane.showMessageDialog(this, 
            "Vehicle Registration Request Sent!\n" +
            "Request ID: " + request.getRequestID() + "\n" +
            "License: " + license + " (" + state + ")\n" +
            "Status: Waiting for VC Controller approval...", 
            "Request Acknowledged", 
            JOptionPane.INFORMATION_MESSAGE);
        
        addNotification("Vehicle registration request for " + license + " submitted and acknowledged by server");

        String ts = TS_FMT.format(LocalDateTime.now());
        tableModel.addRow(new Object[]{
                ts, ownerId, make, model, year, license, state, departureString
        });
    }

    private void loadPaymentInfo() {
        File file = new File(PAYMENT_FILE);
        paymentTableModel.setRowCount(0);

        if (!file.exists()) {
            addPaymentButton.setText("Add Payment Info");
            this.paymentInfo = "";
            ownerUser.setPaymentInfo(""); 
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String loadedInfo = br.readLine();
            if (loadedInfo != null && !loadedInfo.isEmpty()) {
                this.paymentInfo = loadedInfo;
                ownerUser.setPaymentInfo(loadedInfo); 
                String[] parts = paymentInfo.split("\\|");

                if (parts.length == 5) {
                    paymentTableModel.addRow(new Object[]{"Name on Card", parts[0]});
                    paymentTableModel.addRow(new Object[]{"Card Number", "**** **** **** " + parts[1].substring(12)});
                    paymentTableModel.addRow(new Object[]{"Expiry", parts[3] + "/" + parts[4]});
                }

                addPaymentButton.setText("Edit Payment Info");
            } else {
                addPaymentButton.setText("Add Payment Info");
                this.paymentInfo = "";
                ownerUser.setPaymentInfo(""); 
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading payment info from " + PAYMENT_FILE, "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void savePaymentInfo() {
        ownerUser.setPaymentInfo(this.paymentInfo); 
        try (FileWriter fw = new FileWriter(PAYMENT_FILE, false)) {
            fw.write(this.paymentInfo);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving payment info file.", "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearForm() {
        ownerIdField.setText("");
        makeField.setText("");
        modelField.setText("");
        licenseField.setText("");
        stateComboBox.setSelectedIndex(0); 

        LocalDateTime now = LocalDateTime.now();
        yearSpinner.setValue(now.getYear());
        departureMonthSpinner.setValue(now.getMonthValue());
        departureDaySpinner.setValue(now.getDayOfMonth());
        departureHourSpinner.setValue(now.getHour());
    }

    // --- Payment Info Dialog ---
    private class PaymentInfoDialog extends JDialog {
        private JTextField nameField;
        private JTextField cardField;
        private JTextField cvcField;
        private JTextField expMonthField;
        private JTextField expYearField;
        private String savedInfo; 

        PaymentInfoDialog(Window owner) {
            super(owner, "Add/Edit Payment Info", ModalityType.APPLICATION_MODAL);
            setSize(400, 300);
            setLocationRelativeTo(owner);
            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            nameField = new JTextField(20);
            ((AbstractDocument) nameField.getDocument()).setDocumentFilter(new LettersOnlyFilter());

            cardField = new JTextField(16);
            ((AbstractDocument) cardField.getDocument()).setDocumentFilter(new NumbersOnlyFilter(16));

            cvcField = new JTextField(3);
            ((AbstractDocument) cvcField.getDocument()).setDocumentFilter(new NumbersOnlyFilter(3));

            expMonthField = new JTextField(2);
            ((AbstractDocument) expMonthField.getDocument()).setDocumentFilter(new NumbersOnlyFilter(2));

            expYearField = new JTextField(2);
            ((AbstractDocument) expYearField.getDocument()).setDocumentFilter(new NumbersOnlyFilter(2));

            gbc.gridx = 0; gbc.gridy = 0; add(new JLabel("Name on Card:"), gbc);
            gbc.gridx = 1; gbc.gridy = 0; add(nameField, gbc);
            gbc.gridx = 0; gbc.gridy = 1; add(new JLabel("Card Number:"), gbc);
            gbc.gridx = 1; gbc.gridy = 1; add(cardField, gbc);
            gbc.gridx = 0; gbc.gridy = 2; add(new JLabel("CVC:"), gbc);
            gbc.gridx = 1; gbc.gridy = 2; add(cvcField, gbc);

            JPanel expPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            expPanel.add(expMonthField);
            expPanel.add(new JLabel("/"));
            expPanel.add(expYearField);

            gbc.gridx = 0; gbc.gridy = 3; add(new JLabel("Expiry (MM/YY):"), gbc);
            gbc.gridx = 1; gbc.gridy = 3; add(expPanel, gbc);

            JButton saveButton = new JButton("Save");
            saveButton.addActionListener(this::onSavePayment);
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; add(saveButton, gbc);
        }

        public String getSavedInfo() {
            return savedInfo;
        }

        public void prefill(String info) {
            if (info != null && !info.isEmpty()) {
                String[] parts = info.split("\\|");
                if (parts.length == 5) {
                    nameField.setText(parts[0]);
                    cardField.setText(parts[1]);
                    cvcField.setText(parts[2]);
                    expMonthField.setText(parts[3]);
                    expYearField.setText(parts[4]);
                }
            } else {
                nameField.setText("");
                cardField.setText("");
                cvcField.setText("");
                expMonthField.setText("");
                expYearField.setText("");
            }
            this.savedInfo = null;
        }

        private void onSavePayment(ActionEvent e) {
            String name = nameField.getText().trim();
            String card = cardField.getText().trim();
            String cvc = cvcField.getText().trim();
            String expMonth = expMonthField.getText().trim();
            String expYear = expYearField.getText().trim();

            if (name.isEmpty() || card.length() != 16 || cvc.length() != 3 || expMonth.length() != 2 || expYear.length() != 2) {
                JOptionPane.showMessageDialog(this, "Please fill all fields correctly.\nCard Number must be 16 digits.\nCVC must be 3 digits.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            this.savedInfo = name + "|" + card + "|" + cvc + "|" + expMonth + "|" + expYear;
            JOptionPane.showMessageDialog(this, "Payment Info Saved!");
            dispose();
        }
    }

    // --- DocumentFilter Inner Classes ---
    private static class AlphanumericFilter extends DocumentFilter {
        private final int maxLength;

        public AlphanumericFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string == null) return;
            if (fb.getDocument().getLength() + string.length() > maxLength) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            String upper = string.toUpperCase();
            if (upper.matches("[a-zA-Z0-9]+")) {
                super.insertString(fb, offset, string, attr);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) return;
            if (fb.getDocument().getLength() - length + text.length() > maxLength) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            if (text.matches("[a-zA-Z0-9]*")) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    private static class LettersOnlyFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string != null && string.matches("[a-zA-Z -]+")) {
                super.insertString(fb, offset, string, attr);
            } else if (string != null) {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text != null && text.matches("[a-zA-Z -]*")) {
                super.replace(fb, offset, length, text, attrs);
            } else if (text != null && !text.isEmpty()) {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    private static class LicensePlateFilter extends DocumentFilter {
        private static final int MAX_LENGTH = 7;

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string == null) return;

            String upperString = string.toUpperCase();
            if (fb.getDocument().getLength() + upperString.length() > MAX_LENGTH) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            if (upperString.matches("[A-Z0-9]+")) {
                super.insertString(fb, offset, upperString, attr);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) return;

            String upperText = text.toUpperCase();
            if (fb.getDocument().getLength() - length + upperText.length() > MAX_LENGTH) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            if (upperText.matches("[A-Z0-9]*")) {
                super.replace(fb, offset, length, upperText, attrs);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    private static class NumbersOnlyFilter extends DocumentFilter {
        private final int maxLength;

        public NumbersOnlyFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string == null) return;

            if (fb.getDocument().getLength() + string.length() > maxLength) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            if (string.matches("\\d+")) {
                super.insertString(fb, offset, string, attr);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) return;

            if (fb.getDocument().getLength() - length + text.length() > maxLength) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            if (text.matches("\\d*")) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }
}
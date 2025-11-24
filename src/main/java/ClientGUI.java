import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList; 
import java.util.List; 
import java.net.Socket;
import java.util.UUID;

/**
 * ClientGUI with persistent notification client .
 */
public class ClientGUI extends JPanel {

    private final Client clientUser; 
    private final Runnable onLogout;
    private final VCController controller;
    private final Server server;

    // Request tracking
    private Map<String, String> submittedRequests;
    
    // Notification components
    private static NotificationClient backgroundNotificationClient; // STATIC for persistence
    private static Thread notificationThread; // STATIC for persistence
    private Timer statusRefreshTimer;

    private JTextField clientIdField; 
    private JSpinner durationSpinner;
    private JComboBox<String> durationUnitBox;
    private JSpinner redundancySpinner;
    private JSpinner deadlineMonthSpinner;
    private JSpinner deadlineDaySpinner;
    private JSpinner deadlineYearSpinner;
    private JSpinner deadlineHourSpinner;

    private String billingInfo; 
    private JButton addBillingButton;
    private JTable billingTable;
    private DefaultTableModel billingTableModel;
    private BillingInfoDialog billingDialog;

    private JPasswordField oldPassField;
    private JPasswordField newPassField;
    private JPasswordField confirmNewPassField;
    private JLabel passStatusLabel;

    private DefaultListModel<String> notificationListModel;
    private JList<String> notificationList;
    private DefaultTableModel tableModel;
    private JTable table;
    
    private JLabel badgeLabel;
    private int notificationCount = 0;
    private int notificationsTabIndex;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public ClientGUI(Client clientUser, Runnable onLogout, VCController controller) { 
        this.clientUser = clientUser; 
        this.onLogout = onLogout;
        this.controller = controller;
        this.server = controller.getServer();
        this.submittedRequests = new HashMap<>();
        
        // this.billingInfo = clientUser.getBillingInfo(); // Removed
        
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Submit New Job", createSubmitJobPanel());
        tabs.addTab("Manage Existing Jobs", createManageJobsPanel());
        tabs.addTab("Manage Billing", createBillingPanel());
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
        badgeLabel.setVisible(false);
        badgeLabel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1, true));
        
        // Initialize badge with unread count from DB
        int unreadCount = DatabaseManager.getInstance().getUnreadNotificationCount(clientUser.getUserID());
        if (unreadCount > 0) {
            notificationCount = unreadCount;
            badgeLabel.setText(String.valueOf(notificationCount));
            badgeLabel.setVisible(true);
        }

        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        tabHeader.setOpaque(false);
        JLabel tabTitle = new JLabel("Notifications");
        tabHeader.add(tabTitle);
        tabHeader.add(badgeLabel);
        tabs.setTabComponentAt(notificationsTabIndex, tabHeader);

        this.add(tabs, BorderLayout.CENTER);

        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (tabs.getSelectedIndex() == notificationsTabIndex) {
                    resetNotificationBadge();
                    // Mark all as read in DB when tab is opened
                    DatabaseManager.getInstance().markNotificationsRead(clientUser.getUserID());
                } else if (tabs.getSelectedIndex() == 0 || tabs.getSelectedIndex() == 1) { 
                    loadJobsFromCentralStorage(); 
                }
            }
        });
        
        // Load persistent data and restore badge count
        loadJobsFromCentralStorage();
        loadBillingInfo();
        loadBillingInfo();
        loadNotifications(); // Re-enabled to show persistent history

        // Start persistent notification listener (only if not already running)
        startNotificationClient();
    }
    
    // --- Notification Client (Persistent Across Sessions) ---
    
    /**
     * Starts a dedicated thread to connect to the NetworkServer 
     * and listen for pushed messages. Thread persists even after GUI logout.
     */
    private void startNotificationClient() {
        // Check if notification client is already running for this user
        if (backgroundNotificationClient != null && 
            backgroundNotificationClient.getUserID().equals(clientUser.getUserID()) &&
            notificationThread != null && notificationThread.isAlive()) {
            System.out.println("ClientGUI: Notification client already running for " + clientUser.getUserID());
            
            // Reconnect GUI callbacks to the existing client
            backgroundNotificationClient.setNotificationCallback(this::addNotification);
            
            // Load any queued notifications from server
            // checkServerNotifications(); // Removed to prevent duplicates (loaded in constructor)
            
            // Start status refresh timer
            startStatusRefreshTimer();
            return;
        }
        
        // Create new notification client
        backgroundNotificationClient = new NotificationClient(
            "127.0.0.1", 
            Main.SERVER_PORT, 
            clientUser.getUserID(),
            this::addNotification // Callback for adding notifications to GUI
        );
        
        notificationThread = new Thread(backgroundNotificationClient);
        notificationThread.setDaemon(false); // NOT a daemon - stays alive after GUI closes
        notificationThread.start();
        
        // Load any queued notifications from server
        // checkServerNotifications(); // Removed to prevent duplicates (loaded in constructor)
        
        // Start status refresh timer
        startStatusRefreshTimer();
    }
    
    /**
     * Starts timer for status checks/refreshes (Polling for request status and job status changes)
     */
    private void startStatusRefreshTimer() {
        if (statusRefreshTimer != null && statusRefreshTimer.isRunning()) {
            return; // Already running
        }
        
        this.statusRefreshTimer = new Timer(5000, e -> {
            checkRequestStatuses(); 
            refreshJobTable(); 
        });
        this.statusRefreshTimer.start();
    }
    
    /**
     * Notification Client (WebSocket-like) for continuous server push.
     * callback interface to support background operation.
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
        


        @Override
        public void run() {
            while (running) {
                try (Socket socket = new Socket(serverAddress, port);
                     ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

                    System.out.println("NotificationClient: Connected for user " + userID);
                    
                    // Send UserID to register
                    oos.writeObject(userID);
                    oos.flush();

                    // Continuously listen for pushed objects (notifications)
                    while (running && !socket.isClosed()) {
                        try {
                            Object received = ois.readObject();
                            if (received instanceof String) {
                                String notification = (String) received;
                                
                                // Deliver notification via callback
                                if (callback != null) {
                                    callback.onNotificationReceived(notification);
                                } else {
                                    // Fallback: Log to console (Server handles persistence)
                                    System.out.println("NotificationClient: Received (No GUI): " + notification);
                                }
                            }
                        } catch (EOFException e) {
                            break; 
                        } catch (IOException | ClassNotFoundException e) {
                            if (running) {
                                System.err.println("NotificationClient: Error receiving: " + e.getMessage());
                            }
                            break;
                        }
                    }

                } catch (IOException e) {
                    if (running) {
                        System.err.println("NotificationClient: Connection failed: " + e.getMessage());
                        System.out.println("NotificationClient: Retrying in 5 seconds...");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            System.out.println("NotificationClient: Shutdown for " + userID);
        }
        
        /**
         * Fallback for when no GUI is active.
         * Persistence is now handled by the Server/Database.
         */
        private void saveNotificationToFile(String notification) {
            // Deprecated: File persistence removed.
        }
    }
    
    /**
     * Callback interface for notification delivery.
     */
    @FunctionalInterface
    private interface NotificationCallback {
        void onNotificationReceived(String notification);
    }
    
    /**
     * Checks for new notifications from the server queue (fallback).
     */
    private void checkServerNotifications() {
        // Load notifications from Database
        List<String> serverNotifications = DatabaseManager.getInstance().getNotifications(clientUser.getUserID());
        for (String notification : serverNotifications) {
            addNotification(notification);
        }
    }
    
    /**
     * Checks the status of submitted requests and updates the UI accordingly.
     */
    private void checkRequestStatuses() {
        java.util.Iterator<Map.Entry<String, String>> iterator = submittedRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            String jobID = entry.getKey();
            String requestID = entry.getValue();
            
            Request request = server.getRequest(requestID);
            
            if (request == null || !request.getStatus().equals("Pending")) {
                if (request != null && request.getStatus().equals("Approved")) {
                    // Notification handled by Server Push
                } else if (request != null && request.getStatus().equals("Rejected")) {
                    // Notification handled by Server Push
                    removeJobFromTable(jobID);
                }
                
                iterator.remove();
                loadJobsFromCentralStorage(); 
            }
        }
    }
    
    private void removeJobFromTable(String jobID) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getValueAt(i, 2).equals(jobID)) {
                    tableModel.removeRow(i);
                    break;
                }
            }
        });
    }
    


    private JPanel createNotificationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(220, 240, 255));

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
            DatabaseManager.getInstance().clearNotifications(clientUser.getUserID());
        });
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    public void addNotification(String message) {
        String formattedMessage;
        if (message.startsWith("[")) {
             formattedMessage = message;
        } else {
             String timestamp = TS_FMT.format(LocalDateTime.now());
             formattedMessage = "[" + timestamp + "] " + message;
        }
        
        SwingUtilities.invokeLater(() -> {
            notificationListModel.addElement(formattedMessage);
            
            // Always increment badge for NEW notifications
            // Check if we're NOT on the notifications tab before incrementing
            JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, ClientGUI.this);
            if (tabs == null || tabs.getSelectedIndex() != notificationsTabIndex) {
                notificationCount++;
                badgeLabel.setText(String.valueOf(notificationCount));
                badgeLabel.setVisible(true);
            }
            
            // Save to DB REMOVED - Server handles this now to prevent duplicates
        });
    }

    // Also update the saveNotifications method to ensure count is ALWAYS saved:

    private void saveNotifications() {
        // Deprecated: Notifications are saved to DB immediately on add.
    }

    private void resetNotificationBadge() {
        notificationCount = 0;
        badgeLabel.setVisible(false);
    }
    
    private void loadNotifications() {
        notificationListModel.clear();
        List<String> notifs = DatabaseManager.getInstance().getNotifications(clientUser.getUserID());
        for (String n : notifs) {
            notificationListModel.addElement(n);
        }
    }

    private JPanel createSubmitJobPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.setBackground(new Color(220, 240, 255));

        JLabel header = new JLabel("Job Submission Console  |  Welcome " + clientUser.getName(), SwingConstants.CENTER);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        root.add(header);
        root.add(Box.createVerticalStrut(10));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        clientIdField = new JTextField();
        ((AbstractDocument) clientIdField.getDocument()).setDocumentFilter(new AlphanumericFilter(6));

        durationSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
        durationUnitBox = new JComboBox<>(new String[]{"hours", "days"});
        redundancySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));

        LocalDateTime now = LocalDateTime.now().plusHours(1);
        deadlineMonthSpinner = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        deadlineDaySpinner = new JSpinner(new SpinnerNumberModel(now.getDayOfMonth(), 1, 31, 1));
        deadlineYearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), now.getYear(), now.getYear() + 5, 1));
        deadlineHourSpinner = new JSpinner(new SpinnerNumberModel(now.getHour(), 0, 23, 1));

        int r = 0;
        
        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("Client ID(6 chars):"), gc); 
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(clientIdField, gc); 

        JPanel durationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        durationPanel.setOpaque(false);
        durationPanel.add(durationSpinner);
        durationPanel.add(durationUnitBox);

        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("Job Duration:"), gc);
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(durationPanel, gc);

        JPanel deadlinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        deadlinePanel.setOpaque(false);
        deadlinePanel.add(new JLabel("M:"));
        deadlinePanel.add(deadlineMonthSpinner);
        deadlinePanel.add(new JLabel("D:"));
        deadlinePanel.add(deadlineDaySpinner);
        deadlinePanel.add(new JLabel("Y:"));
        deadlinePanel.add(deadlineYearSpinner);
        deadlinePanel.add(new JLabel("Hour (0-23):"));
        deadlinePanel.add(deadlineHourSpinner);

        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("Job Deadline:"), gc);
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(deadlinePanel, gc);

        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("Redundancy Level:"), gc);
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(redundancySpinner, gc);

        JButton submitButton = new JButton("Submit Job");
        JButton clearButton = new JButton("Clear Form");
        JButton logoutButton = new JButton("Logout");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        buttons.setOpaque(false);
        buttons.add(submitButton);
        buttons.add(clearButton);
        buttons.add(logoutButton);

        gc.gridx = 0;
        gc.gridy = r;
        gc.gridwidth = 2;
        form.add(buttons, gc);

        root.add(form);
        root.add(Box.createVerticalStrut(12));

        tableModel = new DefaultTableModel(new Object[]{
                "Timestamp", "Client ID", "Job ID", "Status", "Duration (Hours)", "Deadline", "Redundancy" 
        }, 0); 
        table = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(760, 260));
        root.add(tableScroll);

        submitButton.addActionListener(this::onSubmit);
        clearButton.addActionListener(e -> clearForm());
        logoutButton.addActionListener(e -> {
            // Notification thread runs in background
            // Only stop the status refresh timer
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

        return root;
    }

    private JPanel createManageJobsPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        mainPanel.setBackground(new Color(235, 235, 235));

        JLabel header = new JLabel("Job Management  |  Welcome " + clientUser.getName(), SwingConstants.CENTER);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        mainPanel.add(header);
        mainPanel.add(Box.createVerticalStrut(10));

        JPanel checkStatusPanel = new JPanel(new GridBagLayout());
        checkStatusPanel.setBorder(BorderFactory.createTitledBorder("Check Job Status"));
        checkStatusPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        checkStatusPanel.add(new JLabel("Job ID:"), gbc);
        
        JTextField jobIdField = new JTextField(15);
        gbc.gridx = 1;
        gbc.weightx = 1.0; 
        gbc.fill = GridBagConstraints.HORIZONTAL;
        checkStatusPanel.add(jobIdField, gbc);
        
        JButton checkBtn = new JButton("Check Status");
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        checkStatusPanel.add(checkBtn, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        checkStatusPanel.add(new JLabel("Status:"), gbc);
        
        final JTextField statusField = new JTextField();
        statusField.setEditable(false);
        statusField.setHorizontalAlignment(SwingConstants.CENTER);
        statusField.setPreferredSize(new Dimension(400, 28)); 
        statusField.setBackground(Color.LIGHT_GRAY);
        statusField.setForeground(Color.WHITE);
        statusField.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 1; 
        gbc.weightx = 0.10; 
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST; 
        checkStatusPanel.add(statusField, gbc);
        
        mainPanel.add(checkStatusPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        checkBtn.addActionListener(e -> {
            String id = jobIdField.getText().trim();
            statusField.setText("");
            
            if (id.isEmpty()) {
                statusField.setText("ENTER ID");
                statusField.setBackground(Color.GRAY);
                statusField.setForeground(Color.WHITE);
                return;
            }
            String status = controller.getJobStatus(id);
            statusField.setText(status.toUpperCase());
            statusField.setForeground(Color.WHITE);

            switch (status.toLowerCase()) {
                case "completed":
                    statusField.setBackground(new Color(33, 150, 243));
                    break;
                case "in-progess": 
                case "in-progress":
                    statusField.setBackground(new Color(255, 152, 0));
                    break;
                case "pending":
                case "pending(interrupted)":
                    statusField.setBackground(new Color(255, 193, 7));
                    statusField.setForeground(Color.BLACK); 
                    break;
                case "rejected":
                    statusField.setBackground(new Color(244, 67, 54));
                    break;
                case "failed":
                    statusField.setBackground(new Color(244, 67, 54));
                    break;
                default: 
                    statusField.setBackground(Color.GRAY);
                    statusField.setForeground(Color.WHITE);
            }
        });

        return mainPanel;
    }

    private JPanel createBillingPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10)); 
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(220, 240, 255));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        addBillingButton = new JButton("Add Billing Info"); 
        addBillingButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        addBillingButton.addActionListener(this::onAddBillingInfo);

        JButton deleteBillingButton = new JButton("Delete Info");
        deleteBillingButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        deleteBillingButton.setBackground(new Color(244, 67, 54)); 
        deleteBillingButton.setForeground(Color.BLACK);
        deleteBillingButton.addActionListener(this::onDeleteBillingInfo);

        topPanel.add(addBillingButton);
        topPanel.add(deleteBillingButton);
        panel.add(topPanel, BorderLayout.NORTH);

        billingTableModel = new DefaultTableModel(new Object[]{"Field", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; 
            }
        };
        billingTable = new JTable(billingTableModel);
        billingTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
        billingTable.setRowHeight(20);
        billingTable.setTableHeader(null); 

        JScrollPane scrollPane = new JScrollPane(billingTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
    
    private JPanel createPasswordPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        panel.setBackground(new Color(220, 240, 255));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JLabel header = new JLabel("Change Your Login Password", SwingConstants.CENTER);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(header, gbc);
        
        oldPassField = new JPasswordField(20);
        newPassField = new JPasswordField(20);
        confirmNewPassField = new JPasswordField(20);
        JButton changeButton = new JButton("Set New Password");
        changeButton.addActionListener(this::onChangePassword);
        
        passStatusLabel = new JLabel("Your password will be updated instantly.", SwingConstants.CENTER);
        passStatusLabel.setForeground(Color.BLUE);
        
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        panel.add(new JLabel("Old Password:"), gbc);
        gbc.gridx = 1;
        panel.add(oldPassField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("New Password:"), gbc);
        gbc.gridx = 1;
        panel.add(newPassField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("Confirm New:"), gbc);
        gbc.gridx = 1;
        panel.add(confirmNewPassField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(changeButton, gbc);
        
        passStatusLabel = new JLabel(" ");
        passStatusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        gbc.gridy = 5;
        panel.add(passStatusLabel, gbc);
        
        changeButton.addActionListener(this::onChangePassword);
        
        return panel;
    }

    // --- Actions ---

    private void onSubmit(ActionEvent e) {
        String clientId = clientIdField.getText().trim();
        if (clientId.length() != 6) {
            JOptionPane.showMessageDialog(this, "Client ID must be exactly 6 alphanumeric characters.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int duration = (int) durationSpinner.getValue();
        String unit = (String) durationUnitBox.getSelectedItem();
        if ("days".equals(unit)) {
            duration *= 24;
        }

        int redundancy = (int) redundancySpinner.getValue();

        int m = (int) deadlineMonthSpinner.getValue();
        int d = (int) deadlineDaySpinner.getValue();
        int y = (int) deadlineYearSpinner.getValue();
        int h = (int) deadlineHourSpinner.getValue();
        
        LocalDateTime deadline;
        try {
            deadline = LocalDateTime.of(y, m, d, h, 0);
        } catch (DateTimeException ex) {
            JOptionPane.showMessageDialog(this, "Invalid Date/Time.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (deadline.isBefore(LocalDateTime.now())) {
             JOptionPane.showMessageDialog(this, "Deadline must be in the future.", "Input Error", JOptionPane.ERROR_MESSAGE);
             return;
        }

        String jobId = clientId + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create Job with explicit Client ID and Sender ID
        Job job = new Job(jobId, clientId, clientUser.getUserID(), duration, redundancy, deadline);
        
        Request request = server.createRequest(clientUser.getUserID(), "JOB_SUBMISSION", job);
        
        controller.processJobRequest(request);
        
        submittedRequests.put(job.getJobID(), request.getRequestID());
        
        JOptionPane.showMessageDialog(this, 
            "Job Submission Request Sent!\n" +
            "Request ID: " + request.getRequestID() + "\n" +
            "Job ID: " + job.getJobID() + "\n" +
            "Status: Waiting for VC Controller approval...", 
            "Request Acknowledged", 
            JOptionPane.INFORMATION_MESSAGE);
        clearForm();
    }

    private void loadBillingInfo() {
        User updatedUser = DatabaseManager.getInstance().loadUser(clientUser.getUserID());
        if (updatedUser instanceof Client) {
            Client c = (Client) updatedUser;
            clientUser.setBillingInfo(c.getCardHolder(), c.getCardNumber(), c.getCvc(), c.getExpiry());
            
            billingTableModel.setRowCount(0);
            if (c.getCardNumber() != null && !c.getCardNumber().isEmpty()) {
                billingTableModel.addRow(new Object[]{"Card Holder", c.getCardHolder()});
                billingTableModel.addRow(new Object[]{"Card Number", "**** **** **** " + c.getCardNumber().substring(Math.max(0, c.getCardNumber().length() - 4))});
                billingTableModel.addRow(new Object[]{"Expiry", c.getExpiry()});
                addBillingButton.setText("Edit Billing Info");
            } else {
                addBillingButton.setText("Add Billing Info");
            }
        }
    }

    private void onAddBillingInfo(ActionEvent e) {
        if (billingDialog == null) {
            billingDialog = new BillingInfoDialog(SwingUtilities.getWindowAncestor(this));
        }
        billingDialog.prefill(clientUser.getCardHolder(), clientUser.getCardNumber(), clientUser.getCvc(), clientUser.getExpiry());
        billingDialog.setVisible(true);
        
        if (billingDialog.isSaved()) {
            String[] info = billingDialog.getSavedInfo();
            if (info != null) {
                clientUser.setBillingInfo(info[0], info[1], info[2], info[3]);
                DatabaseManager.getInstance().saveUser(clientUser);
                loadBillingInfo();
            }
        }
    }

    private void onDeleteBillingInfo(ActionEvent e) {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete your billing info?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            clientUser.setBillingInfo("", "", "", "");
            DatabaseManager.getInstance().saveUser(clientUser);
            loadBillingInfo();
        }
    }
    
    // Deprecated
    private void saveBillingInfo() {}

    private void loadJobsFromCentralStorage() {
        List<Job> jobs = controller.getClientJobHistory(clientUser.getUserID());
        tableModel.setRowCount(0);
        for (Job job : jobs) {
            String clientEnteredID = job.getClientEnteredID();
            if (clientEnteredID == null || clientEnteredID.isEmpty()) {
                clientEnteredID = "UNKNOWN";
            }
            
            String timestamp = "N/A";
            if (job.getTimestamp() != null) {
                timestamp = TS_FMT.format(job.getTimestamp());
            }
            
            tableModel.addRow(new Object[]{
                timestamp, 
                clientEnteredID,
                job.getJobID(),
                job.getStatus(),
                job.getDuration(),
                job.getDeadline().toString(),
                job.getRedundancyLevel()
            });
        }
    }

    private void refreshJobTable() {
        loadJobsFromCentralStorage();
    }

    private void onChangePassword(ActionEvent e) {
        String oldPass = new String(oldPassField.getPassword());
        String newPass = new String(newPassField.getPassword());
        String confirmPass = new String(confirmNewPassField.getPassword());

        if (!clientUser.getPassword().equals(oldPass)) {
            passStatusLabel.setText("Incorrect old password.");
            passStatusLabel.setForeground(Color.RED);
            return;
        }

        if (!newPass.equals(confirmPass)) {
            passStatusLabel.setText("New passwords do not match.");
            passStatusLabel.setForeground(Color.RED);
            return;
        }

        if (newPass.isEmpty()) {
            passStatusLabel.setText("Password cannot be empty.");
            passStatusLabel.setForeground(Color.RED);
            return;
        }

        clientUser.setPassword(newPass);
        DatabaseManager.getInstance().saveUser(clientUser);
        
        passStatusLabel.setText("Password updated successfully!");
        passStatusLabel.setForeground(new Color(0, 150, 0));
        
        oldPassField.setText("");
        newPassField.setText("");
        confirmNewPassField.setText("");
    }

    private void clearForm() {
        clientIdField.setText("");
        durationSpinner.setValue(1);
        durationUnitBox.setSelectedIndex(0);
        redundancySpinner.setValue(1);

        LocalDateTime now = LocalDateTime.now().plusHours(1);
        deadlineMonthSpinner.setValue(now.getMonthValue());
        deadlineDaySpinner.setValue(now.getDayOfMonth());
        deadlineYearSpinner.setValue(now.getYear());
        deadlineHourSpinner.setValue(now.getHour());
    }

    // --- Billing Dialog ---
    private class BillingInfoDialog extends JDialog {
        private JTextField holderField;
        private JTextField numberField;
        private JTextField cvcField;
        private JTextField expiryField;
        private String[] savedInfo;
        private boolean isSaved = false;

        BillingInfoDialog(Window owner) {
            super(owner, "Add/Edit Billing Info", ModalityType.APPLICATION_MODAL);
            setSize(400, 300);
            setLocationRelativeTo(owner);
            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            holderField = new JTextField(20);
            ((AbstractDocument) holderField.getDocument()).setDocumentFilter(new LettersOnlyFilter());

            numberField = new JTextField(16);
            ((AbstractDocument) numberField.getDocument()).setDocumentFilter(new NumbersOnlyFilter(16));

            cvcField = new JTextField(3);
            ((AbstractDocument) cvcField.getDocument()).setDocumentFilter(new NumbersOnlyFilter(3));

            expiryField = new JTextField(5); // MM/YY
            
            gbc.gridx = 0; gbc.gridy = 0; add(new JLabel("Card Holder:"), gbc);
            gbc.gridx = 1; gbc.gridy = 0; add(holderField, gbc);
            
            gbc.gridx = 0; gbc.gridy = 1; add(new JLabel("Card Number:"), gbc);
            gbc.gridx = 1; gbc.gridy = 1; add(numberField, gbc);
            
            gbc.gridx = 0; gbc.gridy = 2; add(new JLabel("CVC:"), gbc);
            gbc.gridx = 1; gbc.gridy = 2; add(cvcField, gbc);
            
            gbc.gridx = 0; gbc.gridy = 3; add(new JLabel("Expiry (MM/YY):"), gbc);
            gbc.gridx = 1; gbc.gridy = 3; add(expiryField, gbc);

            JButton saveButton = new JButton("Save");
            saveButton.addActionListener(this::onSaveBilling);
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; add(saveButton, gbc);
        }

        public String[] getSavedInfo() {
            return savedInfo;
        }
        
        public boolean isSaved() {
            return isSaved;
        }

        public void prefill(String holder, String number, String cvc, String expiry) {
            holderField.setText(holder != null ? holder : "");
            numberField.setText(number != null ? number : "");
            cvcField.setText(cvc != null ? cvc : "");
            expiryField.setText(expiry != null ? expiry : "");
            this.savedInfo = null;
            this.isSaved = false;
        }

        private void onSaveBilling(ActionEvent e) {
            String holder = holderField.getText().trim();
            String number = numberField.getText().trim();
            String cvc = cvcField.getText().trim();
            String expiry = expiryField.getText().trim();

            if (holder.isEmpty() || number.length() != 16 || cvc.length() != 3 || expiry.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all fields correctly.\nCard Number must be 16 digits.\nCVC must be 3 digits.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            this.savedInfo = new String[]{holder, number, cvc, expiry};
            this.isSaved = true;
            JOptionPane.showMessageDialog(this, "Billing Info Saved!");
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
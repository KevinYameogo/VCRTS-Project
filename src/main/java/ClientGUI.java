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

    private final String BILLING_FILE;
    private final String NOTIFICATION_FILE;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter JOB_ID_TS_FMT = DateTimeFormatter.ofPattern("HHmmss");

    public ClientGUI(Client clientUser, Runnable onLogout, VCController controller) { 
        this.clientUser = clientUser; 
        this.onLogout = onLogout;
        this.controller = controller;
        this.server = controller.getServer();
        this.submittedRequests = new HashMap<>();
        
        this.billingInfo = clientUser.getBillingInfo(); 
        this.BILLING_FILE = clientUser.getUserID() + "_" + clientUser.getRole() + "_billing.dat"; 
        this.NOTIFICATION_FILE = clientUser.getUserID() + "_" + clientUser.getRole() + "_notifications.txt";

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
        badgeLabel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1, true));

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
                } else if (tabs.getSelectedIndex() == 0 || tabs.getSelectedIndex() == 1) { 
                    loadJobsFromCentralStorage(); 
                }
            }
        });
        
        // Load persistent data and restore badge count
        loadJobsFromCentralStorage();
        loadBillingInfo();
        loadNotifications();

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
            checkServerNotifications();
            
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
        checkServerNotifications();
        
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
        
        public void shutdown() {
            this.running = false;
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
                                    // Fallback: save directly to file if no GUI is active
                                    saveNotificationToFile(notification);
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
     * Saves notification directly to file when no GUI callback is available.
     */
    private void saveNotificationToFile(String notification) {
        String filename = userID + "_client_notifications.txt";
        String timestamp = TS_FMT.format(LocalDateTime.now());
        String formattedNotif = "[" + timestamp + "] " + notification;
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true))) {
            writer.write(formattedNotif);
            writer.newLine();
            System.out.println("NotificationClient: Saved to file: " + formattedNotif);
        } catch (IOException e) {
            System.err.println("NotificationClient: Error saving notification: " + e.getMessage());
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
                System.out.println("NotificationClient: Incremented count to " + currentCount);
            }
        } catch (IOException e) {
            System.err.println("NotificationClient: Error updating count: " + e.getMessage());
        }
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
        List<String> serverNotifications = server.getNotifications(clientUser.getUserID());
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
                    addNotification("✓ Job " + jobID + " has been APPROVED by VC Controller");
                } else if (request != null && request.getStatus().equals("Rejected")) {
                    addNotification("✗ Job " + jobID + " has been REJECTED by VC Controller");
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
            JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, ClientGUI.this);
            if (tabs == null || tabs.getSelectedIndex() != notificationsTabIndex) {
                notificationCount++;
                badgeLabel.setText(String.valueOf(notificationCount));
                badgeLabel.setVisible(true);
            }
            
            saveNotifications();

    
        });
    }

    // Also update the saveNotifications method to ensure count is ALWAYS saved:

    private void saveNotifications() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(NOTIFICATION_FILE))) {
            for (int i = 0; i < notificationListModel.size(); i++) {
                writer.write(notificationListModel.getElementAt(i));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving notifications: " + e.getMessage());
        }
        
        // ALWAYS save the count separately
        try (PrintWriter countWriter = new PrintWriter(new FileWriter(NOTIFICATION_FILE + "_count.txt", false))) {
            countWriter.print(this.notificationCount);
            System.out.println("ClientGUI: Saved notification count: " + this.notificationCount);
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
            System.out.println("ClientGUI: Reset notification count to 0");
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

        if (!currentPass.equals(clientUser.getPassword())) {
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

        clientUser.setPassword(newPass);

        passStatusLabel.setForeground(new Color(34, 139, 34)); 
        passStatusLabel.setText("Password successfully updated! New password is now active.");
        
        oldPassField.setText("");
        newPassField.setText("");
        confirmNewPassField.setText("");
    }

    private void refreshJobTable() {
        int statusColumnIndex = 3; 
        int jobIDColumnIndex = 2;  

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String jobID = (String) tableModel.getValueAt(i, jobIDColumnIndex);
            String liveStatus = controller.getJobStatus(jobID);
            String currentTableStatus = (String) tableModel.getValueAt(i, statusColumnIndex);
            
            if (!liveStatus.equalsIgnoreCase(currentTableStatus) && 
                !liveStatus.equalsIgnoreCase("Job not found") &&
                !currentTableStatus.equalsIgnoreCase("Awaiting Approval")) {
                
                tableModel.setValueAt(liveStatus, i, statusColumnIndex);
            }
        }
    }
    
    private void loadJobsFromCentralStorage() {
        tableModel.setRowCount(0);
        server.reloadState();
        
        List<Job> jobHistory = controller.getClientJobHistory(clientUser.getUserID());
        
        for (Job job : jobHistory) {
             String status = controller.getJobStatus(job.getJobID()); 
             String clientEnteredID = server.getClientIDForJob(job);
             if (clientEnteredID == null) {
                 System.err.println("Client ID not found in server map for job: " + job.getJobID());
                 clientEnteredID = job.getJobID().split("-")[0];
             }
             
             tableModel.addRow(new Object[]{
                 TS_FMT.format(LocalDateTime.now()), 
                 clientEnteredID,
                 job.getJobID(),
                 status, 
                 job.getDuration(), 
                 job.getDeadline().toString(),
                 job.getRedundancyLevel()
             });
        }
        
        System.out.println("ClientGUI: Loaded " + jobHistory.size() + " jobs from central storage.");
    }

    private void onAddBillingInfo(ActionEvent e) {
        if (billingDialog == null) {
            billingDialog = new BillingInfoDialog(SwingUtilities.getWindowAncestor(this));
        }
        billingDialog.prefill(this.billingInfo);
        billingDialog.setVisible(true);

        if (billingDialog.getSavedInfo() != null) {
            this.billingInfo = billingDialog.getSavedInfo();
            saveBillingInfo();
            loadBillingInfo();
        }
    }

    private void onDeleteBillingInfo(ActionEvent e) {
        if (billingInfo == null || billingInfo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No billing info to delete.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete your billing info?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            this.billingInfo = "";
            saveBillingInfo();
            loadBillingInfo();
            JOptionPane.showMessageDialog(this, "Billing Info Deleted.");
        }
    }

    private void onSubmit(ActionEvent e) {
        if (this.billingInfo == null || this.billingInfo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add billing info (Tab 3) before submitting a job.", "Billing Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String clientID = clientIdField.getText().trim(); 
        if (clientID.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a Client ID.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String jobID = clientID + "-" + LocalDateTime.now().format(JOB_ID_TS_FMT) + "-" + ThreadLocalRandom.current().nextInt(100, 1000); 

        if (controller.isJobInSystem(jobID)) {
            JOptionPane.showMessageDialog(this, "A job with ID '" + jobID + "' already exists in the system. Try again.", "Duplicate Job ID", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int redundancy = (Integer) redundancySpinner.getValue();

        LocalDateTime deadline;
        try {
            int year = (Integer) deadlineYearSpinner.getValue();
            int month = (Integer) deadlineMonthSpinner.getValue();
            int day = (Integer) deadlineDaySpinner.getValue();
            int hour = (Integer) deadlineHourSpinner.getValue();
            deadline = LocalDateTime.of(year, month, day, hour, 0);

            if (deadline.isBefore(LocalDateTime.now())) {
                JOptionPane.showMessageDialog(this, "Deadline must be in the future.");
                return;
            }
        } catch (DateTimeException ex) {
            JOptionPane.showMessageDialog(this, "Invalid date (e.g., Feb 30).");
            return;
        }

        int durationAmount = (Integer) durationSpinner.getValue();
        String durationUnit = String.valueOf(durationUnitBox.getSelectedItem());
        int durationInHours = durationUnit.equals("days") ? (durationAmount * 24) : durationAmount;
        String deadlineString = deadline.toString();

        Job newJob = new Job(jobID, durationInHours, redundancy, deadline); 
        
        Request request = server.createRequest(clientUser.getUserID(), "JOB_SUBMISSION", newJob);
        controller.processJobRequest(request); 
        
        submittedRequests.put(jobID, request.getRequestID());
        
        JOptionPane.showMessageDialog(this, 
            "Job Request Sent!\n" +
            "Request ID: " + request.getRequestID() + "\n" +
            "Job ID: " + jobID + "\n" +
            "Status: Waiting for VC Controller approval...", 
            "Request Acknowledged", 
            JOptionPane.INFORMATION_MESSAGE);
        
        addNotification("Job request " + jobID + " submitted and acknowledged by server");

        String ts = TS_FMT.format(LocalDateTime.now());
        tableModel.addRow(new Object[]{ts, clientID, jobID, "Awaiting Approval", durationInHours, deadlineString, redundancy}); 
    }

    private void loadBillingInfo() {
        File file = new File(BILLING_FILE);
        billingTableModel.setRowCount(0);

        if (!file.exists()) {
            addBillingButton.setText("Add Billing Info");
            this.billingInfo = "";
            clientUser.setBillingInfo(""); 
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String loadedInfo = br.readLine();
            if (loadedInfo != null && !loadedInfo.isEmpty()) {
                this.billingInfo = loadedInfo;
                clientUser.setBillingInfo(loadedInfo); 
                String[] parts = billingInfo.split("\\|");

                if (parts.length == 5) {
                    billingTableModel.addRow(new Object[]{"Name on Card", parts[0]});
                    billingTableModel.addRow(new Object[]{"Card Number", "**** **** **** " + parts[1].substring(12)});
                    billingTableModel.addRow(new Object[]{"Expiry", parts[3] + "/" + parts[4]});
                }

                addBillingButton.setText("Edit Billing Info");
            } else {
                addBillingButton.setText("Add Billing Info");
                this.billingInfo = "";
                clientUser.setBillingInfo(""); 
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading billing info from " + BILLING_FILE, "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveBillingInfo() {
        clientUser.setBillingInfo(this.billingInfo); 
        try (FileWriter fw = new FileWriter(BILLING_FILE, false)) {
            fw.write(this.billingInfo);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving billing info file.", "Save Error", JOptionPane.ERROR_MESSAGE);
        }
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
        private JTextField nameField;
        private JTextField cardField;
        private JTextField cvcField;
        private JTextField expMonthField;
        private JTextField expYearField;
        private String savedInfo;

        BillingInfoDialog(Window owner) {
            super(owner, "Add/Edit Billing Info", ModalityType.APPLICATION_MODAL);
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
            saveButton.addActionListener(this::onSaveBilling);
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

        private void onSaveBilling(ActionEvent e) {
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
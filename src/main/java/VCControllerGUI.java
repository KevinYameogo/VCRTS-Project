import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * VCControllerGUI with request approval/rejection functionality.
 */
public class VCControllerGUI extends JPanel {

    private VCController controller;
    private Server server;
    private Runnable onBack;

    private JLabel statusLabel;
    private DefaultTableModel requestTableModel;
    private JTable requestTable;
    private DefaultListModel<String> notificationListModel;
    private JList<String> notificationList;
    private JTextArea fileLogArea;
    private DefaultListModel<Job> jobListModel;

    // Timer for refreshing requests
    private Timer requestRefreshTimer;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // CSV filenames (global, centralized storage owned by VC Controller)
    private static final String JOBS_CSV = "jobs.csv";
    private static final String VEHICLES_CSV = "vehicles.csv";

    public VCControllerGUI(VCController controller, Server server, Runnable onBack) {
        this.controller = controller;
        this.server = server;
        this.onBack = onBack;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 245, 245));

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createMainContentPanel(), BorderLayout.CENTER);

        logToFile("Server started at " + TS_FMT.format(LocalDateTime.now()));
        addNotification("Server started successfully.");

        // Connect GUI back to controller for push notifications/logs
        controller.setControllerGUI(this);

        // Load persistent notifications
        loadNotifications();

        // Start timer to refresh pending requests every 2 seconds
        startRequestRefreshTimer();
    }

    public void stopTimer() {
        if (requestRefreshTimer != null) {
            requestRefreshTimer.stop();
        }
    }

    private void startRequestRefreshTimer() {
        // The timer ensures that VCController instances see the pending request list
        requestRefreshTimer = new Timer(2000, e -> refreshPendingRequests());
        requestRefreshTimer.start();
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JLabel titleLabel = new JLabel("VC Controller Dashboard");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightPanel.setOpaque(false);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusPanel.setOpaque(false);
        JLabel statusTextLabel = new JLabel("Status:");
        statusTextLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusPanel.add(statusTextLabel);

        statusLabel = new JLabel("● Online");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusLabel.setForeground(new Color(76, 175, 80));
        statusPanel.add(statusLabel);

        JButton backButton = new JButton("← Back");
        backButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        backButton.setFocusPainted(false);
        backButton.setBackground(new Color(230, 230, 230));
        backButton.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        backButton.addActionListener((ActionEvent e) -> {
            // Stop the timer and save state before leaving
            stopTimer();
            saveNotifications();
            server.saveState();
            if (onBack != null) {
                onBack.run();
            }
        });

        rightPanel.add(statusPanel);
        rightPanel.add(backButton);
        headerPanel.add(rightPanel, BorderLayout.EAST);

        return headerPanel;
    }

    private JPanel createMainContentPanel() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);

        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        GridBagConstraints leftGbc = new GridBagConstraints();
        leftGbc.fill = GridBagConstraints.BOTH;
        leftGbc.gridx = 0;
        leftGbc.weightx = 1.0;
        leftGbc.insets = new Insets(0, 0, 10, 0);

        leftGbc.gridy = 0;
        leftGbc.weighty = 0.6;
        leftPanel.add(createRequestsPanel(), leftGbc);

        leftGbc.gridy = 1;
        leftGbc.weighty = 0.4;
        leftPanel.add(createFileLogPanel(), leftGbc);

        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.fill = GridBagConstraints.BOTH;
        rightGbc.gridx = 0;
        rightGbc.weightx = 1.0;
        rightGbc.insets = new Insets(0, 0, 10, 0);

        rightGbc.gridy = 0;
        rightGbc.weighty = 0.4;
        rightPanel.add(createNotificationsPanel(), rightGbc);

        rightGbc.gridy = 1;
        rightGbc.weighty = 0.3;
        rightPanel.add(createCheckpointPanel(), rightGbc);

        rightGbc.gridy = 2;
        rightGbc.weighty = 0.3;
        rightPanel.add(createCompletionTimesPanel(), rightGbc);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.6;
        gbc.weighty = 1.0;
        mainPanel.add(leftPanel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.4;
        mainPanel.add(rightPanel, gbc);

        return mainPanel;
    }

    private JPanel createRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel("Pending Requests");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);

        requestTableModel = new DefaultTableModel(
                new Object[]{"Request ID", "Sender", "Type", "Timestamp", "Actions"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; 
            }
        };

        requestTable = new JTable(requestTableModel);
        requestTable.setRowHeight(80); 
        requestTable.setFont(new Font("SansSerif", Font.PLAIN, 12));

        requestTable.getColumn("Actions").setCellRenderer(new ButtonRenderer());
        requestTable.getColumn("Actions").setCellEditor(new ButtonEditor(new JCheckBox()));

        JScrollPane scrollPane = new JScrollPane(requestTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Manual refresh
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setOpaque(false);
        JButton refreshBtn = new JButton("Refresh Requests");
        refreshBtn.addActionListener(e -> refreshPendingRequests());
        bottomPanel.add(refreshBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Refreshes the pending requests table from the server.
     * Ensures the server state is reloaded from disk first for multi-instance sync.
     */
    private void refreshPendingRequests() {
        // Reload server state to ensure we see approvals/rejections made in other instances.
        server.reloadState();

        SwingUtilities.invokeLater(() -> {
            requestTableModel.setRowCount(0);
            for (Request req : server.getPendingRequests()) {
                requestTableModel.addRow(new Object[]{
                        req.getRequestID(),
                        req.getSenderID(),
                        req.getRequestType(),
                        req.getTimestamp().format(TS_FMT),
                        req.getRequestID() 
                });
            }
        });
    }

    private JPanel createNotificationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel("Notifications");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);

        notificationListModel = new DefaultListModel<>();
        notificationList = new JList<>(notificationListModel);
        notificationList.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JScrollPane scrollPane = new JScrollPane(notificationList);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        JButton clearButton = new JButton("Clear All");
        clearButton.addActionListener(e -> {
            notificationListModel.clear();
            saveNotifications(); 
        });
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createFileLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel("File Log");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);

        fileLogArea = new JTextArea();
        fileLogArea.setEditable(false);
        fileLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        fileLogArea.setLineWrap(true);
        fileLogArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(fileLogArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCheckpointPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel("Checkpoint Control");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(titleLabel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;

        jobListModel = new DefaultListModel<>();
        for (Job job : controller.getInProgressJobs()) {
            jobListModel.addElement(job);
        }
        JList<Job> jobList = new JList<>(jobListModel);
        jobList.setVisibleRowCount(3);
        JScrollPane scrollPane = new JScrollPane(jobList);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        contentPanel.add(scrollPane, gbc);

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        buttonPanel.setOpaque(false);

        JButton triggerBtn = new JButton("Trigger Checkpoint");
        JButton refreshBtn = new JButton("Refresh");

        triggerBtn.addActionListener(e -> {
            Job selectedJob = jobList.getSelectedValue();
            if (selectedJob == null) {
                JOptionPane.showMessageDialog(this, "Please select a job first.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            controller.triggerCheckpoint(selectedJob);
            addNotification("Checkpoint triggered for Job " + selectedJob.getJobID());
            logToFile("Checkpoint triggered for Job " + selectedJob.getJobID());
        });

        refreshBtn.addActionListener(e -> refreshJobList());

        buttonPanel.add(triggerBtn);
        buttonPanel.add(refreshBtn);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        contentPanel.add(buttonPanel, gbc);

        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCompletionTimesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel("Completion Times Calculator");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(titleLabel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        final JButton calcBtn = new JButton("<html><center>Calculate All<br>Completion Times</center></html>");
        calcBtn.setPreferredSize(new Dimension(180, 50));
        final String defaultCalcBtnText = "<html><center>Calculate All<br>Completion Times</center></html>";

        final JTextField calcStatusField = new JTextField("Click button for pending job estimates.");
        calcStatusField.setEditable(false);
        calcStatusField.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        calcStatusField.setBackground(new Color(245, 245, 245));
        calcStatusField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        calcBtn.addActionListener(e -> {
            if (controller.getPendingJobs().isEmpty()) {
                calcStatusField.setText("No jobs pending to calculate.");
                JOptionPane.showMessageDialog(this, "No jobs pending in the queue to calculate completion times.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            calcBtn.setEnabled(false);
            calcBtn.setText("Computing...");
            calcStatusField.setText("Computing... please wait.");
            calcStatusField.setForeground(Color.BLUE);

            SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return controller.calculateCompletionTimes();
                }

                @Override
                protected void done() {
                    try {
                        String result = get();
                        JTextArea textArea = new JTextArea(result);
                        textArea.setEditable(false);
                        textArea.setRows(10);
                        textArea.setColumns(40);

                        JScrollPane resultScrollPane = new JScrollPane(textArea);
                        JOptionPane.showMessageDialog(VCControllerGUI.this,
                                resultScrollPane,
                                "Pending Job Estimates",
                                JOptionPane.INFORMATION_MESSAGE);

                        calcStatusField.setText("Calculation completed.");
                        logToFile("Completion times calculated for all pending jobs.");

                    } catch (Exception ex) {
                        calcStatusField.setText("Error: Calculation failed.");
                        ex.printStackTrace();
                    } finally {
                        calcBtn.setEnabled(true);
                        calcBtn.setText(defaultCalcBtnText);
                        calcStatusField.setForeground(Color.BLACK);
                    }
                }
            };

            worker.execute();
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPanel.add(calcBtn, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(10, 10, 5, 10);
        contentPanel.add(calcStatusField, gbc);

        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private void refreshJobList() {
        jobListModel.clear();
        for (Job job : controller.getInProgressJobs()) {
            jobListModel.addElement(job);
        }
    }

    public void addNotification(String message) {
        String timestamp = TS_FMT.format(LocalDateTime.now());
        SwingUtilities.invokeLater(() -> {
            notificationListModel.addElement("[" + timestamp + "] " + message);
            saveNotifications(); // Auto-save when notification added
        });
    }

    public void logToFile(String message) {
        String timestamp = TS_FMT.format(LocalDateTime.now());
        SwingUtilities.invokeLater(() -> {
            fileLogArea.append("[" + timestamp + "] " + message + "\n");
            fileLogArea.setCaretPosition(fileLogArea.getDocument().getLength());
        });
    }

    //  CSV HELPERS (STORE ONLY ON ACCEPT)

    /** Append an approved job submission to jobs.csv */
    private void appendApprovedJobToCSV(Job job, String clientEnteredID) {
        if (job == null) return;
        String ts = TS_FMT.format(LocalDateTime.now());

        File out = new File(JOBS_CSV);
        boolean writeHeader = !out.exists() || out.length() == 0;

        try (FileWriter fw = new FileWriter(out, true)) {
            if (writeHeader) {
                fw.write("timestamp,client_id,job_id,status,duration_hours,deadline,redundancy\n");
            }
            String line = String.join(",",
                    escape(ts),
                    escape(clientEnteredID),
                    escape(job.getJobID()),
                    escape(job.getStatus() == null ? "Pending" : job.getStatus()),
                    String.valueOf(job.getDuration()),
                    escape(String.valueOf(job.getDeadline())),  // LocalDateTime -> ISO string
                    String.valueOf(job.getRedundancyLevel())
            );
            fw.write(line + "\n");
            logToFile("jobs.csv: appended " + job.getJobID());
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error writing jobs.csv: " + ex.getMessage(), "I/O Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Append an approved vehicle registration to vehicles.csv 
     */
    private void appendApprovedVehicleToCSV(Vehicle vehicle, String loginId) {
        if (vehicle == null) return;

        String ownerEnteredId = server.getVehicleOwnerIDForDisplay(vehicle);
        if (ownerEnteredId == null || ownerEnteredId.isEmpty()) {
            // Fallback, though Server should ideally have this mapped
            ownerEnteredId = loginId;
        }

        String ts = TS_FMT.format(LocalDateTime.now());

        File out = new File(VEHICLES_CSV);
        boolean writeHeader = !out.exists() || out.length() == 0;

        try (FileWriter fw = new FileWriter(out, true)) {
            if (writeHeader) {
                fw.write("timestamp,owner_id,license,state,make,model,year,departure_schedule\n");
            }
            String line = String.join(",",
                    escape(ts),
                    escape(ownerEnteredId), 
                    escape(vehicle.getVehicleID()),             // license plate
                    escape(vehicle.getLicenseState()),          // state
                    escape(vehicle.getMake()),                  // make
                    escape(vehicle.getModel()),                 // model
                    escape(String.valueOf(vehicle.getYear())),
                    escape(vehicle.getDepartureSchedule().toString()) // ISO datetime
            );
            fw.write(line + "\n");
            logToFile("vehicles.csv: appended " + vehicle.getVehicleID());
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error writing vehicles.csv: " + ex.getMessage(), "I/O Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        boolean need = s.contains(",") || s.contains("\"") || s.contains("\n");
        String v = need ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
        return v;
    }

    //  PERSISTENT NOTIFICATION METHODS 

    /** Saves notifications to a file for persistence across sessions. */
    private void saveNotifications() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("vccontroller_notifications.txt"))) {
            for (int i = 0; i < notificationListModel.size(); i++) {
                writer.write(notificationListModel.getElementAt(i));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving notifications: " + e.getMessage());
        }
    }

    /** Loads notifications from file when GUI starts. */
    private void loadNotifications() {
        File file = new File("vccontroller_notifications.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            notificationListModel = new DefaultListModel<>();
            while ((line = reader.readLine()) != null) {
                notificationListModel.addElement(line);
            }
            if (notificationList != null) {
                notificationList.setModel(notificationListModel);
            }
        } catch (IOException e) {
            System.err.println("Error loading notifications: " + e.getMessage());
        }
    }

    // BUTTON RENDERER AND EDITOR (ICONS) 

    /**
     * Renders Accept/Reject buttons with icons (✓ / ✗).
     */
    class ButtonRenderer extends JPanel implements TableCellRenderer {
        private JButton acceptButton;
        private JButton rejectButton;

        public ButtonRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

            acceptButton = makeIconButton("✔️", "Accept request");
            rejectButton = makeIconButton("x", "Reject request");

            add(acceptButton);
            add(Box.createVerticalStrut(5));
            add(rejectButton);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            return this;
        }
    }

  /**
 * Handles button clicks in table cells (icons only + tooltips).
 * Also performs CSV writes *only* on acceptance.
 * Ensures notifications are ALWAYS queued and persisted immediately.
 */
class ButtonEditor extends DefaultCellEditor {
    private JPanel panel;
    private JButton acceptButton;
    private JButton rejectButton;
    private String requestID;

    public ButtonEditor(JCheckBox checkBox) {
        super(checkBox);

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        acceptButton = makeIconButton("✓", "Accept request");
        rejectButton = makeIconButton("✗", "Reject request");

        acceptButton.addActionListener(e -> {
            handleAccept();
            fireEditingStopped();
        });

        rejectButton.addActionListener(e -> {
            handleReject();
            fireEditingStopped();
        });

        panel.add(acceptButton);
        panel.add(Box.createVerticalStrut(5));
        panel.add(rejectButton);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                 boolean isSelected, int row, int column) {
        this.requestID = (String) value;
        return panel;
    }

    @Override
    public Object getCellEditorValue() {
        return requestID;
    }

    // 
    private synchronized void handleAccept() {
        Request req = server.getRequest(requestID);
        if (req == null) {
            System.err.println("VCController: Request not found: " + requestID);
            return;
        }

        String senderId = req.getSenderID(); // User's login ID

        if (req.getRequestType().equals("JOB_SUBMISSION")) {
            Job job = (Job) req.getData();
            
            // Build notification message
            String notificationMsg = "Your job " + job.getJobID() + " has been APPROVED and added to the queue.";
            
            // Send notification 
            server.notifyUser(senderId, notificationMsg);
            
            // Approve in controller/server (adds job to queue)
            controller.approveJobSubmission(requestID);

            // CSV WRITE: only on accept
            String clientEnteredID = server.getClientIDForJob(job);
            if (clientEnteredID == null) {
                String id = job.getJobID();
                int dash = id.indexOf('-');
                clientEnteredID = (dash > 0) ? id.substring(0, dash) : id;
            }
            appendApprovedJobToCSV(job, clientEnteredID);
            
            addNotification("Job " + job.getJobID() + " APPROVED and stored to jobs.csv");
            logToFile("Job " + job.getJobID() + " approved and stored (jobs.csv)");

        } else if (req.getRequestType().equals("VEHICLE_REGISTRATION")) {
            Vehicle vehicle = (Vehicle) req.getData();
            
            // Build notification message
            String notificationMsg = "Your vehicle " + vehicle.getVehicleID() + " has been APPROVED and registered.";
            
            // Send notification 
            server.notifyUser(senderId, notificationMsg);
            
            // Approve in controller/server (recruits vehicle)
            controller.approveVehicleRegistration(requestID);

            // CSV WRITE: only on accept
            appendApprovedVehicleToCSV(vehicle, senderId);
            
            addNotification("Vehicle " + vehicle.getVehicleID() + " APPROVED and stored to vehicles.csv");
            logToFile("Vehicle " + vehicle.getVehicleID() + " approved and stored (vehicles.csv)");
        }

        // Refresh list so the processed item disappears
        refreshPendingRequests();
    }

    private synchronized void handleReject() {
        Request req = server.getRequest(requestID);
        if (req == null) {
            System.err.println("VCController: Request not found: " + requestID);
            return;
        }

        String senderId = req.getSenderID();

        if (req.getRequestType().equals("JOB_SUBMISSION")) {
            Job job = (Job) req.getData();
            
            // Build notification message
            String notificationMsg = "Your job request " + job.getJobID() + " has been REJECTED.";
            
            // Send notification 
            server.notifyUser(senderId, notificationMsg);
            
            // Reject in controller/server (removes from server's pending map)
            controller.rejectJobSubmission(requestID);

            // NO CSV WRITE on reject
            addNotification("Job request " + job.getJobID() + " REJECTED - not stored");
            logToFile("Job request " + job.getJobID() + " rejected (no CSV write)");

        } else if (req.getRequestType().equals("VEHICLE_REGISTRATION")) {
            Vehicle vehicle = (Vehicle) req.getData();
            
            // Build notification message
            String notificationMsg = "Your vehicle registration " + vehicle.getVehicleID() + " has been REJECTED.";
            
            // Send notification 
            server.notifyUser(senderId, notificationMsg);
            
            // Reject in controller/server (removes from server's pending map)
            controller.rejectVehicleRegistration(requestID);

            // NO CSV WRITE on reject
            addNotification("Vehicle " + vehicle.getVehicleID() + " REJECTED - not stored");
            logToFile("Vehicle " + vehicle.getVehicleID() + " rejected (no CSV write)");
        }

        // Refresh list so the processed item disappears
        refreshPendingRequests();
    }
}

    //  UTIL: ICON-ONLY BUTTON FACTORY 

    private static JButton makeIconButton(String glyph, String tooltip) {
        JButton b = new JButton(glyph);
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 18)); 
        b.setForeground(Color.BLUE);
        b.setBackground("✓".equals(glyph) ? new Color(76, 175, 80) : new Color(244, 67, 54));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(100, 32));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}

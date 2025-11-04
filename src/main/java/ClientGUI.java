// ClientGUI.java (Job Status Box Final Fix)
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

/**
 * ClientGUI combines job submission, management, billing, and password into a multi-tab panel.
 */
public class ClientGUI extends JPanel {

    // --- USER INFO ---
    private final Client clientUser; 
    private final Runnable onLogout;

    // --- TAB 1 (Submit Job) FIELDS ---
    private JTextField clientIdField;
    private JSpinner durationSpinner;
    private JComboBox<String> durationUnitBox;
    private JSpinner redundancySpinner;

    private JSpinner deadlineMonthSpinner;
    private JSpinner deadlineDaySpinner;
    private JSpinner deadlineYearSpinner;
    private JSpinner deadlineHourSpinner;

    // --- TAB 3 (Billing) FIELDS ---
    private String billingInfo; // Local copy for persistence I/O
    private JButton addBillingButton;
    private JTable billingTable;
    private DefaultTableModel billingTableModel;
    private BillingInfoDialog billingDialog;

    // --- TAB 4 (Password) FIELDS --- 
    private JPasswordField oldPassField;
    private JPasswordField newPassField;
    private JPasswordField confirmNewPassField;
    private JLabel passStatusLabel;

    private DefaultTableModel tableModel;
    private JTable table;
    private DefaultListModel<Job> listModel;

    // --- FILENAMES ---
    private final String CSV_FILE;
    private final String BILLING_FILE;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // --- TAB 2 (Manage Job) FIELDS ---
    private VCController controller;

    public ClientGUI(Client clientUser, Runnable onLogout, VCController controller) { 
        this.clientUser = clientUser; 
        this.onLogout = onLogout;
        this.controller = controller;
        
        // Initialize local copy from Client object
        this.billingInfo = clientUser.getBillingInfo(); 

        // ---User-specific filenames now use the UserID---
        this.CSV_FILE = clientUser.getUserID() + "_job_entries.csv"; 
        this.BILLING_FILE = clientUser.getUserID() + "_billing.dat"; 

        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Submit New Job", createSubmitJobPanel());
        tabs.addTab("Manage Existing Jobs", createManageJobsPanel());
        tabs.addTab("Manage Billing", createBillingPanel());
        tabs.addTab("Change Password", createPasswordPanel()); // NEW TAB

        add(tabs, BorderLayout.CENTER);

        // These load the GUI *and* restore the controller's state
        loadJobsFromCSV();
        loadBillingInfo();
    }

    /**
     * Creates the "Submit New Job" panel (Tab 1)
     */
    private JPanel createSubmitJobPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.setBackground(new Color(220, 240, 255));

        // *** USE clientUser.getName() in welcome message ***
        JLabel header = new JLabel("Job Submission Console  |  Welcome " + clientUser.getName(), SwingConstants.CENTER);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        root.add(header);
        root.add(Box.createVerticalStrut(10));

        // ... (rest of form setup remains the same) ...
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Initialize fields
        clientIdField = new JTextField();
        ((AbstractDocument) clientIdField.getDocument()).setDocumentFilter(new AlphanumericFilter(4));

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
        form.add(new JLabel("Job ID (4 Chars):"), gc);
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

        // --- BUTTONS ---
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

        // === TABLE PANEL ===
        tableModel = new DefaultTableModel(new Object[]{
                "Timestamp", "Job ID", "Duration (Hours)", "Deadline", "Redundancy"
        }, 0);
        table = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(760, 260));
        root.add(tableScroll);

        // === EVENT HANDLERS ===
        submitButton.addActionListener(this::onSubmit);
        clearButton.addActionListener(e -> clearForm());

        logoutButton.addActionListener(e -> onLogout.run());


        return root;
    }

    /**
     * Creates the "Manage Jobs" panel (Tab 2)
     */
    private JPanel createManageJobsPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        mainPanel.setBackground(new Color(235, 235, 235));

        // *** USE clientUser.getName() in welcome message ***
        JLabel header = new JLabel("Job Management  |  Welcome " + clientUser.getName(), SwingConstants.CENTER);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        mainPanel.add(header);
        mainPanel.add(Box.createVerticalStrut(10));

        // ---- Check Job Status Section ----
        JPanel checkStatusPanel = new JPanel(new GridBagLayout());
        checkStatusPanel.setBorder(BorderFactory.createTitledBorder("Check Job Status"));
        checkStatusPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Job ID Label (Column 0)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        checkStatusPanel.add(new JLabel("Job ID:"), gbc);
        
        // Job ID Field (Column 1 - Takes horizontal space)
        JTextField jobIdField = new JTextField(15);
        gbc.gridx = 1;
        gbc.weightx = 1.0; // Pushes it to take available horizontal space
        gbc.fill = GridBagConstraints.HORIZONTAL;
        checkStatusPanel.add(jobIdField, gbc);
        
        // Check Status Button (Column 2 - Fixed size)
        JButton checkBtn = new JButton("Check Status");
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        checkStatusPanel.add(checkBtn, gbc);
        
        // Status Label (Column 0, Row 1)
        gbc.gridx = 0;
        gbc.gridy = 1;
        checkStatusPanel.add(new JLabel("Status:"), gbc);
        
        // Status Field (Column 1 & 2 - Spans two columns, fixed size)
        final JTextField statusField = new JTextField();
        statusField.setEditable(false);
        statusField.setHorizontalAlignment(SwingConstants.CENTER);
        // FIX: Increased size to 300px to fit long status messages
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
        gbc.gridwidth = 1; // Span column 
        gbc.weightx = 0.10; // Use fixed size
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST; // Anchor to the left in its spanned space
        checkStatusPanel.add(statusField, gbc);
        

        
        mainPanel.add(checkStatusPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        // ---- In-Progress Jobs Section ----
        JPanel progressPanel = new JPanel(new GridBagLayout());
        progressPanel.setBorder(BorderFactory.createTitledBorder("In-Progress Jobs"));
        progressPanel.setOpaque(false);
        
        GridBagConstraints pgbc = new GridBagConstraints();
        
        pgbc.insets = new Insets(10, 10, 10, 10);
        pgbc.anchor = GridBagConstraints.NORTHWEST;

        listModel = new DefaultListModel<>();
        for (Job job : controller.getInProgressJobs()) {
            listModel.addElement(job);
        }
        JList<Job> jobList = new JList<>(listModel);
        jobList.setVisibleRowCount(5);
        JScrollPane scrollPane = new JScrollPane(jobList);
        scrollPane.setPreferredSize(new Dimension(180, 100));
        
        // List Box (Allow expansion in both directions)
        pgbc.gridx = 0;
        pgbc.gridy = 0;
        pgbc.gridheight = 3; 
        pgbc.weightx = 1.0; // Allow it to take horizontal space in its column
        pgbc.weighty = 1.0; // Allow it to take vertical space
        pgbc.fill = GridBagConstraints.BOTH; 
        progressPanel.add(scrollPane, pgbc);
        
        // Buttons are in Column 1 (Reset weight)
        pgbc.gridx = 1;
        pgbc.weightx = 0; 
        pgbc.weighty = 0; 
        pgbc.gridheight = 1;
        pgbc.fill = GridBagConstraints.HORIZONTAL; 

        // Trigger button at (1, 0)
        JButton triggerBtn = new JButton("Trigger Checkpoint");
        pgbc.gridy = 0;
        progressPanel.add(triggerBtn, pgbc);

        // Refresh button at (1, 1) - Use shorter text as planned
        JButton refreshBtn = new JButton("Refresh"); 
        pgbc.gridy = 1;
        progressPanel.add(refreshBtn, pgbc);


        // --- *** TEXT-BASED LOADING LOGIC *** ---

        final JButton calcBtn = new JButton("<html>Calculate All<br>Completion Times</html>");
        final String defaultCalcBtnText = "<html>Calculate All<br>Completion Times</html>";

        // Place calc button at (1, 2)
        pgbc.gridy = 2; 
        pgbc.insets = new Insets(10, 10, 10, 10); // Resetting inset after vertical items
        progressPanel.add(calcBtn, pgbc);

        // === Status Field for Calculation ===
        final JTextField calcStatusField = new JTextField("Click button for pending job estimates.");
        calcStatusField.setEditable(false);
        calcStatusField.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        calcStatusField.setBackground(new Color(245, 245, 245));
        calcStatusField.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Calculation Status Field (Span both columns)
        pgbc.gridx = 0;
        pgbc.gridy = 3; 
        pgbc.gridwidth = 2; 
        pgbc.weightx = 1.0;
        pgbc.insets = new Insets(20, 10, 10, 10);
        pgbc.fill = GridBagConstraints.HORIZONTAL;
        progressPanel.add(calcStatusField, pgbc);

        mainPanel.add(progressPanel);

        // ---- Button Actions for Tab 2 ----

        checkBtn.addActionListener(e -> {
            String id = jobIdField.getText().trim();
            
            // FIX: Explicitly clear the status field before running the check
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

            // The status check logic is working as intended, just need to ensure the status is set
            switch (status.toLowerCase()) {
                case "completed":
                    statusField.setBackground(new Color(33, 150, 243)); // Blue
                    break;
                case "in-progess": 
                case "in-progress":
                    statusField.setBackground(new Color(255, 152, 0)); // Orange
                    break;
                case "pending":
                case "pending(interrupted)":
                    statusField.setBackground(new Color(255, 193, 7)); // Yellow
                    statusField.setForeground(Color.BLACK); // Yellow needs black text
                    break;
                case "failed":
                    statusField.setBackground(new Color(244, 67, 54)); // Red
                    break;
                default: 
                    // This handles "Job Not Found" which returns "N/A" or similar if controller is well-designed.
                    statusField.setBackground(Color.GRAY);
                    statusField.setForeground(Color.WHITE);
            }
        });

        triggerBtn.addActionListener(e -> {
            Job selectJob = jobList.getSelectedValue();
            if (selectJob == null) return;
            controller.triggerCheckpoint(selectJob);
            JOptionPane.showMessageDialog(this, "Checkpoint triggered for " + selectJob);
        });

        refreshBtn.addActionListener(e -> {
                    refreshBtn.setText("Refreshing...");
                    refreshJobList();
                    refreshBtn.setText("Refresh"); // Use shorter text
                }
        );

        calcBtn.addActionListener(e -> {
            
            // FIX: Check if there are pending jobs before calling controller
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
                protected String doInBackground() throws Exception {
                    return controller.calculateCompletionTimes();
                }

                @Override
                protected void done() {
                    try {

                        String result = get();
                        
                        // ---Show result in a pop-up dialog ---
                        
                        // 1. Create a JTextArea to hold the multi-line string
                        JTextArea textArea = new JTextArea(result);
                        textArea.setEditable(false);
                        textArea.setRows(10);
                        textArea.setColumns(40);
                        
                        // 2. Put it in a scroll pane in case the list is long
                        JScrollPane resultScrollPane = new JScrollPane(textArea);
                        
                        // 3. Show the dialog
                        JOptionPane.showMessageDialog(ClientGUI.this, 
                                                    resultScrollPane, 
                                                    "Pending Job Estimates", 
                                                    JOptionPane.INFORMATION_MESSAGE);

                        // 4. Update the status field
                        calcStatusField.setText("Calculation complete. See dialog.");

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


        return mainPanel;
    }

    //refresh Helper
    private void refreshJobList() {
        listModel.clear();
        for (Job job : controller.getInProgressJobs()) {
            listModel.addElement(job);
        }
    }

    /**
     * Creates the "Manage Billing" panel (Tab 3)
     */
    private JPanel createBillingPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10)); // Use BorderLayout
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(220, 240, 255));

        // Panel for the buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        addBillingButton = new JButton("Add Billing Info"); // Button text set in loadBillingInfo()
        addBillingButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        addBillingButton.addActionListener(this::onAddBillingInfo);

        // --- Delete Button ---
        JButton deleteBillingButton = new JButton("Delete Info");
        deleteBillingButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        deleteBillingButton.setBackground(new Color(244, 67, 54)); 
        deleteBillingButton.setForeground(Color.BLACK);
        deleteBillingButton.addActionListener(this::onDeleteBillingInfo);

        topPanel.add(addBillingButton);
        topPanel.add(deleteBillingButton);

        panel.add(topPanel, BorderLayout.NORTH);

        // ---Table to display saved info ---
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
    
    /**
     * Creates the "Change Password" panel (Tab 4) - NEW
     */
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
        
        // --- Fields ---
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

    /**
     * Logic for changing and saving the new password. - NEW
     */
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

        // Update the User object and save it immediately
        clientUser.setPassword(newPass);
        // Uses the new persistence layer to save the updated user object with the new password
        FileBasedUserStore.saveUser(clientUser); 

        passStatusLabel.setForeground(new Color(34, 139, 34)); // Forest Green
        passStatusLabel.setText("Password successfully updated! New password is now active.");
        
        // Clear fields
        oldPassField.setText("");
        newPassField.setText("");
        confirmNewPassField.setText("");
    }


    // --- HELPER METHODS ---

    /**
     * Opens the Billing Info dialog.
     */
    private void onAddBillingInfo(ActionEvent e) {
        if (billingDialog == null) {
            billingDialog = new BillingInfoDialog(SwingUtilities.getWindowAncestor(this));
        }
        billingDialog.prefill(this.billingInfo);
        billingDialog.setVisible(true);

        // After dialog is closed, check if billing info was saved
        if (billingDialog.getSavedInfo() != null) {
            this.billingInfo = billingDialog.getSavedInfo();
            saveBillingInfo();
            loadBillingInfo();
        }
    }

    /**
     * Deletes the billing info file.
     */
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

    /**
     * Called by "Submit Job" button.
     * This is the "master" action. It submits, adds to table, and saves to file.
     */
    private void onSubmit(ActionEvent e) {
        if (this.billingInfo == null || this.billingInfo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add billing info (Tab 3) before submitting a job.", "Billing Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String clientId = clientIdField.getText().trim();
        if (clientId.isEmpty() || clientId.length() != 4) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields. Job ID must be 4 characters.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // *** DUPLICATE JOB ID CHECK ***
        if (controller.isJobInSystem(clientId)) {
            JOptionPane.showMessageDialog(this, "A job with ID '" + clientId + "' already exists in the system (pending, in-progress, or archived).", "Duplicate Job ID", JOptionPane.ERROR_MESSAGE);
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

        // Save deadline in ISO format (parsable)
        String deadlineString = deadline.toString();

        Job newJob = new Job(clientId, durationInHours, redundancy, deadline);
        controller.addJob(newJob);

        JOptionPane.showMessageDialog(this, "Job Submitted! Duration: " + durationInHours + " hours.");

        // --- Also add to list and save ---
        // 1. Add to table
        String ts = TS_FMT.format(LocalDateTime.now());
        tableModel.addRow(new Object[]{ts, clientId, durationInHours, deadlineString, redundancy});

        // 2. Save this new entry to file
        File out = new File(CSV_FILE);
        boolean writeHeader = !out.exists() || out.length() == 0;
        try (FileWriter fw = new FileWriter(out, true)) {
            if (writeHeader) {
                fw.write("timestamp,client_id,duration_hours,deadline,redundancy\n");
            }
            // Write new job
            fw.write(ts + "," + clientId + "," + durationInHours + "," + escapeCsv(deadlineString) + "," + redundancy + "\n");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save job to file: " + ex.getMessage());
        }
    }


    /**
     * Loads all jobs from the user's CSV file into the table.
     * ALSO, restores any still-pending jobs back into the VCController
     * to persist the state across application restarts.
     */
    private void loadJobsFromCSV() {
        File file = new File(CSV_FILE);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); 

            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 5) {
                    // 1. Add to table for historical view 
                    // "timestamp,client_id,duration_hours,deadline,redundancy"
                    tableModel.addRow(new Object[]{values[0], values[1], values[2], values[3], values[4]});

                    // 2. Restore job to controller if it should be active
                    try {
                        String clientId = values[1];
                        int durationInHours = Integer.parseInt(values[2]);
                        String deadlineString = values[3].replace("\"", ""); // Basic un-escape
                        LocalDateTime deadline = LocalDateTime.parse(deadlineString); // Parse ISO string
                        int redundancy = Integer.parseInt(values[4]);

                        // If deadline is in the future AND the job isn't already in the system
                        // ie: it hasn't been completed/archived
                        if (deadline.isAfter(LocalDateTime.now()) && !controller.isJobInSystem(clientId)) {
                            // Re-create the job and add it back to the pending queue
                            Job jobToRestore = new Job(clientId, durationInHours, redundancy, deadline);
                            controller.addJob(jobToRestore); // This re-adds it as "Pending"
                            System.out.println("Restored persistent job to controller: " + clientId);
                        }

                    } catch (DateTimeParseException | NumberFormatException e) {
                        System.err.println("Error parsing job from CSV on load: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading job history from " + CSV_FILE, "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads this user's billing info from their text file.
     */
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
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading billing info from " + BILLING_FILE, "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     *Saves this user's billing info to their text file.
     */
    private void saveBillingInfo() {
        clientUser.setBillingInfo(this.billingInfo); 
        try (FileWriter fw = new FileWriter(BILLING_FILE, false)) { // Overwrite
            fw.write(this.billingInfo);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving billing info file.", "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Clears all input fields. */
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

    /** Escapes commas, quotes, and newlines for CSV safety. */
    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    // ---Inner class for the Billing Info Dialog ---
    private class BillingInfoDialog extends JDialog {
        private JTextField nameField;
        private JTextField cardField;
        private JTextField cvcField;
        private JTextField expMonthField;
        private JTextField expYearField;
        private String savedInfo; // Temp variable

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

            gbc.gridx = 0;
            gbc.gridy = 0;
            add(new JLabel("Name on Card:"), gbc);
            gbc.gridx = 1;
            gbc.gridy = 0;
            add(nameField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            add(new JLabel("Card Number:"), gbc);
            gbc.gridx = 1;
            gbc.gridy = 1;
            add(cardField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            add(new JLabel("CVC:"), gbc);
            gbc.gridx = 1;
            gbc.gridy = 2;
            add(cvcField, gbc);

            JPanel expPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            expPanel.add(expMonthField);
            expPanel.add(new JLabel("/"));
            expPanel.add(expYearField);

            gbc.gridx = 0;
            gbc.gridy = 3;
            add(new JLabel("Expiry (MM/YY):"), gbc);
            gbc.gridx = 1;
            gbc.gridy = 3;
            add(expPanel, gbc);

            JButton saveButton = new JButton("Save");
            saveButton.addActionListener(this::onSaveBilling);
            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.gridwidth = 2;
            add(saveButton, gbc);
        }

        public String getSavedInfo() {
            return savedInfo;
        }

        public void prefill(String info) {
            // Pre-fill fields if info already exists
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
                // Clear fields if no info
                nameField.setText("");
                cardField.setText("");
                cvcField.setText("");
                expMonthField.setText("");
                expYearField.setText("");
            }
            this.savedInfo = null; // Reset saved info
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

            // Save the billing info in a simple format
            this.savedInfo = name + "|" + card + "|" + cvc + "|" + expMonth + "|" + expYear;
            
            JOptionPane.showMessageDialog(this, "Billing Info Saved!");
            dispose(); // Close the dialog
        }
    }


    // --- DocumentFilter Inner Classes ---

    /**
     * Allows only letters/numbers up to a max length.
     */
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
            if (string.matches("[a-zA-Z0-9]+")) {
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
    
    /**
     * Allows only letters, spaces, and hyphens.
     */
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
            if (text != null && text.matches("[a-zA-Z -]*")) { // Allow empty string
                super.replace(fb, offset, length, text, attrs);
            } else if (text != null && !text.isEmpty()) {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }
    
    /**
     * Allows only digits up to a max length.
     */
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
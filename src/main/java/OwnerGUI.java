// OwnerGUI.java (FIXED: Removed validation requiring Owner ID to match logged-in User ID)
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
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
 * OwnerGUI is a JPanel that allows vehicle owners to manage their vehicles and their password.
 */
public class OwnerGUI extends JPanel {

    private final Owner ownerUser; // Stored the rich object
    private final Runnable onLogout;

    // --- TAB 1 (Register) FIELDS ---
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

    // --- TAB 2 (Payment) FIELDS ---
    private String paymentInfo; 
    private JButton addPaymentButton; 
    private JTable paymentTable;
    private DefaultTableModel paymentTableModel;
    private PaymentInfoDialog paymentDialog;
    
    // --- TAB 3 (Password) FIELDS --- 
    private JPasswordField oldPassField;
    private JPasswordField newPassField;
    private JPasswordField confirmNewPassField;
    private JLabel passStatusLabel;

    private DefaultTableModel tableModel;
    private JTable table;

    private VCController controller;

    // --- FILENAMES ---
    private final String CSV_FILE; 
    private final String PAYMENT_FILE;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] STATES = {"", "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN",
            "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH",
            "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"};

    public OwnerGUI(Owner ownerUser, Runnable onLogout, VCController controller) { 
        this.ownerUser = ownerUser; 
        this.onLogout = onLogout;
        this.controller = controller;
        
        // Initialize local copy from Owner object
        this.paymentInfo = ownerUser.getPaymentInfo();

        this.CSV_FILE = ownerUser.getUserID() + "_vehicle_entries.csv"; 
        this.PAYMENT_FILE = ownerUser.getUserID() + "_payment.dat";

        this.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Register Vehicle", createRegisterVehiclePanel());
        tabs.addTab("Manage Payment", createPaymentPanel());
        tabs.addTab("Change Password", createPasswordPanel()); // ADDED PASSWORD TAB

        this.add(tabs, BorderLayout.CENTER);

        loadVehiclesFromCSV();
        loadPaymentInfo();
    }

    /**
     * Creates the "Register Vehicle" panel (Tab 1)
     */
    private JPanel createRegisterVehiclePanel() {
        JPanel rootPanel = new JPanel();
        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        rootPanel.setBackground(new Color(187, 213, 237));

        // Use ownerUser.getName()
        JLabel header = new JLabel("Vehicle Registration  |  Welcome " + ownerUser.getName(), SwingConstants.CENTER);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        rootPanel.add(header);
        rootPanel.add(Box.createVerticalStrut(10));

        // form setup (original code structure)
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // --- Initialize Fields ---
        ownerIdField = new JTextField();
        // The filter is kept to enforce the 4-character limit
        ((AbstractDocument) ownerIdField.getDocument()).setDocumentFilter(new AlphanumericFilter(4));
        
        // FIX: Ensure the field starts empty if it is editable
        ownerIdField.setText("");
        // FIX: The field is now editable by default, as the setEditable(false) line was removed.

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

        // --- Initialize Departure Spinners ---
        departureMonthSpinner = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        departureDaySpinner = new JSpinner(new SpinnerNumberModel(now.getDayOfMonth(), 1, 31, 1));
        departureYearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), now.getYear(), now.getYear() + 5, 1));
        departureHourSpinner = new JSpinner(new SpinnerNumberModel(now.getHour(), 0, 23, 1));

        int r = 0; 

        gc.gridx = 0;
        gc.gridy = r;
        // FIX: Removed "(Your Login ID)" text
        form.add(new JLabel("Owner ID (4 Chars):"), gc); 
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(ownerIdField, gc);

        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("Vehicle Make:"), gc);
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(makeField, gc);

        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("Vehicle Model:"), gc);
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(modelField, gc);

        JPanel yearPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        yearPanel.setOpaque(false);
        yearPanel.add(yearSpinner);
        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("Vehicle Year:"), gc);
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(yearPanel, gc);

        JPanel licensePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        licensePanel.setOpaque(false);

        // License Plate row (Simplified layout logic retained from last change for alignment)
        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("License Plate:"), gc);

        gc.gridx = 1;
        gc.gridy = r;
        form.add(licenseField, gc);

        // State field in next column (aligned row)
        gc.gridx = 2;
        gc.gridy = r;
        form.add(new JLabel("State:"), gc);

        gc.gridx = 3;
        gc.gridy = r++;
        form.add(stateComboBox, gc);


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

        gc.gridx = 0;
        gc.gridy = r;
        form.add(new JLabel("Departure Time:"), gc);
        gc.gridx = 1;
        gc.gridy = r++;
        form.add(departurePanel, gc);

        // --- BUTTONS (Simplified) ---
        JButton registerButton = new JButton("Register Vehicle");
        JButton clearButton = new JButton("Clear form");
        JButton logoutButton = new JButton("Logout");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        buttons.setOpaque(false);
        buttons.add(registerButton);
        buttons.add(clearButton);
        buttons.add(logoutButton);

        gc.gridx = 0;
        gc.gridy = r;
        gc.gridwidth = 4; // Spanning more columns due to the license/state layout change
        form.add(buttons, gc);

        rootPanel.add(form);
        rootPanel.add(Box.createVerticalStrut(12));

        // ---Table Columns ---
        tableModel = new DefaultTableModel(new Object[]{
                "Timestamp", "Owner ID", "Make", "Model", "Year", "License", "State", "Departure"
        }, 0);
        table = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(760, 260));
        rootPanel.add(tableScroll);

        // behavior
        registerButton.addActionListener(this::onRegister);
        clearButton.addActionListener(e -> clearForm());
        logoutButton.addActionListener(e -> onLogout.run());
        
        return rootPanel;
    }

    /**
     * Creates the "Manage Payment" panel (Tab 2)
     */
    private JPanel createPaymentPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(187, 213, 237));

        // Panel for the button
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        addPaymentButton = new JButton("Add Payment Info"); 
        addPaymentButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        addPaymentButton.addActionListener(this::onAddPaymentInfo);

        // --- NEW: Delete Button ---
        JButton deletePaymentButton = new JButton("Delete Info");
        deletePaymentButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        deletePaymentButton.setBackground(new Color(244, 67, 54));
        deletePaymentButton.setForeground(Color.BLACK);
        deletePaymentButton.addActionListener(this::onDeletePaymentInfo);

        topPanel.add(addPaymentButton);
        topPanel.add(deletePaymentButton);

        panel.add(topPanel, BorderLayout.NORTH);

        //Table to display saved info ---
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

    /**
     * Creates the "Change Password" panel (Tab 3)
     */
    private JPanel createPasswordPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        panel.setBackground(new Color(187, 213, 237));
        
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
     * Logic for changing and saving the new password.
     */
    private void onChangePassword(ActionEvent e) {
        String currentPass = new String(oldPassField.getPassword());
        String newPass = new String(newPassField.getPassword());
        String confirmPass = new String(confirmNewPassField.getPassword());

        // 1. Check if the current password matches the one stored in the User object
        if (!currentPass.equals(ownerUser.getPassword())) {
            passStatusLabel.setForeground(Color.RED);
            passStatusLabel.setText("Error: Current Password is incorrect.");
            return;
        }

        // 2. Validate new password
        if (newPass.length() < 6) {
            passStatusLabel.setForeground(Color.RED);
            passStatusLabel.setText("Error: New password must be at least 6 characters long.");
            return;
        }

        // 3. Check confirmation
        if (!newPass.equals(confirmPass)) {
            passStatusLabel.setForeground(Color.RED);
            passStatusLabel.setText("Error: New Password and Confirmation do not match.");
            return;
        }

        // 4. Success: Update User object and save
        ownerUser.setPassword(newPass);
        // NOTE: Owner is the subclass of User, so FileBasedUserStore must be called with the subclass object.
        FileBasedUserStore.saveUser(ownerUser); 

        passStatusLabel.setForeground(new Color(34, 139, 34)); // Forest Green
        passStatusLabel.setText("Password successfully updated! New password is now active.");
        
        // Clear fields
        oldPassField.setText("");
        newPassField.setText("");
        confirmNewPassField.setText("");
    }


    /**
     * Opens the Payment Info dialog.
     */
    private void onAddPaymentInfo(ActionEvent e) {
        if (paymentDialog == null) {
            paymentDialog = new PaymentInfoDialog(SwingUtilities.getWindowAncestor(this));
        }
        paymentDialog.prefill(this.paymentInfo);
        paymentDialog.setVisible(true);

        // After dialog is closed, check if payment info was saved
        if (paymentDialog.getSavedInfo() != null) {
            this.paymentInfo = paymentDialog.getSavedInfo();
            savePaymentInfo();
            loadPaymentInfo();
        }
    }

    /**
     * Deletes the payment info file.
     */
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

    /**
     * Called by "Register Vehicle" button.
     * It registers, adds to table, and saves to file.
     */
    private void onRegister(ActionEvent e) {
        if (this.paymentInfo == null || this.paymentInfo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add payment info (Tab 2) before registering a vehicle.", "Payment Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String ownerId = ownerIdField.getText().trim();
        String make = makeField.getText().trim();
        String model = modelField.getText().trim();
        int year = (Integer) yearSpinner.getValue();
        String license = licenseField.getText().trim();
        String state = String.valueOf(stateComboBox.getSelectedItem());

        // --- FIX APPLIED HERE: REMOVE THE VALIDATION CHECK ---
        // The previous code block requiring: ownerId.equals(ownerUser.getUserID()) is gone.
        // This allows any valid 4-char Owner ID to be used for registration.
        
        if (ownerId.isEmpty() || make.isEmpty() || model.isEmpty() || license.isEmpty() || state.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all vehicle fields, including state and Owner ID.");
            return;
        }

       
        // check the controller's "real state" lists
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

        // save departureTime in a parsable format. 
        String departureString = departureTime.toString();
        
        Vehicle newVehicle = new Vehicle(ownerId, make, model, year, license, state, departureTime);
        controller.recruitVehicle(newVehicle);

        JOptionPane.showMessageDialog(this, "Vehicle Registered: " + year + " " + make + " " + model);

        // Add to table and save to file ---
        // 1. Add to table
        String ts = TS_FMT.format(LocalDateTime.now());
        tableModel.addRow(new Object[]{
                ts, ownerId, make, model, year, license, state, departureString
        });

        // 2. Save this new entry to file
        File out = new File(CSV_FILE);
        boolean writeHeader = !out.exists() || out.length() == 0;
        try (FileWriter fw = new FileWriter(out, true)) {
            if (writeHeader) {
                fw.write("timestamp,owner_id,make,model,year,license,state,departure_time\n");
            }
            // Save the parsable departureString
            fw.write(ts + "," + ownerId + "," + make + "," + model + "," + year + "," + license + "," + state + "," + departureString + "\n");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save file  " + ex.getMessage());
        }
    }

    /**
     * Loads all vehicles from the user's CSV file into the table.
     * ALSO, restores any still-active vehicles back into the VCController
     * to persist the state across application restarts.
     */
    private void loadVehiclesFromCSV() {
        File file = new File(CSV_FILE);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 8) {
                    // 1. Add to table for historical view
                    // Columns: "timestamp,owner_id,make,model,year,license,state,departure_time"
                    tableModel.addRow(new Object[]{values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7]});

                    // 2.Restore vehicle to controller if it's still active
                    try {
                        String ownerId = values[1];
                        String make = values[2];
                        String model = values[3];
                        int year = Integer.parseInt(values[4]);
                        String license = values[5];
                        String state = values[6];
                        LocalDateTime departureTime = LocalDateTime.parse(values[7]); // Parse the stored ISO-8601 time

                        // If the vehicle's departure time is still in the future,
                        // it should be active in the system.
                        if (departureTime.isAfter(LocalDateTime.now())) {
                            
                            // We check the controller *first* to avoid double-adding
                            // if this method were ever called twice.
                            if (!controller.isVehicleInSystem(license, state)) {
                                Vehicle vehicleToRestore = new Vehicle(ownerId, make, model, year, license, state, departureTime);
                                controller.recruitVehicle(vehicleToRestore);
                                System.out.println("Restored persistent vehicle to controller: " + license);
                            }
                        }

                    } catch (NumberFormatException | DateTimeParseException e) {
                        System.err.println("Error parsing vehicle from CSV on load: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading vehicle history from " + CSV_FILE, "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads this user's payment info from their text file.
     */
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
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading payment info from " + PAYMENT_FILE, "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Saves this user's payment info to their text file.
     */
    private void savePaymentInfo() {
        ownerUser.setPaymentInfo(this.paymentInfo); 
        try (FileWriter fw = new FileWriter(PAYMENT_FILE, false)) { // Overwrite
            fw.write(this.paymentInfo);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving payment info file.", "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearForm() {
        // Now clears the field since it is editable
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

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    // ---Inner class for the Payment Info Dialog ---
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
            saveButton.addActionListener(this::onSavePayment);
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

            // Save the payment info in a simple format
            this.savedInfo = name + "|" + card + "|" + cvc + "|" + expMonth + "|" + expYear;

            JOptionPane.showMessageDialog(this, "Payment Info Saved!");
            dispose();
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
     * *Allows only 7 alphanumeric chars, and forces uppercase.
     */
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

            if (upperText.matches("[A-Z0-9]*")) { // Allow empty string for deletion
                super.replace(fb, offset, length, upperText, attrs);
            } else {
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
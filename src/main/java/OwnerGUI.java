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
import java.util.HashSet;
import java.util.Set;

/**
 * OwnerGUI is a JPanel that allows vehicle owners to manage their vehicles.
 */
public class OwnerGUI extends JPanel { 

    private final String ownerName; // This is the logged-in user, e.g., "owner"
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
    private JButton addPaymentButton; // Button to open dialog
    private JTable paymentTable;
    private DefaultTableModel paymentTableModel;
    private PaymentInfoDialog paymentDialog;

    private DefaultTableModel tableModel;
    private JTable table;

    //
    private VCController controller;

    // --- FILENAMES ---
    private final String CSV_FILE; // File is now user-specific
    private final String PAYMENT_FILE; // File for this user's payment info
    
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // --- NEW: List of states ---
    private static final String[] STATES = {"", "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", 
        "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", 
        "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", 
        "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"};

    public OwnerGUI(String ownerName, Runnable onLogout,VCController controller) {
        this.ownerName = ownerName;
        this.onLogout = onLogout;
        this.paymentInfo = ""; 
        
        this.CSV_FILE = ownerName + "_vehicle_entries.csv";
        this.PAYMENT_FILE = ownerName + "_payment.dat";

        this.setLayout(new BorderLayout()); 

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Register Vehicle", createRegisterVehiclePanel());
        tabs.addTab("Manage Payment", createPaymentPanel());

        this.add(tabs, BorderLayout.CENTER);

        this.controller = controller;
        
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

        JLabel header = new JLabel("Vehicle Registration  |  Welcome " + ownerName, SwingConstants.CENTER);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        rootPanel.add(header);
        rootPanel.add(Box.createVerticalStrut(10));
        
        // form
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // --- Initialize Fields ---
        ownerIdField = new JTextField();
        ((AbstractDocument) ownerIdField.getDocument()).setDocumentFilter(new AlphanumericFilter(4));
        
        makeField = new JTextField();
        ((AbstractDocument) makeField.getDocument()).setDocumentFilter(new LettersOnlyFilter());
        
        modelField = new JTextField();
        ((AbstractDocument) modelField.getDocument()).setDocumentFilter(new LettersOnlyFilter());

        LocalDateTime now = LocalDateTime.now();
        yearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), 1980, now.getYear(), 1));
        yearSpinner.setPreferredSize(new Dimension(100, 28)); // Fix size

        licenseField = new JTextField();
        ((AbstractDocument) licenseField.getDocument()).setDocumentFilter(new LicensePlateFilter());
        licenseField.setPreferredSize(new Dimension(100, 28)); // Fix size
        
        stateComboBox = new JComboBox<>(STATES); 

        // --- Initialize Departure Spinners ---
        departureMonthSpinner = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        departureDaySpinner = new JSpinner(new SpinnerNumberModel(now.getDayOfMonth(), 1, 31, 1));
        departureYearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), now.getYear(), now.getYear() + 5, 1));
        departureHourSpinner = new JSpinner(new SpinnerNumberModel(now.getHour(), 0, 23, 1));

        int r = 0; // Row counter

        gc.gridx = 0; gc.gridy = r; form.add(new JLabel("Owner ID (4 Chars):"), gc);
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
        
        JPanel licensePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        licensePanel.setOpaque(false);
        JPanel licenseWrap = new JPanel(new BorderLayout());
        licenseWrap.setOpaque(false);
        licenseWrap.add(licenseField, BorderLayout.CENTER);
        
        licensePanel.add(licenseWrap);
        licensePanel.add(new JLabel("State:"));
        licensePanel.add(stateComboBox);
        
        gc.gridx = 0; gc.gridy = r; form.add(new JLabel("License Plate:"), gc);
        gc.gridx = 1; gc.gridy = r++; form.add(licensePanel, gc);


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

        // --- BUTTONS (Simplified) ---
        JButton registerButton = new JButton("Register Vehicle"); 
        JButton clearButton = new JButton("Clear form");
        JButton logoutButton = new JButton("Logout"); 

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5)); 
        buttons.setOpaque(false);
        buttons.add(registerButton); 
        buttons.add(clearButton);
        buttons.add(logoutButton);

        gc.gridx = 0; gc.gridy = r; gc.gridwidth = 2;
        form.add(buttons, gc);

        rootPanel.add(form); 
        rootPanel.add(Box.createVerticalStrut(12));

        // --- UPDATED: Table Columns ---
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
     * NEW: Creates the "Manage Payment" panel (Tab 2)
     */
    private JPanel createPaymentPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10)); // Use BorderLayout
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(187, 213, 237));
        
        // Panel for the button
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        addPaymentButton = new JButton("Add Payment Info"); // Text set in loadPaymentInfo()
        addPaymentButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        addPaymentButton.addActionListener(this::onAddPaymentInfo);
        
        // --- NEW: Delete Button ---
        JButton deletePaymentButton = new JButton("Delete Info");
        deletePaymentButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        deletePaymentButton.setBackground(new Color(244, 67, 54)); // Red
        deletePaymentButton.setForeground(Color.BLACK);
        deletePaymentButton.addActionListener(this::onDeletePaymentInfo);
        
        topPanel.add(addPaymentButton);
        topPanel.add(deletePaymentButton);
        
        panel.add(topPanel, BorderLayout.NORTH);

        // --- NEW: Table to display saved info ---
        paymentTableModel = new DefaultTableModel(new Object[]{"Field", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table non-editable
            }
        };
        paymentTable = new JTable(paymentTableModel);
        paymentTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
        paymentTable.setRowHeight(20);
        paymentTable.setTableHeader(null); // Hide header
        
        JScrollPane scrollPane = new JScrollPane(paymentTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
    
    /**
     * NEW: Opens the Payment Info dialog.
     */
    private void onAddPaymentInfo(ActionEvent e) {
        if (paymentDialog == null) {
            paymentDialog = new PaymentInfoDialog(SwingUtilities.getWindowAncestor(this));
        }
        paymentDialog.prefill(this.paymentInfo); // Prefill with current data
        paymentDialog.setVisible(true); // This will block until the dialog is closed

        // After dialog is closed, check if payment info was saved
        if (paymentDialog.getSavedInfo() != null) {
            this.paymentInfo = paymentDialog.getSavedInfo();
            savePaymentInfo(); // Save to file
            loadPaymentInfo(); // Reload tab
        }
    }
    
    /**
     * NEW: Deletes the payment info file.
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
            savePaymentInfo(); // Save empty string (which deletes file content)
            loadPaymentInfo(); // Refresh tab
            JOptionPane.showMessageDialog(this, "Payment Info Deleted.");
        }
    }

    /**
     * NEW: Called by "Register Vehicle" button.
     * This is the "master" action. It registers, adds to table, and saves to file.
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

        if (ownerId.isEmpty() || make.isEmpty() || model.isEmpty() || license.isEmpty() || state.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all vehicle fields, including state.");
            return;
        }
        
        // --- LOGIC FIX: Check FILE for duplicates ---
        String vehicleSignature = license + state;
        if (getSavedVehicleSignatures().contains(vehicleSignature)) {
            JOptionPane.showMessageDialog(this, "This vehicle (License + State) is already registered in the system.", "Duplicate Error", JOptionPane.ERROR_MESSAGE);
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
        
        // --- THIS IS WHERE YOUR NEW LOGIC GOES ---
        // Vehicle newVehicle = new Vehicle(ownerId, make, model, year, license, state, departureTime);
        // controller.recruitVehicle(newVehicle, this.paymentInfo);
        
        JOptionPane.showMessageDialog(this, "Vehicle Registered: " + year + " " + make + " " + model);
        
        // --- NEW: Add to table and save to file ---
        // 1. Add to table
        String ts = TS_FMT.format(LocalDateTime.now());
        tableModel.addRow(new Object[]{
            ts, ownerId, make, model, year, license, state, departureTime.toString()
        });

        // 2. Save this new entry to file
        File out = new File(CSV_FILE);
        boolean writeHeader = !out.exists() || out.length() == 0;
        try (FileWriter fw = new FileWriter(out, true)) { // Append
            if (writeHeader) {
                fw.write("timestamp,owner_id,make,model,year,license,state,departure_time\n");
            }
            fw.write(ts + "," + ownerId + "," + make + "," + model + "," + year + "," + license + "," + state + "," + departureTime + "\n");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save file  " + ex.getMessage());
        }
    }

    /** Methods removed: onAdd, onSave, isVehicleInTable **/
    
    /**
     * NEW: Reads all rows from file to check for duplicates.
     */
    private Set<String> getSavedVehicleSignatures() {
        Set<String> savedVehicles = new HashSet<>();
        File file = new File(CSV_FILE);
        if (!file.exists()) {
            return savedVehicles;
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                // A "signature" is what makes a vehicle unique: License + State
                String[] values = line.split(",");
                if (values.length >= 7) {
                    savedVehicles.add(values[5] + values[6]); // e.g., "ABC1234CA"
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return savedVehicles;
    }
    
    /**
     * NEW: Loads all vehicles from the user's CSV file into the table.
     */
    private void loadVehiclesFromCSV() {
        File file = new File(CSV_FILE);
        if (!file.exists()) {
            return; // No file to load
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); // Skip header
            
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 8) {
                    // "timestamp,owner_id,make,model,year,license,state,departure_time"
                    tableModel.addRow(new Object[]{values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7]});
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading vehicle history from " + CSV_FILE, "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * NEW: Loads this user's payment info from their text file.
     */
    private void loadPaymentInfo() {
        File file = new File(PAYMENT_FILE);
        paymentTableModel.setRowCount(0); // Clear table
        
        if (!file.exists()) {
            addPaymentButton.setText("Add Payment Info");
            this.paymentInfo = "";
            return;
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String loadedInfo = br.readLine();
            if (loadedInfo != null && !loadedInfo.isEmpty()) {
                this.paymentInfo = loadedInfo;
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
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading payment info from " + PAYMENT_FILE, "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * NEW: Saves this user's payment info to their text file.
     */
    private void savePaymentInfo() {
        try (FileWriter fw = new FileWriter(PAYMENT_FILE, false)) { // Overwrite
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
        stateComboBox.setSelectedIndex(0); // Reset state

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

    // --- NEW: Inner class for the Payment Info Dialog ---
    private class PaymentInfoDialog extends JDialog {
        private JTextField nameField;
        private JTextField cardField;
        private JTextField cvcField;
        private JTextField expMonthField;
        private JTextField expYearField;
        private String savedInfo; // Temp variable

        PaymentInfoDialog(Window owner) {
            super(owner, "Add/Edit Payment Info", Dialog.ModalityType.APPLICATION_MODAL);
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
            dispose(); // Close the dialog
        }
    }

    // --- DocumentFilter Inner Classes ---

    /** Allows only letters/numbers up to a max length. */
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
    
    /** NEW: Allows only letters, spaces, and hyphens. */
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
    
    /** * NEW: Allows only 7 alphanumeric chars, and forces uppercase. 
     */
    private static class LicensePlateFilter extends DocumentFilter {
        private static final int MAX_LENGTH = 7;

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string == null) return;
            
            String upperString = string.toUpperCase();
            if (fb.getDocument().getLength() + upperString.length() > MAX_LENGTH) {
                Toolkit.getDefaultToolkit().beep(); // Beep if over length
                return;
            }
            
            if (upperString.matches("[A-Z0-9]+")) {
                super.insertString(fb, offset, upperString, attr);
            } else {
                Toolkit.getDefaultToolkit().beep(); // Beep if invalid char
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) return;

            String upperText = text.toUpperCase();
            if (fb.getDocument().getLength() - length + upperText.length() > MAX_LENGTH) {
                Toolkit.getDefaultToolkit().beep(); // Beep if over length
                return;
            }

            if (upperText.matches("[A-Z0-9]*")) { // Allow empty string for deletion
                super.replace(fb, offset, length, upperText, attrs);
            } else {
                Toolkit.getDefaultToolkit().beep(); // Beep if invalid char
            }
        }
    }
    
    /** NEW: Allows only digits up to a max length. */
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
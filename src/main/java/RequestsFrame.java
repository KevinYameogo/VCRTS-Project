import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A dedicated frame to view and manage pending requests.
 * Displays Job Requests and Vehicle Requests in separate tabs with detailed information.
 */
public class RequestsFrame extends JFrame {

    private Server server;
    private VCControllerGUI parentGUI;
    
    private DefaultTableModel jobTableModel;
    private DefaultTableModel vehicleTableModel;
    private JTable jobTable;
    private JTable vehicleTable;
    
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RequestsFrame(Server server, VCControllerGUI parentGUI) {
        this.server = server;
        this.parentGUI = parentGUI;

        setTitle("Pending Requests");
        setSize(950, 550); 
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(245, 245, 245)); // Match VCControllerGUI

        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBackground(new Color(245, 245, 245)); 
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel titleLabel = new JLabel("Pending Requests");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(33, 37, 41)); // Darker Grey
        headerPanel.add(titleLabel);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabbedPane.setBackground(new Color(245, 245, 245));
        
        tabbedPane.addTab("Job Requests", createJobRequestsPanel());
        tabbedPane.addTab("Vehicle Requests", createVehicleRequestsPanel());
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Footer with Refresh
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footerPanel.setBackground(new Color(245, 245, 245));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JButton refreshBtn = new JButton("Refresh All");
        styleFooterButton(refreshBtn);
        refreshBtn.addActionListener(e -> refreshAllRequests());
        footerPanel.add(refreshBtn);
        
        JButton closeBtn = new JButton("Close");
        styleFooterButton(closeBtn);
        closeBtn.addActionListener(e -> dispose());
        footerPanel.add(closeBtn);
        
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);
        
        // Initial Load
        refreshAllRequests();
    }

    private JPanel createJobRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Columns: Timestamp, Client ID, Job ID, Status, Duration, Deadline, Redundancy, Actions
        String[] columns = {"Request ID", "Timestamp", "Client ID", "Job ID", "Status", "Duration (Hrs)", "Deadline", "Redundancy", "Actions"};
        
        jobTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 8; 
            }
        };
        
        jobTable = new JTable(jobTableModel);
        styleTable(jobTable);
        
        // Set Action Column Renderer/Editor
        jobTable.getColumn("Actions").setCellRenderer(new ActionButtonRenderer());
        jobTable.getColumn("Actions").setCellEditor(new ActionButtonEditor(new JCheckBox()));
        jobTable.getColumn("Actions").setMinWidth(180);

        JScrollPane scrollPane = new JScrollPane(jobTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createVehicleRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Columns: Timestamp, Owner ID, License, State, Make, Model, Year, Departure, Actions
        String[] columns = {"Request ID", "Timestamp", "Owner ID", "License", "State", "Make", "Model", "Year", "Departure", "Actions"};
        
        vehicleTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 9;
            }
        };
        
        vehicleTable = new JTable(vehicleTableModel);
        styleTable(vehicleTable);
        
        // Set Action Column Renderer/Editor
        vehicleTable.getColumn("Actions").setCellRenderer(new ActionButtonRenderer());
        vehicleTable.getColumn("Actions").setCellEditor(new ActionButtonEditor(new JCheckBox()));
        vehicleTable.getColumn("Actions").setMinWidth(180);

        JScrollPane scrollPane = new JScrollPane(vehicleTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private void styleTable(JTable table) {
        table.setRowHeight(50);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(248,248,255)); // Light Blue Header
        table.getTableHeader().setForeground(new Color(50, 50, 50));
        table.setSelectionBackground(new Color(232, 240, 254));
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(new Color(220, 220, 220));
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
    }

    private void styleFooterButton(JButton btn) {
        if (btn.getText().equals("Refresh All")) {
            btn.setBackground(new Color(27, 129, 168)); // Purple (Matches Accept)
            btn.setForeground(Color.WHITE);
            btn.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
            btn.setOpaque(true);
            btn.setBorderPainted(false);
        } else {
            btn.setBackground(Color.WHITE);
            btn.setForeground(Color.BLACK);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200)),
                    BorderFactory.createEmptyBorder(8, 15, 8, 15)
            ));
            btn.setOpaque(true);
        }
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleActionButton(JButton btn, Color bgColor, Color fgColor) {
        btn.setBackground(bgColor);
        btn.setForeground(fgColor);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
    }

    public void refreshAllRequests() {
        server.reloadState();
        List<Request> requests = server.getPendingRequests();
        
        jobTableModel.setRowCount(0);
        vehicleTableModel.setRowCount(0);
        
        for (Request req : requests) {
            if (req.getRequestType().equals("JOB_SUBMISSION") && req.getData() instanceof Job) {
                Job job = (Job) req.getData();
                
                // Get correct Client ID
                String clientID = job.getClientEnteredID();
                if (clientID == null || clientID.equals("UNKNOWN")) clientID = req.getSenderID(); // Fallback
                
                String status = job.getStatus();
                if (status == null || status.equalsIgnoreCase("Pending")) {
                    status = "Awaiting Approval";
                }

                jobTableModel.addRow(new Object[]{
                    req.getRequestID(),
                    req.getTimestamp().format(TS_FMT),
                    clientID,
                    job.getJobID(),
                    status,
                    job.getDuration(),
                    job.getDeadline().toString(),
                    job.getRedundancyLevel(),
                    req.getRequestID() // ID for actions
                });
            } else if (req.getRequestType().equals("VEHICLE_REGISTRATION") && req.getData() instanceof Vehicle) {
                Vehicle vehicle = (Vehicle) req.getData();
                
                // Get correct Owner ID
                String ownerID = vehicle.getOwnerEnteredID();
                if (ownerID == null || ownerID.equals("UNKNOWN")) ownerID = req.getSenderID(); // Fallback
                
                vehicleTableModel.addRow(new Object[]{
                    req.getRequestID(),
                    req.getTimestamp().format(TS_FMT),
                    ownerID,
                    vehicle.getVehicleID(),
                    vehicle.getLicenseState(),
                    vehicle.getMake(),
                    vehicle.getModel(),
                    vehicle.getYear(),
                    vehicle.getDepartureSchedule().toString(),
                    req.getRequestID() // ID for actions
                });
            }
        }
    }

    // --- Action Buttons (Accept/Reject) ---

    class ActionButtonRenderer extends JPanel implements TableCellRenderer {
        private JButton acceptBtn;
        private JButton rejectBtn;

        public ActionButtonRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 10, 8)); // Centered with gap
            setOpaque(true);
            setBackground(Color.WHITE);

            acceptBtn = new JButton("Accept");
            // Pretty Purple
            styleActionButton(acceptBtn, new Color(27, 129, 168), Color.WHITE); 

            rejectBtn = new JButton("Reject");
            // Pretty Orange/Pink
            styleActionButton(rejectBtn, new Color(197, 134, 192), Color.WHITE); 

            add(acceptBtn);
            add(rejectBtn);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(Color.WHITE);
            }
            return this;
        }
    }

    class ActionButtonEditor extends DefaultCellEditor {
        private JPanel panel;
        private JButton acceptBtn;
        private JButton rejectBtn;
        private String requestID;

        public ActionButtonEditor(JCheckBox checkBox) {
            super(checkBox);

            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8)); // Centered with gap
            panel.setOpaque(true);
            panel.setBackground(Color.WHITE);

            acceptBtn = new JButton("Accept");
            styleActionButton(acceptBtn, new Color(108, 92, 231), Color.WHITE);

            rejectBtn = new JButton("Reject");
            styleActionButton(rejectBtn, new Color(255, 118, 117), Color.WHITE);

            acceptBtn.addActionListener(e -> {
                fireEditingStopped();
                parentGUI.handleAccept(requestID);
                refreshAllRequests(); 
            });

            rejectBtn.addActionListener(e -> {
                fireEditingStopped();
                parentGUI.handleReject(requestID);
                refreshAllRequests(); 
            });

            panel.add(acceptBtn);
            panel.add(rejectBtn);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            this.requestID = (String) value;
            panel.setBackground(table.getSelectionBackground());
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return requestID;
        }
    }
}

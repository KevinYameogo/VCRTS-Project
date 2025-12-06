import javax.swing.SwingUtilities;

public class Main {
    
    public static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        // Initialize the single Server instance
        Server storageServer = new Server();
        
        // Initialize the VC Controller
        VCController controller = new VCController(storageServer);

        // Start the Network Server for socket communication (Notifications, Checkpoints)
        // This runs on a separate thread
        new Thread(new NetworkServer(SERVER_PORT, controller, storageServer)).start();

        // Launch the GUI
        SwingUtilities.invokeLater(() -> {
            new LandingPage(controller).setVisible(true);
        });
    }
}
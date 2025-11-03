import javax.swing.SwingUtilities;

public class Main {
    
    public static final int SERVER_PORT = 12345; 

    public static void main(String[] args) {
        // 1. Create the core components
        Server storageServer = new Server("Server-01", "127.0.0.1"); 
        VCController controller = new VCController(storageServer);
        
        // 2. Create and start the Network Server
        // This will listen for Checkpoints from vehicles
        NetworkServer networkServer = new NetworkServer(SERVER_PORT, controller);
        Thread serverThread = new Thread(networkServer);
        serverThread.start(); 

        // 3. Create the GUI (this runs on its own thread)
        SwingUtilities.invokeLater(() -> {
            new LandingPage(controller).setVisible(true);
        });
    }
}
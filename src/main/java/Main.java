import javax.swing.SwingUtilities;

// Make sure to import your other classes
// import com.yourpackage.LandingPage;
// import com.yourpackage.VCController;
// import com.yourpackage.Server;

public class Main {
    
    public static void main(String[] args) {
        // 1. Create the core components in the correct order
        Server server = new Server(); 
        VCController controller = new VCController(server);
        
        // 2. Create the GUI and "inject" the controller into it
        SwingUtilities.invokeLater(() -> {
            // We pass the one and only controller to the LandingPage
            new LandingPage(controller).setVisible(true);
        });
    }
}
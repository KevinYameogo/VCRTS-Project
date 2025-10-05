
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        // Set a consistent look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // Ignore, proceed with default
        }

        // Launch the LandingPage GUI
        SwingUtilities.invokeLater(() -> new LandingPage());
    }
}


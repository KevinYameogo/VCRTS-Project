import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Dedicated server for handling persistent, two-way notification connections from GUIs.
 * This runs concurrently with the NetworkServer that handles Checkpoints.
 */
public class NetworkNotificationServer implements Runnable {

    private int port;
    private Server server;
    private boolean isRunning;

    // This port matches(should match) the NOTIFICATION_PORT used in ClientGUI/OwnerGUI 
    public NetworkNotificationServer(int port, Server server) {
        this.port = port;
        this.server = server;
        this.isRunning = true;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Notification Server started on port: " + port);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Each client connection is handled by a dedicated thread for continuous listening
                    NotificationHandler handler = new NotificationHandler(clientSocket, server);
                    new Thread(handler).start();

                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Error accepting notification client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start Notification Server on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        this.isRunning = false;
    }
}

/**
 * Handles a single persistent notification socket connection.
 * Object streams are created in the correct order (OOS first, then OIS)
 */
class NotificationHandler implements Runnable {

    private Socket clientSocket;
    private Server server;
    private String userID;

    public NotificationHandler(Socket socket, Server server) {
        this.clientSocket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;

        try {
            // Create ObjectOutputStream FIRST, flush header, THEN ObjectInputStream.
            oos = new ObjectOutputStream(clientSocket.getOutputStream());
            oos.flush(); 
            ois = new ObjectInputStream(clientSocket.getInputStream());

            // STEP 1: Receive UserID for registration
            Object receivedID = ois.readObject();
            if (!(receivedID instanceof String)) {
                return;
            }
            this.userID = (String) receivedID;
            System.out.println("Notification Handler: Received login from " + userID);

            // STEP 2: Register the output stream for pushing messages
            server.registerNotificationClient(userID, oos);

            // STEP 3: Keep the thread alive, waiting for the connection to be closed by the client/server
            while (!clientSocket.isClosed()) {
                // Just sleep; server pushes notifications via the registered ObjectOutputStream
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    System.out.println("Notification Handler for " + userID + " interrupted.");
                    break;
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Notification Handler for " + userID + " disconnected: " + e.getMessage());
        } finally {
            // Deregister and clean up
            server.deregisterNotificationClient(this.userID);
            try {
                if (ois != null) ois.close();
            } catch (IOException ignored) {}
            try {
                if (oos != null) oos.close();
            } catch (IOException ignored) {}
            try {
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
}

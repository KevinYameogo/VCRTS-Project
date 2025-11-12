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

    // NOTE: This port must match the NOTIFICATION_PORT used in ClientGUI/OwnerGUI (12346)
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
        try (
            ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
            ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream())
        ) {
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
            // The OIS is kept open by the outer try-with-resources, and the thread blocks here.
            // Any further object reads would be from the client (not expected in this push model).
            while (!clientSocket.isClosed()) {
                // Wait passively; the server pushes data via the registered OOS
                Thread.sleep(1000); 
            }

        } catch (IOException | ClassNotFoundException e) {
            // Normal behavior on client disconnect/interrupt
            System.out.println("Notification Handler for " + userID + " disconnected.");
        } catch (InterruptedException e) {
            System.out.println("Notification Handler for " + userID + " interrupted.");
        } finally {
            // Deregister and clean up
            server.deregisterNotificationClient(this.userID);
        }
    }
}
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Unified Network Server.
 * Listens on a single port for both:
 * 1. Checkpoint objects from Vehicles.
 * 2. Notification clients (UserIDs) from GUIs.
 */
public class NetworkServer implements Runnable {

    private int port;
    private VCController controller;
    private Server storageServer;
    private boolean isRunning;

    public NetworkServer(int port, VCController controller, Server storageServer) {
        this.port = port;
        this.controller = controller;
        this.storageServer = storageServer;
        this.isRunning = true;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Network Server started on port: " + port);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Handle connection in a separate thread
                    ClientHandler clientHandler = new ClientHandler(clientSocket, controller, storageServer);
                    new Thread(clientHandler).start();

                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        this.isRunning = false;
    }
}

/**
 * Handles a single client connection.
 * Determines if it's a Checkpoint sender or a Notification listener.
 */
class ClientHandler implements Runnable {

    private Socket clientSocket;
    private VCController controller;
    private Server storageServer;
    private String userID; // For notification clients

    public ClientHandler(Socket socket, VCController controller, Server storageServer) {
        this.clientSocket = socket;
        this.controller = controller;
        this.storageServer = storageServer;
    }

    @Override
    public void run() {
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;

        try {
            // Create streams. OOS first to flush header.
            oos = new ObjectOutputStream(clientSocket.getOutputStream());
            oos.flush();
            ois = new ObjectInputStream(clientSocket.getInputStream());

            // Read the first object to determine client type
            Object receivedObject = ois.readObject();

            if (receivedObject instanceof Checkpoint) {
                // --- CHECKPOINT HANDLER ---
                Checkpoint checkpoint = (Checkpoint) receivedObject;
                controller.handleCheckpoint(checkpoint);
                
            } else if (receivedObject instanceof String) {
                // --- NOTIFICATION HANDLER ---
                this.userID = (String) receivedObject;
                System.out.println("NetworkServer: Notification client registered: " + userID);

                // Register the output stream for pushing messages
                storageServer.registerNotificationClient(userID, oos);

                // Keep the connection alive for notifications
                while (!clientSocket.isClosed()) {
                    try {
                        Object obj = ois.readObject();
                        
                    } catch (EOFException e) {
                        break; 
                    } catch (IOException | ClassNotFoundException e) {
                        break; 
                    }
                }
            } else {
                System.out.println("Received unknown object type from " + clientSocket.getInetAddress());
            }

        } catch (IOException | ClassNotFoundException e) {
            
        } finally {
            // Cleanup
            if (userID != null) {
                storageServer.deregisterNotificationClient(userID);
                System.out.println("NetworkServer: Notification client disconnected: " + userID);
            }
            
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
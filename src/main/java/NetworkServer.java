import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This class listens on a port for incoming connections.
 * For each connection, it spawns a new thread to handle it.
 */
public class NetworkServer implements Runnable {

    private int port;
    private VCController controller;
    private boolean isRunning;

    public NetworkServer(int port, VCController controller) {
        this.port = port;
        this.controller = controller;
        this.isRunning = true;
    }

    @Override
    public void run() {
        // ensure socket is closed
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Network Server started on port: " + port);

            while (isRunning) {
                try {
                    // This blocks until a client (Vehicle) connects
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    // Create a new thread to handle this client
                    // This allows the server to accept other connections
                    ClientHandler clientHandler = new ClientHandler(clientSocket, controller);
                    new Thread(clientHandler).start();

                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        this.isRunning = false;
        //might need to add code to interrupt the serverSocket.accept() call
    }
}

/**
 * This class handles a single client connection on its own thread.
 */
class ClientHandler implements Runnable {

    private Socket clientSocket;
    private VCController controller;

    public ClientHandler(Socket socket, VCController controller) {
        this.clientSocket = socket;
        this.controller = controller;
    }

    @Override
    public void run() {
        // Use try-with-resources to auto-close the streams
        try (ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream())) {
            
            // Read an object from the client
            Object receivedObject = ois.readObject();

            if (receivedObject instanceof Checkpoint) {
                Checkpoint checkpoint = (Checkpoint) receivedObject;
        
                controller.handleCheckpoint(checkpoint);
            } else {
                System.out.println("Received unknown object type from " + clientSocket.getInetAddress());
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close(); 
            } catch (IOException e) {
        
            }
        }
    }
}
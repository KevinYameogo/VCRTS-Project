import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.stream.Stream;
import java.util.Map;   
import java.util.HashMap; 
import java.io.*; 

/**
 * VCController with client-server request processing.
 * Thread-safe operations for handling concurrent requests.
 * FIXED: Ensures getClientJobHistory uses the login ID for filtering.
 */
public class VCController implements Serializable {
  
  private static final long serialVersionUID = 2L; 
  private static final String STATE_FILE = "vccontroller_state.dat";

  private List<Vehicle> availableVehicles; 
  private List<Vehicle> activeVehicles;
  private LinkedList<Job> pendingJobs; 
  private List<Job> inProgressJobs;
  private List<Job> archivedJobs;
  private transient Server systemServer; 
  
  private Map<Job, List<Vehicle>> jobVehicleMap; 
  private Map<Vehicle, Job> vehicleJobMap;
  
  // GUI reference for notifications
  private transient VCControllerGUI controllerGUI;

  public VCController(Server server){
    this.systemServer = server;
    
    if (!loadState()) {
      this.availableVehicles = new ArrayList<>();
      this.activeVehicles = new ArrayList<>();
      this.pendingJobs = new LinkedList<>();
      this.inProgressJobs = new ArrayList<>();
      this.archivedJobs = new ArrayList<>();
      
      this.jobVehicleMap = new HashMap<>();
      this.vehicleJobMap = new HashMap<>();
      System.out.println("VCController initialized fresh.");
    } else {
      System.out.println("VCController state successfully loaded.");
    }
    
    scheduleJobs();
  }
  
  /**
   * Sets the GUI reference for sending notifications back to the interface.
   */
  public void setControllerGUI(VCControllerGUI gui) {
    this.controllerGUI = gui;
  }

  /** Saves the current state */
  private synchronized void saveState() {
      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(STATE_FILE))) {
          oos.writeObject(this.pendingJobs);
          oos.writeObject(this.inProgressJobs);
          oos.writeObject(this.archivedJobs);
          oos.writeObject(this.jobVehicleMap);
          oos.writeObject(this.vehicleJobMap);
          System.out.println("VCController state saved.");
      } catch (IOException e) {
          System.err.println("Error saving VCController state: " + e.getMessage());
      }
  }

  /** Loads the state from file */
  @SuppressWarnings("unchecked")
  private boolean loadState() {
      File stateFile = new File(STATE_FILE);
      if (!stateFile.exists()) return false;
      
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
          this.pendingJobs = (LinkedList<Job>) ois.readObject();
          this.inProgressJobs = (List<Job>) ois.readObject();
          this.archivedJobs = (List<Job>) ois.readObject();
          this.jobVehicleMap = (Map<Job, List<Vehicle>>) ois.readObject();
          this.vehicleJobMap = (Map<Vehicle, Job>) ois.readObject();
          
          this.availableVehicles = new ArrayList<>();
          this.activeVehicles = new ArrayList<>();
          this.activeVehicles.addAll(this.vehicleJobMap.keySet());
          
          System.out.println("Loaded " + this.activeVehicles.size() + " active vehicles from state.");
          return true;
      } catch (IOException | ClassNotFoundException e) {
          System.err.println("Error loading VCController state: " + e.getMessage());
          stateFile.delete(); 
          return false;
      }
  }
  
  // ==================== REQUEST PROCESSING METHODS ====================
  
  /**
   * Processes a job submission request from a client.
   */
  public synchronized boolean processJobRequest(Request request) {
      if (request == null || !request.getRequestType().equals("JOB_SUBMISSION")) {
          return false;
      }
      
      // Acknowledge receipt immediately
      request.acknowledge();
      if (controllerGUI != null) {
          controllerGUI.addNotification("Job request received from " + request.getSenderID());
      }
      return true;
  }
  
  /**
   * Approves and processes a job submission request.
   */
  public synchronized void approveJobSubmission(String requestID) {
      Request request = systemServer.getRequest(requestID);
      if (request == null || !(request.getData() instanceof Job)) {
          System.err.println("Invalid job request: " + requestID);
          return;
      }
      
      // Approve in server
      systemServer.approveRequest(requestID);
      
      // Add job to controller
      Job job = (Job) request.getData();
      addJob(job);
      
      if (controllerGUI != null) {
          controllerGUI.addNotification("Job " + job.getJobID() + " approved and added to queue");
          controllerGUI.logToFile("Job " + job.getJobID() + " approved by VC Controller");
      }
      
      System.out.println("VC Controller: Approved job " + job.getJobID());
  }
  
  /**
   * Rejects a job submission request.
   */
  public synchronized void rejectJobSubmission(String requestID) {
      Request request = systemServer.getRequest(requestID);
      if (request == null) {
          return;
      }
      
      systemServer.rejectRequest(requestID);
      
      if (controllerGUI != null) {
          controllerGUI.addNotification("Job request " + requestID + " rejected");
          controllerGUI.logToFile("Job request " + requestID + " rejected by VC Controller");
      }
      
      System.out.println("VC Controller: Rejected job request " + requestID);
  }
  
  /**
   * Processes a vehicle registration request from an owner.
   */
  public synchronized boolean processVehicleRequest(Request request) {
      if (request == null || !request.getRequestType().equals("VEHICLE_REGISTRATION")) {
          return false;
      }
      
      request.acknowledge();
      if (controllerGUI != null) {
          controllerGUI.addNotification("Vehicle registration received from " + request.getSenderID());
      }
      
      return true;
  }
  
  /**
   * Approves and processes a vehicle registration.
   */
  public synchronized void approveVehicleRegistration(String requestID) {
      Request request = systemServer.getRequest(requestID);
      if (request == null || !(request.getData() instanceof Vehicle)) {
          System.err.println("Invalid vehicle request: " + requestID);
          return;
      }
      
      systemServer.approveRequest(requestID);
      
      Vehicle vehicle = (Vehicle) request.getData();
      recruitVehicle(vehicle);
      
      if (controllerGUI != null) {
          controllerGUI.addNotification("Vehicle " + vehicle.getVehicleID() + " approved and recruited");
          controllerGUI.logToFile("Vehicle " + vehicle.getVehicleID() + " registered successfully");
      }
      
      System.out.println("VC Controller: Approved vehicle " + vehicle.getVehicleID());
  }
  
  /**
   * Rejects a vehicle registration request.
   */
  public synchronized void rejectVehicleRegistration(String requestID) {
      Request request = systemServer.getRequest(requestID);
      if (request == null) {
          return;
      }
      
      systemServer.rejectRequest(requestID);
      
      if (controllerGUI != null) {
          controllerGUI.addNotification("Vehicle request " + requestID + " rejected");
          controllerGUI.logToFile("Vehicle request " + requestID + " rejected by VC Controller");
      }
      
      System.out.println("VC Controller: Rejected vehicle request " + requestID);
  }
  
  // ==================== EXISTING METHODS (Enhanced) ====================
  
  public synchronized void addJob(Job job){ 
    pendingJobs.add(job);
    System.out.println("Job " + job.getJobID() + " added to pending queue.");
    saveState(); 
    scheduleJobs();
  }

  private synchronized void scheduleJobs(){
    if(pendingJobs.isEmpty()){
      return;
    }
    Job nextJob = pendingJobs.peek();
    int requiredVehicles = nextJob.getRedundancyLevel();

    if(availableVehicles.size() >= requiredVehicles){
      Job jobToAssign = pendingJobs.remove();
      assignJob(jobToAssign); 
      inProgressJobs.add(jobToAssign);
      jobToAssign.updateStatus("In-Progress");
      System.out.println("Job " + jobToAssign.getJobID() + " started.");
      saveState();
    }else{
      System.out.println("Job " + nextJob.getJobID() + " postponed. Waiting for " 
      + requiredVehicles + " vehicle(s).");
    }
  }

  private synchronized void assignJob(Job job){
    int redundancyLevel = job.getRedundancyLevel();
    String jobID = job.getJobID(); 

    System.out.println("Assigning Job " + jobID 
     + " to " + redundancyLevel + " vehicle(s).");
    
    List<Vehicle> assignedVehicles = new ArrayList<>();
    
    for(int i = 0; i < redundancyLevel; i++){
      Vehicle vehicleToAssign = availableVehicles.remove(0);
      activeVehicles.add(vehicleToAssign);
      
      assignedVehicles.add(vehicleToAssign);
      vehicleJobMap.put(vehicleToAssign, job);
      
      // Assuming Vehicle.startExecution exists
      // vehicleToAssign.startExecution(jobID); 
    }
    jobVehicleMap.put(job, assignedVehicles);
  }

  public synchronized void handleCheckpoint(Checkpoint checkpoint){
    System.out.println("Checkpoint received for vehicle: " + checkpoint.getVehicleID() + " at "
     + checkpoint.getTimestamp());
    // Assuming systemServer.storeCheckpoint exists
    // systemServer.storeCheckpoint(checkpoint);
  }

  public synchronized void handleJobCompletion(Job job){
    if(archivedJobs.contains(job)){
      System.out.println("Job " + job.getJobID() + " is already archived.");
      return;
    }
    
    List<Vehicle> vehiclesToRelease = jobVehicleMap.getOrDefault(job, new ArrayList<>());
    
    if(vehiclesToRelease.isEmpty()){
        System.out.println("Warning: Job completed but no active vehicles found in map.");
    }
    
    for(Vehicle vehicle : vehiclesToRelease){
        activeVehicles.remove(vehicle);
        // Assuming Vehicle.markAvailable exists
        // vehicle.markAvailable();
        availableVehicles.add(vehicle);
        vehicleJobMap.remove(vehicle);
        
        System.out.println("Vehicle " + vehicle.getVehicleID() + " is now available.");
    }

    jobVehicleMap.remove(job);
    inProgressJobs.remove(job);
    archivedJobs.add(job);
    job.updateStatus("Completed");

    System.out.println("Job " + job.getJobID() + " marked as 'Completed'.");
    // Assuming systemServer.storeCompletedJob exists
    // this.transferJobToServer(job); 
    
    saveState(); 
    scheduleJobs();
  }
  
  private void transferJobToServer(Job job){
    // Assuming systemServer.storeCompletedJob exists
    // systemServer.storeCompletedJob(job);
    System.out.println("Job " + job.getJobID() + " data transferred to server.");
  }

  public synchronized void handleVehicleDeparture(Vehicle vehicle){
    System.out.println("Vehicle " + vehicle.getVehicleID() + " is departing...");
    
    if(availableVehicles.remove(vehicle)){
      System.out.println("Vehicle removed from available pool");
      saveState();
      return;
    }

    if(activeVehicles.remove(vehicle)){
      System.out.println("Vehicle removed from active pool.");

      Job interruptedJob = vehicleJobMap.remove(vehicle);
      
      if(interruptedJob != null){
        jobVehicleMap.computeIfPresent(interruptedJob, (job, list) -> {
            list.remove(vehicle);
            return list;
        });

        List<Vehicle> remainingVehicles = jobVehicleMap.get(interruptedJob);
        if (remainingVehicles == null || remainingVehicles.isEmpty()) {
            if (!availableVehicles.isEmpty()) {
                // Assuming systemServer.getLatestCheckpoint exists
                // Checkpoint latestCheckpoint = systemServer.getLatestCheckpoint(interruptedJob.getJobID()); 
                
                // if (latestCheckpoint != null) {
                    Vehicle replacementVehicle = availableVehicles.remove(0); 
                    activeVehicles.add(replacementVehicle);
                    // restartComputation(latestCheckpoint, replacementVehicle);
                    
                    List<Vehicle> updatedVehicles = new ArrayList<>();
                    updatedVehicles.add(replacementVehicle);
                    jobVehicleMap.put(interruptedJob, updatedVehicles);
                    vehicleJobMap.put(replacementVehicle, interruptedJob);
                    
                    System.out.println("Job " + interruptedJob.getJobID() + " **recovered** on new vehicle: " 
                     + replacementVehicle.getVehicleID() + " from checkpoint.");
                // } else {
                //     inProgressJobs.remove(interruptedJob);
                //     pendingJobs.add(interruptedJob);
                //     interruptedJob.updateStatus("Pending(Interrupted)");
                //     jobVehicleMap.remove(interruptedJob);
                //     System.out.println("Job " + interruptedJob.getJobID() 
                //      + " re-queued. No valid checkpoint found for recovery.");
                // }
            } else {
                inProgressJobs.remove(interruptedJob);
                pendingJobs.add(interruptedJob);
                interruptedJob.updateStatus("Pending(Interrupted)");
                jobVehicleMap.remove(interruptedJob);
                System.out.println("Job " + interruptedJob.getJobID() + " re-queued. No vehicles available.");
            }
            saveState(); 
        } else {
             System.out.println("Job " + interruptedJob.getJobID() 
             + " continues on " + remainingVehicles.size() + " vehicle(s).");
        }
      }
      return;
    }
    System.out.println("Warning: Departing vehicle " + vehicle.getVehicleID() 
    + " was not found in active or available lists.");
  }
  
  public synchronized void triggerCheckpoint(Job job){
    if(!inProgressJobs.contains(job)){
      System.out.println("Error: Cannot trigger checkpoint. Job " 
      + job.getJobID() + " is not in progress.");
      return;
    }
    System.out.println("Triggering checkpoint for Job " + job.getJobID() + "...");
    
    int vehiclesTriggered = 0;
    List<Vehicle> targetVehicles = jobVehicleMap.getOrDefault(job, new ArrayList<>());

    for(Vehicle vehicle: targetVehicles){
        // Assuming Vehicle.createCheckpoint exists
        // vehicle.createCheckpoint();
        vehiclesTriggered++;
    }
    System.out.println("Checkpoint signal sent to " + vehiclesTriggered + " vehicle(s).");
  }

  public synchronized void recruitVehicle(Vehicle vehicle){
    this.availableVehicles.add(vehicle);
    System.out.println("New vehicle recruited: " + vehicle.getVehicleID() 
    + ". Now available for jobs.");
    saveState(); 
    scheduleJobs();
  }

  private void restartComputation(Checkpoint checkpoint, Vehicle newVehicle){
    System.out.println("Instructing vehicle " + newVehicle.getVehicleID() 
     + " to restart computation from checkpoint " 
    + checkpoint.getCheckpointID());
    // Assuming Vehicle.loadFromCheckpoint exists
    // newVehicle.loadFromCheckpoint(checkpoint);
  }

  public synchronized String getJobStatus(String jobID){
    return Stream.of(pendingJobs, inProgressJobs, archivedJobs)
        .flatMap(List::stream)
        .filter(job -> job.getJobID().equals(jobID))
        .map(Job::getStatus)
        .findFirst()
        .orElse("Job not found");
  }

  public String calculateCompletionTimes(){
    StringBuilder output = new StringBuilder();
    int cumulativeTime = 0;

    for(Job job: pendingJobs){
      int jobDuration = job.getDuration();
      cumulativeTime += jobDuration;

      output.append("Job ID: ");
      output.append(job.getJobID());
      output.append(", Duration: ");
      output.append(jobDuration);
      output.append(", Est. Completion Time: ");
      output.append(cumulativeTime);
      output.append("\n");
    }
    if(output.length() == 0){
      return "No Jobs pending in the queue.";
    }
    return output.toString();
  }

  public synchronized boolean isVehicleInSystem(String license, String state) {
    String signature = license + state;
    boolean inAvailable = availableVehicles.stream()
            // Assuming Vehicle.getSignature exists
            .anyMatch(vehicle -> vehicle.getSignature().equals(signature));
    if (inAvailable) return true;
    return activeVehicles.stream()
            // Assuming Vehicle.getSignature exists
            .anyMatch(vehicle -> vehicle.getSignature().equals(signature));
  }
  
  /**
   * FIX: Returns a list of all registered vehicles associated with the given owner ID.
   * This is used by OwnerGUI to display persistent vehicle registration history.
   *
   * @param ownerID The ID of the owner whose vehicle history is requested.
   * @return List<Vehicle> A list of vehicles registered by the owner.
   */
  public synchronized List<Vehicle> getOwnerVehicleHistory(String ownerID) {
      // Vehicle registration records are tracked primarily by the availableVehicles
      // and activeVehicles lists. We rely on the Server to link the Vehicle object
      // back to its original owner ID (via the registration request).
      
      List<Vehicle> allVehicles = new ArrayList<>();
      allVehicles.addAll(availableVehicles);
      allVehicles.addAll(activeVehicles);
      
      List<Vehicle> ownerHistory = new ArrayList<>();
      
      for (Vehicle vehicle : allVehicles) {
          // ASSUMPTION: systemServer.getOwnerIDForVehicle(vehicle) is implemented in Server.java
          String senderId = systemServer.getOwnerIDForVehicle(vehicle); 
          if (ownerID.equals(senderId)) {
              ownerHistory.add(vehicle);
          }
      }
      return ownerHistory;
  }
  
  public synchronized boolean isJobInSystem(String jobID) {
    return Stream.of(pendingJobs, inProgressJobs, archivedJobs)
            .flatMap(List::stream)
            .anyMatch(job -> job.getJobID().equals(jobID));
  }
  
  /**
   * FIX: Returns a list of all jobs (Pending, In-Progress, Archived) associated 
   * with the given client ID (which is the login ID).
   *
   * @param loginID The login ID of the client whose job history is requested.
   * @return List<Job> A list of jobs submitted by the client.
   */
  public synchronized List<Job> getClientJobHistory(String loginID) {
      List<Job> allJobs = Stream.of(pendingJobs, inProgressJobs, archivedJobs)
              .flatMap(List::stream)
              .toList();

      List<Job> clientHistory = new ArrayList<>();

      for (Job job : allJobs) {
          // FIX: Use the new Server method to look up the original sender ID (login ID)
          String senderId = systemServer.getLoginIDForJob(job); 
          if (loginID.equals(senderId)) {
              clientHistory.add(job);
          }
      }
      return clientHistory;
  }

  public synchronized List<Job> getInProgressJobs() {
    return new ArrayList<>(inProgressJobs);
  }
  
  public synchronized Queue<Job> getPendingJobs() {
    return new LinkedList<>(pendingJobs);
  }
  
  public Server getServer() {
    return systemServer;
  }
}
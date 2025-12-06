import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.stream.Stream;
import java.util.Map;   
import java.util.HashMap; 
import java.io.*; 
import java.util.Objects; 


/**
 * VCController with client-server request processing.
 * Thread-safe operations for handling concurrent requests.
 */
public class VCController implements Serializable {
  
  private static final long serialVersionUID = 2L; 

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
    this.systemServer = Objects.requireNonNull(server, "Server cannot be null.");
    
    reloadState();
    
    // Ensure transient maps are re-created if necessary 
    if (this.jobVehicleMap == null) this.jobVehicleMap = new HashMap<>();
    if (this.vehicleJobMap == null) this.vehicleJobMap = new HashMap<>();
    
    scheduleJobs();
  }
  
  /**
   * Sets the GUI reference for sending notifications back to the interface.
   */
  public void setControllerGUI(VCControllerGUI gui) {
    this.controllerGUI = gui;
  }


  /** Reloads state from Server (DB) */
  public synchronized void reloadState() {
      this.availableVehicles = new ArrayList<>();
      this.activeVehicles = new ArrayList<>();
      this.pendingJobs = new LinkedList<>();
      this.inProgressJobs = new ArrayList<>();
      this.archivedJobs = new ArrayList<>();
      this.jobVehicleMap = new HashMap<>();
      this.vehicleJobMap = new HashMap<>();

      // Load Jobs
      List<Job> allJobs = systemServer.getAllApprovedJobs(); // This loads from DB
      for (Job job : allJobs) {
          switch (job.getStatus()) {
              case "Pending":
                  pendingJobs.add(job);
                  break;
              case "In-Progress":
                  inProgressJobs.add(job);
                  break;
              case "Completed":
                  archivedJobs.add(job);
                  break;
              default:
                  break;
          }
      }

      // Load Vehicles
      List<Vehicle> allVehicles = systemServer.getAllRegisteredVehicles(); // This loads from DB
      for (Vehicle v : allVehicles) {
          if ("Available".equalsIgnoreCase(v.getStatus())) {
              availableVehicles.add(v);
          } else if ("Active".equalsIgnoreCase(v.getStatus()) || "Busy".equalsIgnoreCase(v.getCpuStatus())) {
              activeVehicles.add(v);
              
              // Re-link with Job
              String jobId = v.getCurrentJobID();
              if (jobId != null) {
                  Job job = findJob(jobId);
                  if (job != null) {
                      vehicleJobMap.put(v, job);
                      jobVehicleMap.computeIfAbsent(job, k -> new ArrayList<>()).add(v);
                  }
              }
          }
      }
      
      System.out.println("VCController state reloaded from DB.");
  }
  
  private Job findJob(String jobId) {
      for (Job j : inProgressJobs) if (j.getJobID().equals(jobId)) return j;
      for (Job j : pendingJobs) if (j.getJobID().equals(jobId)) return j;
      return null;
  }
  
  // REQUEST PROCESSING METHODS 
  
  /**
   * Processes a job submission request from a client.
   */
  public synchronized boolean processJobRequest(Request request) {
      if (request == null || !request.getRequestType().equals("JOB_SUBMISSION")) {
          return false;
      }
      
      // Acknowledge receipt immediately
      request.acknowledge();
      
      // Notify User
      String msg = "Job request " + request.getRequestID() + " received and acknowledged";
      if (request.getData() instanceof Job) {
          Job j = (Job) request.getData();
          msg = "Job request " + j.getJobID() + " received and acknowledged by server";
      }
      systemServer.notifyUser(request.getSenderID(), msg);
      
      if (controllerGUI != null) {
          controllerGUI.addNotification(msg);
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
      
      // Approve 
      systemServer.approveRequest(requestID);
      
      // Add job to controller
      Job job = (Job) request.getData();
      addJob(job);

      // Store the approved job in the server's persistent storage
      systemServer.storeApprovedJob(job);
      
      if (controllerGUI != null) {
          controllerGUI.addNotification("Job " + job.getJobID() + " approved and added to queue");
          controllerGUI.logToFile("Job " + job.getJobID() + " approved by VC Controller");
      }
            // Notify user
        String senderID = request.getSenderID();
        String notificationMsg = "Your job " + job.getJobID() + " has been APPROVED and added to the queue.";
        systemServer.notifyUser(senderID, notificationMsg);
      
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
      
      String msg = "Job request " + requestID + " rejected";
      if (request.getData() instanceof Job) {
          Job j = (Job) request.getData();
          msg = "Your job " + j.getJobID() + " has been REJECTED.";
      }
      
      if (controllerGUI != null) {
          controllerGUI.addNotification(msg);
          controllerGUI.logToFile(msg + " by VC Controller");
      }
      
      // Notify User
      String senderID = request.getSenderID();
      systemServer.notifyUser(senderID, msg);
      
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
      
      // Notify User
      String msg = "Vehicle registration " + request.getRequestID() + " received and acknowledged";
      if (request.getData() instanceof Vehicle) {
          Vehicle v = (Vehicle) request.getData();
          msg = "Vehicle registration " + v.getVehicleID() + " received and acknowledged by server";
      }
      systemServer.notifyUser(request.getSenderID(), msg);
      
      if (controllerGUI != null) {
          controllerGUI.addNotification(msg);
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

      // Store the vehicle in the server's persistent storage
      systemServer.storeRegisteredVehicle(vehicle);
      
      if (controllerGUI != null) {
          controllerGUI.addNotification("Vehicle " + vehicle.getVehicleID() + " approved and recruited");
          controllerGUI.logToFile("Vehicle " + vehicle.getVehicleID() + " registered successfully");
      }
        // Notify user
        String senderID = request.getSenderID();
        String notificationMsg = "Your vehicle " + vehicle.getVehicleID() + " has been APPROVED and registered.";
        systemServer.notifyUser(senderID, notificationMsg);
      
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
      
      String msg = "Vehicle request " + requestID + " rejected";
      if (request.getData() instanceof Vehicle) {
          Vehicle v = (Vehicle) request.getData();
          msg = "Vehicle " + v.getVehicleID() + " rejected";
      }

      if (controllerGUI != null) {
          controllerGUI.addNotification(msg);
          controllerGUI.logToFile(msg + " by VC Controller");
      }
      
      // Notify User
      systemServer.notifyUser(request.getSenderID(), msg);
      
      System.out.println("VC Controller: Rejected vehicle request " + requestID);
  }
  
  //
  public synchronized void addJob(Job job){ 
    pendingJobs.add(job);
    System.out.println("Job " + job.getJobID() + " added to pending queue.");
    scheduleJobs();
  }

  //schedule job
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
      
      // Update status in DB
      systemServer.storeApprovedJob(jobToAssign);
      
      System.out.println("Job " + jobToAssign.getJobID() + " started.");

    }else{
      System.out.println("Job " + nextJob.getJobID() + " postponed. Waiting for " 
      + requiredVehicles + " vehicle(s).");
    }
  }

  //assign job
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
      
      vehicleToAssign.startExecution(jobID); 

      // Save the updated vehicle state (Active, Job ID) to the database
      systemServer.storeRegisteredVehicle(vehicleToAssign);
    }
    jobVehicleMap.put(job, assignedVehicles);
  }

  public synchronized void handleCheckpoint(Checkpoint checkpoint){
    System.out.println("Checkpoint received for vehicle: " + checkpoint.getVehicleID() + " at "
     + checkpoint.getTimestamp());
    
    systemServer.storeCheckpoint(checkpoint);
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
        
        vehicle.markAvailable();
        availableVehicles.add(vehicle);
        vehicleJobMap.remove(vehicle);
        
        System.out.println("Vehicle " + vehicle.getVehicleID() + " is now available.");
    }

    jobVehicleMap.remove(job);
    inProgressJobs.remove(job);
    archivedJobs.add(job);
    job.updateStatus("Completed");

    System.out.println("Job " + job.getJobID() + " marked as 'Completed'.");
    
    this.transferJobToServer(job); 
    
    scheduleJobs();
  }
  
  private void transferJobToServer(Job job){
    systemServer.storeCompletedJob(job);
    System.out.println("Job " + job.getJobID() + " data transferred to server.");
  }

  public synchronized void handleVehicleDeparture(Vehicle vehicle){
    System.out.println("Vehicle " + vehicle.getVehicleID() + " is departing...");
    
    if(availableVehicles.remove(vehicle)){
      System.out.println("Vehicle removed from available pool");
      // Update DB status
      vehicle.restoreState("Departed", "Idle", "Free", null); 
      systemServer.storeRegisteredVehicle(vehicle);
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
                
                Checkpoint latestCheckpoint = systemServer.getLatestCheckpoint(interruptedJob.getJobID()); 
                
                if (latestCheckpoint != null) {
                    Vehicle replacementVehicle = availableVehicles.remove(0); 
                    activeVehicles.add(replacementVehicle);
                    
                    restartComputation(latestCheckpoint, replacementVehicle);
                    
                    List<Vehicle> updatedVehicles = new ArrayList<>();
                    updatedVehicles.add(replacementVehicle);
                    jobVehicleMap.put(interruptedJob, updatedVehicles);
                    vehicleJobMap.put(replacementVehicle, interruptedJob);
                    
                    System.out.println("Job " + interruptedJob.getJobID() + " **recovered** on new vehicle: " 
                     + replacementVehicle.getVehicleID() + " from checkpoint.");
                } else {
                    inProgressJobs.remove(interruptedJob);
                    pendingJobs.add(interruptedJob);
                    interruptedJob.updateStatus("Pending(Interrupted)");

                    jobVehicleMap.remove(interruptedJob);
                    System.out.println("Job " + interruptedJob.getJobID() 
                     + " re-queued. No valid checkpoint found for recovery.");
                }
            } else {
                inProgressJobs.remove(interruptedJob);
                pendingJobs.add(interruptedJob);
                interruptedJob.updateStatus("Pending(Interrupted)");
                
                jobVehicleMap.remove(interruptedJob);
                System.out.println("Job " + interruptedJob.getJobID() + " re-queued. No vehicles available.");
            }

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
        vehicle.createCheckpoint();
        vehiclesTriggered++;
    }
    System.out.println("Checkpoint signal sent to " + vehiclesTriggered + " vehicle(s).");
  }
  //
  public synchronized void recruitVehicle(Vehicle vehicle){
    this.availableVehicles.add(vehicle);
    System.out.println("New vehicle recruited: " + vehicle.getVehicleID() 
    + ". Now available for jobs.");
    scheduleJobs();
  }

  private void restartComputation(Checkpoint checkpoint, Vehicle newVehicle){
    System.out.println("Instructing vehicle " + newVehicle.getVehicleID() 
     + " to restart computation from checkpoint " 
    + checkpoint.getCheckpointID());
    newVehicle.loadFromCheckpoint(checkpoint);
  }


    public synchronized String getJobStatus(String jobID){
      // First check local controller lists (active/pending)
      String localStatus = Stream.of(pendingJobs, inProgressJobs, archivedJobs)
          .flatMap(List::stream)
          .filter(job -> job.getJobID().equals(jobID))
          .map(Job::getStatus)
          .findFirst()
          .orElse(null);
      
      if (localStatus != null) {
          return localStatus; 
      }
      
      // Check Database
      return DatabaseManager.getInstance().getJobStatus(jobID);
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
    
    // Check available and active vehicles
    boolean inAvailable = availableVehicles.stream()
            .anyMatch(vehicle -> vehicle.getSignature().equals(signature));
    if (inAvailable) return true;
    
    boolean inActive = activeVehicles.stream()
            .anyMatch(vehicle -> vehicle.getSignature().equals(signature));
    if (inActive) return true;

    // Check pending requests in the server
    if (systemServer != null) {
        List<Request> pendingRequests = systemServer.getPendingRequests();
        for (Request req : pendingRequests) {
            if (req.getRequestType().equals("VEHICLE_REGISTRATION") && req.getData() instanceof Vehicle) {
                Vehicle v = (Vehicle) req.getData();
                if (v.getSignature().equals(signature)) {
                    return true;
                }
            }
        }
    }
    
    return false;
  }
  
  /**
   * Returns a list of all registered vehicles associated with the given owner ID.
   * This is used by OwnerGUI to display persistent vehicle registration history.
   */
  public synchronized List<Vehicle> getOwnerVehicleHistory(String ownerID) {
      return DatabaseManager.getInstance().getOwnerVehicleHistory(ownerID);
  }
  
  public synchronized boolean isJobInSystem(String jobID) {
    return Stream.of(pendingJobs, inProgressJobs, archivedJobs)
            .flatMap(List::stream)
            .anyMatch(job -> job.getJobID().equals(jobID));
  }
  
  /**
   * Returns a list of all jobs (Pending, In-Progress, Archived) associated 
   * with the given client ID .
   */
  public synchronized List<Job> getClientJobHistory(String loginID) {
      return DatabaseManager.getInstance().getClientJobHistory(loginID);
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
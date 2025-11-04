import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.Map;   // NEW
import java.util.HashMap; // NEW
import java.util.stream.Collectors; // NEW

public class VCController {
  private List<Vehicle> availableVehicles; 
  private List<Vehicle> activeVehicles;
  private Queue<Job> pendingJobs;
  private List<Job> inProgressJobs;
  private List<Job> archivedJobs;
  private Server systemServer; 
  
  // NEW: Explicit structure to map Job to the Vehicles running it
  private Map<Job, List<Vehicle>> jobVehicleMap; 
  // NEW: Helper map for quick reverse lookup (Vehicle to Job)
  private Map<Vehicle, Job> vehicleJobMap;

  //Constructor
  public VCController(Server server){
    this.availableVehicles = new ArrayList<>();
    this.activeVehicles = new ArrayList<>();
    this.pendingJobs = new LinkedList<>();
    this.inProgressJobs = new ArrayList<>();
    this.archivedJobs = new ArrayList<>();
    this.systemServer = server;
    
    // Initialize NEW Maps
    this.jobVehicleMap = new HashMap<>();
    this.vehicleJobMap = new HashMap<>();
  }

  //Add a new job to the pending jobs queue
  public void addJob(Job job){ 
    pendingJobs.add(job);
    System.out.println("Job " + job.getJobID() + " added to pending queue.");
    scheduleJobs();
  }

  //Schedule jobs
  private void scheduleJobs(){
    //no pending jobs, no scheduling.
    if(pendingJobs.isEmpty()){
      return;
    }
    Job nextJob = pendingJobs.peek();
    int requiredVehicles = nextJob.getRedundancyLevel();

    if(availableVehicles.size() >= requiredVehicles){

      Job jobToAssign = pendingJobs.remove();
      assignJob(jobToAssign); // Calls revised assignJob
      inProgressJobs.add(jobToAssign);//move to progess
      jobToAssign.updateStatus("In-Progress");
      System.out.println("Job " + jobToAssign.getJobID() + " started.");

    }else{

      System.out.println("Job " + nextJob.getJobID() + " postponed. Waiting for " 
      + requiredVehicles + " vehicle(s).");
    }
  }

  //assign job to vehicle(s) (REVISED)
  private void assignJob(Job job){
    int redundancyLevel = job.getRedundancyLevel();

    System.out.println("Assigning Job " + job.getJobID() 
     + " to " + redundancyLevel + " vehicle(s).");
    
    List<Vehicle> assignedVehicles = new ArrayList<>();
    
    for(int i = 0; i < redundancyLevel; i++){
      Vehicle vehicleToAssign = availableVehicles.remove(0);
      activeVehicles.add(vehicleToAssign);
      
      // NEW: Update both tracking maps
      assignedVehicles.add(vehicleToAssign);
      vehicleJobMap.put(vehicleToAssign, job);
      
      // CALLS REVISED VEHICLE METHOD (startExecution replaces executeJob(job))
      vehicleToAssign.startExecution(); 
    }
    // NEW: Update Job -> Vehicle map
    jobVehicleMap.put(job, assignedVehicles);
  }

  //receive and store checkpoint from a vehicle
  public void handleCheckpoint(Checkpoint checkpoint){
    System.out.println("Checkpoint received for vehicle: " + checkpoint.getVehicleID() + " at "
     + checkpoint.getTimestamp());
    systemServer.storeCheckpoint(checkpoint);
  }

  //move completed job to archived list. Notify the server (REVISED)
  public void handleJobCompletion(Job job){
    if(archivedJobs.contains(job)){
      System.out.println("Job " + job.getJobID() + " is already archived.");
      return;
    }
    
    // Find all vehicles associated with this job using the Map
    List<Vehicle> vehiclesToRelease = jobVehicleMap.getOrDefault(job, new ArrayList<>());
    
    if(vehiclesToRelease.isEmpty()){
        System.out.println("Warning: Job completed but no active vehicles found in map.");
    }
    
    // Release vehicles and update active/available lists
    for(Vehicle vehicle : vehiclesToRelease){
        
        // Remove from active list
        activeVehicles.remove(vehicle);
        
        // CALLS REVISED VEHICLE METHOD (markAvailable replaces clearVehicleData/eraseJobData)
        vehicle.markAvailable();
        
        // Add back to available pool
        availableVehicles.add(vehicle);
        
        // Remove from reverse map
        vehicleJobMap.remove(vehicle);
        
        System.out.println("Vehicle " + vehicle.getVehicleID() + " is now available.");
    }

    // Remove from main job tracking map
    jobVehicleMap.remove(job);
    
    // Move job from in-progress to archived
    inProgressJobs.remove(job);
    archivedJobs.add(job);
    job.updateStatus("Completed");

    System.out.println("Job " + job.getJobID() + " marked as 'Completed'.");
    this.transferJobToServer(job); 

    scheduleJobs();
  }
  
  // transfers completed job to server
  private void transferJobToServer(Job job){
    systemServer.storeCompletedJob(job);// calls from server
    System.out.println("Job " + job.getJobID() + " data transferred to server.");
  }

  // clears vehicle data (REMOVED - logic moved/renamed to vehicle.markAvailable)
  private void clearVehicleData(Vehicle vehicle){
    // This method is now obsolete/internal, but kept here to show where it was.
    // The logic is now in vehicle.markAvailable() and the map updates in handleJobCompletion.
    // vehicle.markAvailable(); 
    System.out.println("Data erased from Vehicle " + vehicle.getVehicleID() + ".");
  }

  //removes vehicle from list and re-queue job (REVISED)
  public void handleVehicleDeparture(Vehicle vehicle){ //called by leaving vehicle
    System.out.println("Vehicle " + vehicle.getVehicleID() + " is departing...");
    
    if(availableVehicles.remove(vehicle)){
      System.out.println("Vehicle removed from available pool");
      return;
    }

    //if not in available, then should be in activeList
    if(activeVehicles.remove(vehicle)){
      System.out.println("Vehicle removed from active pool.");

      // NEW: Look up job using the reverse map
      Job interruptedJob = vehicleJobMap.remove(vehicle);
      
      if(interruptedJob != null){
          
        // Remove vehicle from the Job->Vehicle map
        jobVehicleMap.computeIfPresent(interruptedJob, (job, list) -> {
            list.remove(vehicle);
            return list;
        });

        // If the job is now running on ZERO vehicles, re-queue it.
        List<Vehicle> remainingVehicles = jobVehicleMap.get(interruptedJob);
        if (remainingVehicles == null || remainingVehicles.isEmpty()) {
            
            // Re-queue the job
            inProgressJobs.remove(interruptedJob);
            pendingJobs.add(interruptedJob);
            interruptedJob.updateStatus("Pending(Interrupted)");
            jobVehicleMap.remove(interruptedJob); // Fully remove the mapping
            
            System.out.println("Job " + interruptedJob.getJobID() 
             + " was interrupted and has been re-queued.");
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
  
  //finds vehicle and instructs creation of checkpoint (REVISED)
  public void triggerCheckpoint(Job job){

    if(!inProgressJobs.contains(job)){
      System.out.println("Error: Cannot trigger checkpoint. Job " 
      + job.getJobID() + " is not in progress.");
      return;
    }
    System.out.println("Triggering checkpoint for Job " + job.getJobID() + "...");
    
    int vehiclesTriggered = 0;
    
    // NEW: Find the vehicles using the map
    List<Vehicle> targetVehicles = jobVehicleMap.getOrDefault(job, new ArrayList<>());

    for(Vehicle vehicle: targetVehicles){
        // CALLS REVISED VEHICLE METHOD
        vehicle.createCheckpoint();
        vehiclesTriggered++;
    }
    System.out.println("Checkpoint signal sent to " + vehiclesTriggered + " vehicle(s).");
  }

  //add vehicle to system's pool of available vehicles.
  public void recruitVehicle(Vehicle vehicle){
    this.availableVehicles.add(vehicle);
    System.out.println("New vehicle recruited: " + vehicle.getVehicleID() 
    + ". Now available for jobs.");
    scheduleJobs();
  }

  //Instructs vehicle to load its state from a specific checkpoint
  private void restartComputation(Checkpoint checkpoint, Vehicle newVehicle){
    System.out.println("Instructing vehicle " + newVehicle.getVehicleID() 
     + " to restart computation from checkpoint " 
    + checkpoint.getCheckpointID());

    newVehicle.loadFromCheckpoint(checkpoint);//calls from vehicle
  }

  //called by client to check status of a job(via GUI)
  public String getJobStatus(String jobID){
    //find any job matching the ID. In pendingJobs
    Stream<Job> pendingStream = pendingJobs.stream();
    String pendingStatus = pendingStream
        .filter(job -> job.getJobID().equals(jobID))
        .map(job -> job.getStatus()) 
        .findFirst()
        .orElse(null); // Return null if not found
    if(pendingStatus != null){
      return pendingStatus;
    }

    //Search inProgressJobs
    Stream<Job> inProgressStream = inProgressJobs.stream();
    String inProgressStatus = inProgressStream
        .filter(job -> job.getJobID().equals(jobID))
        .map(job -> job.getStatus())
        .findFirst()
        .orElse(null);

    if (inProgressStatus != null) {
        return inProgressStatus;
    }

    //Search archivedJobs
    Stream<Job> archivedStream = archivedJobs.stream();
    String archivedStatus = archivedStream
        .filter(job -> job.getJobID().equals(jobID))
        .map(job -> job.getStatus())
        .findFirst()
        .orElse(null);

    if (archivedStatus != null) {
        return archivedStatus;
    }

    return "Job not found";

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
      output.append("\n: ");
    }
    if(output.length() == 0){
      return "No Jobs pending in the queue.";
    }
    return output.toString();
  }

  /**
   * Checks if a vehicle with the given signature is already in the system's
   * live lists (either available or active).
   */
  public boolean isVehicleInSystem(String license, String state) {
    String signature = license + state;
    // Check available list
    boolean inAvailable = availableVehicles.stream()
            .anyMatch(vehicle -> vehicle.getSignature().equals(signature));
            
    if (inAvailable) {
        return true; 
    }

    // Check active list
    boolean inActive = activeVehicles.stream()
            .anyMatch(vehicle -> vehicle.getSignature().equals(signature));

    return inActive;
  }

  /**
   * Checks if a Job ID already exists in any of the controller's lists.
   */
  public boolean isJobInSystem(String jobID) {
    // Check pending jobs
    if (pendingJobs.stream().anyMatch(job -> job.getJobID().equals(jobID))) {
        return true;
    }
    // Check in-progress jobs
    if (inProgressJobs.stream().anyMatch(job -> job.getJobID().equals(jobID))) {
        return true;
    }
    // Check archived jobs
    if (archivedJobs.stream().anyMatch(job -> job.getJobID().equals(jobID))) {
        return true;
    }
    return false; 
  }

  //get inprogress jobs
  public List<Job> getInProgressJobs() {
    return inProgressJobs;
  }
  //
  public Queue<Job> getPendingJobs() {
    return pendingJobs;
  }
  
}
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.Map;   
import java.util.HashMap; 
import java.util.stream.Collectors; 
import java.io.*; 
import javax.swing.JOptionPane; 

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

  //Constructor
  public VCController(Server server){
    this.systemServer = server;
    
    // Attempt to load existing state if file exists
    if (!loadState()) {
      // If load failed or file didn't exist, initialize fresh state
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

  /** Saves the current state of job lists and maps. */
  private void saveState() {
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

  /** Loads the state from file upon startup. 
      populates activeVehicles list using the loaded vehicleJobMap keys. */
  @SuppressWarnings("unchecked")
  private boolean loadState() {
      File stateFile = new File(STATE_FILE);
      if (!stateFile.exists()) return false;
      
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
          // Deserialize in the same order they were written
          this.pendingJobs = (LinkedList<Job>) ois.readObject();
          this.inProgressJobs = (List<Job>) ois.readObject();
          this.archivedJobs = (List<Job>) ois.readObject();
          this.jobVehicleMap = (Map<Job, List<Vehicle>>) ois.readObject();
          this.vehicleJobMap = (Map<Vehicle, Job>) ois.readObject();
          
          // Initialize lists and populate activeVehicles using loaded data.
          this.availableVehicles = new ArrayList<>();
          this.activeVehicles = new ArrayList<>();
          
          // Any vehicle in the vehicleJobMap (which was loaded) is considered active.
          this.activeVehicles.addAll(this.vehicleJobMap.keySet());
          System.out.println("Loaded " + this.activeVehicles.size() + " active vehicles from state.");
        
          
          return true;
      } catch (IOException | ClassNotFoundException e) {
          System.err.println("Error loading VCController state: " + e.getMessage());
          stateFile.delete(); 
          return false;
      }
  }
  
  //Add a new job to the pending jobs queue
  public void addJob(Job job){ 
    pendingJobs.add(job);
    System.out.println("Job " + job.getJobID() + " added to pending queue.");
    saveState(); 
    scheduleJobs();
  }

  //Schedule jobs
  private void scheduleJobs(){
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

  //assign job to vehicle(s) 
  private void assignJob(Job job){
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
    }
    jobVehicleMap.put(job, assignedVehicles);
  }

  //receive and store checkpoint from a vehicle
  public void handleCheckpoint(Checkpoint checkpoint){
    System.out.println("Checkpoint received for vehicle: " + checkpoint.getVehicleID() + " at "
     + checkpoint.getTimestamp());
    systemServer.storeCheckpoint(checkpoint);
  }

  //move completed job to archived list. Notify the server.
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
        activeVehicles.remove(vehicle);
        vehicle.markAvailable();
        availableVehicles.add(vehicle);
        vehicleJobMap.remove(vehicle);
        
        System.out.println("Vehicle " + vehicle.getVehicleID() + " is now available.");
    }

    jobVehicleMap.remove(job);
    
    // Move job from in-progress to archived
    inProgressJobs.remove(job);
    archivedJobs.add(job);
    job.updateStatus("Completed");

    System.out.println("Job " + job.getJobID() + " marked as 'Completed'.");
    this.transferJobToServer(job); 
    
    saveState(); 

    scheduleJobs();
  }
  
  // transfers completed job to server
  private void transferJobToServer(Job job){
    systemServer.storeCompletedJob(job);
    System.out.println("Job " + job.getJobID() + " data transferred to server.");
  }


  //removes vehicle from list and re-queue job OR implements recovery
  public void handleVehicleDeparture(Vehicle vehicle){ //called by leaving vehicle
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
          
        // Remove vehicle from the Job->Vehicle map
        jobVehicleMap.computeIfPresent(interruptedJob, (job, list) -> {
            list.remove(vehicle);
            return list;
        });

        // Check if the job is now running on ZERO vehicles
        List<Vehicle> remainingVehicles = jobVehicleMap.get(interruptedJob);
        if (remainingVehicles == null || remainingVehicles.isEmpty()) {
            
            if (!availableVehicles.isEmpty()) {
                
                // 1. Attempt Recovery: Get the latest checkpoint
                Checkpoint latestCheckpoint = systemServer.getLatestCheckpoint(interruptedJob.getJobID()); 
                
                if (latestCheckpoint != null) {
                    // 2. Recruit the replacement vehicle
                    Vehicle replacementVehicle = availableVehicles.remove(0); 
                    activeVehicles.add(replacementVehicle);
                    
                    // 3. Restart computation on the new vehicle
                    restartComputation(latestCheckpoint, replacementVehicle);
                    
                    // 4. Update maps for the replacement
                    List<Vehicle> updatedVehicles = new ArrayList<>();
                    updatedVehicles.add(replacementVehicle);
                    jobVehicleMap.put(interruptedJob, updatedVehicles);
                    vehicleJobMap.put(replacementVehicle, interruptedJob);
                    
                    System.out.println("Job " + interruptedJob.getJobID() + " **recovered** on new vehicle: " 
                     + replacementVehicle.getVehicleID() + " from checkpoint.");
                     
                } else {
                    // Fallback: Re-queue if no valid checkpoint is found
                    inProgressJobs.remove(interruptedJob);
                    pendingJobs.add(interruptedJob);
                    interruptedJob.updateStatus("Pending(Interrupted)");
                    jobVehicleMap.remove(interruptedJob);
                    System.out.println("Job " + interruptedJob.getJobID() 
                     + " re-queued. No valid checkpoint found for recovery.");
                }
                 
            } else {
                // Fallback: Re-queue if no vehicles are available for immediate replacement
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
  
  //finds vehicle and instructs creation of checkpoint 
  public void triggerCheckpoint(Job job){

    if(!inProgressJobs.contains(job)){
      System.out.println("Error: Cannot trigger checkpoint. Job " 
      + job.getJobID() + " is not in progress.");
      return;
    }
    System.out.println("Triggering checkpoint for Job " + job.getJobID() + "...");
    
    int vehiclesTriggered = 0;
    
    //Find the vehicles using the map
    List<Vehicle> targetVehicles = jobVehicleMap.getOrDefault(job, new ArrayList<>());

    for(Vehicle vehicle: targetVehicles){
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
    saveState(); 
    scheduleJobs();
  }

  //Instructs vehicle to load its state from a specific checkpoint
  private void restartComputation(Checkpoint checkpoint, Vehicle newVehicle){
    System.out.println("Instructing vehicle " + newVehicle.getVehicleID() 
     + " to restart computation from checkpoint " 
    + checkpoint.getCheckpointID());

    newVehicle.loadFromCheckpoint(checkpoint);
  }

  //called by client to check status of a job(via GUI)
  public String getJobStatus(String jobID){
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
    return Stream.of(pendingJobs, inProgressJobs, archivedJobs)
            .flatMap(List::stream)
            .anyMatch(job -> job.getJobID().equals(jobID));
  }

  public List<Job> getInProgressJobs() {
    return inProgressJobs;
  }
  
  public Queue<Job> getPendingJobs() {
    return pendingJobs;
  }
  
}
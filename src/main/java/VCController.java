import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.stream.Stream;

public class VCController {
  private List<Vehicle> availableVehicles; //Vehicle class
  private List<Vehicle> activeVehicles;
  private Queue<Job> pendingJobs;
  private List<Job> inProgressJobs;
  private List<Job> archivedJobs;
  private Server systemServer; //server class


  //Constructor
  public VCController(Server server){
    // We use ArrayList as the concrete implementation of the List interface
    this.availableVehicles = new ArrayList<>();
    this.activeVehicles = new ArrayList<>();
    this.pendingJobs = new LinkedList<>();
    this.inProgressJobs = new ArrayList<>();
    this.archivedJobs = new ArrayList<>();
    this.systemServer = server;
  }

  //Add a new job to the pending jobs queue
  public void addJob(Job job){ 
    pendingJobs.add(job);
    System.out.println("Job " + job.getJobID() + " added to pending queue.");
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
      assignJob(jobToAssign);
      inProgressJobs.add(jobToAssign);//move to progess
      jobToAssign.updateStatus("In-Progess");
      System.out.println("Job " + jobToAssign.getJobID() + " started.");

    }else{

      System.out.println(System.out.println("Job " + nextJob.getJobID() + " postponed. Waiting for " 
      + requiredVehicles + " vehicle(s)."));
    }

  }

  //assign job to vehicle(s)
  private void assignJob(Job job){
    int redundancyLevel = job.getRedundancyLevel();

    System.out.println("Assigning Job " + job.getJobID() 
     + " to " + redundancyLevel + " vehicle(s).");
    
    for(int i = 0; i < redundancyLevel; i++){
      Vehicle vehicleToAssign = availableVehicles.remove(0);
      activeVehicles.add(vehicleToAssign);
      vehicleToAssign.executeJob(job);
    }
  }

  //receive and store checkpoint from a vehicle
  public void handleCheckpoint(Checkpoint checkpoint){
    System.out.println("Checkpoint received for vehicle: " + checkpoint.getVehicleID() + " at "
     + checkpoint.getTimestamp());
    systemServer.storeCheckpoint(checkpoint);
  }

  //move completed job to archived list. Notify the server
  public void handleJobCompletion(Job job){
    if(archivedJobs.contains(job)){
      System.out.println("Job " + job.getJobID() + " is already archived.");
      return;
    }
    //move job from in-prgoress to archived
    inProgressJobs.remove(job);
    archivedJobs.add(job);
    job.updateStatus("Completed");

    System.out.println("Job " + job.getJobID() + " marked as 'Completed'.");
    this.transferJobToServer(job); //transfer to server

    //remove items from activeVehicles
    //find the vehicle(s) that were working on this job
    Iterator<Vehicle> iterator = activeVehicles.iterator();
    while(iterator.hasNext()){
      Vehicle vehicle = iterator.next();
      if(vehicle.getCurrentJob() != null && vehicle.getCurrentJob().equals(job)){
        //erase vehicle's data
        //vehicle ready to work on another job
        this.clearVehicleData(vehicle);
        iterator.remove();
        activeVehicles.add(vehicle);

        System.out.println("Vehicle " + vehicle.getVehicleID() + " is now available.");
      }
    }
  }
  // transfers completed job to server
  private void transferJobToServer(Job job){
    systemServer.storeCompletedJob(job);// calls from server
    System.out.println("Job " + job.getJobID() + " data transferred to server.");
  }

  // clears vehicle data
  private void clearVehicleData(Vehicle vehicle){
    vehicle.eraseJobData(); //calls from vehicle
    System.out.println("Data erased from Vehicle " + vehicle.getVehicleID() + ".");
  }

  //removes vehicle from list and re-queue job
  public void handleVehicleDeparture(Vehicle vehicle){ //called by leaving vehicle
    System.out.println("Vehicle " + vehicle.getVehicleID() + " is departing...");
    
    if(availableVehicles.remove(vehicle)){
      System.out.println("Vehicle removed from available pool");
      return;
    }

    //if not in available, then should be in activeList
    if(activeVehicles.remove(vehicle)){
      System.out.println("Vehicle removed from active pool.");

      //since it was active, handle its job
      Job interruptedJob = vehicle.getCurrentJob();
      //remove from inProgress list and requeue
      if(interruptedJob != null){
        inProgressJobs.remove(interruptedJob);
        pendingJobs.add(interruptedJob);
        interruptedJob.updateStatus("Pending(Interrupted)");
        System.out.println("Job " + interruptedJob.getJobID() 
         + " was interrupted and has been re-queued.");
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
    //find the vehicle working on the target job
    //since we can have redundancyLevel > 0
    for(Vehicle vehicle: activeVehicles){
      if(vehicle.getCurrentJob() != null && vehicle.getCurrentJob().equals(job)){
        vehicle.createCheckpoint();//calls from vehicle
        vehiclesTriggered++;
      }
    }
    System.out.println("Checkpoint signal sent to " + vehiclesTriggered + " vehicle(s).");
  }

  //add vehicle to system's pool of available vehicles.
  public void recruitVehicle(Vehicle vehicle){
    this.availableVehicles.add(vehicle);
    System.out.println("New vehicle recruited: " + vehicle.getVehicleID() 
    + ". Now available for jobs.");
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
}

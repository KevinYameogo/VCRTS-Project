import java.io.Serializable;

public class Job implements Serializable {
    private static final long serialVersionUID = 1L;



//status ="pending";
@Override
    public String toString() {
        // This is what will be displayed in the JList on Tab 2
        return "Job ID: " + jobID + " (" + status + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Job job = (Job) obj;
        return jobID.equals(job.jobID);
    }

    @Override
    public int hashCode() {
        return jobID.hashCode();
    }


}

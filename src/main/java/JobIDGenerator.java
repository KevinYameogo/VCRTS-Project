import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class JobIDGenerator {
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("HHmmss");

    /**
     * Generates a unique Job ID by combining the client's fixed ID and a unique timestamp/random suffix.
     * Format: [Client_Secure_ID]-[HHmmss]-[Random 3-digit]
     * @param clientSecureID The fixed, 6-char ID of the client submitting the job.
     * @return A unique String Job ID.
     */
    public static String generateUniqueJobID(String clientSecureID) {
        String timestampPart = LocalDateTime.now().format(TS_FMT);
        // Generate a random 3-digit number (100 to 999)
        int randomPart = ThreadLocalRandom.current().nextInt(100, 1000); 
        
        // Ensure the fixed client ID is always at the start for traceability
        return clientSecureID + "-" + timestampPart + "-" + randomPart;
    }
}
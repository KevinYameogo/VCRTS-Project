import java.util.Random;

public class ClientIDGenerator {

    // Characters for the random ID, including special chars as requested
    private static final String ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@/$%*!";
    private static final int ID_LENGTH = 6;
    private static final Random RANDOM = new Random();

    /**
     * Generates a random 6-character ID from a pool of letters, numbers, and special symbols.
     * @return A unique 6-character client ID.
     */
    public static String generateRandomID() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            int index = RANDOM.nextInt(ID_CHARS.length());
            sb.append(ID_CHARS.charAt(index));
        }
        return sb.toString();
    }
}
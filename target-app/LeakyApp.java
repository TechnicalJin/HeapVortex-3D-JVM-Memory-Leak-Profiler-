import java.util.HashMap;
import java.util.Map;

public class LeakyApp {

    // Static map that is never cleared: this is the deliberate memory leak.
    private static final Map<String, byte[]> LEAK = new HashMap<>();

    public static void main(String[] args) throws Exception {
        int kbPerEntry = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        long delayMs = args.length > 1 ? Long.parseLong(args[1]) : 50;

        System.out.println("LeakyApp started. PID = " + ProcessHandle.current().pid());
        System.out.println("Leaking " + kbPerEntry + " KB every " + delayMs + " ms");

        int i = 0;
        while (true) {
            LEAK.put("key-" + i++, new byte[kbPerEntry * 1024]);
            if (i % 100 == 0) {
                System.out.println("Leaked entries: " + LEAK.size());
            }
            Thread.sleep(delayMs);
        }
    }
}
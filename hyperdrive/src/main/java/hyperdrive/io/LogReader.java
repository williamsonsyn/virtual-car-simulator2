package hyperdrive.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the log file back for the history screen. Uses FileReader wrapped in a BufferedReader
 * (readLine() only exists on the buffered reader, not on FileReader directly).
 */
public class LogReader {

    // Private constructor: this class is just a pair of static utility methods, never instantiated.
    private LogReader() { }

    /**
     * Reads every line. THROWS the checked exception outward instead of swallowing it,
     * for a caller that wants to know for certain whether the read succeeded.
     */
    public static List<String> readAll(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Reads just the last N lines, for a compact history view.
     * Missing file or a read error is NOT fatal here - returns whatever it managed
     * to read (possibly empty), since a history screen should degrade gracefully.
     */
    public static List<String> readLastLines(String filePath, int maxLines) {
        List<String> recent = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) {
            return recent;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                recent.add(line);
                if (recent.size() > maxLines) {
                    recent.remove(0);   // sliding window: keep only the most recent maxLines
                }
            }
        } catch (IOException e) {
            System.err.println("LogReader: failed to read " + filePath + " - " + e.getMessage());
        }
        return recent;
    }
}

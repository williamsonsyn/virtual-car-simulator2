package hyperdrive.io;

import hyperdrive.model.Notification;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes the session's notifications to a log file, one line each, as they happen.
 * Uses FileWriter wrapped in a BufferedWriter (buffering avoids a disk write on every single line).
 * The file is opened in APPEND mode, so history builds up across runs rather than being overwritten.
 *
 * open()/close() use a classic try/catch/finally on purpose (rather than try-with-resources) -
 * this is the plain, explicit form of manual resource cleanup.
 */
public class Logger {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String filePath;
    private BufferedWriter writer;

    public Logger(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() { return filePath; }

    /** Opens the file for appending, creating its parent folder if needed. */
    public void open() {
        try {
            File file = new File(filePath);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            writer = new BufferedWriter(new FileWriter(file, true));   // true = append
            writeRaw("=== Session started " + LocalDateTime.now().format(TIMESTAMP_FORMAT) + " ===");
        } catch (IOException e) {
            System.err.println("Logger: could not open " + filePath + " - " + e.getMessage());
            writer = null;   // logging is best-effort; the simulation must not crash over a log file
        }
    }

    /** Called automatically by NotificationManager as each notification is added. */
    public void log(Notification n) {
        String line = n.getTimestamp() > 0
                ? formatTimestamp(n.getTimestamp()) + " " + n
                : n.toString();
        writeRaw(line);
    }

    private void writeRaw(String line) {
        if (writer == null) {
            return;   // never opened, or opening failed - fail silently rather than crash the car
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();   // flush per line: a crash mid-session shouldn't lose recent history
        } catch (IOException e) {
            System.err.println("Logger: failed to write - " + e.getMessage());
        }
    }

    private String formatTimestamp(long epochMillis) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault()
        ).format(TIMESTAMP_FORMAT);
    }

    public void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            System.err.println("Logger: failed to close - " + e.getMessage());
        } finally {
            writer = null;
        }
    }
}

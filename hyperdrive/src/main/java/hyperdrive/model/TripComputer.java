package hyperdrive.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Lightweight trip computer with two trips: TRIP SINCE START (resets every ignition cycle) and TRIP LONG TERM (kept
 * between sessions in a small properties file using plain Java I/O - no database). Also keeps the session distance.
 * Fuel used is the real (unscaled) flow, so average consumption is believable.
 */
public class TripComputer {

    /** One trip's totals. Immutable-by-copy: the copy constructor is what the telemetry snapshot hands to the UI. */
    public static class TripData {
        private double distanceKm;
        private double seconds;
        private double fuelLitres;

        public TripData() { }

        public TripData(double distanceKm, double seconds, double fuelLitres) {
            this.distanceKm = distanceKm;
            this.seconds = seconds;
            this.fuelLitres = fuelLitres;
        }

        // Copy constructor
        public TripData(TripData other) {
            this(other.distanceKm, other.seconds, other.fuelLitres);
        }

        void add(double km, double secs, double litres) {
            distanceKm += km;
            seconds += secs;
            fuelLitres += litres;
        }

        public double getDistanceKm() { return distanceKm; }
        public double getSeconds() { return seconds; }
        public double getFuelLitres() { return fuelLitres; }

        public double getAverageSpeedKmh() { return seconds > 1 ? distanceKm / (seconds / 3600.0) : 0.0; }

        /** L/100 km, or 0 if the trip is too short to say. */
        public double getAverageConsumptionL100() { return distanceKm > 0.05 ? fuelLitres / distanceKm * 100.0 : 0.0; }

        /** hh:mm:ss */
        public String getTimeText() {
            long s = (long) seconds;
            return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        }
    }

    private final TripData sinceStart = new TripData();
    private final TripData longTerm = new TripData();
    private double sessionDistanceKm = 0;

    /** Advance by dt seconds. Trip time only counts while the engine is running. */
    public void update(double dt, double speedKmh, double fuelFlowLitresPerHour, boolean engineRunning) {
        double km = speedKmh * dt / 3600.0;
        double secs = engineRunning ? dt : 0.0;
        double litres = fuelFlowLitresPerHour * dt / 3600.0;
        sinceStart.add(km, secs, litres);
        longTerm.add(km, secs, litres);
        sessionDistanceKm += km;
    }

    public void resetSinceStart() {
        sinceStart.distanceKm = 0;
        sinceStart.seconds = 0;
        sinceStart.fuelLitres = 0;
    }

    public TripData getSinceStart() { return new TripData(sinceStart); }
    public TripData getLongTerm() { return new TripData(longTerm); }
    public double getSessionDistanceKm() { return sessionDistanceKm; }

    // ------------------------------------------------------------------ persistence (Java I/O)

    /** Saves the long-term trip. Best-effort: a failure is reported but never crashes the simulator. */
    public void saveLongTerm(String path) {
        Properties p = new Properties();
        p.setProperty("distanceKm", Double.toString(longTerm.distanceKm));
        p.setProperty("seconds", Double.toString(longTerm.seconds));
        p.setProperty("fuelLitres", Double.toString(longTerm.fuelLitres));
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            p.store(writer, "HyperDrive long-term trip");
        } catch (IOException e) {
            System.err.println("TripComputer: could not save " + path + " - " + e.getMessage());
        }
    }

    /** Loads the long-term trip if the file exists; otherwise starts from zero. */
    public void loadLongTerm(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        Properties p = new Properties();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            p.load(reader);
            longTerm.distanceKm = Double.parseDouble(p.getProperty("distanceKm", "0"));
            longTerm.seconds = Double.parseDouble(p.getProperty("seconds", "0"));
            longTerm.fuelLitres = Double.parseDouble(p.getProperty("fuelLitres", "0"));
        } catch (IOException | NumberFormatException e) {
            System.err.println("TripComputer: could not read " + path + " - " + e.getMessage());
        }
    }
}

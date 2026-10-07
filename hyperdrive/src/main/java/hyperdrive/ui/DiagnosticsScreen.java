package hyperdrive.ui;

import hyperdrive.io.LogReader;
import hyperdrive.model.Car;
import hyperdrive.telemetry.TelemetrySnapshot;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.List;

/**
 * The Diagnostics screen: live SafetyCheck results, system self-test status, and recent session
 * log history read back from disk. Needs no new Car methods - getDiagnosticReport() and
 * getSystemStatusLines() already existed from the console steps, and LogReader.readLastLines()
 * already existed from Step 6's file logging.
 *
 * Log history is deliberately NOT refreshed every animation frame, unlike the checks and system
 * status (which are cheap in-memory calls) - re-reading a file from disk 60 times a second for
 * data that only changes on discrete events would be wasteful. It refreshes when this screen
 * becomes visible (see HyperDriveApp.showDiagnostics()) and via its own Refresh button.
 */
public class DiagnosticsScreen extends BorderPane implements Screen {
    private static final int LOG_HISTORY_LINES = 20;

    private final Car car;
    private final String logFilePath;

    private final VBox checksList = new VBox(6);
    private final VBox statusList = new VBox(6);
    private final VBox logList = new VBox(2);

    public DiagnosticsScreen(Car car, String logFilePath) {
        this.car = car;
        this.logFilePath = logFilePath;
        getStyleClass().add("diagnostics-screen");
        setPadding(new Insets(20));

        Label checksHeader = new Label("SAFETY CHECKS (PASS / WARN / FAIL / CRIT)");
        checksHeader.getStyleClass().add("diag-header");
        VBox checksPanel = new VBox(10, checksHeader, checksList);
        checksPanel.getStyleClass().add("side-panel");
        checksPanel.setPadding(new Insets(14));

        Label statusHeader = new Label("SYSTEM STATUS");
        statusHeader.getStyleClass().add("diag-header");
        ScrollPane statusScroll = new ScrollPane(statusList);
        statusScroll.getStyleClass().add("log-scroll");
        statusScroll.setFitToWidth(true);
        statusScroll.setPrefHeight(300);
        VBox statusPanel = new VBox(10, statusHeader, statusScroll);
        statusPanel.getStyleClass().add("side-panel");
        statusPanel.setPadding(new Insets(14));

        HBox topRow = new HBox(16, checksPanel, statusPanel);
        setCenter(topRow);

        Label logHeader = new Label("RECENT SESSION LOG");
        logHeader.getStyleClass().add("diag-header");
        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("fault-toggle-button");
        refreshButton.setOnAction(e -> refreshLogHistory());
        HBox logHeaderRow = new HBox(12, logHeader, refreshButton);

        ScrollPane logScroll = new ScrollPane(logList);
        logScroll.getStyleClass().add("log-scroll");
        logScroll.setFitToWidth(true);
        logScroll.setPrefHeight(180);

        VBox logPanel = new VBox(10, logHeaderRow, logScroll);
        logPanel.getStyleClass().add("side-panel");
        logPanel.setPadding(new Insets(14));
        BorderPane.setMargin(logPanel, new Insets(16, 0, 0, 0));
        setBottom(logPanel);

        refreshLogHistory();
    }

    @Override
    public void refresh(TelemetrySnapshot telemetry) {
        checksList.getChildren().clear();
        for (hyperdrive.safety.DiagnosticResult result : car.getDiagnosticResults()) {
            Label label = new Label(result.toString());
            label.getStyleClass().add(switch (result.getLevel()) {
                case PASS -> "diag-pass";
                case WARNING -> "diag-warn";
                default -> "diag-fail";
            });
            checksList.getChildren().add(label);
        }
        Label overall = new Label("OVERALL: " + car.getDiagnosticLevel());
        overall.getStyleClass().add(car.getDiagnosticLevel() == hyperdrive.enums.DiagnosticLevel.PASS ? "diag-pass"
                : (car.getDiagnosticLevel() == hyperdrive.enums.DiagnosticLevel.WARNING ? "diag-warn" : "diag-fail"));
        checksList.getChildren().add(overall);

        statusList.getChildren().clear();
        for (String line : car.getSystemStatusLines()) {
            Label label = new Label(line);
            label.getStyleClass().add(line.contains("self-test OK") ? "diag-pass" : "diag-fail");
            statusList.getChildren().add(label);
        }
    }

    /** Re-reads the log file from disk. Called when this screen becomes visible, or on demand. */
    public void refreshLogHistory() {
        logList.getChildren().clear();
        List<String> lines = LogReader.readLastLines(logFilePath, LOG_HISTORY_LINES);
        if (lines.isEmpty()) {
            Label empty = new Label("(no log entries yet)");
            empty.getStyleClass().add("hint-label");
            logList.getChildren().add(empty);
            return;
        }
        for (String line : lines) {
            Label label = new Label(line);
            label.getStyleClass().add("log-line");
            logList.getChildren().add(label);
        }
    }
}

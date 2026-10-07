package hyperdrive.ui;

import hyperdrive.telemetry.TelemetrySnapshot;
import javafx.scene.layout.BorderPane;

/**
 * The cockpit: a digital instrument cluster, inspired by the 720S driver display, driven entirely by telemetry.
 * It is only a Screen wrapper around ClusterView - all drawing lives in ClusterRenderer, all display smoothing in
 * ClusterState, and every value comes from the TelemetrySnapshot (which comes from the real vehicle systems).
 */
public class CockpitScreen extends BorderPane implements Screen {
    private final ClusterView cluster = new ClusterView();

    public CockpitScreen() {
        getStyleClass().add("cockpit-screen");
        setCenter(cluster);
    }

    @Override
    public void refresh(TelemetrySnapshot telemetry) {
        // Only repaint while this screen is actually on show: other screens do not need 60 fps of canvas drawing.
        if (getScene() != null && getParent() != null) {
            cluster.update(telemetry);
        }
    }

    /** J key: next page of the left-hand information panel. */
    public void nextPage() { cluster.nextPage(); }
}

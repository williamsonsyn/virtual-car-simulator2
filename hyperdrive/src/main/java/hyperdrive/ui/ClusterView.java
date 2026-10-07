package hyperdrive.ui;

import hyperdrive.telemetry.TelemetrySnapshot;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;

/**
 * The only JavaFX plumbing for the instrument cluster: a Pane that owns one Canvas, keeps it exactly as big as the
 * pane (so the cluster follows window resizing) and asks the ClusterRenderer to repaint it. A Canvas cannot resize
 * itself, which is why layoutChildren() is overridden.
 */
public class ClusterView extends Pane {
    private final Canvas canvas = new Canvas();
    private final ClusterState state = new ClusterState();
    private final ClusterRenderer renderer = new ClusterRenderer();

    public ClusterView() {
        getStyleClass().add("cluster-pane");
        setMinSize(0, 0);
        getChildren().add(canvas);
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
    }

    /** Called once per frame with the latest snapshot: animate the display state, then repaint. */
    public void update(TelemetrySnapshot telemetry) {
        state.update(telemetry);
        if (canvas.getWidth() < 2 || canvas.getHeight() < 2) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        renderer.render(gc, canvas.getWidth(), canvas.getHeight(), state, System.currentTimeMillis());
    }

    public void nextPage() { state.nextPage(); }
    public void previousPage() { state.previousPage(); }
    public ClusterState getState() { return state; }
}

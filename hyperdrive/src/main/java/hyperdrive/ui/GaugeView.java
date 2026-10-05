package hyperdrive.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Rotate;

/**
 * A simple semicircular gauge, like a car's tachometer or speedometer: a track arc, an optional
 * red "redline" zone near the top of the range, and a needle that rotates to point at the
 * current value. One component, reused for both RPM and speed in CockpitScreen with different
 * min/max/redline/unit - same as how one Screen interface serves multiple concrete screens.
 *
 * Angle math: JavaFX's Arc uses MATHEMATICAL angles (0=right, 90=up, counterclockwise-positive).
 * Node.setRotate() (and the Rotate transform used here) uses SCREEN angles (clockwise-positive).
 * Mixing these two conventions without converting is the most common bug in a JavaFX gauge -
 * the conversion happens once, in setValue(), rather than being scattered through the class.
 */
public class GaugeView extends Pane {
    private static final double SIZE = 180;
    private static final double CENTER_X = SIZE / 2;
    private static final double CENTER_Y = SIZE / 2 + 14;
    private static final double RADIUS = 75;
    private static final double NEEDLE_LENGTH = 60;

    private final double minValue;
    private final double maxValue;
    private final Rotate needleRotation = new Rotate(-90, CENTER_X, CENTER_Y);   // -90 = pointing at minValue
    private final Label valueLabel;

    public GaugeView(String title, double minValue, double maxValue, double redlineStart, String unit) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        setPrefSize(SIZE, SIZE);
        setMinSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);

        Arc track = new Arc(CENTER_X, CENTER_Y, RADIUS, RADIUS, 180, -180);
        track.setType(ArcType.OPEN);
        track.setFill(null);
        track.setStroke(Color.web("#2a2f38"));
        track.setStrokeWidth(10);
        track.setStrokeLineCap(StrokeLineCap.BUTT);

        double redlineFraction = clampFraction(redlineStart);
        double redlineStartAngle = 180 - redlineFraction * 180;     // math angle at the redline threshold
        double redlineSweep = (1.0 - redlineFraction) * 180;        // how far from there to max
        Arc redlineArc = new Arc(CENTER_X, CENTER_Y, RADIUS, RADIUS, redlineStartAngle, -redlineSweep);
        redlineArc.setType(ArcType.OPEN);
        redlineArc.setFill(null);
        redlineArc.setStroke(Color.web("#ff3b3b"));
        redlineArc.setStrokeWidth(10);
        redlineArc.setStrokeLineCap(StrokeLineCap.BUTT);

        Line needle = new Line(CENTER_X, CENTER_Y, CENTER_X, CENTER_Y - NEEDLE_LENGTH);   // points "up" unrotated
        needle.setStroke(Color.web("#e7e9ec"));
        needle.setStrokeWidth(3);
        needle.getTransforms().add(needleRotation);

        Circle hub = new Circle(CENTER_X, CENTER_Y, 5);
        hub.setFill(Color.web("#e7e9ec"));

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-family: 'Consolas','Menlo',monospace; -fx-font-size: 10px;");
        titleLabel.setLayoutX(CENTER_X - 35);
        titleLabel.setLayoutY(CENTER_Y - 48);
        titleLabel.setPrefWidth(70);
        titleLabel.setAlignment(Pos.CENTER);

        valueLabel = new Label("0");
        valueLabel.setStyle("-fx-text-fill: #f4f6f8; -fx-font-family: 'Consolas','Menlo',monospace; "
                + "-fx-font-size: 20px; -fx-font-weight: bold;");
        valueLabel.setLayoutX(CENTER_X - 30);
        valueLabel.setLayoutY(CENTER_Y - 26);
        valueLabel.setPrefWidth(60);
        valueLabel.setAlignment(Pos.CENTER);

        Label unitLabel = new Label(unit);
        unitLabel.setStyle("-fx-text-fill: #565c64; -fx-font-family: 'Consolas','Menlo',monospace; -fx-font-size: 9px;");
        unitLabel.setLayoutX(CENTER_X - 25);
        unitLabel.setLayoutY(CENTER_Y + 2);
        unitLabel.setPrefWidth(50);
        unitLabel.setAlignment(Pos.CENTER);

        getChildren().addAll(track, redlineArc, needle, hub, titleLabel, valueLabel, unitLabel);
    }

    public void setValue(double value) {
        double fraction = clampFraction(value);
        needleRotation.setAngle(fraction * 180 - 90);   // see class doc for the angle-convention conversion
        valueLabel.setText(String.format("%.0f", value));
    }

    private double clampFraction(double value) {
        if (maxValue <= minValue) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (value - minValue) / (maxValue - minValue)));
    }
}

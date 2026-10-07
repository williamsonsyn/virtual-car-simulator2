package hyperdrive.ui;

import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.BatteryState;
import hyperdrive.enums.EpbState;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LaunchState;
import hyperdrive.enums.LiftState;
import hyperdrive.enums.PowerState;
import hyperdrive.enums.Severity;
import hyperdrive.enums.StartupPhase;
import hyperdrive.enums.WarningLight;
import hyperdrive.enums.TyreCondition;
import hyperdrive.enums.EngineState;
import hyperdrive.model.Notification;
import hyperdrive.model.TripComputer;
import hyperdrive.systems.Tyre;
import hyperdrive.telemetry.TelemetrySnapshot;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Draws the HYPERDRIVE X-01 digital instrument cluster (inspired by the 720S driver display) onto a JavaFX canvas.
 * Pure drawing: it receives a ClusterState (smoothed values + the latest TelemetrySnapshot) and paints it. It never
 * reads the Car and never decides anything - every number on screen comes from telemetry.
 *
 * Responsive: everything is laid out in a fixed 1000 x 540 "design space" which is scaled uniformly to fit the
 * canvas and centred, so the tachometer stays dominant and the side panels stay balanced at any window size.
 */
public final class ClusterRenderer {
    public static final double W = 1000;
    public static final double H = 540;

    // Tachometer geometry (design units)
    private static final double CX = 500;
    private static final double CY = 282;
    private static final double R = 200;
    private static final double RPM_MAX = 9000;
    private static final double RPM_REDLINE = 8000;

    // Palette
    private static final Color BG_TOP = Color.rgb(8, 12, 22);
    private static final Color BG_BOTTOM = Color.rgb(3, 5, 10);
    private static final Color TRACK = Color.rgb(28, 36, 52);
    private static final Color TEXT = Color.rgb(232, 236, 243);
    private static final Color DIM = Color.rgb(112, 124, 142);
    private static final Color FAINT = Color.rgb(52, 62, 80);
    private static final Color ORANGE = Color.rgb(255, 138, 31);
    private static final Color SPORT_AMBER = Color.rgb(255, 170, 30);
    private static final Color TRACK_RED = Color.rgb(255, 92, 40);
    private static final Color RED = Color.rgb(255, 59, 48);
    private static final Color AMBER = Color.rgb(255, 176, 0);
    private static final Color GREEN = Color.rgb(56, 210, 107);
    private static final Color BLUE = Color.rgb(77, 163, 255);
    private static final Color CYAN = Color.rgb(59, 227, 255);

    private final Map<String, Font> fontCache = new HashMap<>();
    private double wake = 1.0;   // display brightness for the current frame (0 = off)

    // ------------------------------------------------------------------ entry point

    public void render(GraphicsContext gc, double width, double height, ClusterState st, long nowMillis) {
        gc.clearRect(0, 0, width, height);
        gc.setGlobalAlpha(1.0);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, width, height);

        double scale = Math.min(width / W, height / H);
        if (scale <= 0 || st.getTelemetry() == null) {
            return;
        }
        gc.save();
        gc.translate((width - W * scale) / 2.0, (height - H * scale) / 2.0);
        gc.scale(scale, scale);

        drawGlass(gc);
        wake = st.getWake();
        TelemetrySnapshot t = st.getTelemetry();
        if (wake < 0.03) {
            drawSleeping(gc);
        } else {
            gc.setGlobalAlpha(wake);
            Color accent = accentFor(st.getStyle());
            drawDividers(gc, accent);
            drawTachometer(gc, st, t, accent);
            drawCenter(gc, st, t, accent);
            drawLeftPanel(gc, st, t, accent);
            drawRightPanel(gc, st, t, accent);
            drawWarningBar(gc, st, t);
            drawToast(gc, st, nowMillis);
            gc.setGlobalAlpha(1.0);
        }
        gc.restore();
    }

    // ------------------------------------------------------------------ helpers

    private Font font(double size, FontWeight weight) {
        String key = weight + ":" + Math.round(size * 2);
        Font f = fontCache.get(key);
        if (f == null) {
            f = Font.font("System", weight, size);
            fontCache.put(key, f);
        }
        return f;
    }

    private Font italicFont(double size) {
        String key = "I:" + Math.round(size * 2);
        Font f = fontCache.get(key);
        if (f == null) {
            f = Font.font("System", FontWeight.BOLD, FontPosture.ITALIC, size);
            fontCache.put(key, f);
        }
        return f;
    }

    private void alpha(GraphicsContext gc, double a) {
        gc.setGlobalAlpha(Math.max(0.0, Math.min(1.0, a * wake)));
    }

    private void text(GraphicsContext gc, String s, double x, double y, double size, FontWeight w, Color c,
            TextAlignment align) {
        gc.setFont(font(size, w));
        gc.setFill(c);
        gc.setTextAlign(align);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(s, x, y);
    }

    private static Color withAlpha(Color c, double a) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0.0, Math.min(1.0, a)));
    }

    private static Color mix(Color a, Color b, double k) {
        k = Math.max(0.0, Math.min(1.0, k));
        return Color.color(a.getRed() + (b.getRed() - a.getRed()) * k, a.getGreen() + (b.getGreen() - a.getGreen()) * k,
                a.getBlue() + (b.getBlue() - a.getBlue()) * k, 1.0);
    }

    /** Comfort -> Sport -> Track accent colour, blended while the mode animates. */
    private static Color accentFor(double style) {
        if (style <= 1.0) {
            return mix(ORANGE, SPORT_AMBER, style);
        }
        return mix(SPORT_AMBER, TRACK_RED, style - 1.0);
    }

    private static Color severityColor(Severity s) {
        return switch (s) {
            case CRITICAL -> RED;
            case WARNING -> AMBER;
            default -> GREEN;
        };
    }

    /** Clockwise-from-top angle (degrees) of an RPM value on the dial. 6000 RPM is straight up. */
    static double rpmAngle(double rpm) {
        return -174.0 + 0.029 * rpm;
    }

    private static double px(double r, double theta) { return CX + r * Math.sin(Math.toRadians(theta)); }
    private static double py(double r, double theta) { return CY - r * Math.cos(Math.toRadians(theta)); }

    /** Stroke an arc of the dial between two RPM values (JavaFX arcs are counter-clockwise from 3 o'clock). */
    private void ringArc(GraphicsContext gc, double radius, double rpmFrom, double rpmTo) {
        double a1 = rpmAngle(rpmFrom);
        double a2 = rpmAngle(rpmTo);
        gc.strokeArc(CX - radius, CY - radius, radius * 2, radius * 2, 90.0 - a1, -(a2 - a1), ArcType.OPEN);
    }

    private static String clockText() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    // ------------------------------------------------------------------ glass / sleeping

    private void drawGlass(GraphicsContext gc) {
        gc.setGlobalAlpha(1.0);
        LinearGradient g = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, BG_TOP), new Stop(1, BG_BOTTOM));
        gc.setFill(g);
        gc.fillRoundRect(10, 8, W - 20, H - 16, 54, 54);
        gc.setStroke(Color.rgb(40, 48, 64));
        gc.setLineWidth(2);
        gc.strokeRoundRect(10, 8, W - 20, H - 16, 54, 54);
    }

    private void drawSleeping(GraphicsContext gc) {
        gc.setGlobalAlpha(0.35);
        gc.setFont(italicFont(26));
        gc.setFill(DIM);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("HYPERDRIVE  X-01", CX, CY - 8);
        gc.setFont(font(15, FontWeight.NORMAL));
        gc.fillText("IGNITION OFF  -  PRESS I", CX, CY + 26);
        gc.setGlobalAlpha(1.0);
    }

    // ------------------------------------------------------------------ dividers

    private void drawDividers(GraphicsContext gc, Color accent) {
        double[] xs = new double[3];
        double[] ys = new double[3];
        for (int pass = 0; pass < 2; pass++) {
            double w = pass == 0 ? 6 : 1.6;
            double a = pass == 0 ? 0.12 : 0.85;
            gc.setLineWidth(w);
            gc.setStroke(withAlpha(accent, a));
            gc.setLineCap(StrokeLineCap.ROUND);
            // left: top and bottom
            xs[0] = 52; xs[1] = 292; xs[2] = 332;
            ys[0] = 154; ys[1] = 154; ys[2] = 178;
            gc.strokePolyline(xs, ys, 3);
            ys[0] = 420; ys[1] = 420; ys[2] = 396;
            gc.strokePolyline(xs, ys, 3);
            // right: mirrored
            xs[0] = 948; xs[1] = 708; xs[2] = 668;
            ys[0] = 154; ys[1] = 154; ys[2] = 178;
            gc.strokePolyline(xs, ys, 3);
            ys[0] = 420; ys[1] = 420; ys[2] = 396;
            gc.strokePolyline(xs, ys, 3);
        }
    }

    // ------------------------------------------------------------------ tachometer

    private void drawTachometer(GraphicsContext gc, ClusterState st, TelemetrySnapshot t, Color accent) {
        double style = st.getStyle();
        double thickness = 14 + 5 * style;
        double rMid = R - thickness / 2.0;
        double rpm = Math.max(0, Math.min(RPM_MAX, st.getDisplayRpm()));
        boolean limiter = t.isLimiterActive() && t.isEngineRunning();

        gc.setLineCap(StrokeLineCap.BUTT);
        // track and red zone
        gc.setLineWidth(thickness);
        gc.setStroke(TRACK);
        ringArc(gc, rMid, 600, RPM_MAX);
        gc.setStroke(Color.rgb(70, 24, 26));
        ringArc(gc, rMid, RPM_REDLINE, RPM_MAX);

        // lit part of the ring, with a soft glow made from wider translucent strokes
        if (rpm > 620) {
            double normalEnd = Math.min(rpm, RPM_REDLINE);
            for (int pass = 0; pass < 3; pass++) {
                double widen = pass == 0 ? 16 : (pass == 1 ? 8 : 0);
                double a = pass == 0 ? 0.08 : (pass == 1 ? 0.16 : 1.0);
                gc.setLineWidth(thickness + widen);
                gc.setStroke(withAlpha(accent, a));
                ringArc(gc, rMid, 600, normalEnd);
                if (rpm > RPM_REDLINE) {
                    gc.setStroke(withAlpha(RED, a));
                    ringArc(gc, rMid, RPM_REDLINE, rpm);
                }
            }
        }
        // inner ring line
        gc.setLineWidth(1.2);
        gc.setStroke(withAlpha(TEXT, 0.35));
        ringArc(gc, R - thickness - 2, 600, RPM_MAX);

        // ticks and numbers
        double tickOuter = R - thickness - 6;
        for (int r = 1000; r <= 9000; r += 250) {
            boolean major = r % 1000 == 0;
            boolean red = r >= RPM_REDLINE;
            double len = major ? 20 : (r % 500 == 0 ? 12 : 7);
            double ang = rpmAngle(r);
            gc.setLineWidth(major ? 3 : 1.5);
            Color base = red ? RED : TEXT;
            gc.setStroke(withAlpha(base, r <= rpm ? 0.95 : (major ? 0.60 : 0.40)));
            gc.strokeLine(px(tickOuter, ang), py(tickOuter, ang), px(tickOuter - len, ang), py(tickOuter - len, ang));
        }
        for (int k = 1; k <= 9; k++) {
            double ang = rpmAngle(k * 1000.0);
            double rad = tickOuter - 44;
            Color c = k >= 8 ? RED : (k * 1000 <= rpm ? TEXT : Color.rgb(176, 186, 202));
            text(gc, Integer.toString(k), px(rad, ang), py(rad, ang), 31, FontWeight.BOLD, c, TextAlignment.CENTER);
        }
        text(gc, "RPM", CX + 112, CY + 150, 12, FontWeight.BOLD, DIM, TextAlignment.LEFT);
        text(gc, "x1000", CX + 112, CY + 164, 10, FontWeight.NORMAL, DIM, TextAlignment.LEFT);

        // decorative base marker (as on the real display) at the bottom gap
        double[] bx = {CX - 54, CX + 54, CX + 66, CX - 66};
        double[] by = {CY + R - 22, CY + R - 22, CY + R + 6, CY + R + 6};
        gc.setLineWidth(1.5);
        gc.setStroke(withAlpha(accent, 0.8));
        gc.strokePolygon(bx, by, 4);

        drawShiftLights(gc, st, t, rpm, limiter, accent);

        // needle (short pointer inside the ring, as in the reference)
        double ang = rpmAngle(Math.max(rpm, 600));
        double tip = R - thickness - 1;
        double tail = R - thickness - 56;
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth(11);
        gc.setStroke(withAlpha(accent, 0.18));
        gc.strokeLine(px(tail, ang), py(tail, ang), px(tip, ang), py(tip, ang));
        gc.setLineWidth(4);
        gc.setStroke(Color.WHITE);
        gc.strokeLine(px(tail, ang), py(tail, ang), px(tip, ang), py(tip, ang));
        gc.setLineCap(StrokeLineCap.BUTT);

        // Track / Sport emphasise RPM numerically
        if (style >= 1.5) {
            // handled in the centre block (digital RPM)
            return;
        }
    }

    /** Track mode: a row of shift-light blocks around the top of the dial. */
    private void drawShiftLights(GraphicsContext gc, ClusterState st, TelemetrySnapshot t, double rpm,
            boolean limiter, Color accent) {
        double vis = Math.max(0.0, Math.min(1.0, (st.getStyle() - 0.6) / 0.8));
        if (vis <= 0.01) {
            return;
        }
        int blocks = 15;
        double from = 5400;
        double step = 190;
        double radius = R + 16;
        boolean blink = ((int) (st.getClock() * 8)) % 2 == 0;
        gc.setLineCap(StrokeLineCap.BUTT);
        for (int i = 0; i < blocks; i++) {
            double a1 = rpmAngle(from + i * step);
            double a2 = rpmAngle(from + (i + 1) * step) - 0.9;
            Color c = i < 5 ? GREEN : (i < 10 ? AMBER : RED);
            if (limiter) {
                c = blink ? BLUE : Color.rgb(20, 28, 44);
            }
            boolean lit = limiter || rpm >= from + i * step;
            alpha(gc, vis * (lit ? 1.0 : 0.14));
            gc.setLineWidth(11);
            gc.setStroke(lit ? c : mix(c, Color.BLACK, 0.6));
            gc.strokeArc(CX - radius, CY - radius, radius * 2, radius * 2, 90.0 - a1, -(a2 - a1), ArcType.OPEN);
        }
        alpha(gc, 1.0);
    }

    // ------------------------------------------------------------------ centre: speed, gear, launch, startup

    private void drawCenter(GraphicsContext gc, ClusterState st, TelemetrySnapshot t, Color accent) {
        double trackK = Math.max(0.0, Math.min(1.0, st.getStyle() - 1.0));   // 0 = speed-centred, 1 = gear-centred
        boolean launchOn = t.getLaunchState() != LaunchState.OFF;
        Color gearColor = gearColor(t, accent);
        String gearText = t.getGear().getLabel();

        // ---- Comfort / Sport layout: big speed in the middle, gear to the lower right
        alpha(gc, 1.0 - trackK);
        if (trackK < 0.98) {
            text(gc, Long.toString(Math.round(st.getDisplaySpeed())), CX, CY - 8, 96, FontWeight.BOLD, TEXT, TextAlignment.CENTER);
            text(gc, "KM/H", CX, CY + 48, 17, FontWeight.BOLD, DIM, TextAlignment.CENTER);
            drawGearGlyph(gc, gearText, CX + 118, CY + 96, 80, gearColor, t);
            text(gc, t.getTransmissionMode().getLetter(), CX + 150, CY + 70, 22, FontWeight.BOLD,
                    withAlpha(gearColor, 0.9), TextAlignment.LEFT);
        }
        // ---- Track layout: gear dominant, smaller speed on top, digital RPM underneath
        alpha(gc, trackK);
        if (trackK > 0.02) {
            text(gc, Long.toString(Math.round(st.getDisplaySpeed())), CX - 6, CY - 62, 44, FontWeight.BOLD, TEXT, TextAlignment.CENTER);
            text(gc, "KM/H", CX + 44, CY - 56, 12, FontWeight.BOLD, DIM, TextAlignment.LEFT);
            drawGearGlyph(gc, gearText, CX - 6, CY + 26, 124, gearColor, t);
            text(gc, t.getTransmissionMode().getLetter(), CX + 46, CY + 56, 24, FontWeight.BOLD,
                    withAlpha(gearColor, 0.9), TextAlignment.LEFT);
            text(gc, String.format("%,.0f", st.getDisplayRpm()), CX, CY + 98, 28, FontWeight.BOLD, accent, TextAlignment.CENTER);
            text(gc, "RPM", CX, CY + 120, 11, FontWeight.BOLD, DIM, TextAlignment.CENTER);
        }
        alpha(gc, 1.0);

        // Manual shift prompt
        boolean manual = t.getTransmissionMode() == hyperdrive.enums.TransmissionMode.MANUAL;
        if (manual && t.isEngineRunning() && t.getGear().isForward() && st.getDisplayRpm() > 7200
                && ((int) (st.getClock() * 6)) % 2 == 0) {
            double ux = trackK > 0.5 ? CX - 6 : CX + 118;
            double uy = trackK > 0.5 ? CY - 22 : CY + 44;
            gc.setFill(AMBER);
            gc.fillPolygon(new double[] {ux - 12, ux + 12, ux}, new double[] {uy + 8, uy + 8, uy - 10}, 3);
        }

        // ---- status line under the speed block: launch > startup > engine state hints
        double lineY = trackK > 0.5 ? CY + 146 : CY + 80;
        if (launchOn) {
            drawLaunchStatus(gc, t, st, CX, lineY - (trackK > 0.5 ? 6 : 0));
        } else if (t.getStartupPhase().isVisible()) {
            drawStartupStatus(gc, t, st, CX, lineY - (trackK > 0.5 ? 8 : 0));
        } else if (t.isLimiterActive()) {
            text(gc, "RPM LIMIT", CX, lineY, 14, FontWeight.BOLD, BLUE, TextAlignment.CENTER);
        } else if (t.isEngineRunning() && t.getRpmLimit() < 8000) {
            text(gc, String.format("RPM LIMIT %.1f", t.getRpmLimit() / 1000.0), CX, lineY, 12, FontWeight.NORMAL,
                    DIM, TextAlignment.CENTER);
        }
    }

    private Color gearColor(TelemetrySnapshot t, Color accent) {
        LaunchState ls = t.getLaunchState();
        if (ls == LaunchState.READY || ls == LaunchState.LAUNCHING) {
            return GREEN;
        }
        if (ls.isSequenceActive()) {
            return CYAN;
        }
        if (t.isShifting()) {
            return mix(accent, DIM, 0.55);
        }
        if (t.getGear() == GearPosition.R) {
            return TEXT;
        }
        return accent;
    }

    private void drawGearGlyph(GraphicsContext gc, String g, double x, double y, double size, Color c, TelemetrySnapshot t) {
        gc.setLineCap(StrokeLineCap.BUTT);
        text(gc, g, x, y, size * 1.0, FontWeight.BOLD, withAlpha(c, 0.18), TextAlignment.CENTER);   // soft glow layer
        text(gc, g, x, y, size, FontWeight.BOLD, c, TextAlignment.CENTER);
    }

    private void drawLaunchStatus(GraphicsContext gc, TelemetrySnapshot t, ClusterState st, double x, double y) {
        LaunchState ls = t.getLaunchState();
        Color c = switch (ls) {
            case READY, LAUNCHING, COMPLETE -> GREEN;
            case ABORTED, UNAVAILABLE -> ls == LaunchState.UNAVAILABLE ? AMBER : RED;
            default -> CYAN;
        };
        boolean pulse = ls == LaunchState.READY && ((int) (st.getClock() * 4)) % 2 == 0;
        String label = ls == LaunchState.UNAVAILABLE ? "LAUNCH CONTROL UNAVAILABLE" : "LAUNCH  " + ls.getLabel();
        text(gc, label, x, y, 15, FontWeight.BOLD, pulse ? Color.WHITE : c, TextAlignment.CENTER);
        if (ls == LaunchState.UNAVAILABLE || ls == LaunchState.ABORTED) {
            String reason = t.getLaunchReason();
            if (reason.length() > 46) {
                reason = reason.substring(0, 44) + "..";
            }
            text(gc, reason, x, y + 18, 11, FontWeight.NORMAL, DIM, TextAlignment.CENTER);
        } else {
            double w = 170;
            gc.setFill(FAINT);
            gc.fillRoundRect(x - w / 2, y + 12, w, 7, 4, 4);
            gc.setFill(c);
            gc.fillRoundRect(x - w / 2, y + 12, Math.max(4.0, w * t.getLaunchBoostPercent() / 100.0), 7, 4, 4);
            text(gc, "BOOST", x - w / 2 - 8, y + 16, 10, FontWeight.BOLD, DIM, TextAlignment.RIGHT);
        }
    }

    private void drawStartupStatus(GraphicsContext gc, TelemetrySnapshot t, ClusterState st, double x, double y) {
        StartupPhase p = t.getStartupPhase();
        Color c = p == StartupPhase.ENGINE_STARTED ? GREEN : (p == StartupPhase.READY ? TEXT : AMBER);
        boolean blink = p == StartupPhase.CRANKING && ((int) (st.getClock() * 5)) % 2 == 0;
        text(gc, p.getLabel(), x, y, p == StartupPhase.READY ? 13 : 16, FontWeight.BOLD, blink ? Color.WHITE : c, TextAlignment.CENTER);
        if (t.getPowerState() == PowerState.SELF_TEST) {
            double w = 150;
            gc.setFill(FAINT);
            gc.fillRoundRect(x - w / 2, y + 14, w, 4, 2, 2);
            gc.setFill(AMBER);
            gc.fillRoundRect(x - w / 2, y + 14, Math.max(2.0, w * t.getStartupProgress()), 4, 2, 2);
        }
    }

    // ------------------------------------------------------------------ left panel

    private void drawLeftPanel(GraphicsContext gc, ClusterState st, TelemetrySnapshot t, Color accent) {
        // top zone: navigation placeholder (visual only)
        text(gc, "NAVIGATION", 56, 112, 15, FontWeight.BOLD, DIM, TextAlignment.LEFT);
        text(gc, "No route active", 56, 134, 14, FontWeight.NORMAL, Color.rgb(84, 96, 118), TextAlignment.LEFT);

        // middle zone
        if (t.isRearCameraActive()) {
            drawRearCamera(gc, t, accent);
        } else {
            int page = st.getPage();
            text(gc, ClusterState.PAGE_NAMES[page], 56, 176, 15, FontWeight.BOLD, accent, TextAlignment.LEFT);
            for (int i = 0; i < ClusterState.PAGE_NAMES.length; i++) {
                gc.setFill(i == page ? accent : FAINT);
                gc.fillOval(230 + i * 8, 172, 5, 5);
            }
            switch (page) {
                case 0 -> drawVehiclePage(gc, t, accent);
                case 1 -> drawMessagesPage(gc, t);
                case 2 -> drawTripPage(gc, t);
                case 3 -> drawTyresPage(gc, t);
                case 4 -> drawOilPage(gc, t);
                case 5 -> drawBatteryPage(gc, t);
                default -> drawInfoPage(gc, t);
            }
        }

        // bottom zone: trip since start + outside temperature
        TripComputer.TripData trip = t.getTripSinceStart();
        text(gc, "Trip - since start", 56, 446, 14, FontWeight.NORMAL, DIM, TextAlignment.LEFT);
        text(gc, String.format("%.1f km", trip.getDistanceKm()), 56, 468, 20, FontWeight.BOLD, TEXT, TextAlignment.LEFT);
        text(gc, "30\u00B0C", 56, 488, 15, FontWeight.NORMAL, DIM, TextAlignment.LEFT);
    }

    private void drawVehiclePage(GraphicsContext gc, TelemetrySnapshot t, Color accent) {
        List<Notification> msgs = t.getActiveMessages();
        if (msgs.isEmpty()) {
            text(gc, "No messages", 56, 204, 18, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        } else {
            for (int i = 0; i < Math.min(3, msgs.size()); i++) {
                Notification n = msgs.get(i);
                gc.setFill(severityColor(n.getSeverity()));
                gc.fillOval(56, 198 + i * 22, 8, 8);
                text(gc, clip(n.getMessage(), 27), 72, 202 + i * 22, 14, FontWeight.BOLD, TEXT, TextAlignment.LEFT);
            }
            if (msgs.size() > 3) {
                text(gc, "+" + (msgs.size() - 3) + " more (J)", 72, 202 + 3 * 22, 12, FontWeight.NORMAL, DIM, TextAlignment.LEFT);
            }
        }
        drawCar(gc, 188, 330, 130, t, accent, false);
    }

    private void drawMessagesPage(GraphicsContext gc, TelemetrySnapshot t) {
        List<Notification> msgs = t.getActiveMessages();
        int y = 204;
        int shown = 0;
        if (msgs.isEmpty()) {
            text(gc, "No active messages", 56, y, 16, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
            y += 28;
        }
        for (Notification n : msgs) {
            if (shown++ >= 4) {
                break;
            }
            gc.setFill(severityColor(n.getSeverity()));
            gc.fillOval(56, y - 4, 8, 8);
            text(gc, clip(n.getMessage(), 28), 72, y, 14, FontWeight.BOLD, TEXT, TextAlignment.LEFT);
            y += 22;
        }
        text(gc, "RECENT", 56, 312, 12, FontWeight.BOLD, DIM, TextAlignment.LEFT);
        List<Notification> recent = t.getRecentNotifications();
        int row = 0;
        for (int i = recent.size() - 1; i >= 0 && row < 4; i--, row++) {
            Notification n = recent.get(i);
            text(gc, clip(n.getMessage(), 38), 56, 334 + row * 20, 12, FontWeight.NORMAL,
                    n.getSeverity() == Severity.INFO ? Color.rgb(160, 170, 186) : severityColor(n.getSeverity()), TextAlignment.LEFT);
        }
    }

    private void drawTripPage(GraphicsContext gc, TelemetrySnapshot t) {
        drawTripColumn(gc, "SINCE START", t.getTripSinceStart(), 56, 210);
        drawTripColumn(gc, "LONG TERM", t.getTripLongTerm(), 188, 210);
        text(gc, String.format("Session %.1f km", t.getSessionDistanceKm()), 56, 392, 13, FontWeight.NORMAL, DIM, TextAlignment.LEFT);
    }

    private void drawTripColumn(GraphicsContext gc, String title, TripComputer.TripData d, double x, double y) {
        text(gc, title, x, y, 12, FontWeight.BOLD, DIM, TextAlignment.LEFT);
        text(gc, String.format("%.1f km", d.getDistanceKm()), x, y + 28, 20, FontWeight.BOLD, TEXT, TextAlignment.LEFT);
        text(gc, d.getTimeText(), x, y + 58, 15, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        text(gc, String.format("avg %.0f km/h", d.getAverageSpeedKmh()), x, y + 84, 13, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        String cons = d.getAverageConsumptionL100() > 0 ? String.format("%.1f L/100", d.getAverageConsumptionL100()) : "-- L/100";
        text(gc, cons, x, y + 106, 13, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
    }

    private void drawTyresPage(GraphicsContext gc, TelemetrySnapshot t) {
        Tyre[] ty = t.getTyres();
        String[] names = {"FL", "FR", "RL", "RR"};
        for (int i = 0; i < Math.min(4, ty.length); i++) {
            double y = 212 + i * 46;
            Color c = tyreColor(ty[i].getCondition());
            text(gc, names[i], 56, y, 18, FontWeight.BOLD, c, TextAlignment.LEFT);
            boolean ok = ty[i].isSensorWorking();
            text(gc, ok ? String.format("%.2f bar", ty[i].getPressure()) : "-- bar", 100, y - 8, 15, FontWeight.BOLD, TEXT, TextAlignment.LEFT);
            text(gc, ok ? String.format("%.0f\u00B0C", ty[i].getTemperature()) : "--", 100, y + 10, 13, FontWeight.NORMAL, DIM, TextAlignment.LEFT);
            text(gc, String.format("grip %.2f g", ty[i].getGrip()), 190, y - 8, 12, FontWeight.NORMAL, DIM, TextAlignment.LEFT);
            text(gc, ty[i].getCondition().name().replace('_', ' '), 190, y + 10, 12, FontWeight.BOLD, c, TextAlignment.LEFT);
        }
    }

    private void drawOilPage(GraphicsContext gc, TelemetrySnapshot t) {
        Color c = switch (t.getOilState()) {
            case LOW_PRESSURE -> RED;
            case HOT -> AMBER;
            case COLD -> BLUE;
            default -> GREEN;
        };
        text(gc, t.getOilState().name().replace('_', ' '), 56, 214, 20, FontWeight.BOLD, c, TextAlignment.LEFT);
        text(gc, String.format("Temperature   %.0f\u00B0C", t.getOilTemp()), 56, 250, 15, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        text(gc, String.format("Pressure      %.1f bar", t.getOilPressure()), 56, 276, 15, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        text(gc, String.format("RPM limit     %.0f", t.getRpmLimit()), 56, 302, 15, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        text(gc, String.format("Coolant       %.0f\u00B0C  %s", t.getCoolantTemp(), t.getCoolingState()), 56, 328, 15, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        if (t.getOilState() == hyperdrive.enums.OilState.COLD && t.isEngineRunning()) {
            text(gc, "Cold oil limits engine RPM", 56, 366, 12, FontWeight.NORMAL, DIM, TextAlignment.LEFT);
        }
    }

    private void drawBatteryPage(GraphicsContext gc, TelemetrySnapshot t) {
        BatteryState bs = t.getBatteryState();
        Color c = bs == BatteryState.FAULT ? RED : (bs == BatteryState.LOW ? AMBER : GREEN);
        text(gc, bs.name(), 56, 214, 20, FontWeight.BOLD, c, TextAlignment.LEFT);
        text(gc, String.format("Voltage       %.1f V", t.getBatteryVoltage()), 56, 250, 15, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        text(gc, String.format("Charge        %.0f %%", t.getBatteryCharge()), 56, 276, 15, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        text(gc, t.isEngineRunning() ? "Alternator    ON" : "Alternator    OFF", 56, 302, 15, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        gc.setFill(FAINT);
        gc.fillRoundRect(56, 332, 200, 8, 4, 4);
        gc.setFill(c);
        gc.fillRoundRect(56, 332, Math.max(3.0, 2.0 * t.getBatteryCharge()), 8, 4, 4);
    }

    private void drawInfoPage(GraphicsContext gc, TelemetrySnapshot t) {
        String[] lines = {
            "ESC          " + t.getEscMode().getLabel().replace("ESC ", ""),
            "Airbrake     " + t.getAirbrakeState(),
            "Lift         " + t.getLiftState(),
            "Parking brake " + t.getEpbState(),
            String.format("Brake         %s  %.0f\u00B0C", t.getBrakeState(), t.getBrakeDiscTemp()),
            String.format("Steering      %+.0f\u00B0 %s", t.getSteeringAngleDeg(), t.getSteeringDirection()),
            String.format("Lateral %.2f g  %s", Math.abs(t.getLateralG()), t.getLateralState()),
            String.format("Torque %.0f Nm  load %.0f%%", t.getEngineTorqueNm(), t.getEngineLoad() * 100),
            String.format("Fuel %.1f L/h  %.1f L/100", t.getFuelFlowLph(), t.getFuelInstantL100()),
            String.format("Slope %+.0f%%", t.getRoadSlopePercent())
        };
        for (int i = 0; i < lines.length; i++) {
            text(gc, lines[i], 56, 200 + i * 20, 13, FontWeight.NORMAL, TEXT, TextAlignment.LEFT);
        }
    }

    private void drawRearCamera(GraphicsContext gc, TelemetrySnapshot t, Color accent) {
        double x0 = 52;
        double y0 = 164;
        double w = 238;
        double h = 244;
        text(gc, "REAR CAMERA", 56, 176, 15, FontWeight.BOLD, accent, TextAlignment.LEFT);
        gc.setFill(Color.rgb(10, 16, 26));
        gc.fillRoundRect(x0, y0 + 24, w, h - 24, 10, 10);
        gc.setStroke(FAINT);
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x0, y0 + 24, w, h - 24, 10, 10);
        // perspective guide lines
        gc.setStroke(withAlpha(TEXT, 0.25));
        gc.setLineWidth(1.5);
        gc.strokeLine(x0 + 100, y0 + 70, x0 + 40, y0 + h - 4);
        gc.strokeLine(x0 + w - 100, y0 + 70, x0 + w - 40, y0 + h - 4);
        // the car's rear at the top
        gc.setFill(Color.rgb(34, 44, 62));
        gc.fillRoundRect(x0 + w / 2 - 62, y0 + 28, 124, 38, 12, 12);
        gc.setStroke(DIM);
        gc.strokeRoundRect(x0 + w / 2 - 62, y0 + 28, 124, 38, 12, 12);
        // obstacle position: further away = lower on screen
        double dist = t.getRearDistanceM();
        double frac = Math.max(0.0, Math.min(1.0, dist / 3.2));
        double oy = y0 + 76 + frac * (h - 110);
        int level = t.getRearWarningLevel();
        Color lc = level >= 3 ? RED : (level == 2 ? AMBER : (level == 1 ? Color.rgb(230, 220, 90) : GREEN));
        gc.setFill(withAlpha(lc, 0.30));
        gc.fillRoundRect(x0 + w / 2 - 76, oy - 8, 152, 16, 6, 6);
        gc.setFill(lc);
        gc.fillRoundRect(x0 + w / 2 - 52, oy - 4, 104, 8, 4, 4);
        // proximity arcs
        gc.setLineWidth(5);
        for (int i = 0; i < 3; i++) {
            boolean lit = level > i;
            gc.setStroke(lit ? (i == 2 ? RED : (i == 1 ? AMBER : Color.rgb(230, 220, 90))) : FAINT);
            double rr = 52 + i * 18;
            gc.strokeArc(x0 + w / 2 - rr, y0 + 66 - rr * 0.45, rr * 2, rr * 0.9, 215, 110, ArcType.OPEN);
        }
        text(gc, String.format("%.1f m", dist), x0 + w - 12, y0 + 46, 20, FontWeight.BOLD, lc, TextAlignment.RIGHT);
        text(gc, level == 0 ? "CLEAR" : (level >= 3 ? "STOP" : "OBSTACLE"), x0 + 14, y0 + h - 14, 13, FontWeight.BOLD, lc, TextAlignment.LEFT);
    }

    // ------------------------------------------------------------------ right panel

    private void drawRightPanel(GraphicsContext gc, ClusterState st, TelemetrySnapshot t, Color accent) {
        // brand
        gc.setFont(italicFont(30));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(TEXT);
        gc.fillText("HYPERDRIVE", 862, 104);
        gc.setFill(RED);
        gc.fillText("X-01", 948, 104);
        text(gc, String.format("BATT %.1f V   OIL %.1f bar", t.getBatteryVoltage(), t.getOilPressure()), 948, 134, 13,
                FontWeight.NORMAL, DIM, TextAlignment.RIGHT);

        // oil and coolant temperature bars with the vehicle diagram between them
        drawTempBar(gc, 722, 190, 160, t.getOilTemp(), 40, 140, 80, 120, "OIL", true);
        drawTempBar(gc, 906, 190, 160, t.getCoolantTemp(), 40, 130, 80, 107, "COOL", false);
        drawCar(gc, 814, 276, 160, t, accent, true);

        // fuel and range
        boolean low = t.isLowFuel();
        Color fc = low ? AMBER : GREEN;
        text(gc, "FUEL", 700, 393, 11, FontWeight.BOLD, low ? AMBER : DIM, TextAlignment.LEFT);
        int segs = 22;
        double sx = 736;
        double sw = 132;
        double segW = sw / segs;
        int lit = (int) Math.round(t.getFuelLevelPercent() / 100.0 * segs);
        for (int i = 0; i < segs; i++) {
            gc.setFill(i < lit ? fc : FAINT);
            gc.fillRect(sx + i * segW, 386, segW - 2, 16);
        }
        text(gc, String.format("%.0f km", t.getFuelRangeKm()), 948, 392, 19, FontWeight.BOLD, low ? AMBER : TEXT, TextAlignment.RIGHT);

        // handling / powertrain modes
        boolean active = t.getActiveState() == hyperdrive.enums.ActiveState.ACTIVE;
        String h = active ? t.getHandlingMode().getLabel() : "NON-ACTIVE";
        String p = active ? t.getPowertrainMode().getLabel() : "NON-ACTIVE";
        Color vc = active ? (t.getPowertrainMode().getRank() == 0 ? GREEN : TEXT) : GREEN;
        text(gc, "H", 712, 440, 17, FontWeight.BOLD, accent, TextAlignment.LEFT);
        text(gc, h, 738, 440, 17, FontWeight.BOLD, active ? modeColor(t.getHandlingMode().getRank()) : vc, TextAlignment.LEFT);
        text(gc, "P", 712, 462, 17, FontWeight.BOLD, accent, TextAlignment.LEFT);
        text(gc, p, 738, 462, 17, FontWeight.BOLD, active ? modeColor(t.getPowertrainMode().getRank()) : vc, TextAlignment.LEFT);
        text(gc, "ESC " + t.getEscMode().getLabel().replace("ESC ", ""), 712, 484, 12, FontWeight.NORMAL,
                t.getEscMode() == hyperdrive.enums.EscMode.OFF ? AMBER : DIM, TextAlignment.LEFT);
        text(gc, clockText(), 948, 476, 18, FontWeight.NORMAL, TEXT, TextAlignment.RIGHT);
    }

    private static Color modeColor(int rank) {
        return rank == 0 ? GREEN : (rank == 1 ? Color.rgb(255, 214, 120) : Color.rgb(255, 130, 90));
    }

    private void drawTempBar(GraphicsContext gc, double x, double y, double h, double value, double min, double max,
            double greenFrom, double amberFrom, String label, boolean leftSide) {
        int segs = 14;
        double segH = h / segs;
        double frac = Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
        int lit = (int) Math.round(frac * segs);
        for (int i = 0; i < segs; i++) {
            double segTemp = min + (i + 0.5) / segs * (max - min);
            Color c = segTemp >= amberFrom ? RED : (segTemp >= greenFrom ? GREEN : BLUE);
            gc.setFill(i < lit ? c : FAINT);
            gc.fillRect(x, y + h - (i + 1) * segH + 2, 10, segH - 2);
        }
        text(gc, String.format("%.0f\u00B0C", value), x + 5, y - 14, 15, FontWeight.BOLD, TEXT, TextAlignment.CENTER);
        text(gc, label, x + 5, y + h + 14, 11, FontWeight.BOLD, DIM, TextAlignment.CENTER);
    }

    // ------------------------------------------------------------------ top-down vehicle diagram

    /**
     * Technical top-down car: four independently coloured tyres (pressure + temperature beside each), per-wheel brake
     * application, an animated airbrake flap at the rear and lift indicators at the nose.
     */
    private void drawCar(GraphicsContext gc, double cx, double cy, double len, TelemetrySnapshot t, Color accent,
            boolean showTyreValues) {
        double wd = len * 0.44;
        // body outline
        double[] ox = {0.00, 0.10, 0.17, 0.19, 0.20, 0.20, 0.19, 0.15, 0.08};
        double[] oy = {-0.50, -0.47, -0.38, -0.20, -0.05, 0.15, 0.30, 0.42, 0.49};
        double[] bx = new double[ox.length * 2];
        double[] by = new double[ox.length * 2];
        for (int i = 0; i < ox.length; i++) {
            bx[i] = cx + ox[i] * len * 1.02;
            by[i] = cy + oy[i] * len;
            bx[bx.length - 1 - i] = cx - ox[i] * len * 1.02;
            by[bx.length - 1 - i] = cy + oy[i] * len;
        }
        gc.setFill(Color.rgb(22, 30, 44));
        gc.fillPolygon(bx, by, bx.length);
        gc.setLineWidth(1.6);
        gc.setStroke(Color.rgb(132, 146, 168));
        gc.strokePolygon(bx, by, bx.length);
        // cabin / windscreen
        gc.setFill(Color.rgb(12, 18, 30));
        gc.fillRoundRect(cx - wd * 0.30, cy - len * 0.20, wd * 0.60, len * 0.34, 14, 14);
        gc.setStroke(withAlpha(TEXT, 0.35));
        gc.setLineWidth(1);
        gc.strokeRoundRect(cx - wd * 0.30, cy - len * 0.20, wd * 0.60, len * 0.34, 14, 14);
        gc.strokeLine(cx, cy - len * 0.44, cx, cy - len * 0.22);

        // lift: arrows at the nose
        if (t.getLiftState() != LiftState.NORMAL) {
            double lift = t.getLiftProgress();
            gc.setStroke(AMBER);
            gc.setLineWidth(2.2);
            for (int s = -1; s <= 1; s += 2) {
                double ax = cx + s * wd * 0.22;
                double ay = cy - len * 0.54 - lift * 6;
                gc.strokeLine(ax, ay + 8, ax, ay - 4);
                gc.strokeLine(ax - 4, ay, ax, ay - 5);
                gc.strokeLine(ax + 4, ay, ax, ay - 5);
            }
        }

        // airbrake flap at the rear: opens (taller, accent-coloured) with its progress
        double prog = t.getAirbrakeProgress();
        AirbrakeState as = t.getAirbrakeState();
        Color flapColor = (as == AirbrakeState.FAULT) ? RED : (as == AirbrakeState.UNAVAILABLE ? AMBER : (prog > 0.05 ? accent : DIM));
        double flapW = wd * 0.82;
        double flapY = cy + len * 0.45;
        gc.setFill(withAlpha(flapColor, prog > 0.05 ? 0.95 : 0.55));
        gc.fillRoundRect(cx - flapW / 2, flapY - prog * 5, flapW, 3 + prog * 8, 2, 2);

        // tyres
        Tyre[] ty = t.getTyres();
        double tw = len * 0.085;
        double th = len * 0.19;
        double fx = wd * 0.60;
        double[] xs = {cx - fx, cx + fx, cx - fx, cx + fx};
        double[] ys = {cy - len * 0.29, cy - len * 0.29, cy + len * 0.28, cy + len * 0.28};
        boolean flash = ((int) (System.currentTimeMillis() / 250)) % 2 == 0;
        for (int i = 0; i < 4 && i < ty.length; i++) {
            TyreCondition cond = ty[i].getCondition();
            Color c = tyreColor(cond);
            gc.setFill(withAlpha(c, cond == TyreCondition.NORMAL ? 0.22 : 0.45));
            gc.fillRoundRect(xs[i] - tw / 2, ys[i] - th / 2, tw, th, 4, 4);
            gc.setLineWidth(2);
            gc.setStroke(c);
            if (cond == TyreCondition.FAULT) {
                gc.setLineDashes(3, 3);
            }
            gc.strokeRoundRect(xs[i] - tw / 2, ys[i] - th / 2, tw, th, 4, 4);
            gc.setLineDashes((double[]) null);
            if (cond == TyreCondition.LOW_PRESSURE && flash) {
                text(gc, "!", xs[i], ys[i], 14, FontWeight.BOLD, c, TextAlignment.CENTER);
            }
            double brake = t.getWheelBrake()[i];
            if (brake > 0.12) {
                gc.setFill(withAlpha(RED, 0.9));
                double bh = th * Math.min(1.0, brake);
                double bxp = (i % 2 == 0) ? xs[i] + tw / 2 + 2 : xs[i] - tw / 2 - 5;
                gc.fillRect(bxp, ys[i] + th / 2 - bh, 3, bh);
            }
            if (showTyreValues) {
                boolean left = i % 2 == 0;
                double tx = left ? xs[i] - tw / 2 - 8 : xs[i] + tw / 2 + 8;
                TextAlignment al = left ? TextAlignment.RIGHT : TextAlignment.LEFT;
                boolean ok = ty[i].isSensorWorking();
                text(gc, ok ? String.format("%.1f", ty[i].getPressure()) : "--", tx, ys[i] - 8, 15, FontWeight.BOLD,
                        cond == TyreCondition.NORMAL || cond == TyreCondition.COLD ? TEXT : c, al);
                text(gc, ok ? String.format("%.0f\u00B0", ty[i].getTemperature()) : "bar", tx, ys[i] + 9, 12, FontWeight.NORMAL, DIM, al);
            }
        }
    }

    private static Color tyreColor(TyreCondition c) {
        return switch (c) {
            case NORMAL -> GREEN;
            case COLD -> BLUE;
            case HOT -> Color.rgb(255, 110, 40);
            case LOW_PRESSURE -> AMBER;
            case FAULT -> DIM;
        };
    }

    // ------------------------------------------------------------------ warning indicator bar

    private void drawWarningBar(GraphicsContext gc, ClusterState st, TelemetrySnapshot t) {
        boolean selfTest = t.isWarningSelfTest();
        WarningLight[] all = WarningLight.values();
        int count = 0;
        for (WarningLight l : all) {
            if (selfTest || t.getWarningLights().containsKey(l)) {
                count++;
            }
        }
        if (count == 0) {
            return;
        }
        double chipW = 50;
        double gap = 5;
        double total = count * chipW + (count - 1) * gap;
        double x = CX - total / 2.0;
        double y = 517;
        boolean blink = ((int) (st.getClock() * 2.5)) % 2 == 0;
        for (WarningLight l : all) {
            Severity sev = t.getWarningLights().get(l);
            if (!selfTest && sev == null) {
                continue;
            }
            Color c = selfTest ? defaultLightColor(l) : lightColor(l, sev);
            double a = 1.0;
            if (!selfTest && sev == Severity.CRITICAL && l != WarningLight.EPB && !blink) {
                a = 0.55;   // critical warnings pulse gently
            }
            alpha(gc, a);
            gc.setFill(withAlpha(c, 0.16));
            gc.fillRoundRect(x, y - 11, chipW, 22, 6, 6);
            gc.setLineWidth(1.6);
            gc.setStroke(c);
            gc.strokeRoundRect(x, y - 11, chipW, 22, 6, 6);
            text(gc, l.getLabel(), x + chipW / 2, y, 10, FontWeight.BOLD, c, TextAlignment.CENTER);
            x += chipW + gap;
        }
        alpha(gc, 1.0);
    }

    private static Color lightColor(WarningLight l, Severity s) {
        if (l == WarningLight.EPB) {
            return RED;
        }
        return severityColor(s);
    }

    private static Color defaultLightColor(WarningLight l) {
        return switch (l) {
            case OIL, TEMP, BATTERY, BRAKE, EPB, SRS, SEATBELT -> RED;
            case LAUNCH -> GREEN;
            default -> AMBER;
        };
    }

    // ------------------------------------------------------------------ notification pop-up

    private void drawToast(GraphicsContext gc, ClusterState st, long nowMillis) {
        Notification n = st.getToast();
        double op = st.getToastOpacity(nowMillis);
        if (n == null || op <= 0.01) {
            return;
        }
        Color c = n.getSeverity() == Severity.INFO ? BLUE : severityColor(n.getSeverity());
        double w = 460;
        double x = CX - w / 2;
        double y = 14;
        alpha(gc, op);
        gc.setFill(Color.rgb(8, 12, 20));
        gc.fillRoundRect(x, y, w, 30, 15, 15);
        gc.setLineWidth(1.8);
        gc.setStroke(c);
        gc.strokeRoundRect(x, y, w, 30, 15, 15);
        gc.setFill(c);
        gc.fillOval(x + 14, y + 11, 8, 8);
        text(gc, clip(n.getMessage(), 52), x + 32, y + 15, 14, FontWeight.BOLD, TEXT, TextAlignment.LEFT);
        alpha(gc, 1.0);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    // used only to keep an EngineState import meaningful for readers: the cluster reads the engine state from telemetry
    static boolean isEngineOff(TelemetrySnapshot t) {
        return t.getEngineState() == EngineState.OFF && t.getPowerState() != PowerState.CRANKING;
    }

    static boolean isEpbOn(TelemetrySnapshot t) {
        return t.getEpbState() == EpbState.ENGAGED;
    }
}

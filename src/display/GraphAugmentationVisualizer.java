package display;

import helper.Edge;
import helper.Graph;
import helper.Node;
import patterns.Pattern;
import wfc.WaveCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * GraphAugmentationVisualizer:
 *  - Displays the *entire* evolving output graph in a random 2D layout.
 *  - Each Node is shown as a circle, with color coding:
 *      - Gray if it's not tracked by any WaveCell
 *      - Green if it is collapsed to a single Pattern
 *      - Yellow if still in superposition
 *  - The node's text label is either the pattern center label if collapsed,
 *    or a set of possible labels if in superposition.
 *
 *  - Edges are drawn in dark gray lines.
 *
 * NOTE: Removed the debug log text box for now, focusing on the graph display (Reason: Presentation).
 * This visualizer still supports run/wait/step controls via keyboard.
 */
public class GraphAugmentationVisualizer {

    private final Graph graph; // The entire output graph being generated

    // Storage for node positions and adjacency to display
    private final Map<Node, Point> positions     = new HashMap<>();
    private final Set<Node> drawnNodes           = new HashSet<>();
    private final Set<Edge> drawnEdges           = new HashSet<>();

    // Maps each Node to its corresponding WaveCell (if any)
    private final Map<Node, WaveCell> nodeToCellMap = new HashMap<>();

    // Basic GUI
    private final JFrame frame;
    private final GraphPanel graphPanel;
    // (Removed the debug log text area UI box)

    // Flow-control flags for the generation process
    private boolean paused    = true;
    private boolean runMode   = false;
    private boolean singleStep = false;

    private static final int JITTER_STEPS = 15;
    private final Map<Node, Integer> jitterCooldown = new HashMap<>();

    public GraphAugmentationVisualizer(Graph outputGraph) {
        this.graph = outputGraph;

        frame = new JFrame("GraphAugmentationVisualizer (R=run, W=pause, S=step)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 700);

        // Only the graph panel in the center (removed split pane and logArea)
        graphPanel = new GraphPanel();
        graphPanel.setPreferredSize(new Dimension(900, 700));

        JPanel content = new JPanel(new BorderLayout());
        content.add(graphPanel, BorderLayout.CENTER);
        content.setFocusable(true);
        content.setFocusTraversalKeysEnabled(false);

        // Keyboard controls
        content.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                char c = e.getKeyChar();
                switch (c) {
                    case 'r': case 'R':
                        paused = false;
                        runMode = true;
                        singleStep = false;
                        break;
                    case 'w': case 'W':
                        paused = true;
                        runMode = false;
                        singleStep = false;
                        break;
                    case 's': case 'S':
                        paused = true;
                        runMode = false;
                        singleStep = true;
                        break;
                }
            }
        });

        frame.setContentPane(content);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Request focus for key listener
        SwingUtilities.invokeLater(content::requestFocusInWindow);
    }

    /**
     * Register a WaveCell so we can color it green if collapsed or yellow if still in superposition,
     * and display domain labels, etc.
     */
    public void registerWaveCell(WaveCell cell) {
        nodeToCellMap.put(cell.getNode(), cell);
    }

    /**
     * Log a message (printed to console), then re-check if new nodes/edges were added to the graph, then repaint.
     */
    public void logMessage(String msg) {
        System.out.println(msg);
        refreshAllNodesAndEdges();
        graphPanel.repaint();
        // Possibly pause or single-step
        waitIfNeeded();
    }

    /**
     * "Highlighting" a node basically sets the node's label, triggers a small "jitter"
     * so we notice it on screen, then re-check layout.
     */
    public void highlightNode(Node node, String label) {
        if (label != null && !label.isEmpty()) {
            node.setLabel(label);
        }
        refreshAllNodesAndEdges();

        // Force some jitter so we see a minor shift
        jitterCooldown.put(node, JITTER_STEPS);

        graphPanel.repaint();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            // ignore
        }
        waitIfNeeded();
    }

    /**
     * Ensures we have a random position for all nodes in the graph,
     * and that all edges with known endpoints are in drawnEdges.
     */
    private void refreshAllNodesAndEdges() {
        int w = graphPanel.getWidth();
        int h = graphPanel.getHeight();
        if (w < 10) w = 800; // fallback if panel not sized yet
        if (h < 10) h = 600;

        Random rnd = new Random();

        // Add any missing nodes
        for (Node n : graph.getAllNodes()) {
            if (!drawnNodes.contains(n)) {
                drawnNodes.add(n);
                positions.put(n, new Point(rnd.nextInt(w), rnd.nextInt(h)));
            }
        }

        // Rebuild edges from scratch
        drawnEdges.clear();
        for (Edge e : graph.getAllEdges()) {
            Node a = e.getNodeA();
            Node b = e.getNodeB();
            if (drawnNodes.contains(a) && drawnNodes.contains(b)) {
                drawnEdges.add(e);
            }
        }
    }

    /**
     * If runMode => short sleep.
     * If paused => loop until unpaused or a single-step is triggered.
     */
    public void waitIfNeeded() {
        if (runMode) {
            try {
                Thread.sleep(10);
            } catch (Exception e) { /* ignore */ }
            return;
        }
        if (singleStep) {
            singleStep = false;
            paused = true;
            return;
        }
        while (paused && !singleStep && !runMode) {
            try {
                Thread.sleep(100);
            } catch (Exception e) { /* ignore */ }
        }
    }

    private class GraphPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // apply small random "jitter" to any node that has a cooldown
            doJitter();

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(Color.DARK_GRAY);

            // (A) draw edges
            for (Edge e : drawnEdges) {
                Point pa = positions.get(e.getNodeA());
                Point pb = positions.get(e.getNodeB());
                if (pa != null && pb != null) {
                    g2.drawLine(pa.x, pa.y, pb.x, pb.y);
                }
            }

            // (B) draw nodes
            int dia = 40;
            for (Node n : drawnNodes) {
                Point pp = positions.get(n);
                if (pp == null) continue;

                WaveCell wc = nodeToCellMap.get(n);
                String text = (n.getLabel() == null ? "?" : n.getLabel());
                Color fillColor = Color.LIGHT_GRAY; // default if no wavecell

                if (wc != null) {
                    if (wc.isCollapsed()) {
                        // collapsed => green
                        fillColor = Color.GREEN;
                        if (wc.getCollapsedPattern() != null) {
                            text = wc.getCollapsedPattern().getCenterLabel();
                        } else {
                            text = "?";
                        }
                    } else {
                        // superposition => show possible center labels in {}
                        fillColor = Color.YELLOW;
                        Set<String> labels = new TreeSet<>();
                        for (Pattern p : wc.getDomain()) {
                            labels.add(p.getCenterLabel());
                        }
                        text = "{" + String.join(",", labels) + "}";
                    }
                }

                // Circle
                g2.setColor(fillColor);
                g2.fillOval(pp.x - dia/2, pp.y - dia/2, dia, dia);
                g2.setColor(Color.BLACK);
                g2.drawOval(pp.x - dia/2, pp.y - dia/2, dia, dia);

                // text label in the middle
                FontMetrics fm = g2.getFontMetrics();
                int tx = pp.x - fm.stringWidth(text) / 2;
                int ty = pp.y + fm.getAscent() / 2 - 2;
                g2.drawString(text, tx, ty);
            }
        }

        /**
         * Slight random offset for nodes that are "jitterCooldown > 0".
         * Spreads them out visually if they were placed on top of each other.
         */
        private void doJitter() {
            int w = getWidth(), h = getHeight();
            Random rnd = new Random();

            for (Node n : drawnNodes) {
                int c = jitterCooldown.getOrDefault(n, 0);
                if (c > 0) {
                    Point p = positions.get(n);
                    if (p != null) {
                        p.x = Math.max(0, Math.min(w, p.x + rnd.nextInt(9) - 4));
                        p.y = Math.max(0, Math.min(h, p.y + rnd.nextInt(9) - 4));
                    }
                    jitterCooldown.put(n, c - 1);
                }
            }
        }
    }

    /**
     * A convenience method that logs a message, refreshes,
     * and then waits if we're paused or in single-step mode.
     */
    public void pauseAndRefresh(String msg) {
        logMessage(msg); // This calls refreshAllNodesAndEdges() + repaint()
        waitIfNeeded();
    }
}

package display;

import helper.Edge;
import helper.Node;
import patterns.OverlapManager;
import patterns.OverlapManager.PathPair;
import patterns.Pattern;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Queue;

/**
 * OverlapVisualizer:
 *   - Each row corresponds to a "base" Pattern.
 *     - Column 0: draws the base pattern in a ring-based BFS layout (no highlights).
 *     - Columns 1..N: draws each "other" pattern in ring layout,
 *       with edges on overlapping paths highlighted in red.
 *
 * All edges from the pattern's BFS subgraph are drawn in gray, except
 * those that belong to an overlapping path, which are drawn in red.
 */
public class OverlapVisualizer extends JPanel {

    private static final int CELL_SIZE = 240;
    private static final int CELL_GAP  = 10;

    /** Color used to highlight overlapping path edges. */
    private static final Color HIGHLIGHT_COLOR = Color.RED;

    private final List<Pattern> patterns;
    private final OverlapManager overlapManager;

    /**
     * Construct a grid of ring-based BFS panels:
     *   - row i shows "basePattern i" in column 0,
     *     then columns 1..N each show "basePattern i" vs "otherPattern j" overlap highlights.
     */
    public OverlapVisualizer(List<Pattern> patterns, OverlapManager overlapMgr) {
        this.patterns = patterns;
        this.overlapManager = overlapMgr;

        int n = patterns.size();
        setLayout(new GridLayout(n, n+1, CELL_GAP, CELL_GAP));
        setBackground(Color.WHITE);

        // For each "base pattern" row
        for (int baseIndex = 0; baseIndex < n; baseIndex++) {
            Pattern basePattern = patterns.get(baseIndex);

            // Column 0: ring layout of base pattern, no highlights
            RingPanel basePanel = new RingPanel(basePattern, Collections.emptyMap(), "Base P" + baseIndex);
            basePanel.setBorder(new LineBorder(Color.LIGHT_GRAY));
            add(basePanel);

            // Columns 1..N: ring layout of "other pattern" with highlights for overlap
            for (int otherIndex = 0; otherIndex < n; otherIndex++) {
                if (otherIndex == baseIndex) {
                    // For self-overlap, just put a blank panel
                    JPanel blank = new JPanel();
                    blank.setBorder(new LineBorder(Color.LIGHT_GRAY));
                    blank.setBackground(Color.WHITE);
                    blank.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
                    add(blank);
                } else {
                    Pattern otherPattern = patterns.get(otherIndex);
                    List<PathPair> pairs = overlapManager.getOverlapPathPairs(basePattern, otherPattern);

                    if (pairs.isEmpty()) {
                        // No overlaps => blank panel
                        JPanel blank = new JPanel();
                        blank.setBorder(new LineBorder(Color.LIGHT_GRAY));
                        blank.setBackground(Color.WHITE);
                        blank.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
                        add(blank);
                    } else {
                        // Build a color map to highlight edges that appear on any overlapping path
                        Map<Edge, Color> edgeColors = buildEdgeHighlightMap(otherPattern, pairs);
                        RingPanel overlapPanel = new RingPanel(otherPattern, edgeColors, "P" + otherIndex);
                        overlapPanel.setBorder(new LineBorder(Color.LIGHT_GRAY));
                        add(overlapPanel);
                    }
                }
            }
        }
    }

    /**
     * Assign the HIGHLIGHT_COLOR to every edge that appears on an overlap path.
     */
    private Map<Edge, Color> buildEdgeHighlightMap(Pattern pattern, List<PathPair> pairs) {
        Map<Edge, Color> colorMap = new HashMap<>();
        Set<Edge> allEdges = pattern.getNeighborEdges();

        for (PathPair pair : pairs) {
            // pathInB is the reversed path portion in patternB
            List<Node> pathB = pair.pathInB;
            for (int i = 0; i < pathB.size() - 1; i++) {
                Node a = pathB.get(i);
                Node b = pathB.get(i + 1);
                // Find the matching edge in pattern's subgraph (undirected)
                for (Edge e : allEdges) {
                    if ((e.getNodeA().equals(a) && e.getNodeB().equals(b)) ||
                            (e.getNodeA().equals(b) && e.getNodeB().equals(a))) {
                        colorMap.put(e, HIGHLIGHT_COLOR);
                    }
                }
            }
        }
        return colorMap;
    }

    /**
     * A panel that draws a single Pattern in a ring-based BFS layout,
     * optionally highlighting specific edges in the pattern's subgraph.
     */
    private class RingPanel extends JPanel {

        private final Pattern pattern;
        private final Map<Edge, Color> edgeColors; // edges to highlight => color
        private final String title;

        // We do a heuristic for cell dimension, similar to PatternVisualizer logic
        private final int nodeDiam;
        private final int margin;

        RingPanel(Pattern pat, Map<Edge, Color> edgeColors, String title) {
            this.pattern = pat;
            this.edgeColors = edgeColors;
            this.title = title;

            int radius = Math.max(1, pat.getRadius());
            this.nodeDiam = Math.max(10, 22 - 2 * (radius - 1));
            this.margin   = Math.max(10, nodeDiam + 2);
            int size = 240 + (radius - 1) * 50;

            setPreferredSize(new Dimension(size, size));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;

            // The BFS subgraph for this pattern
            Node centerNode = pattern.getCenterNode();
            Set<Node> nodeSet = new HashSet<>(pattern.getNeighborNodes());
            if (centerNode != null) {
                nodeSet.add(centerNode);
            }
            Set<Edge> edgeSet = pattern.getNeighborEdges();

            // BFS distances in the subgraph, so we can do ring layout
            Map<Node, Integer> distMap = computeDistances(centerNode, nodeSet, edgeSet);

            // Group nodes by BFS distance
            Map<Integer, List<Node>> rings = new HashMap<>();
            int maxDist = 0;
            for (Map.Entry<Node, Integer> e : distMap.entrySet()) {
                int d = e.getValue();
                rings.computeIfAbsent(d, k -> new ArrayList<>()).add(e.getKey());
                maxDist = Math.max(maxDist, d);
            }

            // Calculate ring spacing so the outer ring stays within the panel
            int ringSpacing = (Math.min(w, h) / 2 - margin) / Math.max(1, maxDist);

            // Assign coordinates for each node
            Map<Node, Point> coords = new HashMap<>();
            if (centerNode != null) {
                coords.put(centerNode, new Point(cx, cy));
            }

            for (int d = 1; d <= maxDist; d++) {
                List<Node> layer = rings.getOrDefault(d, Collections.emptyList());
                int count = layer.size();
                int r = d * ringSpacing;
                for (int i = 0; i < count; i++) {
                    double theta = 2 * Math.PI * i / count;
                    int x = (int) (cx + r * Math.cos(theta));
                    int y = (int) (cy + r * Math.sin(theta));
                    coords.put(layer.get(i), new Point(x, y));
                }
            }

            // Draw edges
            for (Edge e : edgeSet) {
                Node na = e.getNodeA();
                Node nb = e.getNodeB();
                Point pa = coords.get(na);
                Point pb = coords.get(nb);
                if (pa == null || pb == null) continue;

                // If edgeColors has a color for this edge, highlight it
                Color c = edgeColors.getOrDefault(e, Color.GRAY);
                if (c.equals(HIGHLIGHT_COLOR)) {
                    g2.setStroke(new BasicStroke(2.5f));
                } else {
                    g2.setStroke(new BasicStroke(1f));
                }
                g2.setColor(c);
                g2.drawLine(pa.x, pa.y, pb.x, pb.y);
            }

            // Draw nodes
            for (Node node : nodeSet) {
                Point p = coords.get(node);
                if (p == null) continue;

                int rx = p.x - nodeDiam / 2;
                int ry = p.y - nodeDiam / 2;

                // white fill
                g2.setColor(Color.WHITE);
                g2.fillOval(rx, ry, nodeDiam, nodeDiam);
                // black border
                g2.setColor(Color.BLACK);
                g2.drawOval(rx, ry, nodeDiam, nodeDiam);

                // label text
                String label = (node.getLabel() != null) ? node.getLabel() : "";
                FontMetrics fm = g2.getFontMetrics();
                int tx = p.x - fm.stringWidth(label) / 2;
                int ty = p.y + (fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, tx, ty);
            }

            // Title in top-left corner
            g2.setColor(Color.BLUE);
            g2.setFont(g2.getFont().deriveFont(10f));
            g2.drawString(title, 5, 12);

            g2.dispose();
        }

        /**
         * BFS within this pattern's subgraph to compute each node's distance from center,
         * for ring-based layout.
         */
        private Map<Node, Integer> computeDistances(Node center,
                                                    Set<Node> nodes,
                                                    Set<Edge> edges) {
            Map<Node, Integer> dist = new HashMap<>();
            if (center == null) {
                return dist;
            }
            dist.put(center, 0);

            // adjacency
            Map<Node, Set<Node>> adj = new HashMap<>();
            for (Node nd : nodes) {
                adj.put(nd, new HashSet<>());
            }
            for (Edge e : edges) {
                Node a = e.getNodeA();
                Node b = e.getNodeB();
                if (adj.containsKey(a)) {
                    adj.get(a).add(b);
                }
                if (adj.containsKey(b)) {
                    adj.get(b).add(a);
                }
            }

            Queue<Node> queue = new LinkedList<>();
            queue.offer(center);

            while (!queue.isEmpty()) {
                Node u = queue.poll();
                int du = dist.get(u);
                for (Node v : adj.get(u)) {
                    if (!dist.containsKey(v)) {
                        dist.put(v, du + 1);
                        queue.offer(v);
                    }
                }
            }
            return dist;
        }
    }

    /**
     * Utility method to display the OverlapVisualizer in a scrollable JFrame.
     */
    public static JFrame showOverlap(List<Pattern> patterns, OverlapManager overlapMgr) {


        JFrame frame = new JFrame("Overlap Visualizer (Ring-based BFS layout + Overlap Highlights)");
        OverlapVisualizer panel = new OverlapVisualizer(patterns, overlapMgr);
        JScrollPane scroll = new JScrollPane(panel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        frame.add(scroll);
        frame.setSize(1200, 800);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        return frame;
    }
}

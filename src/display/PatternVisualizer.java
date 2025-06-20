package display;

import helper.Edge;
import helper.Graph;
import helper.Node;
import patterns.Pattern;
import patterns.PatternExtractor;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Queue;

/**
 * Shows each extracted Pattern in a grid. Each panel:
 *  - draws the full BFS subgraph from pattern.getNeighborNodes() plus the center node
 *  - shows every edge from pattern.getNeighborEdges() in gray
 *  - labels each node with node.getLabel()
 * The layout is ring-based (center node in the middle, neighbors on concentric rings).
 */
public class PatternVisualizer {

    /**
     * Creates and displays a grid of ring-based panels, one per extracted Pattern.
     *
     * @param graph  The original graph from which patterns are extracted
     * @param radius The BFS radius used for Pattern extraction
     * @return A JFrame showing all patterns in a grid layout
     */
    public static JFrame showPatterns(Graph graph, int radius) {
        // Extract patterns first
        Map<Pattern, Integer> patternCounts = PatternExtractor.extractPatterns(graph, radius);
        if (patternCounts.isEmpty()) {
            throw new IllegalArgumentException("No patterns extracted for radius=" + radius);
        }
        List<Pattern> patterns = new ArrayList<>(patternCounts.keySet());

        int total = patterns.size();
        // We'll try to make the grid roughly square
        int cols = (int) Math.ceil(Math.sqrt(total));
        int rows = (total + cols - 1) / cols;

        JFrame frame = new JFrame("Pattern Visualizer (radius=" + radius + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(rows, cols, 10, 10));

        // Create a panel for each pattern
        for (Pattern p : patterns) {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(Color.WHITE);

            // The ring-based subgraph panel
            wrapper.add(new PatternPanel(p), BorderLayout.CENTER);

            // Show usage count below the pattern panel
            int cnt = patternCounts.get(p);
            JLabel lbl = new JLabel("Count = " + cnt, SwingConstants.CENTER);
            wrapper.add(lbl, BorderLayout.SOUTH);

            frame.add(wrapper);
        }

        frame.pack();
        // Slightly enlarge to avoid cramped layout
        frame.setSize(frame.getWidth() + 100, frame.getHeight() + 100);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;

    }

    /**
     * Single panel showing the BFS subgraph of a pattern in a ring layout.
     * All edges are drawn in gray, and all nodes are labeled.
     */
    private static class PatternPanel extends JPanel {
        private final Pattern pattern;
        private final Node centerNode;
        private final Set<Node> patNodes;
        private final Set<Edge> patEdges;
        private final int nodeDiam;
        private final int margin;

        PatternPanel(Pattern p) {
            this.pattern = p;
            this.centerNode = p.getCenterNode();

            // The BFS subgraph = { centerNode, neighborNodes, neighborEdges }
            Set<Node> nodeSet = new HashSet<>(p.getNeighborNodes());
            if (centerNode != null) {
                nodeSet.add(centerNode);
            }
            this.patNodes = nodeSet;
            this.patEdges = p.getNeighborEdges();

            // Heuristic for circle sizes
            int rad = Math.max(1, p.getRadius());
            // Node diameter scales inversely w.r.t. bigger radius
            this.nodeDiam = Math.max(10, 22 - 2 * (rad - 1));
            this.margin = Math.max(10, nodeDiam + 2);

            // Panel size can scale with radius
            int size = 240 + (rad - 1) * 50;
            setPreferredSize(new Dimension(size, size));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int cx = w / 2, cy = h / 2;

            // BFS from the center node in the subgraph to get each node's distance
            Map<Node, Integer> dist = computeDistances(centerNode, patNodes, patEdges);

            // Bucket nodes by BFS distance
            Map<Integer, List<Node>> rings = new HashMap<>();
            int maxDist = 0;
            for (Map.Entry<Node, Integer> e : dist.entrySet()) {
                int d = e.getValue();
                rings.computeIfAbsent(d, k -> new ArrayList<>()).add(e.getKey());
                if (d > maxDist) {
                    maxDist = d;
                }
            }

            // Spacing between rings so the outer ring stays within panel
            int ringSpacing = (Math.min(w, h) / 2 - margin) / Math.max(1, maxDist);

            // Coordinates for nodes in each ring
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

            // Draw edges in gray
            g2.setColor(Color.GRAY);
            g2.setStroke(new BasicStroke(1f));
            for (Edge e : patEdges) {
                Node a = e.getNodeA();
                Node b = e.getNodeB();
                Point pa = coords.get(a);
                Point pb = coords.get(b);
                if (pa != null && pb != null) {
                    g2.drawLine(pa.x, pa.y, pb.x, pb.y);
                }
            }

            // Draw nodes as circles with labels
            for (Node nd : patNodes) {
                Point p = coords.get(nd);
                if (p == null) continue;

                int rx = p.x - nodeDiam / 2;
                int ry = p.y - nodeDiam / 2;
                g2.setColor(Color.WHITE);
                g2.fillOval(rx, ry, nodeDiam, nodeDiam);
                g2.setColor(Color.BLACK);
                g2.drawOval(rx, ry, nodeDiam, nodeDiam);

                String label = (nd.getLabel() != null ? nd.getLabel() : "");
                FontMetrics fm = g2.getFontMetrics();
                int tx = p.x - fm.stringWidth(label) / 2;
                int ty = p.y + (fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, tx, ty);
            }

            g2.dispose();
        }

        /**
         * BFS within this pattern's subgraph to compute distances from the center node.
         */
        private Map<Node, Integer> computeDistances(Node center,
                                                    Set<Node> nodes,
                                                    Set<Edge> edges) {
            Map<Node, Integer> dist = new HashMap<>();
            if (center == null) {
                return dist;
            }
            dist.put(center, 0);
            Queue<Node> queue = new LinkedList<>();
            queue.offer(center);

            // Build adjacency for these nodes and edges only
            Map<Node, Set<Node>> adj = new HashMap<>();
            for (Node nd : nodes) {
                adj.put(nd, new HashSet<>());
            }
            for (Edge e : edges) {
                Node na = e.getNodeA();
                Node nb = e.getNodeB();
                if (adj.containsKey(na)) {
                    adj.get(na).add(nb);
                }
                if (adj.containsKey(nb)) {
                    adj.get(nb).add(na);
                }
            }

            // BFS
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
}

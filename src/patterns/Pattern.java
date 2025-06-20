package patterns;

import helper.Node;
import helper.Edge;

import java.util.*;

public class Pattern {
    private final String centerLabel;
    private final List<String> neighborLabels;
    private final Set<String> neighborEdgeLabels;
    private final List<String> directNeighborLabels;
    private final int radius;

    private transient Node centerNode;
    private transient List<Node> neighborNodes;
    private transient Set<Edge> neighborEdges;
    private transient List<Node> directNeighborNodes;
    private transient Map<Node, Integer> distanceMap = new HashMap<>();


    public Pattern(String centerLabel,
                   List<String> neighborLabels,
                   Set<String> neighborEdgeLabels,
                   List<String> directNeighborLabels,
                   int radius) {
        this.centerLabel = centerLabel;

        List<String> nl = new ArrayList<>(neighborLabels);
        Collections.sort(nl);
        this.neighborLabels = Collections.unmodifiableList(nl);

        Set<String> el = new TreeSet<>(neighborEdgeLabels);
        this.neighborEdgeLabels = Collections.unmodifiableSet(el);

        List<String> dl = new ArrayList<>(directNeighborLabels);
        Collections.sort(dl);
        this.directNeighborLabels = Collections.unmodifiableList(dl);

        this.radius = radius;
    }

    public String getCenterLabel() {
        return centerLabel;
    }

    public int getRadius() {
        return radius;
    }

    // Override toString() for better debug output
    @Override
    public String toString() {
        return "Pattern{" +
                "centerLabel='" + centerLabel + '\'' +
                ", radius=" + radius +
                ", neighborLabels=" + neighborLabels +
                '}';
    }
    // Setters for transient reference fields
    public void setCenterNode(Node centerNode) {
        this.centerNode = centerNode;
    }
    public void setNeighborNodes(List<Node> neighborNodes) {
        this.neighborNodes = new ArrayList<>(neighborNodes);
    }
    public void setNeighborEdges(Set<Edge> neighborEdges) {
        this.neighborEdges = new HashSet<>(neighborEdges);
    }
    public void setDirectNeighborNodes(List<Node> directNeighborNodes) {
        this.directNeighborNodes = new ArrayList<>(directNeighborNodes);
    }

    // Getters for transient reference fields
    public Node getCenterNode() {
        return centerNode;
    }
    public List<Node> getNeighborNodes() {
        return neighborNodes;
    }
    public Set<Edge> getNeighborEdges() {
        return neighborEdges;
    }
    public List<Node> getDirectNeighborNodes() {
        return directNeighborNodes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pattern)) return false;
        Pattern p = (Pattern) o;
        return radius == p.radius
                && centerLabel.equals(p.centerLabel)
                && neighborLabels.equals(p.neighborLabels)
                && neighborEdgeLabels.equals(p.neighborEdgeLabels)
                && directNeighborLabels.equals(p.directNeighborLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(centerLabel, neighborLabels, neighborEdgeLabels,
                directNeighborLabels, radius);
    }

    public void setDistanceMap(Map<Node, Integer> distMap) {
        this.distanceMap = distMap;
    }

}

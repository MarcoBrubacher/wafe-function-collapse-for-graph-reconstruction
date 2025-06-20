package wfc;

import helper.Node;
import patterns.Pattern;

import java.util.*;

public class WaveCell {
    private int targetDegree = -1;
    private final Node node;
    private final Set<Pattern> domain;
    private final List<WaveCell> neighbors;
    private boolean explicitlyCollapsed;
    private Map<Pattern, Double> waveFunction = new HashMap<>();

    public WaveCell(Node node, Set<Pattern> initialDomain) {
        this.node = node;
        this.domain = new HashSet<>(initialDomain);
        this.neighbors = new ArrayList<>();
        this.explicitlyCollapsed = false;
    }

    public void collapseTo(Pattern chosen) {
        domain.clear();
        domain.add(chosen);
        explicitlyCollapsed = true;
        this.targetDegree = chosen.getDirectNeighborNodes().size();
        waveFunction.clear();
        waveFunction.put(chosen, 1.0);
    }

    public void addNeighbor(WaveCell neighbor) {
        if (neighbor != null && neighbor != this && !neighbors.contains(neighbor)) {
            neighbors.add(neighbor);
        }
    }

    public void clearDomain() {
        domain.clear();
        waveFunction.clear();
        explicitlyCollapsed = false;
        targetDegree = -1;
    }

    public void removeNeighbor(WaveCell neighbor) {
        neighbors.remove(neighbor);
    }

    public static void validateAllDegrees(Collection<WaveCell> cells) {
        for (WaveCell cell : cells) {
            if (cell.isCollapsed()) {
                Pattern pattern = cell.getCollapsedPattern();
                if (pattern == null) continue;

                int targetDegree = pattern.getDirectNeighborNodes().size();
                int currentDegree = cell.getNeighbors().size();

                if (targetDegree != cell.getTargetDegree()) {
                    throw new IllegalStateException("WaveCell " + cell.getNode().getId() +
                            " has incorrect targetDegree: " + cell.getTargetDegree() +
                            " (should be " + targetDegree + ")");
                }

                if (currentDegree > targetDegree) {
                    throw new IllegalStateException("WaveCell " + cell.getNode().getId() +
                            " has too many neighbors: " + currentDegree +
                            " (target: " + targetDegree + ")");
                }
            }
        }
    }

    public Node getNode() { return node; }
    public Set<Pattern> getDomain() { return domain; }
    public List<WaveCell> getNeighbors() { return neighbors; }
    public boolean isCollapsed() { return explicitlyCollapsed || domain.size() == 1; }
    public int getDomainSize() { return domain.size(); }
    public int getTargetDegree() {return targetDegree; }

    public Pattern getCollapsedPattern() {
        return isCollapsed() ? domain.iterator().next() : null;
    }
}
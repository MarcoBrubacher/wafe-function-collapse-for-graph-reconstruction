package helper;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Computes a degree‐based expansion cap from a training graph.
 *
 * The cap is determined by selecting the specified percentile of node degrees
 * and then applying a slack factor to allow additional headroom.
 */
public class ExpansionCap {

    /**
     * Computes an integer expansion cap based on the graph’s degree distribution.
     *
     * @param  graph        the training Graph from which to derive degrees
     * @param  percentile   value in [0,1] indicating which percentile to use (e.g. 0.90 for 90th)
     * @param  slackFactor  multiplier to apply for extra headroom (e.g. 1.10 for +10%)
     * @return              the ceiling of (percentile‐degree × slackFactor)
     * @throws IllegalArgumentException if the graph has no nodes or percentile is out of range
     */
    public static int computeCap(Graph graph, double percentile, double slackFactor) {
        if (percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile must be between 0.0 and 1.0");
        }
        if (slackFactor < 0.0) {
            throw new IllegalArgumentException("Slack factor must be non‐negative");
        }

        // 1) Collect and sort all node degrees
        List<Integer> sortedDegrees = graph.getAllNodes().stream()
                .map(node -> node.getNeighbors().size())
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        if (sortedDegrees.isEmpty()) {
            throw new IllegalArgumentException("Graph has no nodes; cannot compute expansion cap");
        }

        // 2) Determine the index for the requested percentile
        int idx = (int) Math.ceil(percentile * sortedDegrees.size()) - 1;
        idx = Math.max(0, Math.min(idx, sortedDegrees.size() - 1));
        int baseDegree = sortedDegrees.get(idx);

        // 3) Apply slack factor and round up
        return (int) Math.ceil(baseDegree * slackFactor);
    }
}

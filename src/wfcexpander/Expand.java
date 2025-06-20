package wfcexpander;

import display.GraphAugmentationVisualizer;
import helper.Graph;
import helper.Node;
import patterns.Pattern;
import patterns.PatternCompatibilityHelper;
import wfc.Entropy;
import wfc.WaveCell;

import java.util.*;

public class Expand {

    public static void expand(
            WaveCell frontier,
            Collection<Pattern> allPatterns,
            List<WaveCell> waveCells,
            Graph outputGraph,
            GraphAugmentationVisualizer graphViz,
            PatternCompatibilityHelper compHelper,
            Entropy entropy    // <-- added
    ) {
        // 1) Must be collapsed, have a valid pattern
        if (!shouldExpand(frontier)) {
            return;
        }

        // 2) Compute how many neighbors are needed
        int neededNeighbors = computeNeededNeighbors(frontier);
        if (neededNeighbors <= 0) {
            return;
        }

        // 3) Build domain
        Set<Pattern> compDomain = buildCompatibleDomain(frontier, allPatterns, compHelper);
        if (compDomain.isEmpty()) {
            return;
        }

        // 4) Create new waveCells
        createNewCells(frontier, waveCells, outputGraph, graphViz, compDomain, neededNeighbors, entropy);
    }

    // -------------------------------------------------------------------
    //   Helpers
    // -------------------------------------------------------------------

    private static boolean shouldExpand(WaveCell f) {
        return f != null && f.isCollapsed() && f.getCollapsedPattern() != null;
    }

    private static int computeNeededNeighbors(WaveCell f) {
        int t = f.getTargetDegree();
        if (t < 0) {
            return 0;
        }
        int curr = f.getNeighbors().size();
        int missing = t - curr;
        if (missing <= 0) {
            return 0;
        }
        // half of missing
        return (int) Math.ceil(missing / 2.0);
    }

    private static Set<Pattern> buildCompatibleDomain(
            WaveCell f,
            Collection<Pattern> allPatterns,
            PatternCompatibilityHelper ch
    ) {
        Pattern frontPat = f.getCollapsedPattern();
        Set<Pattern> domain = new HashSet<>();
        for (Pattern cand : allPatterns) {
            if (ch.areCompatible(frontPat, cand)) {
                domain.add(cand);
            }
        }
        return domain;
    }

    private static void createNewCells(
            WaveCell frontier,
            List<WaveCell> waveCells,
            Graph outputGraph,
            GraphAugmentationVisualizer graphViz,
            Set<Pattern> compDomain,
            int cellCount,
            Entropy entropy
    ) {
        for (int i = 0; i < cellCount; i++) {
            String newId = String.valueOf(outputGraph.getAllNodes().size());
            Node newNode = outputGraph.addNode(newId, "");

            // domain = compDomain
            WaveCell newCell = new WaveCell(newNode, new HashSet<>(compDomain));
            waveCells.add(newCell);

            // Connect
            outputGraph.addEdge(frontier.getNode().getId(), newId);
            frontier.addNeighbor(newCell);
            newCell.addNeighbor(frontier);

            // Register in visual
            graphViz.registerWaveCell(newCell);

            // Add to entropy queue so it can eventually be collapsed
            entropy.addToQueue(newCell);
        }
    }
}

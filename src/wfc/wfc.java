package wfc;

import display.GraphAugmentationVisualizer;
import display.OverlapVisualizer;
import display.PatternVisualizer;
import helper.FileHandler;
import helper.Graph;
import helper.Node;
import patterns.OverlapManager;
import patterns.Pattern;
import patterns.PatternCompatibilityHelper;
import patterns.PatternExtractor;
import quality.WFCQualityMetrics;
import wfcexpander.Connect;
import wfcexpander.Expand;
import wfcexpander.Merge;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class wfc {

    // ------------------------------
    //     CONSTANTS & FIELDS
    // ------------------------------

    private static final Random RANDOM = new Random(42);  // For weighted picking

    public static final int RADIUS = 2;       // BFS radius for pattern extraction
    public static final int MAX_ITERATIONS = 10;

    private static final Map<Pattern, Integer> patternUsageCounts = new HashMap<>();
    public static Map<Pattern, Integer> patternFrequencies;

    private static Graph outputGraph;
    private static List<WaveCell> waveCells;
    private static OverlapManager overlapManager;
    private static GraphAugmentationVisualizer graphViz;
    private static PatternCompatibilityHelper compHelper;
    private static List<Pattern> allPatterns;

    // ------------------------------
    //            MAIN
    // ------------------------------

    public static void main(String[] args) throws IOException {
        // 1) Load input
        String inputFolder = "res/syntheticdata/SyntheticGraph/";
        String labelFile   = inputFolder + "graph_labels.txt";
        String edgeFile    = inputFolder + "graph_edges.txt";

        // 2) Output
        String outputFolder  = "res/syntheticdata/OutputGraph/";
        createDirectory(outputFolder);
        String outLabelFile  = outputFolder + "augmented_labels.txt";
        String outEdgeFile   = outputFolder + "augmented_edges.txt";

        // 3) Load training
        Graph sampleGraph = FileHandler.loadGraphFromFiles(labelFile, edgeFile);

        // 4) Extract patterns & set up
        patternFrequencies = PatternExtractor.extractPatterns(sampleGraph, RADIUS);
        allPatterns = new ArrayList<>(patternFrequencies.keySet());
        overlapManager = new OverlapManager(allPatterns, RADIUS, patternFrequencies);
        compHelper     = new PatternCompatibilityHelper(overlapManager);

        // 5) Initialize waveCells, outputGraph, etc.
        Entropy entropy = new Entropy(patternFrequencies, overlapManager);
        outputGraph     = new Graph();
        graphViz        = new GraphAugmentationVisualizer(outputGraph);
        waveCells       = new ArrayList<>();

        // 6) Create seed cell
        Node seedNode   = outputGraph.addNode("0", "");
        WaveCell seedCell = new WaveCell(seedNode, new HashSet<>(allPatterns));
        waveCells.add(seedCell);
        entropy.addToQueue(seedCell);

        // Visual
        graphViz.registerWaveCell(seedCell);
        graphViz.waitIfNeeded();

        // 7) Collapse seed
        collapseCell(seedCell, entropy);
        Pattern seedPat = seedCell.getCollapsedPattern();
        if (seedPat != null) {
            seedNode.setLabel(seedPat.getCenterLabel());
            graphViz.highlightNode(seedNode, seedPat.getCenterLabel());
        }

        // 8) Expand from seed
        WaveCell.validateAllDegrees(waveCells);

        // pass 'entropy' into expand
        Expand.expand(seedCell, allPatterns, waveCells, outputGraph, graphViz, compHelper, entropy);

        // queue uncollapsed
        for (WaveCell cell : waveCells) {
            if (!cell.isCollapsed()) {
                entropy.addToQueue(cell);
            }
        }

        // 9) Main generation loop
        int iterationCount = 0;
        while (iterationCount < MAX_ITERATIONS) {
            iterationCount++;

            WaveCell nextCell = entropy.pollLowestEntropyCell();
            if (nextCell == null || nextCell.getDomainSize() == 0) {
                break;
            }
            graphViz.pauseAndRefresh("→ Iteration:" + iterationCount);

            if (!nextCell.isCollapsed()) {
                // collapse
                collapseCell(nextCell, entropy);

                // expand
                Expand.expand(nextCell, allPatterns, waveCells, outputGraph, graphViz, compHelper, entropy);

                // merge
                Merge.merge(nextCell, waveCells, graphViz, overlapManager, entropy);


                // connect
                List<WaveCell> candidates = waveCells.stream()
                        .filter(c -> c != nextCell)
                        .collect(Collectors.toList());
                Connect.connect(nextCell, candidates, outputGraph, graphViz, overlapManager, patternFrequencies, entropy);


                // constraints
                ConstraintPropagator.enforceConsistency(waveCells, graphViz, compHelper, entropy);

                // update entropies + remove done
                entropy.updateNeighborEntropies(nextCell);
                waveCells.removeIf(wc -> isDone(wc));
            }
        }

        // 10) final cleanup
        graphViz.logMessage("Reached MAX_ITERATIONS = " + MAX_ITERATIONS + ", entering final consolidation...");
        cleanup(entropy);

        // 11) save
        FileHandler.saveGraphToFiles(outputGraph, outLabelFile, outEdgeFile);
        graphViz.logMessage("Saved final output to " + outLabelFile + " and " + outEdgeFile);

        // 12) evaluate & visualize
        WFCQualityMetrics.evaluateGraphQuality(labelFile, edgeFile, outLabelFile, outEdgeFile);
        PatternVisualizer.showPatterns(sampleGraph, RADIUS);
        OverlapVisualizer.showOverlap(allPatterns, overlapManager);
    }

    // ------------------------------
    //   HELPER METHODS
    // ------------------------------

    private static void collapseCell(WaveCell cell, Entropy entropy) {
        Pattern chosen = selectPatternFromDomain(cell, entropy);
        if (chosen != null) {
            cell.collapseTo(chosen);
            cell.getNode().setLabel(chosen.getCenterLabel());

            patternUsageCounts.merge(chosen, 1, Integer::sum);

            entropy.removeFromQueue(cell);
            entropy.updateNeighborEntropies(cell);
        }
    }

    private static Pattern selectPatternFromDomain(WaveCell cell, Entropy entropy) {
        Set<Pattern> domain = cell.getDomain();
        if (domain.isEmpty()) {
            throw new IllegalStateException("Cannot select pattern from empty domain");
        }
        if (domain.size() == 1) {
            return domain.iterator().next();
        }

        // Weighted pick
        Map<Pattern, Double> waveFunction = entropy.updateWaveFunction(domain, cell.getNeighbors());
        double totalWeight = waveFunction.values().stream().mapToDouble(Double::doubleValue).sum();

        double rand = RANDOM.nextDouble() * totalWeight;
        double cum  = 0.0;
        for (Map.Entry<Pattern, Double> e : waveFunction.entrySet()) {
            cum += e.getValue();
            if (cum >= rand) {
                return e.getKey();
            }
        }
        // fallback
        return domain.iterator().next();
    }

    private static boolean isDone(WaveCell cell) {
        if (!cell.isCollapsed()) return false;
        int req = cell.getTargetDegree();
        if (req == -1) return true;
        return cell.getNeighbors().size() >= req;
    }

    private static void cleanup(Entropy entropy) {
        graphViz.logMessage("[Cleanup] Starting enhanced cleanup phase...");

        final int MAX_CLEANUP_ITERATIONS = 20;
        int iteration = 0;
        boolean changed = true;

        // 1) force collapse all uncollapsed
        for (WaveCell c : waveCells) {
            if (!c.isCollapsed()) {
                forceCollapseWaveCell(c, entropy);
            }
        }

        // 2) iterative augmentation
        while (changed && iteration < MAX_CLEANUP_ITERATIONS) {
            iteration++;
            graphViz.logMessage("[Cleanup] Iteration #" + iteration);
            changed = false;

            // re-run constraint
            try {
                ConstraintPropagator.enforceConsistency(waveCells, graphViz, compHelper, entropy);
            } catch (IllegalStateException e) {
                graphViz.logMessage("[Cleanup] Contradiction: " + e.getMessage());
                break;
            }

            List<WaveCell> snapshot = new ArrayList<>(waveCells);
            for (WaveCell frontier : snapshot) {
                if (!frontier.isCollapsed()) {
                    continue;
                }

                int req = frontier.getTargetDegree();
                int curr = frontier.getNeighbors().size();
                if (req == -1 || curr >= req) {
                    continue;
                }

                // merges
                int before = curr;
                Merge.merge(frontier, waveCells, graphViz, overlapManager, entropy);
                curr = frontier.getNeighbors().size();
                if (curr > before) changed = true;


                // connect
                if (curr < req) {
                    before = curr;
                    List<WaveCell> connCandidates = waveCells.stream().filter(c -> c != frontier).collect(Collectors.toList());
                    Connect.connect(frontier, connCandidates, outputGraph, graphViz, overlapManager, patternFrequencies, entropy);
                    curr = frontier.getNeighbors().size();
                    if (curr > before) changed = true;
                }

                // expand
                if (curr < req) {
                    before = curr;
                    Expand.expand(frontier, allPatterns, waveCells, outputGraph, graphViz, compHelper, entropy);
                    curr = frontier.getNeighbors().size();
                    if (curr > before) changed = true;


                    // second pass merges
                    if (curr < req) {
                        before = curr;
                        Merge.merge(frontier, waveCells, graphViz, overlapManager, entropy);
                        curr = frontier.getNeighbors().size();
                        if (curr > before) changed = true;

                        // second pass connect
                        if (curr < req) {
                            before = curr;
                            List<WaveCell> conn2 = waveCells.stream().filter(c -> c != frontier).collect(Collectors.toList());
                            Connect.connect(frontier, conn2, outputGraph, graphViz, overlapManager, patternFrequencies, entropy);
                            curr = frontier.getNeighbors().size();
                            if (curr > before) changed = true;
                        }
                    }
                }
            }

            // force collapse new uncollapsed
            for (WaveCell c : waveCells) {
                if (!c.isCollapsed()) {
                    forceCollapseWaveCell(c, entropy);
                    changed = true;
                }
            }

            if (allCellsSatisfied()) {
                break;
            }
            WaveCell.validateAllDegrees(waveCells);

            waveCells.removeIf(wc -> isDone(wc));
        }
        graphViz.logMessage("[Cleanup] Enhanced cleanup phase finished.");
    }

    private static void forceCollapseWaveCell(WaveCell cell, Entropy entropy) {
        if (cell.isCollapsed()) return;
        Pattern chosen = selectPatternFromDomain(cell, entropy);
        cell.collapseTo(chosen);
        cell.getNode().setLabel(chosen.getCenterLabel());
        patternUsageCounts.merge(chosen, 1, Integer::sum);
        entropy.removeFromQueue(cell);
        entropy.updateNeighborEntropies(cell);
    }

    private static boolean allCellsSatisfied() {
        for (WaveCell c : waveCells) {
            if (!c.isCollapsed()) return false;
            int req = c.getTargetDegree();
            if (req != -1 && c.getNeighbors().size() != req) {
                return false;
            }
        }
        return true;
    }

    public static void createDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}

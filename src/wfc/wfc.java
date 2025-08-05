package wfc;

import constructor.Connect;
import constructor.Expand;
import helper.ExpansionCap;
import helper.Exporter;
import helper.Graph;
import helper.Reader;
import patterns.Pattern;
import patterns.PatternCompatibility;
import patterns.PatternExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Coordinates the full Wave Function Collapse–based graph generation pipeline.
 *
 * For each training graph discovered in the input directory (resuming from the last
 * processed index), this class executes the following stages:
 *
 * 1. Pattern Extraction
 *    - Builds an ego-network Pattern for every node out to RADIUS hops,
 *      capturing node labels, subgraph adjacency, layering, and a refined canonical form.
 *
 * 2. Compatibility Mapping
 *    - Generates multi-radius tables that record which Patterns empirically co-occur
 *      at each hop distance, used later for constraint propagation and edge wiring.
 *
 * 3. Generation Pipeline
 *    a. Growth Phase
 *       - Repeatedly select and collapse the frontier cell with lowest Shannon entropy.
 *       - Expand a controlled number of new neighbor cells around each collapse.
 *       - Prune and force-collapse neighbors via ConstraintPropagator.
 *       - Wire remaining “stubs” among settled cells using Connect.
 *       - Continue until settled cells reach lowerCap × targetSize or the frontier is empty.
 *
 *    b. Cleanup Phase
 *       - Further collapse any remaining frontier cells and wire outstanding stubs.
 *       - Expansion allowance decays linearly from full cap at 100% progress to zero
 *         at the hard limit of upperCap × targetSize.
 *       - Guarantees that all cells are collapsed and minimizes any leftover open stubs.
 *
 * 4. Export and Evaluation
 *    - Writes the final edges and labels files for the generated graph.
 *    - Invokes WFCQualityMetrics to assess how closely the synthetic graph matches
 *      the structural properties of the training data.
 *
 * Configuration constants:
 * - RADIUS: number of hops in each ego-network.
 * - lowerCap: fraction of targetSize at which growth switches to cleanup.
 * - upperCap: hard limit fraction beyond which no new cells are added.
 * - sizeFactor: multiplier defining number of nodes the generated graph should have = sizeFactor × number of nodes the training graphs has.
 * - RESUME_FILE: path used to record and resume the last completed graph index.
 *
 */

public class wfc {
    // radius for ego-network pattern extraction
    private static final int RADIUS = 2;

    // number of nodes our generate graph will have (in bounds of lowerCap to upperCap = sizeFactor × number of nodes in training graph
    private static final int sizeFactor = 2;

    // When settled cells reach lowerCap × targetSize, switch from growth to cleanup
    private static final double lowerCap = 0.9;

    // Hard stop: no new expansions once settled cells ≥ upperCap × targetSize
    private static final double upperCap = 1.1;

    // File used to record and resume the last completed graph index
    private static final Path RESUME_FILE = Paths.get("res/last_iter.txt");

    /**
     * Application entry point: processes all training graphs in sequence, resuming from the last completed index.
     *
     * For each graph index determined from the input directory:
     *  - Constructs paths for the edge and label files.
     *  - Loads the training graph into memory.
     *  - Runs the full WFC generation pipeline to produce a synthetic graph.
     *  - Updates the resume file so that work can safely continue after interruptions.
     *
     * When all graphs have been processed, the resume file is deleted to signal completion.
     *
     * @param args unused
     * @throws IOException if any I/O operation (reading or writing files) fails
     */
    public static void main(String[] args) throws IOException {
        // 1) Figure out where to resume
        int startIndex = loadLastIter();

        // 2) Auto-detect how many training graphs there are
        Path trainingDir = Paths.get("res/trainingGraphs");
        int maxIndex = Files.list(trainingDir)
                .map(Path::getFileName)
                .map(Path::toString)
                // match files like "graphedges3"
                .filter(name -> name.startsWith("graphedges"))
                // extract the numeric suffix
                .map(name -> name.substring("graphedges".length()))
                .mapToInt(Integer::parseInt)
                .max()
                // if none found, fall back to startIndex (so the loop will still run once)
                .orElse(startIndex);

        // 3) Process each graph in [startIndex…maxIndex]
        for (int graphIndex = startIndex; graphIndex <= maxIndex; graphIndex++) {
            Path edgesPath  = trainingDir.resolve("graphedges"  + graphIndex);
            Path labelsPath = trainingDir.resolve("graphlabels" + graphIndex);

            Graph trainingGraph = Reader.load(edgesPath, labelsPath);
            System.out.println("Loaded training graph index " + graphIndex);

            try {
                run(trainingGraph, graphIndex);
                saveLastIter(graphIndex + 1);

            } catch (Exception e) {
                System.err.printf("Error processing graph index %d: %s%n", graphIndex, e.getMessage());
                throw e;
            }
        }

        // Clean up the resume file when we're completely done
        Files.deleteIfExists(RESUME_FILE);
    }




    /**
     * Prepares, finishes and runs the full WFC generation pipeline using the given training graph.
     *
     *
     * Steps:
     * 1. Compute global parameters:
     *    - targetSize: twice the number of training nodes
     *    - expansionCap: 90th-percentile degree × slack factor
     * 2. Extract ego-network patterns at the specified radius and build
     *    multi-radius compatibility tables.
     * 3. Build lookup maps:
     *    - centerLabelMap: pattern ID → the label to assign on collapse
     *    - degreeMap:     pattern ID → original center-node degree
     * 4. Initialize WFC state:
     *    - frontier:       list of uncollapsed Cells (start with one seed)
     *    - settled:        list of collapsed Cells
     *    - adjacency:      bidirectional map tracking cell neighbors
     *    - degreeTargets:  desired degree for each collapsed Cell
     *    - propagator:     enforces local compatibility constraints
     *    - connector:      wires stubs based on compatibility tables
     *    - entropy:        selects collapse order by Shannon entropy
     * 5. Growth phase:
     *    - Iteratively collapse the lowest-entropy frontier cell
     *    - Expand new neighbor Cells around each collapse
     *    - Propagate constraints locally, then reconnect stubs globally
     *    - Repeat until lowerCap reached
     * 6. Cleanup phase:
     *    - Finalize wiring and collapse to completion beyond lowerCap of target
     * 7. Export:
     *    - Write settled Cells’ edges and labels to disk using Exporter
     *
     * @param trainingGraph the input graph from which to learn patterns
     * @param iteration     index used to name output files uniquely
     * @throws IOException if reading or writing any file fails
     */
    public static void run(Graph trainingGraph, int iteration) throws IOException {
        // a) Compute overall parameters
        int targetSize = trainingGraph.getAllNodes().size() * sizeFactor;
        int expansionCap = ExpansionCap.computeCap(trainingGraph, 0.90, 1.10);

        // b) 1) Pattern extraction & compatibility
        List<Pattern> patterns = PatternExtractor.extractPatterns(trainingGraph, RADIUS);
        Set<Integer> allPatternIds = patterns.stream()
                .map(Pattern::getId)
                .collect(Collectors.toSet());
        Map<Integer, Map<Integer, Set<Integer>>> compatByRadius =
                PatternCompatibility.computeCompatibilityByRadius(trainingGraph, RADIUS);

        // c) 2) Build lookup maps for collapse and expansion
        Map<Integer, Integer> centerLabelMap = patterns.stream()
                .collect(Collectors.toMap(Pattern::getId, Pattern::getCenterLabel));
        Map<Integer, Integer> degreeMap = patterns.stream()
                .collect(Collectors.toMap(Pattern::getId, Pattern::getCenterNodeDegree));

        // d) 3) Initialize WFC state
        List<Cell> frontier = new ArrayList<>();
        List<Cell> settled = new ArrayList<>();
        Map<Cell, List<Cell>> adjacency = new HashMap<>();
        Map<Cell, Integer> degreeTargets = new HashMap<>();
        ConstraintPropagator propagator = new ConstraintPropagator(compatByRadius);
        Connect connector = new Connect(adjacency, compatByRadius);

        // Seed: one cell containing all patterns
        Cell seed = new Cell(allPatternIds);
        frontier.add(seed);
        adjacency.put(seed, new ArrayList<>());

        Entropy entropy = new Entropy(frontier, patterns);

        // e) 4) Growth phase: collapse, expand, propagate, connect
        generation(targetSize, expansionCap,
                frontier, settled, adjacency, degreeTargets,
                centerLabelMap, degreeMap, allPatternIds,
                propagator, connector, entropy);

        // f) 5) Cleanup phase: finalize graph beyond ~80% of target
        performCleanup(targetSize, expansionCap,
                frontier, settled, adjacency,
                degreeTargets, centerLabelMap, degreeMap,
                allPatternIds, propagator, connector, entropy);

        // g) Export final graph to files
        Path outEdges  = Paths.get("res/generatedGraphs/graphedges"  + iteration);
        Path outLabels = Paths.get("res/generatedGraphs/graphlabels" + iteration);
        Exporter.export(settled, adjacency, outEdges, outLabels);
    }

    /**
     * Growth phase: constructs the generated graph by alternating collapse, expansion,
     * constraint propagation, and stub wiring until approximately 90% of the target
     * number of collapsed cells is reached or the frontier is empty.
     *
     * Steps:
     * - Progress check: if settled cells ≥ 90% of targetSize, exit growth.
     * - Entropy collapse: recompute entropies on frontier, collapse the lowest-entropy cell,
     *   record its pattern and update degreeTargets.
     * - Budgeted expansion: compute remaining slots (expansionCap minus frontier size);
     *   if positive, expand only around the newly collapsed cell.
     * - Local propagation: prune and force-collapse any neighbors of the new cell.
     * - Global wiring: connect stubs among all settled cells according to compatibility,
     *   then re-propagate across settled cells to catch any new forced collapses.
     *
     * Repeat until no frontier remains, no further collapse is possible, or settled
     * count reaches the growth threshold.
     *
     * @param targetSize       desired number of collapsed cells × 2 for progress tracking
     * @param expansionCap     base number of expansion slots per collapse wave
     * @param frontier         mutable list of uncollapsed cells forming the WFC frontier
     * @param settled          mutable list of cells already collapsed
     * @param adjacency        bidirectional adjacency map of all cells
     * @param degreeTargets    map from each settled Cell to its target degree
     * @param centerLabelMap   map from pattern ID to the cell’s center label
     * @param degreeMap        map from pattern ID to its original node degree
     * @param allPatternIds    complete set of pattern IDs (used when initializing new cells)
     * @param propagator       enforces local compatibility constraints
     * @param connector        wires remaining stubs based on compatibility tables
     * @param entropy          selects collapse order by Shannon entropy
     */
    private static void generation(int targetSize,
                                   int expansionCap,
                                   List<Cell> frontier,
                                   List<Cell> settled,
                                   Map<Cell, List<Cell>> adjacency,
                                   Map<Cell, Integer> degreeTargets,
                                   Map<Integer, Integer> centerLabelMap,
                                   Map<Integer, Integer> degreeMap,
                                   Set<Integer> allPatternIds,
                                   ConstraintPropagator propagator,
                                   Connect connector,
                                   Entropy entropy) {
        while (!frontier.isEmpty()) {
            // 1) Progress check: exit growth phase if we've reached ~90% of the target
            double progress = (double) settled.size() / targetSize;
            if (progress >= lowerCap || settled.size() >= targetSize) {
                break;
            }

            // 2) Entropy-based collapse:
            //    - recompute entropies, pick lowest >0, collapse, and record its degree
            entropy.computeEntropies();
            int collapseIdx = entropy.collapseNextCell();
            if (collapseIdx < 0) {
                break;  // no further collapses possible
            }
            Cell collapsedCell = frontier.remove(collapseIdx);
            settled.add(collapsedCell);
            int pid = collapsedCell.getCollapsedPattern();
            degreeTargets.put(collapsedCell, degreeMap.get(pid));

            // 3) Budgeted expansion:
            //    - remaining slots = expansionCap – current frontier size
            //    - if positive, expand around this cell
            int remainingSlots = expansionCap - frontier.size();
            if (remainingSlots > 0) {
                Map<Cell, Integer> centerDegrees = Collections.singletonMap(
                        collapsedCell, degreeMap.get(pid));
                Expand.expand(
                        Collections.singletonList(collapsedCell),
                        remainingSlots,
                        centerDegrees,
                        allPatternIds,
                        frontier,
                        adjacency
                );
            }

            // 4) Local propagation: prune & force-collapse neighbors of the newly collapsed cell
            propagate(
                    Collections.singletonList(collapsedCell),
                    propagator,
                    frontier,
                    settled,
                    adjacency,
                    centerLabelMap,
                    degreeMap,
                    allPatternIds,
                    expansionCap
            );

            // 5) Global wiring & second propagation:
            //    - connect stubs among all settled cells
            //    - then re-run propagation over settled set to catch any new forced collapses
            connector.connect(settled, degreeTargets);
            propagate(
                    settled,
                    propagator,
                    frontier,
                    settled,
                    adjacency,
                    centerLabelMap,
                    degreeMap,
                    allPatternIds,
                    expansionCap
            );
        }
    }


    /**
     * Performs iterative constraint propagation, forced collapse, and local expansion.
     *
     * Starting from a collection of newly collapsed cells, this method:
     * 1. Prunes the domains of all frontier cells based on compatibility with the current wave of collapsed cells.
     * 2. Identifies any cells whose domain has been reduced to exactly one pattern (forced to collapse).
     * 3. Collapses those forced cells immediately and records their original degrees.
     * 4. Allocates a small number of new frontier cells around each newly collapsed cell,
     *    scaled by √(number of forced cells) to prevent bursts of growth.
     * 5. Repeats the process until no further cells are forced by pruning.
     *
     * Forced collapse occurs only when a cell’s possible-pattern set shrinks to size == 1
     * as a direct result of propagation. Expansion uses the provided baseExpansionCap to
     * control how many new cells may be created in each wave.
     *
     * @param recentlyCollapsed   the cells most recently collapsed (first wave seeds)
     * @param propagator          the propagator that prunes domains based on compatibility
     * @param frontier            mutable list of cells still uncollapsed
     * @param settled             mutable list of cells already collapsed
     * @param adjacencyMap        bidirectional adjacency of all cells
     * @param centerLabels        map from pattern ID → center label (for collapse)
     * @param originalDegrees     map from pattern ID → original center-node degree (for expansion)
     * @param allPatternIds       comprehensive set of all pattern IDs (for initializing new cells)
     * @param baseExpansionCap    baseline number of expansions allowed per wave
     */
    private static void propagate(Collection<Cell> recentlyCollapsed,
                                  ConstraintPropagator propagator,
                                  List<Cell> frontier,
                                  List<Cell> settled,
                                  Map<Cell, List<Cell>> adjacencyMap,
                                  Map<Integer, Integer> centerLabels,
                                  Map<Integer, Integer> originalDegrees,
                                  Set<Integer> allPatternIds,
                                  int baseExpansionCap) {
        // Initialize the first wave of collapsed-cell seeds
        List<Cell> collapsedCells = new ArrayList<>(recentlyCollapsed);

        // Continue until no new cells are forced to collapse
        while (!collapsedCells.isEmpty()) {
            // 1) Prune all frontier cells based on current wave of collapsed cells
            List<Cell> forced = propagator.propagate(collapsedCells, frontier, adjacencyMap);
            if (forced.isEmpty()) {
                break;  // no further forced collapses this wave
            }

            // 2) Immediately collapse each forced cell and record its degree
            Map<Cell, Integer> forcedDegrees = new LinkedHashMap<>();
            for (Cell cell : forced) {
                // Exactly one possibility remains
                int chosenPattern = cell.getPossiblePatterns().iterator().next();
                cell.collapseTo(chosenPattern, centerLabels.get(chosenPattern));
                frontier.remove(cell);
                settled.add(cell);
                forcedDegrees.put(cell, originalDegrees.get(chosenPattern));
            }

            // 3) Compute this wave’s expansion budget:
            //    scale baseExpansionCap by sqrt(size of forced set), then subtract current frontier size
            int waveSize = forced.size();
            int scaledCap = (int) Math.ceil(Math.sqrt(waveSize)) * baseExpansionCap;
            int expansionBudget = scaledCap - frontier.size();

            // 4) Expand around newly collapsed cells if budget permits
            if (expansionBudget > 0) {
                Expand.expand(
                        forced,
                        expansionBudget,
                        forcedDegrees,
                        allPatternIds,
                        frontier,
                        adjacencyMap
                );
            }

            // Prepare next wave using the cells just collapsed
            collapsedCells = forced;
        }
    }


    /**
     * Cleanup phase: finalize wiring and collapse remaining cells after the growth phase (~90% of target).
     *
     * This method continues the WFC process by satisfying outstanding edge requirements (stubs) and closing
     * out the frontier. It gradually reduces new cell creation as the target size is reached, and stops at
     * an upper size cap. The goal is to end up with no open connections if possible (fully connected graph),
     * or otherwise as few open stubs as possible if the size limit prevents further expansion. In all cases,
     * every cell will be collapsed by the end of this phase.
     *
     * Steps:
     * 1. **Compute Expansion Allowance:** Determine how many new cells can be added this iteration based on
     *    current progress and how many edges remain unsatisfied. The allowance decays linearly from the base
     *    expansion cap at 100% of target size down to zero at the upperCap (e.g., 110% of target).
     * 2. **Check Completion/Limit:** If all edges are satisfied and no frontier remains, or if the hard size
     *    limit is reached, break out of the loop (cleanup done or size cap reached).
     * 3. **Fill Stubs if Needed:** If there are open stubs but no frontier cells to collapse (i.e., nowhere to
     *    attach new neighbors), allocate new uncollapsed cells to fulfill those stubs (up to the expansion
     *    allowance). This prevents stranded open connections.
     * 4. **Phase A – Connect Stubs:** Attempt to greedily connect any pairs of collapsed cells that both have
     *    open stubs, without violating compatibility. If any edges are added, propagate constraints globally
     *    (this may force-collapse some frontier cells) and then loop back to recompute the situation.
     * 5. **Phase B – Collapse Frontier:** If no stub connections were made in Phase A, pick the lowest-entropy
     *    frontier cell and collapse it to a pattern. Move it to settled, record its target degree from the
     *    pattern, and immediately try to connect its open edge slots to existing cells. If the expansion
     *    allowance permits, also create new neighbor cells to satisfy the collapsed cell’s remaining degree
     *    requirements. After this, propagate constraints from this newly collapsed cell (and any new cells
     *    added) to prune domains or force additional collapses.
     * 6. **Repeat:** Continue the loop to gradually close open stubs and collapse all cells, until done.
     * 7. **Finalize – Collapse All:** After exiting the main loop, collapse any cells still in the frontier
     *    one by one (no new expansions at this stage). After each collapse, connect any possible edges and
     *    propagate constraints. This guarantees all cells are finalized.
     *
     * By the end, every cell is collapsed. If the upper size cap prevented closing all stubs, only a minimal
     * number of open stubs will remain unsatisfied.
     */
    private static void performCleanup(int targetSize,
                                       int baseExpansionCap,
                                       List<Cell> frontier,           // list of uncollapsed frontier cells
                                       List<Cell> settled,            // list of collapsed cells
                                       Map<Cell, List<Cell>> adjacency,
                                       Map<Cell, Integer> targetDegree, // desired degree for each collapsed cell
                                       Map<Integer, Integer> patternCenterLabel, // map: pattern ID → center label
                                       Map<Integer, Integer> patternDegree,      // map: pattern ID → original degree
                                       Set<Integer> allPatternIds,
                                       ConstraintPropagator propagator,
                                       Connect connector,
                                       Entropy entropy) {
        final double DECAY_START = 1.00;          // begin throttling expansions at 100% of target size
        final double DECAY_END   = upperCap;      // no expansions allowed at or beyond hard cap (e.g., 110% of target)
        final int hardUpperBound = (int) Math.ceil(targetSize * upperCap);

        // Main loop: continue until all stubs closed & frontier empty, or size limit reached
        while (true) {
            // 1. Calculate progress and dynamic expansion allowance for this iteration
            double progress = (double) settled.size() / targetSize;
            int openStubs   = countOpenStubs(settled, targetDegree, adjacency);
            // Linearly decay expansion budget from baseExpansionCap down to 0 as progress goes from 100% to upperCap%
            int linearBudget = (int) Math.ceil(computeLinearDecay(progress, DECAY_START, DECAY_END) * baseExpansionCap);
            // Determine how many new cells are actually needed to close all remaining stubs (beyond those already in frontier)
            int missingToClose = Math.max(0, openStubs - frontier.size());
            // Allow at most the smaller of the decayed budget or the number needed
            int expansionAllowance = Math.min(linearBudget, missingToClose);

            // 2. Check termination conditions
            boolean allEdgesSatisfied   = (openStubs == 0);
            boolean noFrontierCells     = frontier.isEmpty();
            boolean atSizeLimit         = (settled.size() >= hardUpperBound);
            if ((allEdgesSatisfied && noFrontierCells) || atSizeLimit) {
                // Completed all connections (and nothing left to collapse), or reached hard size cap
                break;
            }

            // 3. If there are open stubs but no frontier cells to collapse, expand new cells to fill those stubs
            if (noFrontierCells && openStubs > 0 && expansionAllowance > 0) {
                // For each missing stub, create a new uncollapsed cell and attach it to a collapsed cell with an open slot
                int newCellsToAdd = expansionAllowance;
                for (Cell cell : settled) {
                    // Calculate how many extra neighbors this cell still needs
                    int needed = targetDegree.getOrDefault(cell, 0) - adjacency.getOrDefault(cell, Collections.emptyList()).size();
                    for (int i = 0; i < needed && newCellsToAdd > 0; i++) {
                        // Initialize a new frontier cell with all possible patterns
                        Cell newCell = new Cell(allPatternIds);
                        frontier.add(newCell);
                        // Update adjacency: link the new cell with the current settled cell
                        adjacency.computeIfAbsent(cell, k -> new ArrayList<>()).add(newCell);
                        adjacency.computeIfAbsent(newCell, k -> new ArrayList<>()).add(cell);
                        newCellsToAdd--;
                        if (newCellsToAdd == 0) break;
                    }
                    if (newCellsToAdd == 0) break;
                }
                // Continue to next iteration with newly added frontier cells (progress unchanged, frontier no longer empty)
                continue;
            }

            // 4. Phase A – Greedily connect available stubs among collapsed cells
            int edgesAdded = connector.connect(settled, targetDegree);
            if (edgesAdded > 0) {
                // If any edges were added, propagate constraints globally in case these new connections force collapses
                propagate(settled, propagator, frontier, settled, adjacency,
                        patternCenterLabel, patternDegree, allPatternIds, expansionAllowance);
                // After propagation, re-evaluate openStubs/frontier in the next loop iteration
                continue;
            }

            // 5. Phase B – Collapse one low-entropy frontier cell (if any remain)
            if (!frontier.isEmpty()) {
                entropy.computeEntropies();
                int collapseIndex = entropy.collapseNextCell();
                if (collapseIndex >= 0) {
                    // Collapse the chosen frontier cell to a concrete pattern
                    Cell collapsedCell = frontier.remove(collapseIndex);
                    settled.add(collapsedCell);
                    int patternId = collapsedCell.getCollapsedPattern();
                    // Record the target degree of this collapsed cell based on its pattern
                    targetDegree.put(collapsedCell, patternDegree.get(patternId));

                    // Immediately attempt to connect this new cell's stubs to any other compatible settled cells
                    connector.connect(settled, targetDegree);
                    // If the new cell still has open slots and we have budget, expand new neighbors for it
                    if (expansionAllowance > 0) {
                        Map<Cell, Integer> singleCenterMap = Collections.singletonMap(collapsedCell, targetDegree.get(collapsedCell));
                        Expand.expand(Collections.singletonList(collapsedCell), expansionAllowance,
                                singleCenterMap, allPatternIds, frontier, adjacency);
                    }
                    // Propagate constraints from this collapse (and any new cells it introduced)
                    propagate(Collections.singletonList(collapsedCell), propagator, frontier, settled, adjacency,
                            patternCenterLabel, patternDegree, allPatternIds, expansionAllowance);
                    // Continue to re-evaluate after collapsing and expanding
                    continue;
                }
            }

            // 6. If no frontier collapse was possible and no edges were added, no further progress can be made under current conditions
            break;
        }

        // 7. Final phase – collapse all remaining frontier cells without adding new cells
        while (!frontier.isEmpty()) {
            entropy.computeEntropies();
            int collapseIndex = entropy.collapseNextCell();
            if (collapseIndex < 0) {
                // No collapsible cell found (should not normally happen unless contradiction); break to avoid infinite loop
                break;
            }
            // Collapse the frontier cell and finalize it
            Cell collapsedCell = frontier.remove(collapseIndex);
            settled.add(collapsedCell);
            int patternId = collapsedCell.getCollapsedPattern();
            targetDegree.put(collapsedCell, patternDegree.get(patternId));
            // Connect any possible stub pairings now that this cell is collapsed
            connector.connect(settled, targetDegree);
            // Propagate constraints from this newly collapsed cell (no new expansions at this stage)
            propagate(Collections.singletonList(collapsedCell), propagator, frontier, settled, adjacency,
                    patternCenterLabel, patternDegree, allPatternIds, 0);
        }

        // 8. (Optional) Final attempt to connect any remaining stubs among fully settled cells
        int remainingOpenStubs = countOpenStubs(settled, targetDegree, adjacency);
        if (remainingOpenStubs > 0) {
            connector.connect(settled, targetDegree);
            // Note: We do not propagate here since no uncollapsed cells remain.
            // Any remaining stubs at this point are due to compatibility or cap limitations and will remain as is.

        }
        // (Optional) print remaining stub connections if needed
        int missingEdges = countOpenStubs(settled, targetDegree, adjacency);
        System.out.printf("cleanup | done %d stub connections remain unsatisfied%n", missingEdges);
    }


    /**
     * Computes a linear decay factor between 1.0 and 0.0 as progress increases.
     *
     * When progress ≤ startProgress, returns 1.0 (full budget).
     * When progress ≥ endProgress, returns 0.0 (no budget).
     * Between these points, returns a linearly interpolated value.
     *
     * @param progress      current progress ratio (e.g. settledSize / targetSize)
     * @param startProgress progress at which decay begins (inclusive)
     * @param endProgress   progress at which decay ends (inclusive)
     * @return              a factor in [0.0, 1.0] for scaling expansion budgets
     */
    private static double computeLinearDecay(double progress,
                                             double startProgress,
                                             double endProgress) {
        if (progress <= startProgress) {
            return 1.0;
        }
        if (progress >= endProgress) {
            return 0.0;
        }
        // Linearly interpolate between startProgress→1.0 and endProgress→0.0
        return 1.0 - (progress - startProgress) / (endProgress - startProgress);
    }

    /**
     * Sums the number of open edge slots (“stubs”) across all collapsed cells.
     *
     * For each cell, compares its desired degree (from targetDegree) to its current
     * adjacency list size. If the cell has fewer edges than targetDegree, the deficit
     * is added to the total count of open stubs.
     *
     * @param collapsedCells list of cells already collapsed
     * @param targetDegree   map from cell → desired number of edges
     * @param cellAdjacency  current adjacency lists for each cell
     * @return               total number of missing edges across all collapsed cells
     */
    private static int countOpenStubs(List<Cell> collapsedCells,
                                      Map<Cell, Integer> targetDegree,
                                      Map<Cell, List<Cell>> cellAdjacency) {
        int open = 0;
        for (Cell cell : collapsedCells) {
            int desired = targetDegree.getOrDefault(cell, 0);
            int actual  = cellAdjacency.getOrDefault(cell, Collections.emptyList()).size();
            if (actual < desired) {
                open += (desired - actual);
            }
        }
        return open;
    }

    /**
     * Loads the last completed iteration index from the resume file.
     *
     * If the file exists and contains a valid integer on its first line, returns that value.
     * Otherwise (file missing, empty, or parse error), returns 1 as the default start iteration.
     *
     * @return the iteration number to resume from (≥1)
     */
    private static int loadLastIter() {
        try {
            if (Files.exists(RESUME_FILE)) {
                List<String> lines = Files.readAllLines(RESUME_FILE, StandardCharsets.UTF_8);
                if (!lines.isEmpty()) {
                    return Integer.parseInt(lines.get(0).trim());
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // Fall through to default
        }
        return 1;
    }

    /**
     * Saves the next iteration index to the resume file for continuation.
     *
     * Creates parent directories if necessary, and writes the integer as the
     * only line in the file. Ignores any I/O exceptions.
     *
     * @param nextIter the iteration number to write for future resumption
     */
    private static void saveLastIter(int nextIter) {
        try {
            Files.createDirectories(RESUME_FILE.getParent());
            Files.write(RESUME_FILE,
                    Collections.singletonList(String.valueOf(nextIter)),
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // If saving fails, we simply lose the resume state
        }
    }

}

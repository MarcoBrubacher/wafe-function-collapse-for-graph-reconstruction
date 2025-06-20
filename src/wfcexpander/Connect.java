package wfcexpander;

import display.GraphAugmentationVisualizer;
import helper.Graph;
import patterns.OverlapManager;
import patterns.Pattern;
import wfc.Entropy;
import wfc.WaveCell;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Connect: tries to add up to M = (targetDegree - currentDegree) neighbors
 * for a collapsed frontier cell, picking the best synergy.
 *
 * Now refactored to:
 *  - remove from entropy queue if we connect a cell that was not in queue
 *  - or add to queue if connecting new waveCells
 *  (Though typically we do not create new waveCells here.  We only connect
 *   existing ones.)
 */
public class Connect {

    public static void connect(
            WaveCell frontier,             // The collapsed frontier cell
            List<WaveCell> allCandidates,
            Graph outputGraph,
            GraphAugmentationVisualizer graphViz,
            OverlapManager overlapManager,
            Map<Pattern, Integer> frequencies,
            Entropy entropy                   // <-- added
    ) {
        // 1) Must be collapsed
        if (!frontier.isCollapsed()) {
            return;
        }
        Pattern frontierPat = frontier.getCollapsedPattern();
        if (frontierPat == null) {
            return;
        }

        // 2) M = max(0, targetDegree - currentDegree)
        int targetDegree = frontier.getTargetDegree();
        int currentDegree = frontier.getNeighbors().size();
        int M = Math.max(0, targetDegree - currentDegree);
        if (M == 0) return;

        // 3) Build synergy data
        List<CandidateData> ranked = buildCandidateData(frontier, allCandidates, overlapManager);

        // sort desc by synergy
        ranked.sort(Comparator.comparingDouble(cd -> -cd.synergy));

        // 4) Connect until M reached or out of candidates
        int connectionsMade = 0;
        for (CandidateData cd : ranked) {
            if (connectionsMade >= M) {
                break;
            }
            WaveCell candidate = cd.candidate;

            // Check capacity
            if (!candidateHasCapacity(candidate)) {
                continue;
            }

            // Connect
            outputGraph.addEdge(frontier.getNode().getId(), candidate.getNode().getId());
            frontier.addNeighbor(candidate);
            candidate.addNeighbor(frontier);
            connectionsMade++;

            System.out.printf(
                    "Connecting %s to %s (similarity=%.4f)%n",
                    frontier.getNode().getId(), candidate.getNode().getId(), cd.synergy
            );

            // If candidate is uncollapsed, ensure it is in the queue
            if (!candidate.isCollapsed()) {
                if (!entropy.isInQueue(candidate)) {
                    entropy.addToQueue(candidate);
                } else {
                    entropy.rebuildQueue(Collections.singleton(candidate));
                }
            }
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------
    private static boolean candidateHasCapacity(WaveCell c) {
        int t = c.getTargetDegree();
        if (t < 0) return true;  // no strict requirement
        return c.getNeighbors().size() < t;
    }

    private static List<CandidateData> buildCandidateData(
            WaveCell frontier,
            List<WaveCell> allCandidates,
            OverlapManager overlapManager
    ) {
        Pattern frontierPat = frontier.getCollapsedPattern();
        List<CandidateData> result = new ArrayList<>();

        for (WaveCell cand : allCandidates) {
            // skip if same or already connected
            if (cand == frontier || frontier.getNeighbors().contains(cand)) {
                continue;
            }
            // skip capacity
            if (!candidateHasCapacity(cand)) {
                continue;
            }
            // synergy
            double synergy = computeSynergy(frontierPat, cand, overlapManager);
            if (synergy > 0) {
                result.add(new CandidateData(cand, synergy));
            }
        }
        return result;
    }

    private static double computeSynergy(Pattern frontPat, WaveCell candidate, OverlapManager om) {
        if (candidate.isCollapsed()) {
            Pattern candPat = candidate.getCollapsedPattern();
            if (candPat == null) return 0.0;
            return om.getPairwiseScore(frontPat, candPat);
        } else {
            // uncollapsed but domain might contain frontPat
            // for simple logic, require domain == { frontPat }
            if (candidate.getDomain().size() == 1 && candidate.getDomain().contains(frontPat)) {
                return om.getPairwiseScore(frontPat, frontPat);
            }
            return 0.0;
        }
    }

    private static class CandidateData {
        final WaveCell candidate;
        final double synergy;
        CandidateData(WaveCell c, double s) {
            candidate = c;
            synergy = s;
        }
    }
}

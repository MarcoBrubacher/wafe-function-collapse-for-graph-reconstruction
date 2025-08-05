package constructor;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class ExpansionPlanner {
    private final List<Integer> prerunFrontiers;
    private final int realSteps;
    private final double jitterRatio;

    /**
     * @param prerunFrontiers  the N‐sized frontier curve you learned
     * @param realSteps        how many total expansions you’ll do (2N)
     * @param jitterRatio      how strongly to scale jitter = ratio*|error|
     */
    public ExpansionPlanner(List<Integer> prerunFrontiers,
                            int realSteps,
                            double jitterRatio) {
        this.prerunFrontiers = prerunFrontiers;
        this.realSteps       = realSteps;
        this.jitterRatio     = jitterRatio;
    }

    /** Which index into the prerun series corresponds to this step **/
    public int phaseIndex(int step) {
        int last = prerunFrontiers.size() - 1;
        return Math.min(last,
                (int)Math.round((double)step * last / (realSteps - 1)));
    }

    /** Given the current frontier size, what’s our target? **/
    public int dynamicTarget(int initialFrontier, int step) {
        int idx = phaseIndex(step);
        return prerunFrontiers.get(idx);
    }

    /** Compute each seed’s “capacity” = ceil(deg/2) **/
    public int[] capacities(int[] degrees) {
        return IntStream.of(degrees)
                .map(d -> (d + 1)/ 2)
                .toArray();
    }

    /** Sum of all capacities **/
    public int totalCapacity(int[] caps) {
        return IntStream.of(caps).sum();
    }

    /**
     * Proportional‐share burst before jitter:
     *    burst = round(delta * cap_i / sumCap)
     */
    public int baseBurst(int cap, int sumCap, int delta) {
        if (sumCap == 0) return 0;
        return (int)Math.round((double)delta * cap / sumCap);
    }

    /** error‐proportional one‐sided jitter window **/
    public int jitterWindow(int delta) {
        return Math.max(1,
                (int)Math.ceil(Math.abs(delta) * jitterRatio));
    }

    /**
     * Apply a small [0..window] one‐sided “bump”
     * (only when baseBurst<cap to avoid overshoot).
     */
    public int applyJitter(int baseBurst, int cap, int window, Random rng) {
        if (baseBurst >= cap) return cap;
        int bump = rng.nextInt(window + 1);
        return Math.min(cap, baseBurst + bump);
    }
}

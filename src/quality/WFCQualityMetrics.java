package quality;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.scoring.ClusteringCoefficient;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.io.*;
import java.util.*;

/**
 * Evaluates structural similarity of an augmented (generated) graph to its training graph.
 *
 * Metrics included:
 *   - Degree MMD
 *   - Clustering Coefficient MMD
 *   - Laplacian Spectral EMD
 *   - Node-label distribution JS divergence
 *   - Edge-label distribution JS divergence
 *   - Label assortativity difference
 *   - Connected-components difference
 */
public class WFCQualityMetrics {

    /**
     * Reads a graph from two files: one containing (nodeID, nodeLabel) pairs,
     * and one containing edges (nodeID1 nodeID2).  Each nodeID is preserved
     * as its own vertex; only true self-loops (ID==ID) are skipped.
     */
    public static Graph<String, DefaultEdge> readGraph(String labelFile, String edgeFile) {
        Graph<String, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        Map<String,String> idToLabel = new HashMap<>();

        // 1) Load nodeID→label map
        try (BufferedReader br = new BufferedReader(new FileReader(labelFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tok = line.split("\\s+");
                String nodeID = tok[0];
                String lbl    = (tok.length > 1 ? tok[1] : nodeID);
                idToLabel.put(nodeID, lbl);
                // Ensure the node exists
                if (!g.containsVertex(nodeID)) {
                    g.addVertex(nodeID);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2) Load edges by IDs, skip only true self‐loops
        try (BufferedReader br = new BufferedReader(new FileReader(edgeFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tok = line.split("\\s+");
                if (tok.length < 2) continue;
                String u = tok[0], v = tok[1];
                if (u.equals(v)) continue;               // skip self-loop
                if (!g.containsVertex(u)) g.addVertex(u);
                if (!g.containsVertex(v)) g.addVertex(v);
                if (!g.containsEdge(u,v)) {
                    g.addEdge(u,v);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return g;
    }

    /**
     * Main entry: compare structural & label/edge‐label metrics
     * between the training and generated graphs.
     */
    public static void evaluateGraphQuality(
            String trainLabelFile, String trainEdgeFile,
            String genLabelFile,   String genEdgeFile
    ) {
        // 1) Build raw graphs (IDs as vertices)
        Graph<String, DefaultEdge> train = readGraph(trainLabelFile, trainEdgeFile);
        Graph<String, DefaultEdge> gen = readGraph(genLabelFile, genEdgeFile);

        // 2) Degree MMD
        List<Double> degT = new ArrayList<>(), degG = new ArrayList<>();
        for (String v : train.vertexSet()) degT.add((double) train.degreeOf(v));
        for (String v : gen.vertexSet()) degG.add((double) gen.degreeOf(v));
        double mmdDegree = mmd(degT, degG);

        // 3) Clustering‐MMD
        ClusteringCoefficient<String, DefaultEdge> ccT = new ClusteringCoefficient<>(train);
        ClusteringCoefficient<String, DefaultEdge> ccG = new ClusteringCoefficient<>(gen);
        List<Double> clT = new ArrayList<>(ccT.getScores().values());
        List<Double> clG = new ArrayList<>(ccG.getScores().values());
        double mmdClust = mmd(clT, clG);

        // 4) Laplacian Spectrum EMD
        double emdSpec = emdSpectrum(train, gen);

        // 5) Read full node‐label lists (with duplicates) for true JS divergence
        List<String> labT = readLabelList(trainLabelFile);
        List<String> labG = readLabelList(genLabelFile);
        double jsLabel = jsd(labT, labG);
        printLabelFrequencyDifferences(labT, labG);

        // 6) Edge‐label JS divergence from actual endpoint labels
        List<String> edgeLabT = readEdgeLabelList(train, trainLabelFile);
        List<String> edgeLabG = readEdgeLabelList(gen, genLabelFile);
        double jsEdge = jsd(edgeLabT, edgeLabG);
        printEdgeLabelFrequencyDifferences(edgeLabT, edgeLabG);

        // 7) Label assortativity difference (using actual labels)
        Map<String, String> trainMap = readLabelMap(trainLabelFile);
        Map<String, String> genMap = readLabelMap(genLabelFile);
        double assortT = computeLabelAssortativity(train, trainMap);
        double assortG = computeLabelAssortativity(gen, genMap);
        double assortDiff = Math.abs(assortT - assortG);

        // 8) Connected‐components diff
        int compT = new ConnectivityInspector<>(train).connectedSets().size();
        int compG = new ConnectivityInspector<>(gen).connectedSets().size();
        int compDiff = Math.abs(compT - compG);


        // --- Composite Fidelity Score Calculation ---
        double fDegree = Math.exp(-mmdDegree);           // small MMD ⇒ fidelity≈1
        double fClust = Math.exp(-mmdClust);            // small MMD ⇒ fidelity≈1
        double fSpec = Math.exp(-emdSpec);             // small EMD ⇒ fidelity≈1
        double fNode = 1.0 - jsLabel;                  // small JS ⇒ fidelity≈1
        double fEdge = 1.0 - jsEdge;                   // small JS ⇒ fidelity≈1
        double fAssort = 1.0 - assortDiff;             // small diff ⇒ fidelity≈1

// Average them for a single Composite Fidelity Score
        double composite = (fDegree + fClust + fSpec + fNode + fEdge + fAssort) / 6.0;

// --------------- Print results ---------------
        System.out.printf("Degree Distribution MMD: %.6f%n", mmdDegree);
        System.out.printf("Clustering Coefficient MMD: %.6f%n", mmdClust);
        System.out.printf("Laplacian Spectral EMD: %.6f%n", emdSpec);
        System.out.printf("Node Label JS Divergence: %.6f%n", jsLabel);
        System.out.printf("Edge Label JS Divergence: %.6f%n", jsEdge);
        System.out.printf("Label Assortativity diff: %.6f%n", assortDiff);
        System.out.printf("Connected Components diff: %d%n", compDiff);
        System.out.printf("Composite Fidelity Score: %.3f%n", composite);
        // Append metrics to metrics_log.txt in a single line
        try (FileWriter fw = new FileWriter("src/quality/metrics_log.txt", true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.printf("%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%.3f%n",
                    mmdDegree, mmdClust, emdSpec, jsLabel, jsEdge, assortDiff, compDiff, composite);
        } catch (IOException e) {
            System.err.println("Error writing metrics to file: " + e.getMessage());
        }


    }

    //————— Helpers —————————————————————————————————————————————————

    /** Read a map nodeID→label from file */
    private static Map<String,String> readLabelMap(String labelFile) {
        Map<String,String> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(labelFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tok = line.split("\\s+");
                String id = tok[0], lbl = (tok.length>1? tok[1] : id);
                map.put(id, lbl);
            }
        } catch(IOException e){ e.printStackTrace(); }
        return map;
    }

    /** Read full list of node labels (with duplicates) */
    private static List<String> readLabelList(String labelFile) {
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(labelFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tok = line.split("\\s+");
                String lbl = (tok.length>1? tok[1] : tok[0]);
                out.add(lbl);
            }
        } catch(IOException e){ e.printStackTrace(); }
        return out;
    }

    /** Build list of edge‐label strings “A--B” for every edge using the given labelMap */
    private static List<String> readEdgeLabelList(Graph<String,DefaultEdge> g, String labelFile) {
        Map<String,String> map = readLabelMap(labelFile);
        List<String> out = new ArrayList<>();
        for (DefaultEdge e : g.edgeSet()) {
            String u = g.getEdgeSource(e), v = g.getEdgeTarget(e);
            String lu = map.get(u), lv = map.get(v);
            String type = (lu.compareTo(lv) < 0 ? lu + "--" + lv : lv + "--" + lu);
            out.add(type);
        }
        return out;
    }

    /** Print node label frequency comparison */
    private static void printLabelFrequencyDifferences(List<String> T, List<String> G) {
        Map<String,Integer> tcnt = countLabels(T), gcnt = countLabels(G);
        int n = T.size(), m = G.size();
        System.out.println("\nNode Label Frequency:");
        for (String lab : new TreeSet<>(tcnt.keySet())) {
            double pT = 100.0*tcnt.get(lab)/n, pG = 100.0*gcnt.getOrDefault(lab,0)/m;
            System.out.printf("  %s: Train=%.2f%%, Gen=%.2f%%%n", lab, pT, pG);
        }
    }

    /** Print edge label frequency comparison */
    private static void printEdgeLabelFrequencyDifferences(List<String> T, List<String> G) {
        Map<String,Integer> tcnt = countLabels(T), gcnt = countLabels(G);
        int n=T.size(), m=G.size();
        System.out.println("\nEdge Label Frequency:");
        for (String type : new TreeSet<>(tcnt.keySet())) {
            double pT = 100.0*tcnt.get(type)/n, pG = 100.0*gcnt.getOrDefault(type,0)/m;
            System.out.printf("  %s: Train=%.2f%%, Gen=%.2f%%%n", type, pT, pG);
        }
    }

    private static Map<String,Integer> countLabels(List<String> L) {
        Map<String,Integer> map = new HashMap<>();
        for (String s : L) map.merge(s,1,Integer::sum);
        return map;
    }

    /** Gaussian RBF MMD */
    private static double mmd(List<Double> x, List<Double> y) {
        if (x.isEmpty()||y.isEmpty()) return 1.0;
        double σ=1, kxx=0, kyy=0, kxy=0;
        for(double a:x)for(double b:x)kxx+=Math.exp(-Math.pow(a-b,2)/(2*σ*σ));
        for(double a:y)for(double b:y)kyy+=Math.exp(-Math.pow(a-b,2)/(2*σ*σ));
        for(double a:x)for(double b:y)kxy+=Math.exp(-Math.pow(a-b,2)/(2*σ*σ));
        double mmd2 = kxx/(x.size()*x.size())
                + kyy/(y.size()*y.size())
                - 2*kxy/(x.size()*y.size());
        return Math.sqrt(Math.max(mmd2,0));
    }

    /** Laplacian spectrum EMD */
    private static double emdSpectrum(Graph<String,DefaultEdge> a, Graph<String,DefaultEdge> b) {
        double[] e1=lapEig(a), e2=lapEig(b);
        Arrays.sort(e1); Arrays.sort(e2);
        List<Double> A=new ArrayList<>(), B=new ArrayList<>();
        for(double v:e1) A.add(v);
        for(double v:e2) B.add(v);
        return emd1D(A,B);
    }
    private static double emd1D(List<Double>A,List<Double>B){
        Collections.sort(A);Collections.sort(B);
        int i=0,j=0, n=A.size(), m=B.size();
        double ca=0,cb=0,emd=0;
        while(i<n&&j<m){
            double d=Math.min(1.0/n - ca, 1.0/m - cb);
            emd += d*Math.abs(A.get(i)-B.get(j));
            ca+=d; cb+=d;
            if(Math.abs(ca - 1.0/n)<1e-9){ i++; ca=0; }
            if(Math.abs(cb - 1.0/m)<1e-9){ j++; cb=0; }
        }
        return emd;
    }

    /** Compute Laplacian eigenvalues */
    private static double[] lapEig(Graph<String,DefaultEdge> g) {
        int n=g.vertexSet().size();
        if(n==0) return new double[]{0};
        Map<String,Integer> idx=new HashMap<>(); int c=0;
        for(String v:g.vertexSet()) idx.put(v,c++);
        double[][] L=new double[n][n];
        for(String v:g.vertexSet()){
            int i=idx.get(v), deg=g.degreeOf(v);
            L[i][i]=deg;
            for(DefaultEdge e:g.edgesOf(v)){
                String u = org.jgrapht.Graphs.getOppositeVertex(g,e,v);
                int j=idx.get(u);
                if(i!=j) L[i][j] -=1;
            }
        }
        RealMatrix M = new Array2DRowRealMatrix(L);
        return new EigenDecomposition(M).getRealEigenvalues();
    }

    /** JS divergence (base-2) between two label lists */
    private static double jsd(List<String> P, List<String> Q) {
        Map<String,Integer> p = countLabels(P), q = countLabels(Q);
        Set<String> keys = new HashSet<>(p.keySet()); keys.addAll(q.keySet());
        int n=P.size(), m=Q.size();
        double js=0;
        for(String k:keys){
            double pp=p.getOrDefault(k,0)/(double)n;
            double qq=q.getOrDefault(k,0)/(double)m;
            double mu=0.5*(pp+qq);
            if(pp>0) js += 0.5*pp*Math.log(pp/mu);
            if(qq>0) js += 0.5*qq*Math.log(qq/mu);
        }
        return js/Math.log(2);
    }

    /**
     * Compute label assortativity (homophily) using node labels as categories.
     */
    private static double computeLabelAssortativity(
            Graph<String,DefaultEdge> g, Map<String,String> labelMap
    ) {
        // Map each label to index
        Map<String,Integer> idx = new HashMap<>();
        int k=0;
        for(String v: g.vertexSet()){
            String lab = labelMap.getOrDefault(v,v);
            if(!idx.containsKey(lab)) idx.put(lab, k++);
        }
        // Build edge counts matrix
        double[][] e = new double[k][k];
        for(DefaultEdge ed: g.edgeSet()){
            String u=g.getEdgeSource(ed), v=g.getEdgeTarget(ed);
            int i=idx.get(labelMap.get(u)), j=idx.get(labelMap.get(v));
            e[i][j] +=1;
            if(i!=j) e[j][i] +=1;
        }
        double total=0;
        for(double[] row:e) for(double x:row) total+=x;
        if(total==0) return 0;
        double[] a = new double[k];
        double trace=0;
        for(int i=0;i<k;i++){
            double rowSum=0;
            for(int j=0;j<k;j++) rowSum+=e[i][j];
            a[i]=rowSum/total;
            trace+= e[i][i]/total;
        }
        double a2=0; for(double x:a) a2+=x*x;
        if(Math.abs(1-a2)<1e-9) return 0;
        return (trace - a2)/(1 - a2);
    }
}

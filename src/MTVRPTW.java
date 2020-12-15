import javax.xml.crypto.Data;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.*;
import java.util.logging.Logger;

public class MTVRPTW {

    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());
    static final boolean SHOW_ROUTE_STATS = false;

    /**
     * The algorithm is inspired by the greedy nearest neighbor algorithm for VRP.
     * In this case, a new customer is inserted into the route based on
     * the ready time of the vehicle after serving the customer.
     *
     * If no feasible customer exists, the vehicle will go back to the depot,
     * then try to find a new customer for the new trip.
     *
     * If there is still no feasible customer, initialize a new vehicle (a new route).
     *
     * @param dataModel
     */
    public static int runGreedyEarliestNeighbor(DataModel dataModel) {
//        logger.info("Start greedy earliest neighbor algorithm");
//        long startTime = System.nanoTime();
        GreedyAlgorithm greedyAlgorithm = new GreedyAlgorithm(dataModel);
        List<Route> solution = greedyAlgorithm.run();
        assert Utils.isValidSolution(dataModel, solution);
//        logger.info(Utils.getSolutionStats(solution, SHOW_ROUTE_STATS));
//        logger.info("Computational time: " + (System.nanoTime() - startTime) / 1_000_000_000.0 + "\n");
        return solution.size();
    }

    public static int runSolomonI1Algorithm(DataModel dataModel) {
        SolomonI1Algorithm solomonI1Algorithm = new SolomonI1Algorithm(dataModel);
        List<Route> solution = solomonI1Algorithm.run();
        assert Utils.isValidSolution(dataModel, solution);
        return solution.size();
    }

    /**
     * Algorithm: a multi-start strategy where we consider different threshold for the maximum # clusters.
     * For each number of cluster, we find the best solution by running a cluster-route-merge algorithm
     * then select the best result (solution) in all solutions.
     * The algorithm for each set of clusters is as follow:
     *      1. Cluster demand nodes
     *      2. Parallel construct a solution for each cluster
     *      3. Merge these solutions
     *
     */
    public static int runClusterRouteMergeAlgorithm(DataModel dataModel) {
        List<List<Route>> solutions = new ArrayList<>();
        // Try different # of clusters
        for (int numClusters = 1; numClusters <= dataModel.numClustersThreshold; numClusters++) {
            // Cluster - Route - Merge algorithm
            ClusterRouting clusterRouting = new ClusterRouting(dataModel);  // also pass dataModel
            // Do 3 steps: cluster, parallel construction, merge
            List<Route> routes = clusterRouting.run(numClusters);
            solutions.add(routes);
        }

        List<Route> finalSolution = Utils.getBestSolution(solutions);
        assert Utils.isValidSolution(dataModel, finalSolution);
        return finalSolution.size();
    }

    /**
     * This is an extended version of the cluster-route-merge algorithm proposed by Chang, 2020.
     * The main difference is that the cluster-route-merge is performed in a DFS manner,
     * and there is a solution improvement phase (tabu search) after the initial solution construction.
     *
     * Algorithm: a multi-start strategy where we consider different threshold for the maximum # clusters.
     * For each number of cluster, we find the best solution by running initial construction and improvement phase,
     * then select the best result (solution) in all solutions.
     * The algorithm for each # cluster is as follow:
     *      1. Initial construction:
     *          1.1. Cluster demand nodes by time window
     *          1.2. Parallel construct a solution for each cluster with Solomon's sequential insertion heuristic
     *          1.3. Merge these solutions iteratively
     *      2. Improvement phase
     *
     */
    public static int runChangsAlgorithm(DataModel dataModel) {
        List<List<Route>> solutions = new ArrayList<>();
        // Step 2-9 (currently 2-7): Initial solution construction
        for (int numClusters = 1; numClusters <= dataModel.numClustersThreshold; numClusters++) {
//            logger.info("Try " + numClusters + " clusters");
            ChangsAlgorithm changsAlgorithm = new ChangsAlgorithm(dataModel);  // also pass dataModel
            // Do 3 steps: cluster, parallel construction, merge
            List<Route> solution = changsAlgorithm.run(numClusters);
            if (solution != null) solutions.add(solution);
        }
        List<Route> finalSolution = Utils.getBestSolution(solutions);
        assert Utils.isValidSolution(dataModel, finalSolution);
        return finalSolution.size();
    }

    public static void main(String[] args) {
        int numAlgorithms = 4;
        File inputDirectory = new File(System.getProperty("user.dir") + "/inputs/");

        // Read configurations
        // TODO: experiment with multiple configurations
        Configurations configs = new Configurations(0, inputDirectory.getPath() + "/parameters.txt");

        String[] testSets = inputDirectory.list((dir, name) -> new File(dir, name).isDirectory());
        assert testSets != null;
        for (String testSet : testSets) {
            String testDirectory = inputDirectory + "/" + testSet;
            String[] inputFiles = Objects.requireNonNull(new File(testDirectory).list((dir, name) -> new File(dir, name).isFile()));
            int[][] results = new int[numAlgorithms][inputFiles.length];
            for (int i = 0; i < inputFiles.length; i++) {
                DataModel dataModel = new DataModel(testDirectory + "/" + inputFiles[i], configs);
                results[0][i] = MTVRPTW.runGreedyEarliestNeighbor(dataModel);
                results[1][i] = MTVRPTW.runSolomonI1Algorithm(dataModel);
                results[2][i] = MTVRPTW.runClusterRouteMergeAlgorithm(dataModel);
                results[3][i] = MTVRPTW.runChangsAlgorithm(dataModel);
            }
            double[] averageResults = new double[numAlgorithms];
            for (int i = 0; i < numAlgorithms; i++) {
                averageResults[i] = Arrays.stream(results[i]).average().orElse(Double.NaN);
            }
            logger.info(testSet + " - average result: " + Arrays.toString(averageResults));
        }
    }
}

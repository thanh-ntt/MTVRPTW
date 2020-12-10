import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Logger;

public class MTVRPTW {

    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

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
    public void runClusterRouteMergeAlgorithm(DataModel dataModel) {
        logger.info("Start cluster-route-merge algorithm");
        long startTime = System.nanoTime();
        List<List<Route>> solutions = new ArrayList<>();

        // Try different # of clusters
        for (int numClusters = 1; numClusters <= dataModel.numClustersThreshold; numClusters++) {
            // Cluster - Route - Merge algorithm
//            logger.info("Try " + numClusters + " clusters");
            ClusterRouting clusterRouting = new ClusterRouting(dataModel);  // also pass dataModel
            // Do 3 steps: cluster, parallel construction, merge
            List<Route> routes = clusterRouting.run(numClusters);
            solutions.add(routes);
        }

        List<Route> finalSolution = Utils.getBestSolution(solutions);
        assert Utils.isValidSolution(dataModel, finalSolution);
        logger.info(Utils.getSolutionStats(finalSolution));
        logger.info("Computational time: " + (System.nanoTime() - startTime) / 1_000_000_000.0);
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
    public void runChangsAlgorithm(DataModel dataModel) {
        logger.info("Start Chang's algorithm");
        long startTime = System.nanoTime();
        // Step 1: try different # of clusters
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
//        logger.info("Final solution, # vehicles: " + (finalSolution == null ? "-1" : finalSolution.size()));
        assert Utils.isValidSolution(dataModel, finalSolution);
        logger.info(Utils.getSolutionStats(finalSolution));
        logger.info("Computational time: " + (System.nanoTime() - startTime) / 1_000_000_000.0);
    }

    /**
     * Read algorithm parameters:
     *      threshold of # clusters
     *      vehicle capacity
     */
    public static void readInputParameters(DataModel dataModel, String inputDirectory) {
        try {
            File inputParametersFile = new File( inputDirectory + "/parameters.txt");
            Scanner inputParameter = new Scanner(inputParametersFile);
            dataModel.setInputTestFolder(inputDirectory + "/" + inputParameter.nextLine());
            dataModel.setNumClustersThreshold(inputParameter.nextInt());
            dataModel.setVehicleCapacity(inputParameter.nextInt());
            dataModel.setAlphaParameters(inputParameter.nextDouble(), inputParameter.nextDouble());
            dataModel.setPNeighbourhoodSize(inputParameter.nextInt());
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        MTVRPTW mtvrptw = new MTVRPTW();
        String inputDirectory = System.getProperty("user.dir") + "/input/";
        logger.info("Reading input from " + inputDirectory);
        DataModel dataModel = new DataModel();  // read from input (add parameters later)
        readInputParameters(dataModel, inputDirectory);
        dataModel.readInputAndPopulateData();
        mtvrptw.runClusterRouteMergeAlgorithm(dataModel);
        mtvrptw.runChangsAlgorithm(dataModel);
    }
}

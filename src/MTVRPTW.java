import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class MTVRPTW {

    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());
    static final boolean SHOW_SOLUTION_STATS = false;
    static final boolean SHOW_ROUTE_STATS = false;

    public static void main(String[] args) {
        StringBuilder logMsg = new StringBuilder();
        ConstructionAlgorithm[] constructionAlgorithms
                = {new GreedyAlgorithm(), new SolomonI1Algorithm(), new ClusterRouting(), new ChangsAlgorithm()};
//        solutionConstructionAlgorithms = Arrays.copyOfRange(solutionConstructionAlgorithms, 1, 2);
        int numAlgorithms = constructionAlgorithms.length;
        File inputDirectory = new File(System.getProperty("user.dir") + "/inputs/");

        // Read configurations
        // TODO: experiment with multiple configurations
        Configurations configs = new Configurations(0, inputDirectory.getPath() + "/parameters.txt");

        String[] testSets = inputDirectory.list((dir, name) -> new File(dir, name).isDirectory());
        Arrays.sort(testSets);
        int[] cumulativeLength = new int[numAlgorithms + 2];
        for (String testSet : testSets) {
            String testDirectory = inputDirectory + "/" + testSet;
            String[] inputFiles = Objects.requireNonNull(new File(testDirectory).list((dir, name) -> new File(dir, name).isFile()));
            Arrays.sort(inputFiles);
            List<Route>[][] solutions = new ArrayList[numAlgorithms + 2][inputFiles.length];
            int[][] results = new int[solutions.length][inputFiles.length];
            for (int i = 0; i < inputFiles.length; i++) {
                DataModel dataModel = new DataModel(testDirectory + "/" + inputFiles[i], configs);
                for (int j = 0; j < results.length - 2; j++) {
                    solutions[j][i] = constructionAlgorithms[j].run(dataModel);
                    results[j][i] = solutions[j][i].size();
                }
                // Local search
                solutions[results.length - 2][i] = RelocateAlgorithm.run(solutions[1][i], dataModel);
                results[results.length - 2][i] = solutions[results.length - 2][i].size();
                // ILS
                solutions[results.length - 1][i] = new ILS().run(dataModel);
                results[results.length - 1][i] = solutions[results.length - 1][i].size();
            }
            int[] sumSolutionSize = new int[results.length];
            for (int i = 0; i < results.length; i++) {
                sumSolutionSize[i] = Arrays.stream(results[i]).sum();
                cumulativeLength[i] += Arrays.stream(results[i]).sum();
            }
            logMsg.append(testSet + ":\n");
            if (SHOW_SOLUTION_STATS) {
                for (int i = 0; i < results.length; i++) {
                    logMsg.append(i + ": " + Arrays.toString(results[i]) + "\n");
                }
            }
            logMsg.append("Sum: " + Arrays.toString(sumSolutionSize) + "\n");
            logMsg.append("Average: " + Arrays.toString(Arrays.stream(sumSolutionSize)
                    .mapToDouble(x -> Math.round(x * 100.0 / inputFiles.length) / 100.0).toArray()) + "\n\n");
        }
        logMsg.append("Cumulative sum: " + Arrays.toString(cumulativeLength) + "\n");
        logger.info(logMsg.toString());
    }
}

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class MTVRPTW {

    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());
    static final int numCustomers = 200;
    static final TEST_CONFIG CONFIG = TEST_CONFIG.TEST_SOLUTION_CONSTRUCTION;
    static final boolean SHOW_TEST_CASE_STATS = true;

    static File inputDirectory;
    static Configurations configs;

    public static void main(String[] args) {
        String inputFolder = "/" + String.valueOf(numCustomers) + "/";
        inputDirectory = new File(System.getProperty("user.dir") + "/input/" + inputFolder);
        // TODO: experiment with multiple configurations
        configs = new Configurations(0, numCustomers, System.getProperty("user.dir") + "/input/" + "/parameters.txt");
        if (CONFIG == TEST_CONFIG.TEST_SOLUTION_CONSTRUCTION) {
            testSolutionConstruction();
        } else if (CONFIG == TEST_CONFIG.TEST_LS) {
            testLS();
        } else if (CONFIG == TEST_CONFIG.TEST_ALL) {
            testAll();
        }
    }

    public static void testAll() {
        StringBuilder logMsg = new StringBuilder();
        ConstructionAlgorithm[] constructionAlgorithms
                = {new GreedyAlgorithm(), new SolomonI1Algorithm(), new ClusterRouting(), new ChangsAlgorithm()};
//        solutionConstructionAlgorithms = Arrays.copyOfRange(solutionConstructionAlgorithms, 1, 2);
        int numAlgorithms = constructionAlgorithms.length;

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

                    assert Utils.isValidSolution(dataModel, solutions[j][i]);
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
            if (SHOW_TEST_CASE_STATS) {
                logMsg.append(Arrays.toString(inputFiles) + "\n");
                for (int i = 0; i < results.length; i++) {
                    logMsg.append(i + ": " + Arrays.toString(results[i]) + "\n");
                }
            }
            logMsg.append("Sum: " + Arrays.toString(sumSolutionSize) + "\n");
            logMsg.append("Average: " + Arrays.toString(Arrays.stream(sumSolutionSize)
                    .mapToDouble(x -> Math.round(x * 100.0 / inputFiles.length) / 100.0).toArray()) + "\n\n");
            logger.info(logMsg.toString());
            logMsg = new StringBuilder();
        }
        logMsg.append("Cumulative sum: " + Arrays.toString(cumulativeLength) + "\n");
        logger.info(logMsg.toString());
    }

    public static void testSolutionConstruction() {
        StringBuilder logMsg = new StringBuilder();

        String[] testSets = inputDirectory.list((dir, name) -> new File(dir, name).isDirectory());
        Arrays.sort(testSets);

        int cumulativeLength = 0;

        for (String testSet : testSets) {
            String testDirectory = inputDirectory + "/" + testSet;
            String[] inputFiles = Objects.requireNonNull(new File(testDirectory).list((dir, name) -> new File(dir, name).isFile()));
            Arrays.sort(inputFiles);
            List<Route>[] solution = new ArrayList[inputFiles.length];
            for (int i = 0; i < inputFiles.length; i++) {
                DataModel dataModel = new DataModel(testDirectory + "/" + inputFiles[i], configs);
                solution[i] = new ILS().run(dataModel);

                assert Utils.isValidSolution(dataModel, solution[i]);
            }
            logMsg.append(testSet + "\n");
            if (SHOW_TEST_CASE_STATS) {
                logMsg.append(Arrays.toString(inputFiles) + "\n");
                logMsg.append(Arrays.toString(Arrays.stream(solution).mapToInt(List::size).toArray()) + "\n");
            }
            int sum = Arrays.stream(solution).mapToInt(List::size).sum();
            cumulativeLength += sum;
            logMsg.append("Sum: " + sum + ", average: " + (1.0 * sum / inputFiles.length) + "\n");
            logger.info(logMsg.toString());
            logMsg = new StringBuilder();
        }
        logMsg.append("Cumulative sum: " + cumulativeLength + "\n");
        logger.info(logMsg.toString());
    }

    public static void testLS() {
        StringBuilder logMsg = new StringBuilder();

        String[] testSets = inputDirectory.list((dir, name) -> new File(dir, name).isDirectory());
        Arrays.sort(testSets);

        int cumulativeLength = 0;

        for (String testSet : testSets) {
            String testDirectory = inputDirectory + "/" + testSet;
            String[] inputFiles = Objects.requireNonNull(new File(testDirectory).list((dir, name) -> new File(dir, name).isFile()));
            Arrays.sort(inputFiles);
            List<Route>[] solution = new ArrayList[inputFiles.length];
            for (int i = 0; i < inputFiles.length; i++) {
                DataModel dataModel = new DataModel(testDirectory + "/" + inputFiles[i], configs);
                List<Route> initialSolution = new SolomonI1Algorithm().run(dataModel);
                solution[i] = RelocateAlgorithm.run(initialSolution, dataModel);
//                solution[i] = OrOpt2OptAlgorithm.run(initialSolution, dataModel);

                assert Utils.isValidSolution(dataModel, solution[i]);
            }
            logMsg.append(testSet + "\n");
            if (SHOW_TEST_CASE_STATS) {
                logMsg.append(Arrays.toString(inputFiles) + "\n");
                logMsg.append(Arrays.toString(Arrays.stream(solution).mapToInt(List::size).toArray()) + "\n");
            }
            int sum = Arrays.stream(solution).mapToInt(List::size).sum();
            cumulativeLength += sum;
            logMsg.append("Sum: " + sum + ", average: " + (1.0 * sum / inputFiles.length) + "\n");
            logger.info(logMsg.toString());
            logMsg = new StringBuilder();
        }
        logMsg.append("Cumulative sum: " + cumulativeLength + "\n");
        logger.info(logMsg.toString());
    }
}

enum TEST_CONFIG {
    TEST_LS,
    TEST_ALL,
    TEST_SOLUTION_CONSTRUCTION;
}

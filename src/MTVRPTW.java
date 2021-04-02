import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Main class for Multi-Trip Vehicle Routing Problem with Time Window.
 * Reading test case and determine which algorithms to run.
 * Print output statistics.
 */
public class MTVRPTW {
    static final int numCustomers = 100;  // Set this to 100 to test Solomon's test set
    static final TEST_CONFIG CONFIG = TEST_CONFIG.TEST_ILS;
    static final boolean SHOW_TEST_CASE_STATS = true;
    static File inputDirectory, outputDirectory;

    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

    public static void main(String[] args) throws IOException {
        String inputFolder = "/" + String.valueOf(numCustomers) + "/";
        inputDirectory = new File(System.getProperty("user.dir") + "/input/" + inputFolder);
        outputDirectory = new File(System.getProperty("user.dir") + "/output/" + inputFolder);
        if (!outputDirectory.exists()) outputDirectory.mkdir();
        if (CONFIG == TEST_CONFIG.TEST_ILS) {
            testILS();
        } else if (CONFIG == TEST_CONFIG.TEST_LS) {
            testLS();
        } else if (CONFIG == TEST_CONFIG.TEST_ALL) {
            testAll();
        }
    }

    /**
     * Test the solution algorithm (iterated local search).
     */
    public static void testILS() throws IOException {
        StringBuilder logMsg = new StringBuilder();

        String[] testSets = inputDirectory.list((dir, name) -> new File(dir, name).isDirectory());
        Arrays.sort(testSets);
//        testSets = new String[]{"R1", "R2", "RC1", "RC2", "C1", "C2"};

        int cumulativeLength = 0;

        File summaryResultFile = new File(outputDirectory + "/" + "summary.txt");
        FileWriter fileWriter = new FileWriter(summaryResultFile);
        fileWriter.write("Format:\nTest set:\nTest cases:[]\n# vehicles:[]\nTotal distance:[]\nRuntime:[]\nCumulative:\n");

        for (String testSet : testSets) {
            String testDirectory = inputDirectory + "/" + testSet;
            String[] inputFiles = Objects.requireNonNull(new File(testDirectory).list((dir, name) -> new File(dir, name).isFile()));
            Arrays.sort(inputFiles);
            List<Route>[] solution = new ArrayList[inputFiles.length];
            int[] solutionSizes = new int[inputFiles.length];
            double[] distanceTraveled = new double[inputFiles.length];
            double[] runtimes = new double[inputFiles.length];
            for (int i = 0; i < inputFiles.length; i++) {
                DataModel dataModel = new DataModel(testDirectory + "/" + inputFiles[i], numCustomers);
                long start = System.nanoTime();
                solution[i] = new SolutionAlgorithm().run(dataModel);

                assert Utils.isValidSolution(dataModel, solution[i]);

                Utils.writeOutputToFile(solution[i], outputDirectory, testSet, inputFiles[i]);

                solutionSizes[i] = solution[i].size();
                distanceTraveled[i] = ((int) (Utils.getTotalDistance(dataModel, solution[i]) * 10)) / 10.0;
                runtimes[i] = System.nanoTime() - start;
            }
            logMsg.append(testSet + "\n");
            if (SHOW_TEST_CASE_STATS) {
                logMsg.append(Arrays.toString(inputFiles) + "\n");
                logMsg.append(Arrays.toString(solutionSizes) + "\n");
                logMsg.append(Arrays.toString(distanceTraveled) + "\n");

                // Show runtimes nicely in second
                for (int i = 0; i < runtimes.length; i++) {
                    runtimes[i] /= 1e7;
                    runtimes[i] = ((int) runtimes[i]) / 100.0;
                }
                logMsg.append(Arrays.toString(runtimes) + "\n");
            }
            int sum = Arrays.stream(solutionSizes).sum();
            cumulativeLength += sum;
            logMsg.append("Cumulative # vehicles: " + sum + ", average: " + (1.0 * sum / inputFiles.length) + "\n");
            logger.info(logMsg.toString());
            fileWriter.write(logMsg.toString());
            logMsg = new StringBuilder();
        }
        logMsg.append("\nTotal # vehicles (all test sets): " + cumulativeLength + "\n");
        logger.info(logMsg.toString());
        fileWriter.write(logMsg.toString());
        fileWriter.close();
    }

    /**
     * Test a local search algorithm.
     * The solution construction algorithm is default to MTSolomonAlgorithm.
     * The solution improvement (local search) algorithm is default to RelocateAlgorithm
     */
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
                DataModel dataModel = new DataModel(testDirectory + "/" + inputFiles[i], numCustomers);
                List<Route> initialSolution = new MTSolomonAlgorithm().run(dataModel);
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

    /**
     * Test different algorithms:
     *  - Solution construction algorithms: Greedy, MTSolomonAlgorithm, ClusterRouteMergeDFS
     *  - Local search algorithm: RelocateAlgorithm
     *  - Iterated local search (solution algorithm)
     */
    public static void testAll() {
        StringBuilder logMsg = new StringBuilder();
        ConstructionAlgorithm[] constructionAlgorithms
                = {new Greedy(), new MTSolomonAlgorithm(), new ClusterRouteMergeDFS()};
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
                DataModel dataModel = new DataModel(testDirectory + "/" + inputFiles[i], numCustomers);
                for (int j = 0; j < results.length - 2; j++) {
                    solutions[j][i] = constructionAlgorithms[j].run(dataModel);
                    results[j][i] = solutions[j][i].size();

                    assert Utils.isValidSolution(dataModel, solutions[j][i]);
                }
                // Local search
                solutions[results.length - 2][i] = RelocateAlgorithm.run(solutions[1][i], dataModel);
                results[results.length - 2][i] = solutions[results.length - 2][i].size();
                // ILS
                solutions[results.length - 1][i] = new SolutionAlgorithm().run(dataModel);
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
}

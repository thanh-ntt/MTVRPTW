import javax.xml.crypto.Data;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class MTVRPTW {

    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());
    static final boolean SHOW_ROUTE_STATS = false;

    public static void main(String[] args) {
        SolutionConstructionAlgorithm[] solutionConstructionAlgorithms
                = {new GreedyAlgorithm(), new SolomonI1Algorithm(), new ClusterRouting(), new ChangsAlgorithm()};
        int numAlgorithms = solutionConstructionAlgorithms.length;
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
                for (int j = 0; j < numAlgorithms; j++) {
                    results[j][i] = solutionConstructionAlgorithms[j].run(dataModel).size();
                }
            }
            double[] averageResults = new double[numAlgorithms];
            for (int i = 0; i < numAlgorithms; i++) {
                averageResults[i] = Arrays.stream(results[i]).average().orElse(Double.NaN);
            }
            logger.info(testSet + ": "
                    + Arrays.toString(Arrays.stream(averageResults).map(x -> Math.round(x * 100.0) / 100.0).toArray()));
        }
    }
}

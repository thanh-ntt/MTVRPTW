import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Logger;

public class MTVRPTW {

    int numClustersThreshold;

    // TODO: write logs
    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

    /**
     * Main algorithm.
     */
    public void runAlgorithm() {
        String inputDirectory = System.getProperty("user.dir") + "/input/R110ok/";
        DataModel dataModel = new DataModel(inputDirectory);  // read from input (add parameters later)
        readInputParameters(dataModel, inputDirectory);

        // Step 1: try different # of clusters
        List<VehicleRouting> solutions = new ArrayList<>();

        // Step 2-9 (currently 2-7): Initial solution construction
        for (int numClusters = 1; numClusters <= numClustersThreshold; numClusters++) {
            VehicleRouting vehicleRouting = new VehicleRouting(dataModel);  // also pass dataModel
            // Do 3 steps: cluster, parallel construction, merge
            vehicleRouting.initialConstruction(numClusters);
            // assert vehicleRouting.validRouting()
            solutions.add(vehicleRouting);
        }
    }

    /**
     * Read algorithm parameters:
     *      threshold of # clusters
     *      vehicle capacity
     */
    public void readInputParameters(DataModel dataModel, String inputDirectory) {
        try {
            File inputParametersFile = new File( inputDirectory + "/parameters.txt");
            Scanner inputParameter = new Scanner(inputParametersFile);
            this.numClustersThreshold = inputParameter.nextInt();
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
        mtvrptw.runAlgorithm();
    }
}

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Configurations {
    public final int configId;
    public int numClustersThreshold;
    public double capacityRatio;
    public int deltaThreshold;
    public double distanceRatio, timeRatio;
    public int exchangeOperatorAcceptanceCriterion, relocateOperatorAcceptanceCriterion;
    public Configurations(int id, String filePath) {
        configId = id;
        readConfigurations(filePath);
    }

    void readConfigurations(String filePath) {
        try {
            File file = new File(filePath);
            Scanner scan = new Scanner(file);
            numClustersThreshold = scan.nextInt();
            capacityRatio = scan.nextDouble();
            deltaThreshold = scan.nextInt();
            distanceRatio = scan.nextDouble();
            timeRatio = scan.nextDouble();
            exchangeOperatorAcceptanceCriterion = scan.nextInt();
            relocateOperatorAcceptanceCriterion = scan.nextInt();
        }  catch (FileNotFoundException e) {
            System.out.println("Cannot find file");
            e.printStackTrace();
        }
    }
}

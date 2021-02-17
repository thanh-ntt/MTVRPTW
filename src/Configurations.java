import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Configurations {
    public final int configId;
    public int numCustomers;
    public int numClustersThreshold;
    public double capacityRatio;
    public int deltaThreshold;
    public int exchangeOperatorAcceptanceCriterion, relocateOperatorAcceptanceCriterion;
    public Configurations(int id, int n, String filePath) {
        configId = id;
        numCustomers = n;
        readConfigurations(filePath);
    }

    void readConfigurations(String filePath) {
        try {
            File file = new File(filePath);
            Scanner scan = new Scanner(file);
            numClustersThreshold = scan.nextInt();
            capacityRatio = scan.nextDouble();
            deltaThreshold = scan.nextInt();
            exchangeOperatorAcceptanceCriterion = scan.nextInt();
            relocateOperatorAcceptanceCriterion = scan.nextInt();
        }  catch (FileNotFoundException e) {
            System.out.println("Cannot find file");
            e.printStackTrace();
        }
    }
}

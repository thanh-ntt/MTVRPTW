import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Configurations {
    public final int configId;
    public int numClustersThreshold;
    public double alpha1;
    public double alpha2;
    public int pNeighborhoodSize;
    public int deltaThreshold;
    public Configurations(int id, String filePath) {
        configId = id;
        readConfigurations(filePath);
    }

    void readConfigurations(String filePath) {
        try {
            File file = new File(filePath);
            Scanner scan = new Scanner(file);
            numClustersThreshold = scan.nextInt();
            alpha1 = scan.nextDouble();
            alpha2 = scan.nextDouble();
            pNeighborhoodSize = scan.nextInt();
            deltaThreshold = scan.nextInt();
        }  catch (FileNotFoundException e) {
            System.out.println("Cannot find file");
            e.printStackTrace();
        }
    }
}

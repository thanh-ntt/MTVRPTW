import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.IntStream;

/**
 * A data model to store all information related to a test case.
 */
public class DataModel {
    private double[][] distanceTable;
    private int[][] timeWindows;
    private int[] serviceTimes;
    private int[] demands;
    private double[] latestDepartureTimes;  // latest time the vehicle can leave the depot and still serve each customer
    private int vehicleCapacity;
    private Node[] nodes;  // depot + all customers
    private int numNodes;  // depot + customers

    public DataModel(String inputFilePath, int numCustomers) {
        numNodes = numCustomers + 1;
        try {
            this.readInputs(inputFilePath);
        }  catch (FileNotFoundException e) {
            System.out.println("Cannot find file");
            e.printStackTrace();
        }
    }

    void readInputs(String inputFilePath) throws FileNotFoundException {
        File file = new File(inputFilePath);
        Scanner scan = new Scanner(file);

        for (int i = 0; i < 4; i++) scan.nextLine();
        scan.nextInt();
        vehicleCapacity = scan.nextInt();
        for (int i = 0; i < 5; i++) scan.nextLine();

        distanceTable = new double[numNodes][numNodes];
        timeWindows = new int[numNodes][2];
        serviceTimes = new int[numNodes];
        demands = new int[numNodes];
        latestDepartureTimes = new double[numNodes];
        nodes = new Node[numNodes];

        for (int i = 0; i < numNodes; i++) {
            nodes[i] = new Node(scan.nextInt(), scan.nextDouble(), scan.nextDouble(),
                    (int) scan.nextDouble(), (int) scan.nextDouble(), (int) scan.nextDouble(), (int) scan.nextDouble());
        }

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                distanceTable[i][j] = Math.sqrt(Math.pow(nodes[i].xCoord - nodes[j].xCoord, 2) + Math.pow(nodes[i].yCoord - nodes[j].yCoord, 2));
            }
            timeWindows[i][0] = nodes[i].readyTime;
            timeWindows[i][1] = nodes[i].dueTime;
            serviceTimes[i] = nodes[i].serviceTime;
            demands[i] = nodes[i].demand;
            latestDepartureTimes[i] = Math.min(nodes[i].dueTime - distFromDepot(nodes[i]), nodes[0].dueTime - 2 * distFromDepot(nodes[i]) - nodes[i].serviceTime);
        }
    }

    // Getters & setters
    public int getTotalDemands() {
        return IntStream.of(demands).sum();
    }

    public int getVehicleCapacity() {
        return vehicleCapacity;
    }

    public Set<Node> getDemandNodes() {
        Set<Node> demandNodes = new HashSet<>();
        // Skip depot
        for (int i = 1; i < numNodes; i++)
            demandNodes.add(nodes[i]);
        return demandNodes;
    }

    public double distFromDepot(Node node) {
        return distanceTable[node.id][0];
    }

    public double getLatestDepartureTime(Set<Node> unRoutedCustomers) {
        return unRoutedCustomers.stream().mapToDouble(c -> latestDepartureTimes[c.id]).min().orElseGet(() -> -1.0);
    }

    public double getLatestDepartureTime(Node u) {
        return latestDepartureTimes[u.id];
    }

    public double dist(Node source, Node destination) {
        return distanceTable[source.id][destination.id];
    }

    public Node getDepot() {
        return nodes[0];
    }
}

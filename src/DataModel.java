import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.IntStream;

// TODO: make all variables protected, so other classes cannot change them
public class DataModel {
    double[][] distanceTable;
    // TODO: refactor, only store Node[] array, not a lot of variables like this
    int[][] timeWindows;
    int[] serviceTimes;
    int[] demands;
    double[] latestDepartureTimes;  // latest time the vehicle can leave the depot and still serve each customer
    int vehicleCapacity;
    Node[] nodes;  // depot + all customers
    int numCustomers;
    int numNodes;  // depot + customers

    Configurations configs;

    // TODO: Move some methods to Utils class

    public DataModel(String inputFilePath, Configurations configs) {
        // Read configs
        this.configs = configs;
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
        numCustomers = configs.numCustomers;
        numNodes = numCustomers + 1;
        scan.nextInt();
        setVehicleCapacity(scan.nextInt());
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

    public int getNumCustomers() {
        return numCustomers;
    }

    void setVehicleCapacity(int capacity) {
        this.vehicleCapacity = (int) (capacity * configs.capacityRatio);
    }

    public int getDeltaThreshold() {
        return this.configs.deltaThreshold;
    }

    public int getVehicleCapacity() {
        assert vehicleCapacity > 0;
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

    public double dist(Node source, Node destination) {
        return distanceTable[source.id][destination.id];
    }

    public Node getDepot() {
        return nodes[0];
    }
}

class Node {
    final int id;
    final int demand;
    final int readyTime, dueTime;
    final int serviceTime;
    final double xCoord;
    final double yCoord;

    public Node(int id, double xCoord, double yCoord, int demand, int readyTime, int dueTime, int serviceTime) {
        this.id = id;
        this.xCoord = xCoord;
        this.yCoord = yCoord;
        this.demand = demand;
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.serviceTime = serviceTime;
    }

    public String toString() {
        if (id == 0) return "Depot";
        else return "c" + id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        return id == ((Node) o).id;
    }
}
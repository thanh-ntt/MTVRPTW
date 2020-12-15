import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.IntStream;

public class DataModel {
    double[][] distanceTable;
    // TODO: refactor, only store Node[] array, not a lot of variables like this
    int[][] timeWindows;
    int[] serviceTimes;
    int[] demands;
    double[] latestDepartureTimes;  // latest time the vehicle can leave the depot and still serve each customer
    int vehicleCapacity;
    Node[] nodes;  // depot + all customers
    int numCustomers = 100;
    int numNodes = 101;  // depot + customers
    double alpha1, alpha2;  // parameters used in parallel construction heuristic - I1 insertion heuristic (Solomon, 1987)
    int pNeighbourhoodSize;
    int numClustersThreshold;
    int deltaThreshold;
    String inputFilePath;

    // TODO: Move some methods to Utils class

    public DataModel() {
        distanceTable = new double[numNodes][numNodes];
        timeWindows = new int[numNodes][2];
        serviceTimes = new int[numNodes];
        demands = new int[numNodes];
        latestDepartureTimes = new double[numNodes];
        nodes = new Node[numNodes];
    }

    void readInputs() throws FileNotFoundException {
        File file = new File(inputFilePath);
        Scanner scan = new Scanner(file);

        setVehicleCapacity(scan.nextInt());

        for (int i = 0; i < numNodes; i++) {
            nodes[i] = new Node(scan.nextInt() - 1, scan.nextDouble(), scan.nextDouble(),
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
            latestDepartureTimes[i] = nodes[i].dueTime - getDistanceFromDepot(nodes[i]);
        }
    }

    // Getters & setters
    public int getTotalDemands() {
        return IntStream.of(demands).sum();
    }

    public int getNumCustomers() {
        return numCustomers;
    }

    public void setInputFilePath(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }

    public void setNumClustersThreshold(int numClustersThreshold) {
        this.numClustersThreshold = numClustersThreshold;
    }

    void setVehicleCapacity(int capacity) {
        this.vehicleCapacity = capacity;
        for (int demand : demands)
            assert this.vehicleCapacity >= demand;
    }

    public void setAlphaParameters(double first, double second) {
        alpha1 = first;
        alpha2 = second;
        assert alpha1 + alpha2 == 1;
    }

    public void setPNeighbourhoodSize(int size) {
        assert size >= 1;
        pNeighbourhoodSize = size;
    }

    public void setDeltaThreshold(int threshold) {
        assert threshold >= 1;
        deltaThreshold = threshold;
    }

    public int getDeltaThreshold() {
        return this.deltaThreshold;
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

    public double getDistanceFromDepot(Node node) {
        return distanceTable[node.id][0];
    }

    public double getLatestDepartureTimeFor(Node node) {
        return latestDepartureTimes[node.id];
    }

    public double getLatestDepartureTime(Set<Node> unRoutedCustomers) {
        return unRoutedCustomers.stream().mapToDouble(this::getLatestDepartureTimeFor).min().orElseGet(() -> -1.0);
    }

    public double getTravelTime(Node source, Node destination) {
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
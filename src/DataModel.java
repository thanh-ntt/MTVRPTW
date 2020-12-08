import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.IntStream;

public class DataModel {
    double[][] distanceTable;
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
    String inputTestFolder;

    // TODO: Move some methods to Utils class

    public DataModel() {
        distanceTable = new double[numNodes][numNodes];
        timeWindows = new int[numNodes][2];
        serviceTimes = new int[numNodes];
        demands = new int[numNodes];
        latestDepartureTimes = new double[numNodes];
    }

    void readInputAndPopulateData() {
        readInputFromFiles(inputTestFolder);
        populateNodeData();
    }

    void populateNodeData() {
        nodes = new Node[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodes[i] = new Node(i, demands[i], timeWindows[i][0], timeWindows[i][1], serviceTimes[i]);
            latestDepartureTimes[i] = nodes[i].dueTime - getDistanceFromDepot(nodes[i]);
        }
    }

    // TODO: read input from Solomon's benchmark (well-known format) instead of this customized format
    void readInputFromFiles(String inputTestDirectory) {
        try {
            File inputDistanceFile = new File(inputTestDirectory + "/input_distancetable.txt");
            Scanner inputDistance = new Scanner(inputDistanceFile);
            for (int i = 0; i < numNodes; i++) {
                for (int j = 0; j < numNodes; j++) {
                    distanceTable[i][j] = inputDistance.nextDouble();
                }
            }
            File inputTimeWindowFile = new File(inputTestDirectory + "/input_timewindow.txt");
            Scanner inputTimeWindow = new Scanner(inputTimeWindowFile);
            for (int i = 0; i < numNodes; i++) {
                timeWindows[i][0] = inputTimeWindow.nextInt();
                timeWindows[i][1] = inputTimeWindow.nextInt();
            }
            File inputServiceTimeFile = new File(inputTestDirectory + "/input_servicetime.txt");
            Scanner inputServiceTime = new Scanner(inputServiceTimeFile);
            for (int i = 0; i < numNodes; i++) {
                serviceTimes[i] = inputServiceTime.nextInt();
            }
            File inputDemandFile = new File(inputTestDirectory + "/input_demand.txt");
            Scanner inputDemand = new Scanner(inputDemandFile);
            for (int i = 0; i < numNodes; i++) {
                demands[i] = inputDemand.nextInt();
            }
        } catch (FileNotFoundException e) {
            System.out.println("Cannot find file");
            e.printStackTrace();
        }
    }

    // Getters & setters

    public int getTotalDemands() {
        return IntStream.of(demands).sum();
    }

    public int getNumCustomers() {
        return numCustomers;
    }

    public void setInputTestFolder(String inputTestFolder) {
        this.inputTestFolder = inputTestFolder;
    }

    public void setNumClustersThreshold(int numClustersThreshold) {
        this.numClustersThreshold = numClustersThreshold;
    }

    public void setVehicleCapacity(int capacity) {
        this.vehicleCapacity = capacity;
        for (int demand : demands)
            assert this.vehicleCapacity >= demand;
    }

    public void setAlphaParameters(double first, double second) {
        alpha1 = first;
        alpha2 = second;
        assert alpha1 + alpha1 == 1;
    }

    public void setPNeighbourhoodSize(int size) {
        assert size >= 1;
        pNeighbourhoodSize = size;
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
    int id;
    int demand;
    int readyTime, dueTime;
    int serviceTime;

    public Node(int id, int demand, int readyTime, int dueTime, int serviceTime) {
        this.id = id;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.IntStream;

public class DataModel {
    double[][] distanceTable;
    int[][] timeWindows;
    int[] serviceTimes;
    int[] demands;
    int vehicleCapacity;
    Node[] nodes;  // depot + all customers
    int numCustomers = 100;
    int numNodes = 101;  // depot + customers

    public DataModel(String inputDirectory) {
        distanceTable = new double[numNodes][numNodes];
        timeWindows = new int[numNodes][2];
        serviceTimes = new int[numNodes];
        demands = new int[numNodes];
        readInputFromFiles(inputDirectory);
        populateNodeData();
    }

    void populateNodeData() {
        nodes = new Node[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodes[i] = new Node(i, demands[i], timeWindows[i][0], timeWindows[i][1], serviceTimes[i]);
        }
    }

    void readInputFromFiles(String inputDirectory) {
        try {
            File inputDistanceFile = new File(inputDirectory + "/input_distancetable.txt");
            Scanner inputDistance = new Scanner(inputDistanceFile);
            for (int i = 0; i < numNodes; i++) {
                for (int j = 0; j < numNodes; j++) {
                    distanceTable[i][j] = inputDistance.nextDouble();
                }
            }
            File inputTimeWindowFile = new File(inputDirectory + "/input_timewindow.txt");
            Scanner inputTimeWindow = new Scanner(inputTimeWindowFile);
            for (int i = 0; i < numNodes; i++) {
                timeWindows[i][0] = inputTimeWindow.nextInt();
                timeWindows[i][1] = inputTimeWindow.nextInt();
            }
            File inputServiceTimeFile = new File(inputDirectory + "/input_servicetime.txt");
            Scanner inputServiceTime = new Scanner(inputServiceTimeFile);
            for (int i = 0; i < numNodes; i++) {
                serviceTimes[i] = inputServiceTime.nextInt();
            }
            File inputDemandFile = new File(inputDirectory + "/input_demand.txt");
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

    public void setVehicleCapacity(int capacity) {
        this.vehicleCapacity = capacity;
        for (int demand : demands)
            assert this.vehicleCapacity >= demand;
    }

    public int getVehicleCapacity() {
        assert vehicleCapacity > 0;
        return vehicleCapacity;
    }

    public List<Node> getDemandNodes() {
        List<Node> demandNodes = new ArrayList<>();
        // Skip depot
        for (int i = 1; i < numNodes; i++)
            demandNodes.add(nodes[i]);
        return demandNodes;
    }

    public double getDistanceFromDepot(Node node) {
        return distanceTable[node.id][0];
    }

    public double getDistance(Node source, Node destination) {
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
        return "C" + id;
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
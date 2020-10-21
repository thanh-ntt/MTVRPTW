import java.util.*;

public class Route {
    DataModel dataModel;
    List<Node> routedPath;
    // Time that the truck arrives at customer i (correspond to ith Node in routedPath)
    // Note that this time can be different from service time, since service time = max(arrival time, ready time)
    List<Double> arrivalTimes;
    Node depot;
    int totalDemand;

    public Route(DataModel dataModel, Node seed) {
        this.dataModel = dataModel;
        depot = dataModel.getDepot();
        totalDemand = 0;
        routedPath = new ArrayList<>(Arrays.asList(depot, seed, depot));
        initializeServiceTimeFromRoutedPath(routedPath);
    }

    /**
     * This method initialize the service time for each customer in the routedPath
     * and stores in serviceTimes list.
     * It also checks (assert) for time feasibility of the path / route.
     */
    void initializeServiceTimeFromRoutedPath(List<Node> routedPath) {
        arrivalTimes = new ArrayList<>();
        for (int i = 0; i < routedPath.size(); i++) {
            Double arrivalTime = null;
            if (i == 0) {
                arrivalTime = (double) routedPath.get(0).readyTime;  // Depot
            } else {
                double previousCustomerServiceTime = Math.max(arrivalTimes.get(i - 1), routedPath.get(i - 1).readyTime);
                // arrival time = starting service time at previous node + service time + time travel
                arrivalTime = previousCustomerServiceTime + routedPath.get(i - 1).serviceTime + dataModel.getTravelTime(routedPath.get(i - 1), routedPath.get(i));
            }
            assert arrivalTime <= routedPath.get(i).dueTime;
            arrivalTimes.add(arrivalTime);
        }
    }

    /**
     * Insert the new customer (node) at position p in the route
     * and update the arrival time for all following customers
     * @param p index in the route to be inserted (0-based indexing)
     * @param node the new customer to be inserted
     */
    public void insertAtPosition(int p, Node node) {
        assert !node.equals(depot);
        routedPath.add(p, node);
        totalDemand += node.demand;

        // update arrival time for all nodes after position p
        double arrivalTimeAtP = getStartingServiceTimeAt(p - 1) + routedPath.get(p - 1).serviceTime + dataModel.getTravelTime(routedPath.get(p - 1), node);
        arrivalTimes.add(p, arrivalTimeAtP);
        for (int i = p + 1; i < arrivalTimes.size(); i++) {
            double arrivalTimeAtI = getStartingServiceTimeAt(i - 1) + routedPath.get(i - 1).serviceTime + dataModel.getTravelTime(routedPath.get(i - 1), routedPath.get(i));
            assert arrivalTimeAtI <= routedPath.get(i).dueTime;
            arrivalTimes.set(i, arrivalTimeAtI);
        }
    }

    public boolean canInsertAtPosition(int position, Node node) {
        return checkCapacityConstraint(node) && checkTimeConstraint(position, node);
    }

    boolean checkCapacityConstraint(Node node) {
        return totalDemand + node.demand <= dataModel.getVehicleCapacity();
    }

    /**
     * Check if it's feasible (in terms of time constraint) to insert a new customer u
     * between i(p-1) = m and ip = n in the current route: (i0, ..., i(p-1), ip, ..., i0)
     * This is a direct implementation of Solomon (1987), lemma 1.1
     */
    boolean checkTimeConstraint(int p, Node u) {
        // Time feasibility for customer u
        double arrivalTimeCustomerU = getStartingServiceTimeAt(p - 1) + routedPath.get(p - 1).serviceTime + dataModel.getTravelTime(routedPath.get(p - 1), u);
        if (arrivalTimeCustomerU > u.dueTime) return false;

        double pushForward = getPushForwardTimeAtNextCustomer(u, p);
        // Check time window constraint at p
        if (getStartingServiceTimeAt(p) + pushForward > routedPath.get(p).dueTime) return false;

        // Check time window constraints at r > p
        for (int r = p + 1; r < routedPath.size(); r++) {
            double prevPushForward = pushForward;
            // How long the truck has to wait at customer r (before insertion of u)
            double waitingTimeAtR = Math.max(routedPath.get(r).readyTime - arrivalTimes.get(r), 0);
            pushForward = Math.max(0, prevPushForward - waitingTimeAtR);

            // All time window constraint after customer r will remains (satisfied)
            if (pushForward == 0) return true;
            // Check time window constraint at r
            if (getStartingServiceTimeAt(r) + pushForward > routedPath.get(r).dueTime) return false;
        }
        return true;
    }

    /**
     * Return the push forward time at customer ip if a new customer u is inserted between i(p-1) = m and ip = n
     * -> Route before insertion: (i0, ..., i(p-1), ip, ..., i0)
     * -> Route after insertion: (i0, ..., i(p-1), u, ip, ..., i0)
     */
    double getPushForwardTimeAtNextCustomer(Node u, int p) {
        Node m = routedPath.get(p - 1), n = routedPath.get(p);
        double arrivalTimeCustomerU = getStartingServiceTimeAt(p - 1) + m.serviceTime + dataModel.getTravelTime(m, u);
        assert arrivalTimeCustomerU > u.dueTime;  // this time feasibility condition should be checked before
        double serviceTimeCustomerU = Math.max(arrivalTimeCustomerU, u.readyTime);
        double oldServiceTimeCustomerN = getStartingServiceTimeAt(p);
        double newArrivalTimeCustomerN = serviceTimeCustomerU + u.serviceTime + dataModel.getTravelTime(u, n);
        double newServiceTimeCustomerN = Math.max(newArrivalTimeCustomerN, n.readyTime);
        double pushForward = newServiceTimeCustomerN - oldServiceTimeCustomerN;
        return pushForward;
    }

    /**
     * Return the starting service time at customer with index p in the routedPath
     */
    double getStartingServiceTimeAt(int p) {
        return Math.max(arrivalTimes.get(p), routedPath.get(p).readyTime);
    }

    public int getLength() {
        return routedPath.size();
    }

    public int getTotalDemand() {
        return totalDemand;
    }

    @Override
    public String toString() {
        return Arrays.toString(routedPath.toArray());
    }
}

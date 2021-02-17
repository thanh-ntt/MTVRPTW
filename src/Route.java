import java.util.*;
import java.util.stream.Collectors;

public class Route {
    DataModel dataModel;
    List<Node> routedPath;
    // Time that the truck arrives at customer i (correspond to ith Node in routedPath)
    // Note that this time can be different from service time, since service time = max(arrival time, ready time)
    List<Double> arrivalTimes;
    Node depot;
    // The total demand of all customers in the current trip
    // A trip is defined as the first customer that leaves depot until coming back to the depot
    // If routedPath = [depot, c1, c2, c3, depot, c4, c5, depot]
    // and demand = [0, 1, 2, 1, 0, 2, 3, 0]
    // Then vehicleLoadInCurTrip = [0, 4, 4, 4, 4, 5, 5, 5]
    List<Integer> vehicleLoadInCurTrip;

    /**
     * Initialize a route with only depot.
     * @param dataModel
     */
    public Route(DataModel dataModel) {
        this.dataModel = dataModel;
        this.depot = dataModel.getDepot();
        this.vehicleLoadInCurTrip = new ArrayList<>(Arrays.asList(0));
        this.routedPath = new ArrayList<>(Arrays.asList(depot));
        this.arrivalTimes = new ArrayList<>(Arrays.asList(0.0));
    }

    /**
     * Deep copy of a route
     * @param r the input route
     */
    public Route(Route r) {
        this.dataModel = r.dataModel;
        this.routedPath = new ArrayList<>(r.routedPath);
        this.arrivalTimes = new ArrayList<>(r.arrivalTimes);
        this.depot = r.depot;
        this.vehicleLoadInCurTrip = new ArrayList<>(r.vehicleLoadInCurTrip);
    }

    public Route(DataModel dataModel, Node seed) {
        this.dataModel = dataModel;
        depot = dataModel.getDepot();
        vehicleLoadInCurTrip = new ArrayList<>(Arrays.asList(0, seed.demand, seed.demand));
        routedPath = new ArrayList<>(Arrays.asList(depot, seed, depot));
        initializeArrivalTimes(routedPath, routedPath.get(0).readyTime);
    }

    public Route(DataModel dataModel, Node seed, double departureTimeFromDepot) {
        this.dataModel = dataModel;
        depot = dataModel.getDepot();
        vehicleLoadInCurTrip = new ArrayList<>(Arrays.asList(0, seed.demand, seed.demand));
        routedPath = new ArrayList<>(Arrays.asList(depot, seed, depot));
        initializeArrivalTimes(routedPath, departureTimeFromDepot);
    }

    /**
     * Constructor to create a route by merging 2 routes l, m (in the same order).
     */
    public Route(Route l, Route m) {
        assert l.getLength() > 2 && m.getLength() > 2;  // each route should consist of at least 1 customer
        this.dataModel = l.dataModel;
        depot = dataModel.getDepot();
        routedPath = new ArrayList<>(l.routedPath);
        routedPath.addAll(m.routedPath.subList(1, m.routedPath.size()));  // skip the first depot in m
        initializeArrivalTimes(routedPath, routedPath.get(0).readyTime);

        // TODO: refactor this (put into initializeVehicleLoad method)
        // Initialize vehicle load in each trip
        vehicleLoadInCurTrip = new ArrayList<>(Collections.nCopies(routedPath.size(), 0));

        int lastDepotIdx = 0, curIdx = 1, loadSum = 0;
        while (curIdx < vehicleLoadInCurTrip.size()) {
            if (routedPath.get(curIdx) == depot) {
                for (int i = lastDepotIdx + 1; i <= curIdx; i++)
                    vehicleLoadInCurTrip.set(i, loadSum);
                lastDepotIdx = curIdx;
                loadSum = 0;
            } else {
                loadSum += routedPath.get(curIdx).demand;
            }
            curIdx++;
        }
    }

    public void initializeVariables() {
        initializeVehicleLoad();
        initializeArrivalTimes(routedPath, 0);
    }

    /**
     * Initialize vehicle load in current route.
     * This should only be called once the routedPath is set
     */
    void initializeVehicleLoad() {
        // Initialize vehicle load in each trip
        vehicleLoadInCurTrip = new ArrayList<>(Collections.nCopies(routedPath.size(), 0));

        int lastDepotIdx = 0, curIdx = 1, loadSum = 0;
        while (curIdx < vehicleLoadInCurTrip.size()) {
            if (routedPath.get(curIdx) == depot) {
                for (int i = lastDepotIdx + 1; i <= curIdx; i++)
                    vehicleLoadInCurTrip.set(i, loadSum);
                lastDepotIdx = curIdx;
                loadSum = 0;
            } else {
                loadSum += routedPath.get(curIdx).demand;
            }
            curIdx++;
        }
    }

    /**
     * Construct a new route from the list of demand nodes.
     * @param dataModel
     */
    public Route(DataModel dataModel, List<Node> routedPath) {
        this.dataModel = dataModel;
        this.depot = dataModel.getDepot();
        this.routedPath = routedPath;
        initializeArrivalTimes(routedPath, 0);
        initializeVehicleLoad();
    }

    public int getVehicleLoadCurTrip(int p) {
        return vehicleLoadInCurTrip.get(p);
    }

    /**
     * This method initialize the arrival time at each customer in the routedPath
     * and stores in arrivalTimes list.
     * It also checks (assert) for time feasibility of the path / route
     * (arrival time at each customer is before due time).
     */
    void initializeArrivalTimes(List<Node> routedPath, double departureTimeFromDepot) {
        arrivalTimes = new ArrayList<>();
        for (int i = 0; i < routedPath.size(); i++) {
            Double arrivalTime = null;
            if (i == 0) {
                arrivalTime = departureTimeFromDepot;  // Depot
            } else {
                double previousCustomerServiceTime = Math.max(arrivalTimes.get(i - 1), routedPath.get(i - 1).readyTime);
                // arrival time = starting service time at previous node + service time + time travel
                arrivalTime = previousCustomerServiceTime + routedPath.get(i - 1).serviceTime + dataModel.dist(routedPath.get(i - 1), routedPath.get(i));
            }
            assert arrivalTime <= routedPath.get(i).dueTime;
            arrivalTimes.add(arrivalTime);
        }
    }

    /**
     * Insert the new customer u at position p in the route
     * and update the arrival time for all following customers
     * @param p index in the route to be inserted (0-based indexing)
     * @param u the new customer to be inserted
     */
    public void insertAtPosition(int p, Node u) {
        int previousVehicleLoad = vehicleLoadInCurTrip.get(p);
        routedPath.add(p, u);
        vehicleLoadInCurTrip.add(p, previousVehicleLoad + u.demand);

        // Update the vehicle load for other nodes in the trip
        int idx = p - 1;
        while (idx >= 0 && routedPath.get(idx) != depot) {
            vehicleLoadInCurTrip.set(idx, previousVehicleLoad + u.demand);
            idx--;
        }
        idx = p + 1;
        while (idx < routedPath.size()) {
            vehicleLoadInCurTrip.set(idx, previousVehicleLoad + u.demand);
            if (routedPath.get(idx) == depot) break;  // add up to the next depot
            idx++;
        }

        // update arrival time for all nodes after position p
        double arrivalTimeAtP = getStartingServiceTimeAt(p - 1) + routedPath.get(p - 1).serviceTime + dataModel.dist(routedPath.get(p - 1), u);
        arrivalTimes.add(p, arrivalTimeAtP);
        for (int i = p + 1; i < arrivalTimes.size(); i++) {
            double arrivalTimeAtI = getStartingServiceTimeAt(i - 1) + routedPath.get(i - 1).serviceTime + dataModel.dist(routedPath.get(i - 1), routedPath.get(i));
            if (arrivalTimeAtI == arrivalTimes.get(i)) break;  // early termination
            arrivalTimes.set(i, arrivalTimeAtI);
        }
    }

    public boolean canAppendAtLastPosition(Node u) {
        return getLatestArrivalTimeAtDepot() <= dataModel.getLatestDepartureTime(u);
    }

    /**
     * Inserting the new customer at the end of the route.
     * This is a simplified version of insertAtPosition.
     * @param u the customer to be inserted
     */
    public void appendAtLastPosition(Node u) {
        int length = getLength();
        routedPath.add(length, u);

        int newVehicleLoad = vehicleLoadInCurTrip.get(length - 1) + u.demand;
        vehicleLoadInCurTrip.add(length, newVehicleLoad);
        int idx = length - 1;
        while (idx >= 0 && routedPath.get(idx) != depot) {
            vehicleLoadInCurTrip.set(idx, newVehicleLoad);
            idx--;
        }

        double arrivalTimeAtU = getStartingServiceTimeAt(length - 1) + routedPath.get(length - 1).serviceTime + dataModel.dist(routedPath.get(length - 1), u);
        arrivalTimes.add(length, arrivalTimeAtU);
    }

    public Node removeCustomerAtIndex(int p) {
        Node u = routedPath.remove(p);

        int previousVehicleLoad = vehicleLoadInCurTrip.get(p);
        vehicleLoadInCurTrip.remove(p);  // remove at index p
        arrivalTimes.remove(p);

        // Update the vehicle load for other nodes in the trip
        int idx = p - 1;
        while (idx >= 0 && routedPath.get(idx) != depot) {
            vehicleLoadInCurTrip.set(idx, previousVehicleLoad - u.demand);
            idx--;
        }
        idx = p;
        while (idx < routedPath.size()) {
            vehicleLoadInCurTrip.set(idx, previousVehicleLoad - u.demand);
            if (routedPath.get(idx) == depot) break;  // add up to the next depot
            idx++;
        }

        // update arrival time for all nodes after u
        for (int i = p; i < arrivalTimes.size(); i++) {
            double arrivalTimeAtI = getStartingServiceTimeAt(i - 1) + routedPath.get(i - 1).serviceTime + dataModel.dist(routedPath.get(i - 1), routedPath.get(i));
            if (arrivalTimeAtI == arrivalTimes.get(i)) break;  // early termination
            arrivalTimes.set(i, arrivalTimeAtI);
        }

        return u;
    }

    /**
     * Add a dummy depot to the end of the route.
     */
    public void addDummyDepot() {
        routedPath.add(routedPath.size(), depot);
        arrivalTimes.add(arrivalTimes.get(arrivalTimes.size() - 1));  // duplicate arrival time of last depot
        vehicleLoadInCurTrip.add(0);
    }

    /**
     * Remove the last depot if it's a dummy depot
     */
    public void removeDummyDepot() {
        if (routedPath.get(routedPath.size() - 1) == depot && routedPath.get(routedPath.size() - 2) == depot) {
            routedPath.remove(routedPath.size() - 1);
            arrivalTimes.remove(arrivalTimes.size() - 1);
            vehicleLoadInCurTrip.remove(vehicleLoadInCurTrip.size() - 1);
        }
    }

    public boolean canInsertCustomerAt(int p, Node u) {
        return checkCapacityConstraint(p, u.demand) && checkTimeConstraint(p, u);
    }

    // Only need to check capacity when the removing customer is depot
    public boolean canRemoveCustomerAt(int p) {
        return (routedPath.get(p) != depot) || (vehicleLoadInCurTrip.get(p) + vehicleLoadInCurTrip.get(p + 1) <= dataModel.vehicleCapacity);
    }

    /**
     * Check if the capacity constraint is still satisfied with delta change in vehicle load (in current trip).
     * @param p index of the position of any customer in the trip
     * @param delta the change in vehicle load
     * @return
     */
    public boolean checkCapacityConstraint(int p, int delta) {
        return vehicleLoadInCurTrip.get(p) + delta <= dataModel.getVehicleCapacity();
    }

    /**
     * Check if it's feasible (in terms of time constraint) to insert a new customer u
     * between i(p-1) = m and ip = n in the current route: (i0, ..., i(p-1), ip, ..., i0)
     * This is a direct implementation of Solomon (1987), lemma 1.1
     */
    boolean checkTimeConstraint(int p, Node u) {
        // Time feasibility for customer u
        double arrivalTimeCustomerU = getStartingServiceTimeAt(p - 1) + routedPath.get(p - 1).serviceTime + dataModel.dist(routedPath.get(p - 1), u);
        if (arrivalTimeCustomerU > u.dueTime) return false;

        double pushForward = getPushForwardTimeAfterInsertion(u, p);
        return checkPushForwardTimeFromPosition(pushForward, p);
    }

    /**
     * Check if the time window constraints in the route from node p are all satisfied with a given
     * push forward time from the previous nodes.
     * @param pushForward the push forward time from previous nodes
     * @param p starting index to check (until end of route)
     */
    boolean checkPushForwardTimeFromPosition(double pushForward, int p) {
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
    double getPushForwardTimeAfterInsertion(Node u, int p) {
        Node m = routedPath.get(p - 1), n = routedPath.get(p);
        double arrivalTimeAtU = getStartingServiceTimeAt(p - 1) + m.serviceTime + dataModel.dist(m, u);
        assert !Utils.greaterThan(arrivalTimeAtU, u.dueTime);  // this time feasibility condition should be checked before
        double startingServiceTimeAtU = Math.max(arrivalTimeAtU, u.readyTime);
        double oldStartingServiceTimeCustomerN = getStartingServiceTimeAt(p);
        double newArrivalTimeCustomerN = startingServiceTimeAtU + u.serviceTime + dataModel.dist(u, n);
        double newStartingServiceTimeCustomerN = Math.max(newArrivalTimeCustomerN, n.readyTime);
        double pushForward = newStartingServiceTimeCustomerN - oldStartingServiceTimeCustomerN;
        return pushForward;
    }

    /**
     * Return the starting service time at customer with index p in the routedPath
     */
    double getStartingServiceTimeAt(int p) {
        return Math.max(arrivalTimes.get(p), routedPath.get(p).readyTime);
    }

    Node getCustomerAt(int p) {
        return routedPath.get(p);
    }

    double getLatestArrivalTimeAtDepot() {
        return getArrivalTimeAt(arrivalTimes.size() - 1);
    }

    double getArrivalTimeAt(int p) {
        return arrivalTimes.get(p);
    }

    public int getLength() {
        return routedPath.size();
    }

    public int getNumDemandNodes() {
        return (int) routedPath.stream().filter(c -> c != depot).count();
    }

    public List<Node> getDemandNodes() {
        return routedPath.stream().filter(c -> c != depot).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return Arrays.toString(routedPath.toArray());
    }
}

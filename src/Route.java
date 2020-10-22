import java.util.*;

public class Route {
    DataModel dataModel;
    List<Node> routedPath;
    // Time that the truck arrives at customer i (correspond to ith Node in routedPath)
    // Note that this time can be different from service time, since service time = max(arrival time, ready time)
    List<Double> arrivalTimes;
    Node depot;
    // The total demand of all customers in the current trip (reset to 0 when returns to depot)
    // If routedPath = [depot, c1, c2, c3, depot, c4, c5, depot]
    // and demand = [0, 1, 2, 1, 0, 2, 3, 0]
    // Then vehicleLoadInCurTrip = [0, 4, 4, 4, 4, 5, 5, 5]
    List<Integer> vehicleLoadInCurTrip;

    public Route(DataModel dataModel, Node seed) {
        this.dataModel = dataModel;
        depot = dataModel.getDepot();
        vehicleLoadInCurTrip = new ArrayList<>(Arrays.asList(0, seed.demand, seed.demand));
        routedPath = new ArrayList<>(Arrays.asList(depot, seed, depot));
        initializeArrivalTimes(routedPath);
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
        initializeArrivalTimes(routedPath);

        // Initialize vehicle load in each trip
        vehicleLoadInCurTrip = new ArrayList<>();
        for (int i = 0; i < routedPath.size(); i++)
            vehicleLoadInCurTrip.add(0);

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
     * This method initialize the arrival time at each customer in the routedPath
     * and stores in arrivalTimes list.
     * It also checks (assert) for time feasibility of the path / route
     * (arrival time at each customer is before due time).
     */
    void initializeArrivalTimes(List<Node> routedPath) {
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
     * Insert the new customer u at position p in the route
     * and update the arrival time for all following customers
     * @param p index in the route to be inserted (0-based indexing)
     * @param u the new customer to be inserted
     */
    public void insertAtPosition(int p, Node u) {
        assert u != depot;  // there is a separate method addDummyDepot
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
        double arrivalTimeAtP = getStartingServiceTimeAt(p - 1) + routedPath.get(p - 1).serviceTime + dataModel.getTravelTime(routedPath.get(p - 1), u);
        arrivalTimes.add(p, arrivalTimeAtP);
        for (int i = p + 1; i < arrivalTimes.size(); i++) {
            double arrivalTimeAtI = getStartingServiceTimeAt(i - 1) + routedPath.get(i - 1).serviceTime + dataModel.getTravelTime(routedPath.get(i - 1), routedPath.get(i));
            assert arrivalTimeAtI <= routedPath.get(i).dueTime;
            arrivalTimes.set(i, arrivalTimeAtI);
        }
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

    public boolean canInsertAtPosition(int p, Node u) {
        return checkCapacityConstraint(p, u) && checkTimeConstraint(p, u);
    }

    boolean checkCapacityConstraint(int p, Node u) {
        return vehicleLoadInCurTrip.get(p) + u.demand <= dataModel.getVehicleCapacity();
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
        return checkPushForwardTimeFromNode(pushForward, p);
    }

    /**
     * Check if the time window constraints in the route from node p are all satisfied with a given
     * push forward time from the previous nodes.
     * @param pushForward the push forward time from previous nodes
     * @param p starting index to check (until end of route)
     */
    boolean checkPushForwardTimeFromNode(double pushForward, int p) {
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

    @Override
    public String toString() {
        return Arrays.toString(routedPath.toArray());
    }
}

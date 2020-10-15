import java.util.*;

public class Route {
    DataModel dataModel;
    List<Node> routedPath;
    Node depot;
    int totalDemand;

    public Route(DataModel dataModel, Node seed) {
        this.dataModel = dataModel;
        depot = dataModel.getDepot();
        totalDemand = 0;
        routedPath = new ArrayList<>(Arrays.asList(depot, seed, depot));
    }

    public void insertAtPosition(int position, Node node) {
        assert !node.equals(depot);
        routedPath.add(position, node);
        totalDemand += node.demand;
    }

    /**
     * Check if it's feasible to insert the customer between i(p-1) and ip
     * in the current route: (i0, ..., i(p-1), ip, ..., i0)
     * TODO: add push-forward concept Solomon (1987) to speedup the check.
     */
    public boolean canInsertAtPosition(int position, Node node) {
        if (totalDemand + node.demand > dataModel.getVehicleCapacity()) return false;
        double curTime = routedPath.get(0).readyTime; // arrival time at node i
        Double arrivalTime;
        for (int i = 1; i < position; i++) {
            arrivalTime = getArrivalTime(curTime, routedPath.get(i - 1), routedPath.get(i));
            if (arrivalTime == null) return false;  // violate time constraint
            curTime = arrivalTime;
        }

        arrivalTime = getArrivalTime(curTime, routedPath.get(position - 1), node);
        if (arrivalTime == null) return false;  // violate time constraint
        curTime = arrivalTime;

        for (int i = position; i < routedPath.size(); i++) {
            arrivalTime = getArrivalTime(curTime, routedPath.get(i - 1), routedPath.get(i));
            if (arrivalTime == null) return false;  // violate time constraint
            curTime = arrivalTime;
        }

        return true;
    }

    /**
     * Get the arrival time if going from s to t
     * Here we use the same metric for distance and time
     * (distance can be easily converted to time with a fixed vehicle speed)
     * @return arrival time at node d if not violate time constraint, else null
     */
    Double getArrivalTime(double sArrivalTime, Node s, Node d) {
        double dArrivalTime = Math.max(d.readyTime, sArrivalTime + s.serviceTime + dataModel.getDistance(s, d));
        return dArrivalTime > d.dueTime ? null : dArrivalTime;
    }

    public int getLength() {
        return routedPath.size();
    }

    public int getTotalDemand() {
        return totalDemand;
    }
}

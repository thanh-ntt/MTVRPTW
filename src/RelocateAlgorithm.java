import java.util.*;

public class RelocateAlgorithm implements LocalSearchAlgorithm {
    DataModel dataModel;
    @Override
    public List<Route> run(List<Route> solution, DataModel dataModel) {
        this.dataModel = dataModel;
        List<List<Route>> neighbourSolutions = new ArrayList<>();
        List<Integer> selectedRouteIndices = selectRouteIndices(solution);
        for (int idx : selectedRouteIndices) {
            // Use deep copy so that we can modify routes without changing the original solution
            List<Route> curSolution = Utils.deepCopySolution(solution);
            Route r1 = curSolution.remove(idx);  // selected route

            // Try exchange operator
            // For each customer in r1
            for (int p1 = 1; p1 < r1.getLength(); p1++) {
                if (r1.getCustomerAt(p1) != dataModel.getDepot()) {  // skip depot
                    // Find the route and position that is feasible and has min cost to exchange
                    double minCost = 1e9;  // find the exchange with minimum cost
                    int r2Idx = -1, p2Idx = -1;  // index of the route and position of the customer to exchange
                    for (int j = 0; j < curSolution.size(); j++) {  // iterate all remaining routes
                        Route r2 = curSolution.get(j);
                        for (int p2 = 1; p2 < r2.getLength(); p2++) {  // iterate all demand nodes in r2
                            if (r2.getCustomerAt(p2) != dataModel.getDepot()) {  // skip depot
                                if (Utils.checkExchangeOperator(dataModel, r1, p1, r2, p2)) {
                                    double cost = Utils.getCostExchangeOperator(dataModel, r1, p1, r2, p2);
                                    if (cost < minCost) {
                                        minCost = cost;
                                        r2Idx = j;
                                        p2Idx = p2;
                                    }
                                }
                            }
                        }
                    }
                    // Exchange operator
                    if (r2Idx != -1) {
                        Node u1 = r1.removeCustomerAtIndex(p1);
                        Route r2 = curSolution.get(r2Idx);
                        Node u2 = r2.removeCustomerAtIndex(p2Idx);

                        r1.insertAtPosition(p1, u2);
                        r2.insertAtPosition(p2Idx, u1);

                        assert Utils.isValidRoute(dataModel, r1) && Utils.isValidRoute(dataModel, r2);
                    }
                }
            }

            // Try relocate operator
            int p1 = 1;
            while (p1 < r1.getLength()) {  // for each customer in r1
                boolean inserted = false;
                Node u = r1.getCustomerAt(p1);
                if (r1.getCustomerAt(p1) != dataModel.getDepot()) {
                    // Try to insert u into the best position
                    double minCost = 1e9;
                    int r2Idx = -1, p2Idx = -1;  // index of the route and position of the customer to exchange
                    for (int j = 0; j < curSolution.size() && !inserted; j++) {
                        Route r2 = curSolution.get(j);
                        for (int p2 = 1; p2 < r2.getLength() && !inserted; p2++) {
                            if (r2.canInsertCustomerAt(p2, u)) {
                                r1.removeCustomerAtIndex(p1);
                                r2.insertAtPosition(p2, u);
                                inserted = true;  // once inserted (relocated), move to the next customer
                            }
                        }
                    }
                }
                if (!inserted) p1++;
            }

            // Add back selected route if needed
            if (!r1.getDemandNodes().isEmpty()) curSolution.add(r1);

            // Add to neighbourSolution
            neighbourSolutions.add(curSolution);
        }

        // Find best local move
        List<Route> bestNeighborhood = Collections.min(neighbourSolutions, (a, b) -> {
            if (a.size() != b.size()) return a.size() - b.size();
            else {  // compare shortest route length
                int shortestRouteLengthA = a.stream().min(Comparator.comparingInt(Route::getLength)).get().getLength();
                int shortestRouteLengthB = b.stream().min(Comparator.comparingInt(Route::getLength)).get().getLength();
                return shortestRouteLengthA - shortestRouteLengthB;
            }
        });

        assert Utils.isValidSolution(dataModel, bestNeighborhood);

        return bestNeighborhood;
    }

    public List<Integer> selectRouteIndices(List<Route> solution) {
        // TODO: select more clever
        List<Integer> selectedRouteIndices = new ArrayList<>();
        for (int i = 0; i < solution.size(); i++) {
            selectedRouteIndices.add(i);
        }
        return selectedRouteIndices;
    }
}

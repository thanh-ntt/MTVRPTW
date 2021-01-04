import java.util.*;

/**
 * Local search with built-in perturbation.
 * Perturbation: exchange operator
 * Local search: relocate operator
 */
public class RelocateAlgorithm {
    public static List<Route> run(List<Route> solution, DataModel dataModel) {
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
                    // Exchange depending on the acceptance criterion: first-feasible (0) / best-feasible (1)
                    if (dataModel.configs.exchangeOperatorAcceptanceCriterion == 0) {  // first-feasible
                        boolean exchanged = false;  // once exchanged, move to the next customer in r1
                        for (int j = 0; j < curSolution.size() && !exchanged; j++) {  // iterate all remaining routes
                            Route r2 = curSolution.get(j);
                            for (int p2 = 1; p2 < r2.getLength() && !exchanged; p2++) {  // iterate all demand nodes in r2
                                if (r2.getCustomerAt(p2) != dataModel.getDepot()) {  // skip depot
                                    if (Utils.checkExchangeOperator(dataModel, r1, p1, r2, p2)) {
                                        Node u1 = r1.removeCustomerAtIndex(p1);
                                        Node u2 = r2.removeCustomerAtIndex(p2);
                                        r1.insertAtPosition(p1, u2);
                                        r2.insertAtPosition(p2, u1);
                                        exchanged = true;
                                    }
                                }
                            }
                        }
                    } else {  // 1 - best-feasible
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
            }

            // Try relocate operator
            // Relocate depending on the acceptance criterion: first-feasible (0) / best-feasible (1)
            if (dataModel.configs.relocateOperatorAcceptanceCriterion == 0) {  // first-feasible
                int p1 = 1;
                while (p1 < r1.getLength()) {  // for each customer in r1
                    boolean inserted = false;  // once inserted, move to the next customer in r1
                    Node u = r1.getCustomerAt(p1);
                    if (r1.getCustomerAt(p1) != dataModel.getDepot()) {
                        // Try to insert u into the first feasible position
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
            } else {  // 1 - best-feasible
                int p1 = 1;
                while (p1 < r1.getLength()) {  // for each customer in r1
                    Node u = r1.getCustomerAt(p1);
                    if (r1.getCustomerAt(p1) != dataModel.getDepot()) {
                        // Try to insert u into the best position
                        double minCost = 1e9;
                        int r2Idx = -1, p2Idx = -1;  // index of the route and position of the customer to exchange
                        for (int j = 0; j < curSolution.size(); j++) {
                            Route r2 = curSolution.get(j);
                            for (int p2 = 1; p2 < r2.getLength(); p2++) {
                                if (r2.canInsertCustomerAt(p2, u)) {
                                    double cost = Utils.getCostRelocateOperator(dataModel, r1, p1, r2, p2);
                                    if (cost < minCost) {
                                        minCost = cost;
                                        r2Idx = j;
                                        p2Idx = p2;
                                    }
                                }
                            }
                        }
                        if (r2Idx != -1) {
                            Node u1 = r1.removeCustomerAtIndex(p1);
                            assert u1 == u;
                            Route r2 = curSolution.get(r2Idx);
                            r2.insertAtPosition(p2Idx, u1);

                            assert Utils.isValidRoute(dataModel, r2);
                            p1--;  // Decrease p1 since already remove customer at p1
                        }
                    }
                    p1++;
                }
            }

            // Add back selected route if needed
            if (!r1.getDemandNodes().isEmpty()) curSolution.add(r1);

            // Add to neighbourSolution
            neighbourSolutions.add(curSolution);
        }

        // Find best local move
        // TODO: find better heuristic to approximate search neighbor
        List<Route> bestNeighborhood = Collections.min(neighbourSolutions, (a, b) -> {
            if (a.size() != b.size()) return a.size() - b.size();
//            else return Utils.compareTotalDistance(dataModel, a, b);
            else {  // compare shortest route length
                int shortestRouteLengthA = a.stream().min(Comparator.comparingInt(Route::getLength)).get().getLength();
                int shortestRouteLengthB = b.stream().min(Comparator.comparingInt(Route::getLength)).get().getLength();
                return shortestRouteLengthA - shortestRouteLengthB;
            }
        });

        assert Utils.isValidSolution(dataModel, bestNeighborhood);

        return bestNeighborhood;
    }

    public static List<Integer> selectRouteIndices(List<Route> solution) {
        // TODO: select more clever
//        double averageRouteLength = solution.stream().mapToInt(r -> r.getLength()).average().getAsDouble();
        List<Integer> selectedRouteIndices = new ArrayList<>();
        for (int i = 0; i < solution.size(); i++) {
//            if (solution.get(i).getLength() <= averageRouteLength) {
                selectedRouteIndices.add(i);
//            }
        }
        return selectedRouteIndices;
    }
}

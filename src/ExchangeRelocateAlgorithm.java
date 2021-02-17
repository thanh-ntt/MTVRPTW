import java.util.*;

/**
 * Local search with built-in perturbation.
 * Perturbation: exchange operator
 * Local search: relocate operator
 */
public class ExchangeRelocateAlgorithm {
    public static List<Route> run(List<Route> solution, DataModel dataModel, Parameter parameter) {
        List<List<Route>> neighbourSolutions = new ArrayList<>();
        List<Integer> selectedRouteIndices = selectRouteIndices(solution);
        for (int r1Idx : selectedRouteIndices) {
            // Use deep copy so that we can modify routes without changing the original solution
            List<Route> curSolution = Utils.deepCopySolution(solution);
            Route r1 = curSolution.get(r1Idx);  // selected route

            // Try exchange - relocate
            // For each customer in r1
            int p1 = 1;
            while (p1 < r1.getLength()) {  // for each customer in r1
                if (r1.getCustomerAt(p1) == dataModel.getDepot()) continue;  // skip depot
                boolean exchangeRelocated = false;
                outerLoop:
                for (int r2Idx = 0; r2Idx < curSolution.size(); r2Idx++) {
                    if (r2Idx == r1Idx) continue;
                    Route r2 = curSolution.get(r2Idx);
                    for (int p2 = 1; p2 < r2.getLength(); p2++) {
                        if (r2.getCustomerAt(p2) == dataModel.getDepot()) continue;  // skip depot
                        if (Utils.checkExchangeOperator(dataModel, r1, p1, r2, p2)) {
                            // Can exchange customer at p1 and p2 in 2 routes r1, r2
                            // Now try if we can insert customer at p2 (of r2) into another route
                            // Final result: u1 insert into p2 of r2, u2 insert into p3 of r3, r1 length decrease
                            Node u1 = r1.getCustomerAt(p1), u2 = r2.getCustomerAt(p2);
                            for (int r3Idx = 0; r3Idx < curSolution.size(); r3Idx++) {
                                if (r3Idx == r2Idx || r3Idx == r1Idx) continue;
                                Route r3 = curSolution.get(r3Idx);
                                for (int p3 = 1; p3 < r3.getLength(); p3++) {
                                    if (r3.canInsertCustomerAt(p3, u2)) {
                                        r2.removeCustomerAtIndex(p2);  // remove u2 from r2
                                        r2.insertAtPosition(p2, u1);  // insert u1 into r2
                                        r3.insertAtPosition(p3, u2);  // insert u2 into r3
                                        r1.removeCustomerAtIndex(p1);  // remove u1 from r1
                                        exchangeRelocated = true;
                                        break outerLoop;
                                    }
                                }
                            }
                        }
                    }
                }
                if (!exchangeRelocated) {  // if have exchange, relocated, no need to increase p1 counter
                    p1++;
                }
            }

            if (r1.getNumDemandNodes() == 0) {
                curSolution.remove(r1Idx);
            }

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


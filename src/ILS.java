import jdk.jshell.execution.Util;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Iterated local search for the MTVRPTW.
 */
public class ILS implements ConstructionAlgorithm {
    DataModel dataModel;
    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
//        List<Route> initialSolution = new SolomonI1Algorithm().run(dataModel);
//        List<Route> initialSolution = new ChangsAlgorithm().run(dataModel);
//        List<Route> bestSolution = initialSolution;

//        for (Parameter parameter : PARAMETERS) {
//            List<Route> curSolution = runWithParameter(dataModel, initialSolution, parameter);
//            if (curSolution.size() < bestSolution.size()) {
//                bestSolution = curSolution;
//            }
//        }
//        return bestSolution;
        List<Route> initialSolution = new SolomonI1Algorithm().run(dataModel);
        List<Route> curSolution = initialSolution;
//        curSolution = OrOptAlgorithm.run(curSolution, dataModel);
//        int numIterations = 0;
//        while (numIterations++ < 100) {
//            List<Route> nextSolution = RelocateAlgorithm.run(curSolution, dataModel, new Parameter());
//            curSolution = nextSolution;  // accept all
//        }
//        return curSolution;

//        // While termination condition not satisfied
        int totalIterations = 0, maxNumIterations = 1000, maxNumIntensification = 1;
        while (totalIterations < maxNumIterations) {
            int i = 0;
            while (i++ < maxNumIntensification && totalIterations++ < maxNumIterations) {
                List<Route> nextSolution = RelocateAlgorithm.run(curSolution, dataModel);
                if (nextSolution.size() < curSolution.size()) {
                    i = 0;
                }
                curSolution = nextSolution;  // accept all
            }
            // Perturbation
            curSolution = runPerturbation(curSolution);
            curSolution = OrOptAlgorithm.run(curSolution, dataModel);
        }
        return curSolution;
    }

    int getShortestRouteLength(List<Route> solution) {
        return solution.stream().mapToInt(Route::getLength).min().getAsInt();
    }
//
//    List<Route> runPerturbation(List<Route> s1) {
//        List<Route> s2 = Utils.deepCopySolution(s1);
//        // Find shortest route
//        Route shortestRoute = null;
//        int shortestRouteLength = (int) 1e9;
//        for (Route r : s2) {
//            if (r.getLength() < shortestRouteLength) {
//                shortestRouteLength = r.getLength();
//                shortestRoute = r;
//            }
//        }
//        // Re-route all other routes
//        s2.remove(shortestRoute);
//        Set<Node> customers = Utils.getRoutedCustomers(s2);
//
//        s2 = new SolomonI1Algorithm().runAllInitializationCriteria(dataModel, customers);
//        if (s2.size() + 1 > s1.size()) return s1;
//        s2.add(shortestRoute);
//        assert Utils.isValidSolution(dataModel, s2);
//        return s2;
//    }

    // TODO: bound this perturbation scheme, currently most random exchange fails
    List<Route> runPerturbation(List<Route> s) {
        int n = s.size();
        Random random = new Random(0);
        int numExchangeThreshold = 100;
        int count = 0;
        while (count < numExchangeThreshold) {
            int r1Idx = random.nextInt(n), r2Idx = random.nextInt(n);
            if (r1Idx == r2Idx) continue;
            Route r1 = s.get(r1Idx), r2 = s.get(r2Idx);
            int p1 = random.nextInt(r1.getLength()), p2 = random.nextInt(r2.getLength());
            Node u1 = r1.getCustomerAt(p1), u2 = r2.getCustomerAt(p2);
            if (u1 == dataModel.getDepot() || u2 == dataModel.getDepot()) continue;
            if (Utils.checkExchangeOperator(dataModel, r1, p1, r2, p2)) {
                r1.removeCustomerAtIndex(p1);
                r2.removeCustomerAtIndex(p2);
                r1.insertAtPosition(p1, u2);
                r2.insertAtPosition(p2, u1);
                count++;
            }
        }
        return s;
    }

    List<Route> getBetterSolution(List<Route> s1, List<Route> s2) {
        if (s1.size() < s2.size()) return s1;
        else if (s1.size() > s2.size()) return s2;
        else {
            int shortestRouteS1 = s1.stream().mapToInt(Route::getLength).min().getAsInt();
            int shortestRouteS2 = s2.stream().mapToInt(Route::getLength).min().getAsInt();
            if (shortestRouteS1 < shortestRouteS2) return s1;
            else return s2;
        }
    }
}

import jdk.jshell.execution.Util;

import java.util.List;
import java.util.Set;

/**
 * Iterated local search for the MTVRPTW.
 */
public class ILS implements ConstructionAlgorithm {
    DataModel dataModel;
    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<Route> initialSolution = new SolomonI1Algorithm().run(dataModel);
//        List<Route> initialSolution = new ChangsAlgorithm().run(dataModel);
        List<Route> bestSolution = initialSolution;
        List<Route> curSolution = initialSolution;

        int numIterations = 0;
        while (numIterations++ < 100) {
            List<Route> nextSolution = RelocateAlgorithm.run(curSolution, dataModel);
            curSolution = nextSolution;  // accept all
        }

//        // While termination condition not satisfied
//        int totalIterations = 0;
//        while (totalIterations < 100) {
//            int i = 0;
//            while (i++ < 10 && totalIterations < 100) {
//                totalIterations++;
//                List<Route> nextSolution = RelocateAlgorithm.run(curSolution, dataModel);
//                if (nextSolution.size() < curSolution.size()
//                        || getShortestRouteLength(nextSolution) < getShortestRouteLength(curSolution)) {
//                    curSolution = nextSolution;
//                    i = 0;
//                }
//            }
//            // Perturbation
//            curSolution = runPerturbation(curSolution);
//        }
        return curSolution;
    }

    int getShortestRouteLength(List<Route> solution) {
        return solution.stream().mapToInt(Route::getLength).min().getAsInt();
    }

    List<Route> runPerturbation(List<Route> s1) {
        List<Route> s2 = Utils.deepCopySolution(s1);
        // Find shortest route
        Route shortestRoute = null;
        int shortestRouteLength = (int) 1e9;
        for (Route r : s2) {
            if (r.getLength() < shortestRouteLength) {
                shortestRouteLength = r.getLength();
                shortestRoute = r;
            }
        }
        // Re-route all other routes
        s2.remove(shortestRoute);
        Set<Node> customers = Utils.getRoutedCustomers(s2);

        s2 = new SolomonI1Algorithm().runAllInitializationCriteria(dataModel, customers);
        if (s2.size() + 1 > s1.size()) return s1;
        s2.add(shortestRoute);
        assert Utils.isValidSolution(dataModel, s2);
        return s2;
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

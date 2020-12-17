import java.util.List;

/**
 * Iterated local search for the MTVRPTW.
 */
public class ILS implements ConstructionAlgorithm {
    DataModel dataModel;
    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<Route> initialSolution = new SolomonI1Algorithm().run(dataModel);
        List<Route> bestSolution = initialSolution;
        List<Route> curSolution = initialSolution;

        // While termination condition not satisfied
        int numIterations = 0;
        while (numIterations++ < 20) {
            List<Route> nextSolution = RelocateAlgorithm.run(curSolution, dataModel);
            curSolution = nextSolution;  // accept all
        }
        return curSolution;
    }
}

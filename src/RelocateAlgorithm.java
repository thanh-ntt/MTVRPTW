import java.util.ArrayList;
import java.util.List;

public class RerouteRelocateAlgorithm implements LocalSearchAlgorithm {
    DataModel dataModel;
    @Override
    public List<Route> run(List<Route> solution, DataModel dataModel) {
        this.dataModel = dataModel;
        List<Route> selectedRoutes = selectRoutes(solution);
        for (Route selectedRoute : selectedRoutes) {
            // Use shallow copy of the solution so that we can remove the selectedRoute effectively
            List<Route> copy = new ArrayList<>(solution);
            copy.remove(selectedRoute);
            // Use deep copy so that we can modify the routes without affecting original solution
            List<Route> curSolution = Utils.deepCopySolution(copy);


        }
    }

    public List<Route> selectRoutes(List<Route> solution) {
        // TODO: select more clever
        return new ArrayList<>(solution);
    }
}

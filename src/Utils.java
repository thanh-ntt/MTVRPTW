import java.util.*;

public class Utils {
    public static String getRouteStats(Route route) {
        StringBuilder sb = new StringBuilder();
        sb.append("Path: " + Arrays.toString(route.routedPath.toArray()) + "\n");
        sb.append("Arrival time: " + Arrays.toString(route.arrivalTimes.toArray()) + "\n");
        return sb.toString();
    }
}

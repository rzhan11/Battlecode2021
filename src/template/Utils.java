package template;

import battlecode.common.*;

import java.util.Comparator;

import static template.Robot.*;
import static template.Nav.*;

public class Utils {

    public static boolean inArray(Object[] arr, Object item, int length) {
        for(int i = length; --i >= 0;) {
            if(arr[i].equals(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean inArray(int[] arr, int item, int length) {
        for(int i = length; --i >= 0;) {
            if(arr[i] == item) {
                return true;
            }
        }
        return false;
    }

    public static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    public static void swap(Object[] arr, int i, int j) {
        Object temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    public static void delete(int[] arr, int i, int len) {
        arr[i] = arr[len - 1];
    }

    public static void delete(Object[] arr, int i, int len) {
        arr[i] = arr[len - 1];
    }

    public static Direction getRandomDir() {
        return DIRS[(int)(Math.random() * 8)];
    }

    /*
    Returns the robot in 'arr' that is closest to 'loc'
     */
    public static RobotInfo getClosest(MapLocation loc, RobotInfo[] arr, int len) {
        RobotInfo bestRobot = null;
        int bestDist = P_INF;
        for (int i = len; --i >= 0;) {
            RobotInfo ri = arr[i];
            int dist = loc.distanceSquaredTo(ri.location);
            if (dist < bestDist) {
                bestRobot = ri;
                bestDist = dist;
            }
        }
        return bestRobot;
    }

    public static MapLocation getClosest(MapLocation loc, MapLocation[] arr, int len) {
        MapLocation bestLoc = null;
        int bestDist = P_INF;
        for (int i = len; --i >= 0;) {
            MapLocation ri = arr[i];
            int dist = loc.distanceSquaredTo(ri);
            if (dist < bestDist) {
                bestLoc = ri;
                bestDist = dist;
            }
        }
        return bestLoc;
    }
}

class RobotCompare implements Comparator<RobotInfo> {
    public int compare(RobotInfo ri1, RobotInfo ri2) {
        return ri1.getID() - ri2.getID();
    }
}
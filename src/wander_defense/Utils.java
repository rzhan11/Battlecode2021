package wander_defense;

import battlecode.common.*;

import static wander_defense.Debug.*;
import static wander_defense.Robot.*;
import static wander_defense.Map.*;
import static wander_defense.Nav.*;

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

    public static Direction getDir(int dx, int dy) {
        return new MapLocation(0, 0).directionTo(new MapLocation(dx, dy));
    }

    public static Direction getDir(double dx, double dy) {
        return new MapLocation(0, 0).directionTo(new MapLocation((int) (100 * dx), (int) (100 * dy)));
    }

    public static Direction getRandomDir() {
        return DIRS[(int)(Math.random() * 8)];
    }

    public static Direction getRandomDirCenter() {
        return ALL_DIRS[(int)(Math.random() * 9)];
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

    public static int getMaxSurround(MapLocation loc, int offset) {
        // these are closest x/y distance to map edges
        int dx = P_INF;
        int dy = P_INF;
        if (XMIN != -1) {
            dx = Math.min(dx, loc.x - XMIN);
        }
        if (XMAX != -1) {
            dx = Math.min(dx, XMAX - loc.x);
        }
        if (YMIN != -1) {
            dy = Math.min(dy, loc.y - YMIN);
        }
        if (YMAX != -1) {
            dy = Math.min(dy, YMAX - loc.y);
        }

        // guarantees dx <= dy
        if (dx > dy) {
            int temp = dx;
            dx = dy;
            dy = temp;
        }

        switch(offset) {
            case 1:
                if (dy == 0) {
                    return 3; // 0, 0
                } else {
                    if (dx == 0) {
                        return 5; // 0, 1+
                    } else {
                        return 8; // default, 1+, 1+
                    }
                }

            case 2:
                if (dy == 0) {
                    return 8; // 0, 0
                } else if (dy == 1) { // dx <= 1
                    if (dx == 0) {
                        return 11; // 0, 1
                    } else {
                        return 15; // 1, 1
                    }
                } else {
                    switch(dx) {
                        case 0: return 14; // 0, 2
                        case 1: return 19; // 1, 2
                        default: return 24; // default, 2+, 2+
                    }
                }

            default:
                logi("WARNING: Tried to get max surround of unknown offset " + offset);
                return -1;
        }
    }
}
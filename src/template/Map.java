package template;

import battlecode.common.*;

import static template.Comms.*;
import static template.Debug.*;
import static template.HardCode.*;
import static template.Robot.*;

public class Map {
    public static int XMIN = -1;
    public static int YMIN = -1;
    public static int XMAX = -1;
    public static int YMAX = -1;
    public static int XLEN = -1;
    public static int YLEN = -1;

    public static MapLocation addDir(MapLocation loc, Direction dir, int len) {
        return loc.translate(dir.dx * len, dir.dy * len);
    }

    public static MapLocation convertToKnownBounds(MapLocation loc) {
        int x = loc.x;
        int y = loc.y;
        if (XMIN != -1 && x < XMIN) {
            x = XMIN;
        }
        if (XMAX != -1 && x > XMAX) {
            x = XMAX;
        }
        if (YMIN != -1 && y < YMIN) {
            y = YMIN;
        }
        if (YMAX != -1 && y > YMAX) {
            y = YMAX;
        }
        return new MapLocation(x, y);
    }

    public static boolean inKnownBounds(MapLocation loc) {
        if (XMIN != -1 && loc.x < XMIN) {
            return false;
        }
        if (XMAX != -1 && loc.x > XMAX) {
            return false;
        }
        if (YMIN != -1 && loc.y < YMAX) {
            return false;
        }
        if (YMAX != -1 && loc.y > YMAX) {
            return false;
        }
        return true;
    }

    public static void updateMapBounds() throws GameActionException {
        int maxRadius = (int) Math.sqrt(mySensorRadius);
        boolean notHQ = (myType != RobotType.ENLIGHTENMENT_CENTER);
        MapLocation loc;

        if (XMIN == -1) {
            loc = here.translate(-maxRadius, 0);
            if (!rc.onTheMap(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(-i, 0);
                    if (rc.onTheMap(loc)) {
                        XMIN = loc.x;
                        if (notHQ) {
                            writeXBounds();
                        }
                        if (XMAX != -1) {
                            XLEN = XMAX - XMIN + 1;
                        }
                        break;
                    }
                }
            }
        }
        if (YMIN == -1) {
            loc = here.translate(0, -maxRadius);
            if (!rc.onTheMap(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(0, -i);
                    if (rc.onTheMap(loc)) {
                        YMIN = loc.y;
                        if (notHQ) {
                            writeYBounds();
                        }
                        if (YMAX != -1) {
                            YLEN = YMAX - YMIN + 1;
                        }
                        break;
                    }
                }
            }
        }
        if (XMAX == -1) {
            loc = here.translate(maxRadius, 0);
            if (!rc.onTheMap(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(i, 0);
                    if (rc.onTheMap(loc)) {
                        XMAX = loc.x;
                        if (notHQ) {
                            writeXBounds();
                        }
                        if (XMIN != -1) {
                            XLEN = XMAX - XMIN + 1;
                        }
                        break;
                    }
                }
            }
        }
        if (YMAX == -1) {
            loc = here.translate(0, maxRadius);
            if (!rc.onTheMap(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(0, i);
                    if (rc.onTheMap(loc)) {
                        YMAX = loc.y;
                        if (notHQ) {
                            writeYBounds();
                        }
                        if (YMIN != -1) {
                            YLEN = YMAX - YMIN + 1;
                        }
                        break;
                    }
                }
            }
        }
    }

    public static void reportMapBounds() {

    }

    public static int dir2int(Direction dir) {
        switch (dir) {
            case NORTH:
                return 0;
            case NORTHEAST:
                return 1;
            case EAST:
                return 2;
            case SOUTHEAST:
                return 3;
            case SOUTH:
                return 4;
            case SOUTHWEST:
                return 5;
            case WEST:
                return 6;
            case NORTHWEST:
                return 7;
            case CENTER:
                return 8;
            default:
                logi("ERROR: Sanity check failed in 'dir2int'");
                return -1;
        }
    }

    /*
    Does not support dir = CENTER
     */
    public static Direction[] getClosestDirs(Direction dir) {
        return HardCode.CLOSEST_DIRS[dir2int(dir)];
    }
}

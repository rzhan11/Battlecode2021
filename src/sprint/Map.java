package sprint;

import battlecode.common.*;

import static sprint.Comms.*;
import static sprint.Debug.*;
import static sprint.HardCode.*;
import static sprint.Robot.*;

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

    public static MapLocation processExploreLoc(MapLocation loc) {
        int radius;
        switch(myType) {
            case MUCKRAKER:
            case POLITICIAN:
            case SLANDERER:
                radius = 3;
                break;
            default:
                radius = 0;
                break;
        }
        return convertToKnownBounds(loc, radius);
    }

    public static MapLocation convertToKnownBounds(MapLocation loc, int offset) {
        int x = loc.x;
        int y = loc.y;
        if (XMIN != -1 && x < XMIN + offset) {
            x = XMIN + offset;
        }
        if (XMAX != -1 && x > XMAX - offset) {
            x = XMAX - offset;
        }
        if (YMIN != -1 && y < YMIN + offset) {
            y = YMIN + offset;
        }
        if (YMAX != -1 && y > YMAX - offset) {
            y = YMAX - offset;
        }
        return new MapLocation(x, y);
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

    public static boolean isMapKnown() {
        return XMIN != -1 && XMAX != -1 && YMIN != -1 && YMAX != -1;
    }
    public static boolean isMapXKnown() {
        return XMIN != -1 && XMAX != -1;
    }
    public static boolean isMapYKnown() {
        return YMIN != -1 && YMAX != -1;
    }

    public static void updateMapBounds() throws GameActionException {
        int maxRadius = (int) Math.sqrt(myDetectionRadius);
        boolean notHQ = (myType != RobotType.ENLIGHTENMENT_CENTER);
        MapLocation loc;

        if (XMIN == -1) {
            loc = here.translate(-maxRadius, 0);
            if (!rc.canDetectLocation(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(-i, 0);
                    if (rc.canDetectLocation(loc)) {
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
            if (!rc.canDetectLocation(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(0, -i);
                    if (rc.canDetectLocation(loc)) {
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
            if (!rc.canDetectLocation(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(i, 0);
                    if (rc.canDetectLocation(loc)) {
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
            if (!rc.canDetectLocation(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(0, i);
                    if (rc.canDetectLocation(loc)) {
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

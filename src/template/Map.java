package template;

import battlecode.common.*;

import static template.Robot.*;
import static template.Debug.*;
import static template.HardCode.*;

public class Map {
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
        if (YMIN != -1 && y < YMAX) {
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
        MapLocation loc;

        if (XMIN == -1) {
            loc = here.translate(-maxRadius, 0);
            if (!rc.onTheMap(loc)) {
                for (int i = maxRadius - 1; i >= 1; i--) {
                    loc = here.translate(-i, 0);
                    if (rc.onTheMap(loc)) {
                        XMIN = loc.x;
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
                        if (YMIN != -1) {
                            YLEN = YMAX - YMIN + 1;
                        }
                        break;
                    }
                }
            }
        }
    }

    public static void updateIsDirMoveable() throws GameActionException {
        // add information about if direction is moveable
        for (int i = 0; i < DIRS.length; i++) {
            MapLocation adjLoc = rc.adjacentLocation(DIRS[i]);
            isDirMoveable[i] = rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc);
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
                return 0;
        }
    }

    public static Direction[] getClosestDirs(Direction dir) {
        switch (dir) {
            case NORTH:
                return HardCode.CLOSEST_DIRS[0];
            case NORTHEAST:
                return HardCode.CLOSEST_DIRS[1];
            case EAST:
                return HardCode.CLOSEST_DIRS[2];
            case SOUTHEAST:
                return HardCode.CLOSEST_DIRS[3];
            case SOUTH:
                return HardCode.CLOSEST_DIRS[4];
            case SOUTHWEST:
                return HardCode.CLOSEST_DIRS[5];
            case WEST:
                return HardCode.CLOSEST_DIRS[6];
            case NORTHWEST:
                return HardCode.CLOSEST_DIRS[7];
            default: // only triggered if 'dir' == CENTER
                return DIRS;
        }
    }

}

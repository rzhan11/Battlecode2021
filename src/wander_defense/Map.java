package wander_defense;

import battlecode.common.*;

import static wander_defense.Comms.*;
import static wander_defense.CommManager.*;
import static wander_defense.Debug.*;
import static wander_defense.HQTracker.*;
import static wander_defense.Robot.*;
import static wander_defense.Utils.*;

public class Map {
    public static int XMIN = -1;
    public static int YMIN = -1;
    public static int XMAX = -1;
    public static int YMAX = -1;

    public static boolean notHSymmetry = false;
    public static boolean notVSymmetry = false;
    public static boolean notRSymmetry = false;

    public static Symmetry theSymmetry = null;

    public static Message symmetryBroadcast;

    public static MapLocation addDir(MapLocation loc, Direction dir, int len) {
        return loc.translate(dir.dx * len, dir.dy * len);
    }

    public static MapLocation getExploreNavLoc(MapLocation loc) {
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

    public static MapLocation getFarthestLoc(MapLocation loc, Direction dir) {
        MapLocation temp = addDir(loc, dir, MAX_MAP_SIZE);
        int x = temp.x;
        int y = temp.y;
        if (XMIN != -1 && x < XMIN) {
            y -= (XMIN - x) * dir.dy;
            x = XMIN;
        }
        if (XMAX != -1 && x > XMAX) {
            y -= (x - XMAX) * dir.dy;
            x = XMAX;
        }
        if (YMIN != -1 && y < YMIN) {
            x -= (YMIN - y) * dir.dx;
            y = YMIN;
        }
        if (YMAX != -1 && y > YMAX) {
            x -= (y - YMAX) * dir.dx;
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
                        break;
                    }
                }
            }
        }
    }

    public static Direction getNewBoundsTaskDir() {
        if (isMapKnown()) {
            return null;
        }

        int dx = 0;
        if (XMIN == -1) {
            if (XMAX == -1) {
                dx = (Math.random() < 0.5) ? -1 : 1; // randomly pick left/right
            } else {
                dx = -1;
            }
        } else if (XMAX == -1) {
            dx = 1;
        }

        int dy = 0;
        if (YMIN == -1) {
            if (YMAX == -1) {
                dy = (Math.random() < 0.5) ? -1 : 1; // randomly pick left/right
            } else {
                dy = -1;
            }
        } else if (YMAX == -1) {
            dy = 1;
        }
        return getDir(dx, dy);
    }

    public static void printSymmetry() {
        if (theSymmetry != null) {
            log("SYM " + theSymmetry);
        } else {
            log("SYM: " + (notHSymmetry?1:0) + (notVSymmetry?1:0) + (notRSymmetry?1:0));
        }
    }

    public static void updateSymmetryBroadcast() throws GameActionException {
        if (!notHSymmetry && !notVSymmetry && !notRSymmetry) {
            return;
        }

        if (symmetryBroadcast == null) {
            symmetryBroadcast = new Message(SYMMETRY_MSG, 0, true);
            queueMessage(symmetryBroadcast);
        }

        int info = 0;
        if (notHSymmetry) {
            info += 1;
        }
        if (notVSymmetry) {
            info += 2;
        }
        if (notRSymmetry) {
            info += 4;
        }
        symmetryBroadcast.info = info;
    }

    public static void updateTheSymmetry() {
        if (theSymmetry != null) {
            return;
        }
        if (notHSymmetry && notVSymmetry) {
            theSymmetry = Symmetry.R;
        }
        else if (notHSymmetry && notRSymmetry) {
            theSymmetry = Symmetry.V;
        }
        else if (notVSymmetry && notRSymmetry) {
            theSymmetry = Symmetry.H;
        }
    }

    public static void updateSymmetryByHQ() throws GameActionException {
        if (theSymmetry != null) {
            return;
        }

        boolean changed = false;

        // check H
        if (isMapXKnown() && !notHSymmetry) {
            for (int i = knownHQCount; --i >= 0;) {
                MapLocation symLoc = getSymmetricLocation(hqLocs[i], Symmetry.H);
                if (rc.canSenseLocation(symLoc) && !inArray(hqLocs, symLoc, knownHQCount)) {
                    // if we can sense it by location, then we should have added it to hqLocs
                    log("[NOT H]");
                    notHSymmetry = true;
                    changed = true;
                    break;
                }
            }
        }
        // check V
        if (isMapYKnown() && !notVSymmetry) {
            for (int i = knownHQCount; --i >= 0;) {
                MapLocation symLoc = getSymmetricLocation(hqLocs[i], Symmetry.V);
                if (rc.canSenseLocation(symLoc) && !inArray(hqLocs, symLoc, knownHQCount)) {
                    log("[NOT V]");
                    notVSymmetry = true;
                    changed = true;
                    break;
                }
            }
        }
        // check R
        if (isMapKnown() && !notRSymmetry) {
            for (int i = knownHQCount; --i >= 0;) {
                MapLocation symLoc = getSymmetricLocation(hqLocs[i], Symmetry.R);
                if (rc.canSenseLocation(symLoc) && !inArray(hqLocs, symLoc, knownHQCount)) {
                    log("[NOT R]");
                    notRSymmetry = true;
                    changed = true;
                    break;
                }
            }
        }

        if (changed) {
            writeSymmetry(false);
        }

        updateTheSymmetry();
    }

    public static void updateSymmetryByPassability() throws GameActionException {
        if (theSymmetry != null) {
            return;
        }

        int[][] relLocs;
        switch(myType) {
            case POLITICIAN:
                relLocs = HardCode.BOX_EDGES;
                break;
            case MUCKRAKER:
            case SLANDERER:
            case ENLIGHTENMENT_CENTER:
            default:
                logi("WARNING: Tried to updateSymmetryByPassability for unsupported type " + myType);
                return;
        }

        here = rc.getLocation();

        boolean willCheckH = isMapXKnown() && !notHSymmetry;
        boolean willCheckV = isMapYKnown() && !notVSymmetry;
        boolean willCheckR = isMapKnown() && !notRSymmetry;

        int symCount = (willCheckH?1:0) + (willCheckV?1:0) + (willCheckR?1:0);
        if (symCount == 0) {
            return;
        }

        boolean changed = false;

        if (willCheckH) {
            for (int i = relLocs.length; --i >= 0;) {
                MapLocation loc = here.translate(relLocs[i][0], relLocs[i][1]);
                if (checkSymmetryWrong(loc, getSymmetricLocation(loc, Symmetry.H))) {
                    notHSymmetry = true;
                    changed = true;
                    break;
                }
            }
        }

        if (willCheckV) {
            for (int i = relLocs.length; --i >= 0;) {
                MapLocation loc = here.translate(relLocs[i][0], relLocs[i][1]);
                if (checkSymmetryWrong(loc, getSymmetricLocation(loc, Symmetry.V))) {
                    notVSymmetry = true;
                    changed = true;
                    break;
                }
            }
        }

        if (willCheckR) {
            for (int i = relLocs.length; --i >= 0;) {
                MapLocation loc = here.translate(relLocs[i][0], relLocs[i][1]);
                if (checkSymmetryWrong(loc, getSymmetricLocation(loc, Symmetry.R))) {
                    notRSymmetry = true;
                    changed = true;
                    break;
                }
            }
        }

        if (changed) {
            writeSymmetry(false);
        }

        updateTheSymmetry();
    }

    // returns true if this DISPROVES symmetry
    // returns false if it confirms symmetry or if test cannot be performed
    public static boolean checkSymmetryWrong(MapLocation loc1, MapLocation loc2) throws GameActionException {
        if (!rc.canSenseLocation(loc1) || !rc.canSenseLocation(loc2)) {
            return false;
        }
        return rc.sensePassability(loc1) != rc.sensePassability(loc2);
    }

    public static MapLocation getSymmetricLocation(MapLocation loc, Symmetry sym) {
        switch(sym) {
            case H:
                return new MapLocation(XMIN + XMAX - loc.x, loc.y);
            case V:
                return new MapLocation(loc.x, YMIN + YMAX - loc.y);
            case R:
                return new MapLocation(XMIN + XMAX - loc.x, YMIN + YMAX - loc.y);
            default:
                return null;
        }
    }

    public static void updateSymHQLocs() throws GameActionException {
        symHQCount = 0;

        // early exit
        if (knownHQCount == MAX_HQ_COUNT) {
            return;
        }

        // if the symmetry is known, use it to find new locations
        if (theSymmetry != null) {
            switch(theSymmetry) {
                case H:
                    if (isMapXKnown()) {
                        findHQLocFromSymmetry();
                    }
                    break;
                case V:
                    if (isMapYKnown()) {
                        findHQLocFromSymmetry();
                    }
                    break;
                case R:
                    if (isMapKnown()) {
                        findHQLocFromSymmetry();
                    }
                    break;
            }
            return;
        }

        // if the symmetry is not know, try to determine potential hq locations

        if (!notHSymmetry && isMapXKnown()) {
            for (int i = knownHQCount; --i >= 0;) {
                MapLocation loc = hqLocs[i];
                MapLocation symLoc = getSymmetricLocation(loc, Symmetry.H);
                if (!inArray(hqLocs, symLoc, knownHQCount)) {
                    symHQLocs[symHQCount] = symLoc;
                    symHQType[symHQCount] = Symmetry.H;
                    symHQCount++;
                }
            }
        }
        if (!notVSymmetry && isMapYKnown()) {
            for (int i = knownHQCount; --i >= 0;) {
                MapLocation loc = hqLocs[i];
                MapLocation symLoc = getSymmetricLocation(loc, Symmetry.V);
                if (!inArray(hqLocs, symLoc, knownHQCount)) {
                    symHQLocs[symHQCount] = symLoc;
                    symHQType[symHQCount] = Symmetry.V;
                    symHQCount++;
                }
            }
        }
        if (!notRSymmetry && isMapKnown()) {
            for (int i = knownHQCount; --i >= 0;) {
                MapLocation loc = hqLocs[i];
                MapLocation symLoc = getSymmetricLocation(loc, Symmetry.R);
                if (!inArray(hqLocs, symLoc, knownHQCount)) {
                    symHQLocs[symHQCount] = symLoc;
                    symHQType[symHQCount] = Symmetry.R;
                    symHQCount++;
                }
            }
        }


    }

    public static void findHQLocFromSymmetry() throws GameActionException {
        for (int i = knownHQCount; --i >= 0;) {
            MapLocation loc = hqLocs[i];
            MapLocation symLoc = getSymmetricLocation(loc, theSymmetry);
            if (!inArray(hqLocs, symLoc, knownHQCount)) {
                saveHQLoc(symLoc);
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

    public static int getNumOpenDirs() throws GameActionException {
        int count = 0;
        for (int i = DIRS.length; --i >= 0;) {
            MapLocation loc = rc.adjacentLocation(DIRS[i]);
            if (rc.onTheMap(loc) && !rc.isLocationOccupied(loc)) {
                count++;
            }
        }
        return count;
    }

    public static Direction getCircleDirLeft(MapLocation loc, MapLocation center) {
        int dx = loc.x - center.x;
        int dy = loc.y - center.y;

        if (Math.abs(dx) > Math.abs(dy)) {
            return (dx > 0) ? Direction.NORTH : Direction.SOUTH;
        } else if (Math.abs(dx) < Math.abs(dy)) {
            return (dy > 0) ? Direction.WEST : Direction.EAST;
        } else {
            if (dx > 0) {
                return (dy > 0) ? Direction.WEST : Direction.NORTH;
            } else {
                return (dy > 0) ? Direction.SOUTH : Direction.EAST;
            }
        }
    }

    public static Direction getCircleDirRight(MapLocation loc, MapLocation center) {
        int dx = loc.x - center.x;
        int dy = loc.y - center.y;

        if (Math.abs(dx) > Math.abs(dy)) {
            return (dx > 0) ? Direction.SOUTH : Direction.NORTH;
        } else if (Math.abs(dx) < Math.abs(dy)) {
            return (dy > 0) ? Direction.EAST : Direction.WEST;
        } else {
            if (dx > 0) {
                return (dy > 0) ? Direction.SOUTH : Direction.WEST;
            } else {
                return (dy > 0) ? Direction.EAST : Direction.NORTH;
            }
        }
    }
}

enum Symmetry {
    H,
    V,
    R
}
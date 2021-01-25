package newday;

import battlecode.common.*;


import static newday.Slanderer.*;
import static newday.Bug.*;
import static newday.Debug.*;
import static newday.Map.*;
import static newday.Robot.*;
import static newday.Utils.*;

public class Nav {

    public static MapLocation smartMoveTargetLoc = new MapLocation(0, 0);
    public static int attemptsSinceLastMove;

    public static Direction smartMove(MapLocation loc) throws GameActionException {
        if (!smartMoveTargetLoc.equals(loc)) {
            smartMoveTargetLoc = loc;
            attemptsSinceLastMove = 0;
        }

        if (attemptsSinceLastMove >= 3) {
            return moveLog(loc);
        } else {
            Direction dir = fuzzyTo(loc);
            if (dir == null) {
                attemptsSinceLastMove++;
            } else {
                attemptsSinceLastMove = 0;
            }
            return dir;
        }
    }

    public static Direction moveLog(MapLocation loc) throws GameActionException {
        Direction move = path(loc);
        if (move == null) {
            tlog("But no move found");
        }
        return move;
    }

    public static void updateIsDirMoveable() throws GameActionException {
        // add information about if direction is moveable
        MapLocation centerLoc = (myMasterLoc != null) ? myMasterLoc : spawnLoc;
        if (myType == RobotType.SLANDERER && !here.isAdjacentTo(centerLoc)) {
            // don't move closer to hq if too close
            for (int i = DIRS.length; --i >= 0;) {
                MapLocation adjLoc = rc.adjacentLocation(DIRS[i]);
                isDirMoveable[i] = rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)
                        && !adjLoc.isAdjacentTo(centerLoc);
            }

        } else {
            for (int i = DIRS.length; --i >= 0;) {
                MapLocation adjLoc = rc.adjacentLocation(DIRS[i]);
                isDirMoveable[i] = rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc);
            }
        }

        isStuck = true;
        for (int i = DIRS.length; --i >= 0;) {
            if (isDirMoveable[i]) {
                isStuck = false;
                break;
            }
        }
    }

    /*
    Returns true if we can move in a direction to a tile
    Returns false otherwise
    */
    public static boolean checkDirMoveable(Direction dir) throws GameActionException {
		MapLocation loc = rc.adjacentLocation(dir);
        return isDirMoveable[dir2int(dir)] && rc.sensePassability(loc) >= minPassability;
    }

    public static void drawCheckDirMoveable() throws GameActionException {
        for (Direction dir: DIRS) {
            if (!isDirMoveable[dir2int(dir)]) {
                drawDot(here.add(dir), RED);
            } else if (rc.sensePassability(here.add(dir)) < minPassability) {
                drawDot(here.add(dir), ORANGE);
            } else {
                drawDot(here.add(dir), GREEN);
            }
        }
    }

    /*
    Tries to move in the target direction
    Returns the Direction that we moved in
    Returns null if did not move
    */
    public static Direction tryMove(Direction dir) throws GameActionException {
        if (checkDirMoveable(dir)) {
            Actions.doMove(dir);
            return dir;
        }
        return null;
    }

    /*
    Tries to move in the target direction, or rotateLeft/rotateRight of it
    Returns the Direction that we moved in
    Returns null if did not move
    */
    public static Direction tryMoveApprox (Direction dir) throws GameActionException {
        if (checkDirMoveable(dir)) {
            Actions.doMove(dir);
            return dir;
        }
        Direction leftDir = dir.rotateLeft();
        if (checkDirMoveable(leftDir)) {
            Actions.doMove(leftDir);
            return leftDir;
        }
        Direction rightDir = dir.rotateRight();
        if (checkDirMoveable(rightDir)) {
            Actions.doMove(rightDir);
            return rightDir;
        }
        return null;
    }




    public static boolean wanderLeft;

    public static MapLocation wanderCenter;

    public static boolean canWander(Direction dir, int centerRadius, int cornerRadius) {
        MapLocation loc = rc.adjacentLocation(dir);
        ttlog(isDirMoveable[dir2int(dir)] + " " + !wanderCenter.isWithinDistanceSquared(loc, centerRadius));
        return isDirMoveable[dir2int(dir)]
                && !wanderCenter.isWithinDistanceSquared(loc, centerRadius)
                && avoidCorner(loc, cornerRadius) == null;
    }

    public static Direction[] getWanderDirs(Direction dir) {
        Direction[] dirs = new Direction[8];
        Direction opp = dir.opposite();
        // init direction
        dirs[0] = wanderLeft ? dir.rotateLeft() : dir.rotateRight(); // in
        dirs[1] = dir; // straight
        dirs[2] = wanderLeft ? dir.rotateRight() : dir.rotateLeft(); // out
        // perpendicular
        dirs[3] = wanderLeft ? dir.rotateLeft().rotateLeft() : dir.rotateRight().rotateRight(); // in
        dirs[4] = wanderLeft ? dir.rotateRight().rotateRight() : dir.rotateLeft().rotateLeft(); // out
        // turning around
        dirs[5] = wanderLeft ? opp.rotateRight() : opp.rotateLeft(); // in
        dirs[6] = opp; // straight
        dirs[7] = wanderLeft ? opp.rotateLeft() : opp.rotateRight(); // out
        return dirs;
    }

    public static Direction[] getOuterWanderDirs(Direction dir) {
        Direction[] dirs = new Direction[8];
        Direction opp = dir.opposite();
        // init direction
        dirs[0] = wanderLeft ? dir.rotateRight() : dir.rotateLeft(); // out
        dirs[1] = dir; // straight
        dirs[2] = wanderLeft ? dir.rotateLeft() : dir.rotateRight(); // in
        // perpendicular
        dirs[3] = wanderLeft ? dir.rotateRight().rotateRight() : dir.rotateLeft().rotateLeft(); // out
        dirs[4] = wanderLeft ? dir.rotateLeft().rotateLeft() : dir.rotateRight().rotateRight(); // in
        // turning around
        dirs[5] = wanderLeft ? opp.rotateLeft() : opp.rotateRight(); // out
        dirs[6] = opp; // straight
        dirs[7] = wanderLeft ? opp.rotateRight() : opp.rotateLeft(); // in
        return dirs;
    }

    /*
    Circles around spawn point
     */
    public static Direction wander(int centerRadius, int cornerRadius, boolean goInner) throws GameActionException {
        log("Trying to wander");
        tlog(wanderLeft ? "Going left": "Going right");

        wanderCenter = getCenterLoc();

//        tlog("Info: " + wanderCenter + " " + radius);

        // check if too close to spawn
        if (here.isWithinDistanceSquared(wanderCenter, centerRadius)) {
//            tlog("Too close to wanderCenter");
            return fuzzyAway(wanderCenter);
        }

        // try to circle
        Direction dir2center = wanderCenter.directionTo(here);
        Direction wanderDir = wanderLeft ? dir2center.rotateLeft().rotateLeft() : dir2center.rotateRight().rotateRight();

        // if we would hit the wall
        if (!rc.onTheMap(rc.adjacentLocation(wanderDir))) {
            wanderLeft = !wanderLeft;
            wanderDir = wanderLeft ? dir2center.rotateLeft().rotateLeft() : dir2center.rotateRight().rotateRight();
        }

//        log("Init wanderDir " + wanderDir + " " + dir2center);

        Direction[] possDirs = goInner ? getWanderDirs(wanderDir) : getOuterWanderDirs(wanderDir);

        for (int i = 0; i < 8; i++) {
//            log("Considering " + i + " " + possDirs[i]);
            if (canWander(possDirs[i], centerRadius, cornerRadius)) {
                if (i >= 5) {
                    wanderLeft = !wanderLeft;
                }
                Actions.doMove(possDirs[i]);
                return possDirs[i];
            }
        }

        tlog("Cannot wander");
        return null;
    }

    public static MapLocation avoidCorner(MapLocation loc, int minDist) {
        MapLocation cornerBL = new MapLocation(XMIN, YMIN);
        MapLocation cornerBR = new MapLocation(XMAX, YMIN);
        MapLocation cornerTL = new MapLocation(XMIN, YMAX);
        MapLocation cornerTR = new MapLocation(XMAX, YMAX);
        if (loc.isWithinDistanceSquared(cornerBL, minDist)) {
            return cornerBL;
        } else if (loc.isWithinDistanceSquared(cornerBR, minDist)) {
            return cornerBR;
        } else if (loc.isWithinDistanceSquared(cornerTL, minDist)) {
            return cornerTL;
        } else if (loc.isWithinDistanceSquared(cornerTR, minDist)) {
            return cornerTR;
        }
        return null;
    }


    public static Direction fuzzyTo(MapLocation dangerLoc) throws GameActionException {
        log("Fuzzy to " + dangerLoc);

        double curRootDist = Math.sqrt(here.distanceSquaredTo(dangerLoc));

        Direction bestDir = null;
        double bestSpeed = N_INF;
        for (int i = 8; --i >= 0;) { // 7->0
            Direction dir = DIRS[i];
            if (isDirMoveable[dir2int(dir)]) {
                MapLocation adjLoc = rc.adjacentLocation(dir);
                double rootDist = Math.sqrt(dangerLoc.distanceSquaredTo(adjLoc));
                double speed = (curRootDist - rootDist) / (1 + 1 / rc.sensePassability(adjLoc));
                if (speed > bestSpeed) {
                    bestDir = dir;
                    bestSpeed = speed;
                }
            }
        }

        if (bestSpeed > 0) {
            Actions.doMove(bestDir);
        } else {
            tlog("Can't move");
        }
        return bestDir;
    }

    public static Direction fuzzyAway(MapLocation dangerLoc) throws GameActionException {
        log("Fuzzy away from " + dangerLoc);

        double curRootDist = Math.sqrt(here.distanceSquaredTo(dangerLoc));

        Direction bestDir = null;
        double bestSpeed = N_INF;
        for (int i = 8; --i >= 0;) { // 7->0
            Direction dir = DIRS[i];
            if (isDirMoveable[dir2int(dir)]) {
                MapLocation adjLoc = rc.adjacentLocation(dir);
                double rootDist = Math.sqrt(dangerLoc.distanceSquaredTo(adjLoc));
                double speed = (rootDist - curRootDist) / (1 + 1 / rc.sensePassability(adjLoc));
                if (speed > bestSpeed) {
                    bestDir = dir;
                    bestSpeed = speed;
                }
            }
        }

        if (bestSpeed > 0) {
            Actions.doMove(bestDir);
        } else {
            tlog("Can't move");
        }
        return bestDir;
    }

    // for when we are medium close to targetHQ
    public static boolean circleHQLeft;


    public static Direction tryCircleApproach(MapLocation centerLoc) throws GameActionException {
        log("Circle to " + centerLoc);

        double curRootDist = Math.sqrt(here.distanceSquaredTo(centerLoc));
        int curMaxXY = Math.max(Math.abs(here.x - centerLoc.x), Math.abs(here.y - centerLoc.y));

        Direction bestDir = null;
        double bestSpeed = N_INF;
        for (int i = 8; --i >= 0;) { // 7->0
            Direction dir = DIRS[i];
            if (isDirMoveable[dir2int(dir)]) {
                MapLocation adjLoc = rc.adjacentLocation(dir);
                int maxXY = Math.max(Math.abs(adjLoc.x - centerLoc.x), Math.abs(adjLoc.y - centerLoc.y));
                if (maxXY < curMaxXY) {
                    double rootDist = Math.sqrt(centerLoc.distanceSquaredTo(adjLoc));
                    double speed = (curRootDist - rootDist) / (1 + 1 / rc.sensePassability(adjLoc));
                    if (speed > bestSpeed) {
                        bestDir = dir;
                        bestSpeed = speed;
                    }
                }
            }
        }

        if (bestSpeed > 0) {
            Actions.doMove(bestDir);
            return bestDir;
        } else {
            return tryCircleLoc(centerLoc);
        }
    }

    public static Direction tryCircleLoc(MapLocation centerLoc) throws GameActionException {
        // try circling hq
        Direction relativeLeft = getCircleDirLeft(here, centerLoc);
        Direction relativeRight = getCircleDirRight(here, centerLoc);
        if (circleHQLeft) {
            if (isDirMoveable[dir2int(relativeLeft)]) {
                log("Circle left");
                Actions.doMove(relativeLeft);
                return relativeLeft;
            } else if (isDirMoveable[dir2int(relativeRight)]) {
                log("Circle right, switched");
                circleHQLeft = false;
                Actions.doMove(relativeRight);
                return relativeRight;
            } else {
                log("Tried circle left, stuck");
                return null;
            }
        } else {
            if (isDirMoveable[dir2int(relativeRight)]) {
                log("Circle right");
                Actions.doMove(relativeRight);
                return relativeRight;
            } else if (isDirMoveable[dir2int(relativeLeft)]) {
                log("Circle left, switched");
                circleHQLeft = true;
                Actions.doMove(relativeLeft);
                return relativeLeft;
            } else {
                log("Tried circle right, stuck");
                return null;
            }
        }
    }
}

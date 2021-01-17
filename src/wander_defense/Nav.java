package wander_defense;

import battlecode.common.*;


import static wander_defense.Robot.*;
import static wander_defense.Debug.*;
import static wander_defense.Map.*;
import static wander_defense.Bug.*;

public class Nav {

    public static Direction moveLog(MapLocation loc) throws GameActionException {
        Direction move = path(loc);
        if (move == null) {
            tlog("But no move found");
        }
        return move;
    }

    public static void updateIsDirMoveable() throws GameActionException {
        // add information about if direction is moveable
        for (int i = 0; i < DIRS.length; i++) {
            MapLocation adjLoc = rc.adjacentLocation(DIRS[i]);
            isDirMoveable[i] = rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc);
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




    final public static int SLANDERER_WANDER_RADIUS = 4;
    final public static int POLITICIAN_WANDER_RADIUS = 13;

    public static boolean wanderLeft = true;

    public static MapLocation wanderCenter;

    public static boolean canWander(Direction dir, int radius) {
        MapLocation loc = rc.adjacentLocation(dir);
        ttlog(isDirMoveable[dir2int(dir)] + " " + !wanderCenter.isWithinDistanceSquared(loc, radius));
        return isDirMoveable[dir2int(dir)] && !wanderCenter.isWithinDistanceSquared(loc, radius);
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

    /*
    Circles around spawn point
     */
    public static Direction wander(int radius) throws GameActionException {
        log("Trying to wander");
        tlog(wanderLeft ? "Going left": "Going right");

        updateWanderCenter();
        tlog("wanderCenter " + wanderCenter);

        // check if too close to spawn
        if (here.isWithinDistanceSquared(wanderCenter, radius)) {
            tlog("Too close to wanderCenter");
            return fuzzyAway(wanderCenter);
        }

        // try to circle
        Direction dir2center = wanderCenter.directionTo(here);
        Direction wanderDir;
        if (wanderLeft) {
            wanderDir = dir2center.rotateLeft().rotateLeft();
        } else {
            wanderDir = dir2center.rotateRight().rotateRight();
        }

        Direction[] wanderDirs = getWanderDirs(wanderDir);
//        log("Init wanderDir " + wanderDir + " " + dir2center);

        for (int i = 0; i < 8; i++) {
//            log("Considering " + i + " " + wanderDirs[i]);
            if (canWander(wanderDirs[i], radius)) {
                if (i >= 5) {
                    wanderLeft = !wanderLeft;
                }
                Actions.doMove(wanderDirs[i]);
                return wanderDirs[i];
            }
        }

        tlog("Cannot wander");
        return null;
    }

    public static void updateWanderCenter() {
        if (myMasterLoc != null) {
            log("Using master");
            wanderCenter = myMasterLoc;
        } else {
            log("Using spawn");
            wanderCenter = spawnLoc;
        }
    }


    public static Direction fuzzyTo(MapLocation dangerLoc) throws GameActionException {
        log("Fuzzy to from " + dangerLoc);

        double curRootDist = Math.sqrt(here.distanceSquaredTo(dangerLoc));

        Direction bestDir = null;
        double bestSpeed = N_INF;
        for (int i = 8; --i >= 0;) { // 7->0
            Direction dir = DIRS[i];
            if (isDirMoveable[dir2int(dir)]) {
                MapLocation adjLoc = rc.adjacentLocation(dir);
                double rootDist = Math.sqrt(dangerLoc.distanceSquaredTo(adjLoc));
                double speed = (curRootDist - rootDist) * rc.sensePassability(adjLoc);
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
                double speed = (rootDist - curRootDist) * rc.sensePassability(adjLoc);
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
}

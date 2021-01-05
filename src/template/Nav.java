package template;

import battlecode.common.*;


import static template.Robot.*;

public class Nav {

    /*
    Returns true if we can move in a direction to a tile
    Returns false otherwise
    */
    public static boolean checkDirMoveable(Direction dir) throws GameActionException {
//		MapLocation loc = rc.adjacentLocation(dir);
//		return rc.onTheMap(loc) && rc.canMove(dir);
        switch (dir) {
            case NORTH:
                return isDirMoveable[0];
            case NORTHEAST:
                return isDirMoveable[1];
            case EAST:
                return isDirMoveable[2];
            case SOUTHEAST:
                return isDirMoveable[3];
            case SOUTH:
                return isDirMoveable[4];
            case SOUTHWEST:
                return isDirMoveable[5];
            case WEST:
                return isDirMoveable[6];
            case NORTHWEST:
                return isDirMoveable[7];
        }
        return false;
    }

    /*
    Tries to move in the target direction
    Returns the Direction that we moved in
    Returns null if did not move
    */
    public static Direction tryMove(Direction dir) throws GameActionException {
        if (checkDirMoveable(dir)) {
//            Actions.doMove(dir); // move(dir);
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
//            Actions.doMove(dir); // move(dir);
            return dir;
        }
        Direction leftDir = dir.rotateLeft();
        if (checkDirMoveable(leftDir)) {
//            Actions.doMove(leftDir); // move(leftDir);
            return leftDir;
        }
        Direction rightDir = dir.rotateRight();
        if (checkDirMoveable(rightDir)) {
//            Actions.doMove(rightDir); // move(rightDir)
            return rightDir;
        }
        return null;
    }

	/*
	---------------
	BUG PATHFINDING
	---------------
	Uses the bug pathfinding algorithm to navigate around obstacles towards a target MapLocation
	Details here: https://www.cs.cmu.edu/~motionplanning/lecture/Chap2-Bug-Alg_howie.pdf

	Taken/adapted from TheDuck314 Battlecode 2016

	Assumes that we are ready to move
	Returns the Direction we moved in
	Returns null if did not move
	*/

    final public static int MAX_BUG_HISTORY_LENGTH = 100;

    public static MapLocation bugTarget = null;

    public static boolean bugTracing = false;
    public static MapLocation bugLastWall = null;
    public static int bugClosestDistanceToTarget = P_INF;
    public static int bugTurnsWithoutWall = 0;
    public static boolean bugRotateLeft = true; // whether we are rotating left or right

    public static MapLocation[] bugVisitedLocations = null;
    public static int bugVisitedLocationsIndex;
    public static int bugVisitedLocationsLength;

    public static Direction bugNavigate(MapLocation target) throws GameActionException {
        log("Bug navigating to " + target);

        if (!target.equals(bugTarget)) {
            bugTarget = target;
            bugTracing = false;
            bugClosestDistanceToTarget = here.distanceSquaredTo(bugTarget);
        }

        if (here.equals(bugTarget)) {
            return null;
        }

        // bugClosestDistanceToTarget = Math.min(bugClosestDistanceToTarget, here.distanceSquaredTo(bugTarget));

        Direction destDir = here.directionTo(bugTarget);

        // log("BUG_NAVIGATE");
        // tlog("bugTarget: " + bugTarget);
        // tlog("bugClosestDistanceToTarget: " + bugClosestDistanceToTarget);
        // tlog("destDir: " + destDir);
        // tlog("bugTracing: " + bugTracing);

        if (!bugTracing) { // try to go directly towards the target
            Direction tryMoveResult = tryMove(destDir);
            if (tryMoveResult != null) {
                return tryMoveResult;
            } else {
                bugStartTracing();
            }
        } else { // we are on obstacle, trying to get off of it
            if (here.distanceSquaredTo(bugTarget) < bugClosestDistanceToTarget) {
                Direction tryMoveResult = tryMove(destDir);
                if (tryMoveResult != null) { // we got off of the obstacle
                    bugTracing = false;
                    return tryMoveResult;
                }
            }
        }

        Direction moveDir = bugTraceMove(false);

        // TODO FOR BATTLECODE 2021: LOOK INTO THIS
        if (bugTurnsWithoutWall >= 2) {
            bugTracing = false;
        }

        return moveDir;
    }

    /*
    Runs if we just encountered an obstacle
    */
    public static void bugStartTracing() throws GameActionException {
        bugTracing = true;

        bugVisitedLocations = new MapLocation[MAX_BUG_HISTORY_LENGTH];
        bugVisitedLocationsIndex = 0;
        bugVisitedLocationsLength = 0;

        bugTurnsWithoutWall = 0;
        bugClosestDistanceToTarget = P_INF;

        Direction destDir = here.directionTo(bugTarget);

        Direction leftDir = destDir;
        MapLocation leftDest;
        int leftDist = Integer.MAX_VALUE;
        for (int i = 0; i < 8; ++i) {
            leftDir = leftDir.rotateLeft();
            leftDest = rc.adjacentLocation(leftDir);
            if (checkDirMoveable(leftDir)) {
                leftDist = leftDest.distanceSquaredTo(bugTarget);
                break;
            }
        }

        Direction rightDir = destDir;
        MapLocation rightDest;
        int rightDist = Integer.MAX_VALUE;
        for (int i = 0; i < 8; ++i) {
            rightDir = rightDir.rotateRight();
            rightDest = rc.adjacentLocation(rightDir);
            if (checkDirMoveable(rightDir)) {
                rightDist = rightDest.distanceSquaredTo(bugTarget);
                break;
            }
        }


        if (leftDist < rightDist) { // prefer rotate right if equal
            bugRotateLeft = true;
            bugLastWall = rc.adjacentLocation(leftDir.rotateRight());
        } else {
            bugRotateLeft = false;
            bugLastWall = rc.adjacentLocation(rightDir.rotateLeft());
        }
        // log("START_TRACING");
        // tlog("bugRotateLeft: " + bugRotateLeft);
        // tlog("bugLastWall: " + bugLastWall);
    }

    /*
    Returns the Direction that we moved in
    Returns null if we did not move
    */
    public static Direction bugTraceMove(boolean recursed) throws GameActionException {

        Direction curDir = here.directionTo(bugLastWall);

        // adds to array
        bugVisitedLocations[bugVisitedLocationsIndex] = here;
        bugVisitedLocationsIndex = (bugVisitedLocationsIndex + 1) % MAX_BUG_HISTORY_LENGTH;
        bugVisitedLocationsLength = Math.min(bugVisitedLocationsLength + 1, MAX_BUG_HISTORY_LENGTH);

        if (rc.canMove(curDir)) {
            bugTurnsWithoutWall += 1;
        } else {
            bugTurnsWithoutWall = 0;
        }

        for (int i = 0; i < 8; ++i) {
            if (bugRotateLeft) {
                curDir = curDir.rotateLeft();
            } else {
                curDir = curDir.rotateRight();
            }
            MapLocation curDest = rc.adjacentLocation(curDir);
            if (!rc.onTheMap(curDest) && !recursed) {
                // tlog("Hit the edge of map, reverse and recurse");
                // if we hit the edge of the map, reverse direction and recurse
                bugRotateLeft = !bugRotateLeft;
                return bugTraceMove(true);
            }
            if (checkDirMoveable(curDir)) {
                Actions.doMove(curDir);
                if (inArray(bugVisitedLocations, curDest, bugVisitedLocationsLength)) {
                    log("Resetting bugTracing");
                    bugTracing = false;
                }
                return curDir;
            } else {
                bugLastWall = rc.adjacentLocation(curDir);
            }
        }

        return null;
    }
}

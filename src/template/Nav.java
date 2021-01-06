package template;

import battlecode.common.*;



import java.util.HashMap;

import static template.Robot.*;
import static template.Debug.*;
import static template.Utils.*;
import static template.Map.*;

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

	/*
	---------------
	BUG PATHFINDING
	---------------
	Uses the bug pathfinding algorithm to navigate around obstacles towards a target MapLocation
	Details here: https://www.cs.cmu.edu/~motionplanning/lecture/Chap2-Bug-Alg_howie.pdf

	Adapted from Smite's Battlecode 2020 Pathing

	Assumes that we are ready to move
	Returns the Direction we moved in
	Returns null if did not move
	*/

    final public static int MAX_HISTORY_LENGTH = 30;

    /*
    'minPassability' starts out as 'DEFAULT_MIN_PASSABILITY'
    Every 'PATIENCE' turns, 'minPassability' is multiplied by 'PATIENCE_FACTOR'
     */
    final public static double DEFAULT_MIN_PASSABILITY = 0.5;;
    final public static int PATIENCE = 10; // every 'PATIENCE' turns, minPassability decreases
    final public static double PATIENCE_FACTOR = 0.5;



    public static MapLocation bugTarget = null;

    public static int historyIndex = 0;
    public static MapLocation[] history = new MapLocation[MAX_HISTORY_LENGTH];
    public static HashMap<MapLocation, Integer> historyFreq;

    public static boolean bugTracing = false;
    public static MapLocation bugWallLoc = null;
    public static int bugClosestDistance = P_INF;
    public static boolean bugRotateLeft = true; // whether we are rotating left or right

    public static int turnsWithoutCloser = 0;
    public static double minPassability = DEFAULT_MIN_PASSABILITY;

    public static void updateHistory() {
        if (history[historyIndex] != null) {
            historyFreq.merge(history[historyIndex], -1, Integer::sum);
        }
        history[historyIndex] = here;
        historyFreq.merge(here, 1, Integer::sum);
        historyIndex = (historyIndex + 1) % MAX_HISTORY_LENGTH;
    }

    public static void resetHistory() {
//        log("Resetting history");
        historyIndex = 0;
        for (int i = 0; i < MAX_HISTORY_LENGTH; i++) {
            history[i] = null;
        }
        historyFreq = new HashMap<MapLocation, Integer>();
        historyFreq.put(here, 1);
    }

    /*
    Called during "bug" method to reset certain variables
     */
    public static void resetTracing(MapLocation loc) {
//        log("Resetting bug tracing");
        bugClosestDistance = bugTarget.distanceSquaredTo(loc);
        bugTracing = false;
        bugWallLoc = null;
    }

    public static void resetMinPassability() {
        turnsWithoutCloser = 0;
        minPassability = DEFAULT_MIN_PASSABILITY;
    }

    /*
    Only called when the target is actually changing
     */
    public static void setTarget(MapLocation target) {
//        log("Setting target " + target);
        bugTarget = target;
        resetMinPassability();
        resetTracing(here);
        resetHistory();
    }

    public static Direction path(MapLocation target) throws GameActionException {
        // reset bug
        if (!target.equals(bugTarget)) {
            logi("Pathing to new target " + target);
            setTarget(target);
        }
        return navigate(1);
    }

    public static Direction navigate(int speculation) throws GameActionException {
        if (historyFreq.getOrDefault(here, 0) >= 3) {
            logi("Been here too many times " + historyFreq.getOrDefault(here, 0));
            resetTracing(here);
            resetHistory();
        }
        if (speculation == 1) {
            return bug();
        } else {
            logi("ERROR: speculation != 1 is not implemented yet");
            return null;
        }
    }

    private static Direction bug() throws GameActionException {
        if (here.equals(bugTarget)) {
            return null;
        }

        updateHistory();

        updateMinPassability();
//        drawCheckDirMoveable();

//        log("Starting wall loc " + bugWallLoc);
//        log("bugTracing " + bugTracing);

        Direction targetDir = here.directionTo(bugTarget);

        // find valid move that would get us closest to the target
        Direction bestDir = null;
        int bestDist = P_INF;
        for (Direction dir: getClosestDirs(targetDir)) {
            MapLocation adjLoc = here.add(dir);
            int dist = adjLoc.distanceSquaredTo(bugTarget);
            if (checkDirMoveable(dir) && dist < bestDist) {
                bestDir = dir;
                bestDist = dist;
            }
        }

        // if there are no possible moves, exit
        if (bestDir == null) {
            return null;
        }

        // check if best current move would get us to location closer than ever before
        if (bestDist < bugClosestDistance) {
//            log("Best distance");
            if (bugTracing) {
                resetTracing(here.add(bestDir));
            }
            Actions.doMove(bestDir);
            return bestDir;
        }

        // find initial tracing direction
        if (!bugTracing) {
//            log("Starting tracing");
            bugTracing = true;
            bugWallLoc = here.add(targetDir);
            // find closest left/right directions
            Direction leftDir = getNextMoveableDir(true);
            Direction rightDir = getNextMoveableDir(false);
            if (leftDir == null || rightDir == null) { // should never be true
                logi("ERROR: Sanity check failed - 'bug': leftDir/rightDir == null");
            }
            // calculate which direction gets you closer
            MapLocation leftLoc = here.add(leftDir);
            MapLocation rightLoc = here.add(rightDir);
            if (bugTarget.distanceSquaredTo(leftLoc) <= bugTarget.distanceSquaredTo(rightLoc)) {
                bugRotateLeft = true;
            } else {
                bugRotateLeft = false;
            };
        } else {
//            log("Continuing tracing");
        }

        return followWall(false);
    }

    public static Direction followWall(boolean recursed) throws GameActionException {
        // calculate direction to move in
        Direction dir = here.directionTo(bugWallLoc);
        for (int i = 0; i < 8; i++) {
            if (checkDirMoveable(dir)) {
                break;
            } else {
                // check if we should bounce off the wall
                MapLocation loc = here.add(dir);
                if (!rc.onTheMap(loc) && !recursed) {
                    bugRotateLeft = !bugRotateLeft;
                    return followWall(true);
                }
                // rotate to new potential direction
                dir = bugRotateLeft ? dir.rotateLeft() : dir.rotateRight();
            }
        }
        if (dir == null) {
            logi("ERROR: Sanity check failed - 'followWall': dir == null");
        }
        // update bugWallLoc
        Direction newWallDir = bugRotateLeft ? dir.rotateRight() : dir.rotateLeft();
//        MapLocation oldWallLoc = bugWallLoc;
        bugWallLoc = here.add(newWallDir);
//        drawLine(here, oldWallLoc, BROWN);
//        drawLine(here, bugWallLoc, BLACK);
//        log("bugRotateLeft " + bugRotateLeft);
//        log("oldWallLoc " + oldWallLoc);
//        log("newWallLoc " + bugWallLoc);
        // actually move
        Actions.doMove(dir);
        return dir;
    }

    public static Direction getNextMoveableDir(boolean rotateLeft) throws GameActionException {
        Direction dir = here.directionTo(bugWallLoc);
        for (int i = 0; i < 8; i++) {
            if (checkDirMoveable(dir)) {
                return dir;
            }
            if (rotateLeft) {
                dir = dir.rotateLeft();
            } else {
                dir = dir.rotateRight();
            }
        }
        return null;
    }

    public static void updateMinPassability() {
        if (turnsWithoutCloser > 0 && turnsWithoutCloser % PATIENCE == 0) {
            minPassability *= PATIENCE_FACTOR;
        }
    }
}

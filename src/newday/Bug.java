package newday;

import battlecode.common.*;

import java.util.HashMap;

import static newday.Constants.*;
import static newday.Debug.*;
import static newday.Map.*;
import static newday.Robot.*;

public class Bug extends Nav {

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
    final public static int PATIENCE = 5; // every 'PATIENCE' turns, minPassability decreases
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
        tlog("Resetting history");
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
    public static void resetTracing() {
        tlog("Resetting bug tracing");
        bugClosestDistance = bugTarget.distanceSquaredTo(here);
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
        tlog("Setting target " + target);
        bugTarget = target;
        resetMinPassability();
        resetTracing();
        resetHistory();
    }

    public static Direction path(MapLocation target) throws GameActionException {
        log("Pathing to " + target);
        // reset bug
        if (!target.equals(bugTarget)) {
            tlog("New target " + target);
            setTarget(target);
        }
        return navigate(1);
    }

    public static Direction navigate(int speculation) throws GameActionException {
        if (historyFreq.getOrDefault(here, 0) >= 3) {
            tlog("Been here too many times " + historyFreq.getOrDefault(here, 0));
            resetTracing();
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

        turnsWithoutCloser++;
        updateMinPassability();
//        drawCheckDirMoveable();

//        tlog("Starting wall loc " + bugWallLoc);
//        tlog("bugTracing " + bugTracing);

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
//            tlog("Best distance");
            if (bugTracing) {
                resetTracing();
            }
            turnsWithoutCloser = 0;

            Actions.doMove(bestDir);
            bugClosestDistance = rc.getLocation().distanceSquaredTo(bugTarget);
            return bestDir;
        }

        // find initial tracing direction
        if (!bugTracing) {
            tlog("Starting tracing");
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
//            tlog("Continuing tracing");
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
        MapLocation oldWallLoc = bugWallLoc;
        bugWallLoc = here.add(newWallDir);
        drawLine(here, oldWallLoc, BROWN);
        drawLine(here, bugWallLoc, BLACK);
//        tlog("bugRotateLeft " + bugRotateLeft);
//        tlog("oldWallLoc " + oldWallLoc);
//        tlog("newWallLoc " + bugWallLoc);
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

    public static void updateMinPassability() throws GameActionException {
        // check if all adj tiles are low passability
        double bestPass = 0.0;
        double avgPass = 0.0;
        int count = 0;
        for (Direction dir: DIRS) {
            MapLocation adjLoc = rc.adjacentLocation(dir);
            if (rc.onTheMap(adjLoc)) {
                double pass = rc.sensePassability(adjLoc);
                bestPass = Math.max(bestPass, pass);
                avgPass += pass;
                count++;
            }
        }
        avgPass /= count;
        if (bestPass < minPassability) {
            minPassability = avgPass;
        }

        // updates based on patience factor
        if (turnsWithoutCloser > 0 && turnsWithoutCloser % PATIENCE == 0) {
            minPassability *= PATIENCE_FACTOR;
        }
        tlog("minPassability " + minPassability);
    }
}

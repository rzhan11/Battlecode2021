package template;

import battlecode.common.*;

import static template.Nav.*;
import static template.Debug.*;


public class Muckraker extends Robot {

    public static void run() throws GameActionException {
        // turn 1
        try {
            updateTurnInfo();
            firstTurnSetup();
            loghalf(); turn(); loghalf();
        } catch (Exception e) {
            e.printStackTrace();
        }
        endTurn();

        // turn 2+
        while (true) {
            try {
                updateTurnInfo();
                loghalf(); turn(); loghalf();
            } catch (Exception e) {
                e.printStackTrace();
            }
            endTurn();
        }
    }

    // final constants

    // max distance from an hq, such that we can kill all slanderers spawned by it
    //try to block the enemy hq
    final public static int GOOD_DIST_TO_ENEMY_HQ = 2;

    // variables

    // only contains information about detected locations that it cannot sense
    public static MapLocation[] detectedLocs;

    public static RobotInfo[] closeEnemySlanderers;
    public static int closeEnemySlandererCount;

    public static MapLocation chaseLoc;
    public static int targetHQChecked = -100;
    public final static int CHECK_HQ_TURNS = 200;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        initExploreLoc();

        closeEnemySlanderers = new RobotInfo[maxSensedUnits];
    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateDetectedLocs();
        updateCloseEnemySlanderers();
        updateExploreLoc();

        if (!rc.isReady()) {
            return;
        }

        // expose an enemy slanderer if possible
        MapLocation exposeLoc = getBestExpose();
        if (exposeLoc != null) {
            Actions.doExpose(exposeLoc);
            return;
        }

        // TODO add better target locking
        // move towards sensed enemy slanderers
        chaseLoc = getBestChase();
        if (chaseLoc != null) {
            drawLine(here, chaseLoc, PINK);
            log("Chasing slanderer " + chaseLoc);
            moveLog(chaseLoc);
            return;
        }

        // move towards targetEnemyHQ
        //unless we have seen it's ours recently
        if (targetEnemyHQLoc != null && targetHQChecked + CHECK_HQ_TURNS < roundNum) {
            //once the hq has been converted stop
            if (rc.canSenseLocation(targetEnemyHQLoc) && rc.senseRobotAtLocation(targetEnemyHQLoc).team==rc.getTeam()) {
                targetHQChecked = roundNum;
            } else {
                if (here.isWithinDistanceSquared(targetEnemyHQLoc, GOOD_DIST_TO_ENEMY_HQ)) {
                    log("Close to targetEnemyHQ");
                    // check directions to find more passable tile
                    Direction bestDir = null;
                    double bestPass = myPassability;
                    for (int i = 0; i < 8; i++) {
                        MapLocation adjLoc = rc.adjacentLocation(DIRS[i]);
                        if (isDirMoveable[i] && adjLoc.isWithinDistanceSquared(targetEnemyHQLoc, GOOD_DIST_TO_ENEMY_HQ) && rc.sensePassability(adjLoc) > bestPass) {
                            bestDir = DIRS[i];
                            bestPass = rc.sensePassability(adjLoc);
                        }
                    }
                    if (bestDir == null) {
                        tlog("Best local position");
                        return;
                    } else {
                        tlog("Moving to better position");
                        Actions.doMove(bestDir);
                        return;
                    }
                } else {
                    log("Moving towards targetEnemyHQ");
                    moveLog(targetEnemyHQLoc);
                    return;
                }
            }
        }


        explore();
    }

    public static MapLocation getBestExpose() {
        RobotInfo bestExpose = null;
        int bestValue = -1;
        for (int i = closeEnemySlandererCount; --i >=0;) {
            RobotInfo ri = closeEnemySlanderers[i];
            if (ri.influence > bestValue) {
                bestExpose = ri;
                bestValue = ri.influence;
            }
        }
        if (bestExpose == null) {
            return null;
        } else {
            return bestExpose.getLocation();
        }
    }

    public static MapLocation getBestChase() {
        RobotInfo bestExpose = null;
        int bestValue = -1;
        for (int i = enemySlandererCount; --i >=0;) {
            RobotInfo ri = enemySlanderers[i];
            if (ri.influence > bestValue) {
                bestExpose = ri;
                bestValue = ri.influence;
            }
        }
        if (bestExpose == null) {
            return null;
        } else {
            return bestExpose.getLocation();
        }
    }

    private static void updateCloseEnemySlanderers() {
        closeEnemySlandererCount = 0;
        for (int i = enemySlandererCount; --i >=0;) {
            RobotInfo ri = enemySlanderers[i];
            if (here.isWithinDistanceSquared(ri.location, myActionRadius)) {
                closeEnemySlanderers[closeEnemySlandererCount++] = ri;
            }
        }
    }

    public static void updateDetectedLocs() {
        MapLocation[] allDetectedLocs = rc.detectNearbyRobots();
        int count = 0;
        for (MapLocation loc: allDetectedLocs) {
            if (!rc.canSenseLocation(loc)) {
                count++;
            }
        }
        detectedLocs = new MapLocation[count];
        count = 0;
        for (MapLocation loc: allDetectedLocs) {
            if (!rc.canSenseLocation(loc)) {
                detectedLocs[count++] = loc;
            }
        }
    }
}

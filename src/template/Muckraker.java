package template;

import battlecode.common.*;

import static template.Map.*;
import static template.Nav.*;
import static template.Debug.*;


public class Muckraker extends Robot {

    public static void run() throws GameActionException {
        // turn 1
        try {
            updateTurnInfo();
            postUpdateInit();
            firstTurnSetup();
            turn();
        } catch (Exception e) {
            e.printStackTrace();
        }
        endTurn();

        // turn 2+
        while (true) {
            try {
                updateTurnInfo();
                turn();
            } catch (Exception e) {
                e.printStackTrace();
            }
            endTurn();
        }
    }

    // final constants

    // max distance from an hq, such that we can kill all slanderers spawned by it
    final public static int GOOD_DIST_TO_ENEMY_HQ = 4;

    // variables

    // only contains information about detected locations that it cannot sense
    public static MapLocation[] detectedLocs;
    public static RobotInfo[] enemySlanderers;
    public static RobotInfo[] closeEnemySlanderers;

    public static MapLocation chaseLoc;

    public static Direction exploreDir;
    public static MapLocation exploreLoc;

    public static MapLocation targetEnemyHQ;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {

        // default exploreDir is randomized
        exploreDir = DIRS[myID % 8];
        for (Direction dir: DIRS) {
            MapLocation adjLoc = here.add(dir);
            RobotInfo ri = rc.senseRobotAtLocation(adjLoc);
            if (ri != null) {
                if (ri.getType() == RobotType.ENLIGHTENMENT_CENTER && ri.getTeam() == us) {
                    exploreDir = DIRS[rc.getFlag(ri.getID()) % 8];
                    break;
                }
            }
        }

        exploreLoc = addDir(spawnLoc, exploreDir, MAX_MAP_SIZE);
    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateDetectedLocs();
        updateEnemySlanderers();
        updateExploreLoc();

        // notify master of hqs
        MapLocation enemyHQ = findEnemyHQ();
        if (enemyHQ != null) {
            Comms.writeFoundEnemyHQ(enemyHQ);
        }

        if (!rc.isReady()) {
            return;
        }

        // expose an enemy slanderer if possible
        MapLocation exposeLoc = getBestExpose();
        if (exposeLoc != null) {
            Actions.doExpose(exposeLoc);
            return;
        }

        // move towards sensed enemy slanderers
        chaseLoc = getBestChase();
        if (chaseLoc != null) {
            drawLine(here, chaseLoc, PINK);
            log("Chasing slanderer " + chaseLoc);
            moveLog(exploreLoc);
            return;
        }

        // move towards targetEnemyHQ
        if (targetEnemyHQ != null) {
            if (here.isWithinDistanceSquared(targetEnemyHQ, GOOD_DIST_TO_ENEMY_HQ)) {
                log("Close to targetEnemyHQ");
                // check directions to find more passable tile
                Direction bestDir = null;
                double bestPass = myPassability;
                for (int i = 0; i < 8; i++) {
                    MapLocation adjLoc = rc.adjacentLocation(DIRS[i]);
                    if (isDirMoveable[i] && adjLoc.isWithinDistanceSquared(targetEnemyHQ, GOOD_DIST_TO_ENEMY_HQ) && rc.sensePassability(adjLoc) > bestPass) {
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
                moveLog(targetEnemyHQ);
                return;
            }
        }


        // move towards explore loc
        rc.setIndicatorLine(here, exploreLoc, PURPLE[0], PURPLE[1], PURPLE[2]);
        log("Exploring: " + exploreLoc);
        moveLog(exploreLoc);
    }

    public static MapLocation findEnemyHQ() {
        for (RobotInfo ri: sensedEnemies) {
            if (ri.type == RobotType.ENLIGHTENMENT_CENTER) {
                return ri.location;
            }
        }
        return null;
    }

    public static MapLocation getBestExpose() {
        RobotInfo bestExpose = null;
        int bestValue = -1;
        for (RobotInfo ri: closeEnemySlanderers) {
            int value = ri.getInfluence();
            if (value > bestValue) {
                bestExpose = ri;
                bestValue = value;
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
        for (RobotInfo ri: enemySlanderers) {
            int value = ri.getInfluence();
            if (value > bestValue) {
                bestExpose = ri;
                bestValue = value;
            }
        }
        if (bestExpose == null) {
            return null;
        } else {
            return bestExpose.getLocation();
        }
    }

    private static void updateEnemySlanderers() {
        int count = 0;
        int closeCount = 0;
        for (int i = 0; i < sensedEnemies.length; i++) {
            if (sensedEnemies[i].type == RobotType.SLANDERER) {
                count++;
                if (here.isWithinDistanceSquared(sensedEnemies[i].location, myActionRadius)) {
                    closeCount++;
                }
            }
        }
        enemySlanderers = new RobotInfo[count];
        closeEnemySlanderers = new RobotInfo[closeCount];
        count = 0;
        closeCount = 0;
        for (int i = 0; i < sensedEnemies.length; i++) {
            if (sensedEnemies[i].type == RobotType.SLANDERER) {
                enemySlanderers[count++] = sensedEnemies[i];
                if (here.isWithinDistanceSquared(sensedEnemies[i].location, myActionRadius)) {
                    closeEnemySlanderers[closeCount++] = sensedEnemies[i];
                }
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

    public static void updateExploreLoc() {
        exploreLoc = convertToKnownBounds(exploreLoc);
        if (rc.canSenseLocation(exploreLoc)) {
            exploreDir = exploreDir.rotateLeft();
            //this if makes som mucks cross the center instead of sticking to the outside
            if ((rc.getID()&8) == 0) { //initial directions is ID%8, so this is independent
                exploreDir = exploreDir.rotateLeft();
                exploreDir = exploreDir.rotateLeft();
            }
            exploreLoc = convertToKnownBounds(addDir(spawnLoc, exploreDir, MAX_MAP_SIZE));
        }
    }
}

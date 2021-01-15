package template;

import battlecode.common.*;

import static template.Nav.*;
import static template.Debug.*;


public class Muckraker extends Robot {

    // final constants

    final public static int CHASE_MEMORY = 10;

    // max distance from an hq, such that we can kill all slanderers spawned by it
    // try to block the enemy hq from spawning slanderers
    final public static int GOOD_DIST_TO_ENEMY_HQ = 2;

    // variables

    // only contains information about detected locations that it cannot sense
    public static MapLocation[] detectedLocs;

    public static MapLocation bestExposeLoc;

    public static MapLocation closestEnemySlanderer;
    public static MapLocation lastSeenSlanderer;
    public static int lastSeenSlandererRound;


    public static int targetHQChecked = -100;
    public final static int CHECK_HQ_TURNS = 200;

    public static MapLocation targetEnemyHQLoc;
    public static int targetEnemyHQID;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        initExploreLoc();
    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateDetectedLocs();
        updateExploreLoc();
        updateEnemies();

        if (!rc.isReady()) {
            return;
        }

        // expose an enemy slanderer if possible
        if (bestExposeLoc != null) {
            Actions.doExpose(bestExposeLoc);
            return;
        }

        //if early game and on a diagonal from our HQ, don't move
        // commented out since rushes are not a big worry
//        if (myMasterLoc!=null && here.distanceSquaredTo(myMasterLoc) == 2 && roundNum<350) {
//            return;
//        }

        // TODO add better target locking
        // move towards sensed enemy slanderers / previously seen enemies
        if (closestEnemySlanderer != null) {
            drawLine(here, closestEnemySlanderer, PINK);
            log("Chasing slanderer " + closestEnemySlanderer);
            // use fuzzy
            fuzzyTo(closestEnemySlanderer);
            return;
        } else if (lastSeenSlanderer != null) {
            log("Memory chase " + lastSeenSlanderer);
            fuzzyTo(lastSeenSlanderer);
            return;
        }

        // UPDATED: 'targetEnemyHQ' updates such that it reflects changes in hq teams and stuff
        // todo delete unnecessary parts of the code here
        // move towards targetEnemyHQ
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

    public static void updateEnemies() throws GameActionException {
        updateTargetEnemyHQ();
        updateEnemySlanderers();
    }

    public static void updateTargetEnemyHQ() throws GameActionException {
        // update targetEnemyHQLoc
        // todo make this locking, general improvements to make it similar to poli chasing code
        targetEnemyHQLoc = null;
        targetEnemyHQID = -1;
        for (int i = knownHQCount; --i >= 0; ) {
            if (hqTeams[i] == them) {
                targetEnemyHQLoc = hqLocs[i];
                targetEnemyHQID = hqIDs[i];
                break;
            }
        }
        log("targetEnemyHQ " + targetEnemyHQLoc + " " + targetEnemyHQID);
    }

    public static void updateEnemySlanderers() {
        // find closestEnemySlanderer and bestExposeLoc
        int bestDist = P_INF;
        closestEnemySlanderer = null;
        int bestExposeValue = N_INF;
        bestExposeLoc = null;
        for (int i = enemySlandererCount; --i >= 0;) {
            RobotInfo ri = enemySlanderers[i];
            int dist = here.distanceSquaredTo(ri.location);
            // check if this is closestEnemySlanderer
            if (dist < bestDist) {
                closestEnemySlanderer = ri.location;
                bestDist = dist;
            }
            // check for bestExpose
            if (dist <= myActionRadius && ri.influence > bestExposeValue) {
                bestExposeLoc = ri.location;
                bestExposeValue = ri.influence;
            }
        }

        // save closestEnemySlanderer to memory
        if (closestEnemySlanderer != null) {
            lastSeenSlanderer = closestEnemySlanderer;
            lastSeenSlandererRound = roundNum;
        }

        // check if we need to reset lastSeenSlanderer
        // if its too old or we are close to it, reset
        if (lastSeenSlanderer != null) {
            if (roundNum - lastSeenSlandererRound > CHASE_MEMORY || here.isWithinDistanceSquared(lastSeenSlanderer, 8)) {
                lastSeenSlanderer = null;
                lastSeenSlandererRound = -1;
            }
        }
        log("lastSeenSlanderer " + lastSeenSlanderer + " " + lastSeenSlandererRound);
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

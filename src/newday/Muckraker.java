package newday;

import battlecode.common.*;

import static newday.Comms.*;
import static newday.Debug.*;
import static newday.HQTracker.*;
import static newday.Nav.*;
import static newday.Utils.*;


public class Muckraker extends Robot {

    // final constants

    final public static int CHASE_MEMORY = 10;
    final public static int SWITCH_TARGET_ROUNDS = 30;

    // max distance from an hq, sucalh that we can kill all slanderers spawned by it
    // try to block the enemy hq from spawning slanderers
    final public static int VERY_CLOSE_ENEMY_HQ_DIST = 2;
    final public static int MEDIUM_CLOSE_ENEMY_HQ_DIST = 8;

    // variables

    // only contains information about detected locations that it cannot sense
    public static MapLocation[] detectedLocs;

    public static MapLocation bestExposeLoc;

    public static MapLocation closestEnemySlanderer;
    public static MapLocation lastSeenSlanderer;
    public static int lastSeenSlandererRound;

    public static int targetHQIndex = -1;
    public static MapLocation targetHQLoc = null;
    public static int targetHQID = -1;
    public static int minDistToTargetHQ = P_INF;
    public static int lastCloserRound = -1;

//    public static boolean useBug;


    public static boolean hqAttacker;
    public static boolean doSwarm;

    public static int lastBuffRound = 0;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        if (rc.getConviction() < 5) {
            initExploreTask();
        } else {
            initExploreTask(random() < 0.25);
        }


//        useBug = (random() < 0.5);

        if (rc.getConviction() < 5) {
            if (roundNum < 50) {
                hqAttacker = false;
            } else {
                hqAttacker = (random() < 0.25);
            }
        } else {
            hqAttacker = true;
        }
        doSwarm = false;
    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateAttackBehavior();

        updateDetectedLocs();
        updateExploreTask();
        Muckraker.updateTargetHQ();
        updateEnemySlanderers();
        updateAllyMuckraker();

        log("[ATTACK] " + hqAttacker + " " + doSwarm);

        if (!rc.isReady()) {
            return;
        }

        // expose an enemy slanderer if possible
        if (bestExposeLoc != null) {
            Actions.doExpose(bestExposeLoc);
            return;
        }

        // move towards sensed enemy slanderers / previously seen enemies
        if (closestEnemySlanderer != null) {
            drawLine(here, closestEnemySlanderer, PINK);
            log("Chasing slanderer " + closestEnemySlanderer);
            // use fuzzy
            smartMove(closestEnemySlanderer);
            return;
        } else if (lastSeenSlanderer != null) {
            log("Memory chase " + lastSeenSlanderer);
            smartMove(lastSeenSlanderer);
            return;
        }

//        if (closestAllyMuckraker != null && here.isWithinDistanceSquared(closestAllyMuckraker, 2)) {
//            fuzzyAway(closestAllyMuckraker);
//            return;
//        }

        if (hqAttacker) {
            // move towards targetHQ
            if (targetHQLoc != null) {
                if (here.isWithinDistanceSquared(targetHQLoc, VERY_CLOSE_ENEMY_HQ_DIST)) {
                    log("Very close to targetHQ");

                    // read ally messages that are super close
                    // this checks if an ally has already reported the 'surround' status
                    checkLocalSurround();
                    tryCircleLoc(targetHQLoc);

                    return;
                } else if (here.isWithinDistanceSquared(targetHQLoc, MEDIUM_CLOSE_ENEMY_HQ_DIST)) {
                    log("Medium close to targetHQ");
                    // try going very close
                    for (int i = DIRS.length; --i >= 0;) {
                        if (isDirMoveable[i] && targetHQLoc.isWithinDistanceSquared(rc.adjacentLocation(DIRS[i]), VERY_CLOSE_ENEMY_HQ_DIST)) {
                            tlog("Moving very close");
                            Actions.doMove(DIRS[i]);
                            return;
                        }
                    }
                    tryCircleLoc(targetHQLoc);
                    return;
                } else {
//                if (useBug) {
//                    log("Bugging to targetHQ");
//                    moveLog(targetHQLoc);
//                } else {
                    log("Fuzzy to targetHQ");
                    drawLine(here, targetHQLoc, RED);
                    smartMove(targetHQLoc);
//                }
                    return;
                }
            }

            explore();
            return;
        } else {
            explore();
            return;
        }
    }

    private static void updateAttackBehavior() {
        if (curEnemyBuff > 1.0000001) {
            lastBuffRound = roundNum;
        }
        if (roundNum - lastBuffRound > 150) {
            lastBuffRound = roundNum;
            // convert more and more mucks to attackers over time
            if (!hqAttacker) {
                hqAttacker = (random() < 0.5);
            }
        }
        if (roundNum % 50 == 0) {
            doSwarm = (random() < 0.5);
        }
    }

    public static MapLocation closestAllyMuckraker;

    public static void updateAllyMuckraker() throws GameActionException {
        int bestDist = P_INF;
        closestAllyMuckraker = null;
        for (int i = sensedAllies.length; --i >= 0;) {
            RobotInfo ri = sensedAllies[i];
            if (ri.type == RobotType.MUCKRAKER) {
                int dist = here.distanceSquaredTo(ri.location);
                if (dist < bestDist) {
                    bestDist = dist;
                    closestAllyMuckraker = ri.location;
                }
            }
        }
    }

    public static void resetTargetHQ() throws GameActionException {
        targetHQIndex = -1;
        targetHQLoc = null;
        targetHQID = -1;
        minDistToTargetHQ = P_INF;
        lastCloserRound = -1;
    }

    public static void updateTargetHQ() throws GameActionException {
        if (!hqAttacker) {
            return;
        }


        // update closest dist
        if (targetHQIndex != -1) {
            int dist = here.distanceSquaredTo(targetHQLoc);
            if (dist < minDistToTargetHQ) {
                minDistToTargetHQ = dist;
                lastCloserRound = roundNum;
            }
        }

        // check if target hq needs to be reset/changed
        if (targetHQIndex != -1) { // reset if target hq is not enemy/unknown
            if (hqTeams[targetHQIndex] != them && hqTeams[targetHQIndex] != null) {
                log("Resetting targetHQ, not enemy");
                resetTargetHQ();
            }
        }
        if (targetHQIndex != -1) { // reset if target hq is dead
            if (targetHQID > 0 && !rc.canGetFlag(targetHQID)) {
                log("Resetting targetHQ, dead");
                resetTargetHQ();
            }
        }
        if (targetHQIndex != -1) {
            if (checkHQIgnoreStatus(targetHQIndex)) {
                log("Resetting targetHQ, ignored");
                resetTargetHQ();
            }
        }
        if (targetHQIndex != -1) { // reset if its been too long since we got closer
            if (roundNum - lastCloserRound >= SWITCH_TARGET_ROUNDS
                    && here.distanceSquaredTo(targetHQLoc) > MEDIUM_CLOSE_ENEMY_HQ_DIST) {
                log("Resetting targetHQ, switching");
                hqIgnoreRounds[targetHQIndex] = roundNum; // ignoring the current target
                resetTargetHQ();
            }
        }
        if (targetHQIndex != -1) {
            // reset if surround flag has been triggered
            if (checkHQSurroundStatus(targetHQIndex)) {
                int dist = here.distanceSquaredTo(targetHQLoc);
                // and we are close to it
                if (dist > MEDIUM_CLOSE_ENEMY_HQ_DIST) {
                    log("Resetting targetHQ, surround & far");
                    resetTargetHQ();
                } else if (dist > VERY_CLOSE_ENEMY_HQ_DIST) {
                    // or if we are medium from it, but very close has it surrounded
                    RobotInfo[] veryCloseAllies = rc.senseNearbyRobots(targetHQLoc, VERY_CLOSE_ENEMY_HQ_DIST, us);
                    int veryCloseMax = getMaxSurround(targetHQLoc, 1);
                    if (veryCloseAllies.length == veryCloseMax) {
                        log("Resetting targetHQ, surround & medium");
                        resetTargetHQ();
                    }
                }
            }
        }
        // if we can see it
        if (targetHQIndex != -1) {
            if (!doSwarm && here.distanceSquaredTo(targetHQLoc) <= 8) {
                log("Resetting targetHQ, seen");
                hqIgnoreRounds[targetHQIndex] = roundNum; // ignoring the current target
                resetTargetHQ();
            }
        }

        if (targetHQIndex == -1) {
            int bestDist = P_INF;
            for (int i = knownHQCount; --i >= 0;) {
                // new targets must be enemy/unknown team and not surrounded
                if (hqTeams[i] == them) {// || hqTeams[i] == null) {
                    if (!checkHQSurroundStatus(i) && !checkHQIgnoreStatus(i)) {
                        int dist = here.distanceSquaredTo(hqLocs[i]);
                        if (dist < bestDist) {
                            targetHQIndex = i;
                            bestDist = dist;
                        }
                    }
                }
            }
            // if we found a targethq, assign other relevant variables
            if (targetHQIndex != -1) {
                targetHQLoc = hqLocs[targetHQIndex];
                targetHQID = hqIDs[targetHQIndex];
                minDistToTargetHQ = here.distanceSquaredTo(targetHQLoc);
                lastCloserRound = roundNum;
            }
        }

        log("targetHQ: " + targetHQIndex + " " + targetHQID + " " + targetHQLoc);
        if (targetHQIndex != -1) {
            log("switchTimer: " + (roundNum - lastCloserRound));
        }
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

    // returns true if my id is lowest or 2nd lowest in the group
    public static boolean checkIfIAmReporter(RobotInfo[] robots, int compareID, int length) {
        int low1 = P_INF;
        int low2 = P_INF;
        for (int i = length; --i >= 0;) {
            if (robots[i].type == RobotType.MUCKRAKER && robots[i].ID < low1) {
                low1 = robots[i].ID;
            } else if (robots[i].ID < low2) {
                low2 = robots[i].ID;
            }
        }
        return compareID < low1 || compareID < low2;
    }

    public static void checkLocalSurround() throws GameActionException {
        RobotInfo[] veryCloseAllies = rc.senseNearbyRobots(targetHQLoc, VERY_CLOSE_ENEMY_HQ_DIST, us);
        // check if I need to report
        if (checkIfIAmReporter(veryCloseAllies, myID, veryCloseAllies.length)) {
            tlog("[REPORTER]");
            RobotInfo[] mediumCloseAllies = rc.senseNearbyRobots(targetHQLoc, MEDIUM_CLOSE_ENEMY_HQ_DIST, us);

            int veryCloseMax = getMaxSurround(targetHQLoc, 1);
            int mediumCloseMax = getMaxSurround(targetHQLoc, 2);
            int veryCloseCount = 1 + veryCloseAllies.length;
            int mediumCloseCount = 1 + mediumCloseAllies.length;
            tlog("Very close: " + veryCloseCount + "/" + veryCloseMax);
            tlog("Med close: " + mediumCloseCount + "/" + mediumCloseMax);

            int outerRingMax = mediumCloseMax - veryCloseMax;
            int outerRingCount = mediumCloseCount - veryCloseCount;
            tlog("Outer ring: " + outerRingCount + "/" + outerRingMax);

            boolean wasSurrounded = checkHQSurroundStatus(targetHQIndex);
            boolean isSurrounded = veryCloseCount >= veryCloseMax
                    || mediumCloseCount >= 0.8 * mediumCloseMax
                    || outerRingCount >= outerRingMax;

            tlog("was/is " + wasSurrounded + " " + isSurrounded);

            // update after reporting, to not affect the 'if' statement
            updateHQSurroundRound(targetHQIndex, isSurrounded);

            // report if status has changed, or it has been 5 rounds since last report
            if (roundNum - hqReportSurroundRounds[targetHQIndex] > SURROUND_UPDATE_FREQ) {
                if (wasSurrounded != isSurrounded || isSurrounded) {
                    writeReportSurrounded(targetHQIndex, isSurrounded);
                }
            }
        }
    }
}

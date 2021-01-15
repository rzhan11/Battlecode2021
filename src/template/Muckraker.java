package template;

import battlecode.common.*;

import static template.Comms.readMessage;
import static template.Comms.writeReportSurrounded;
import static template.Debug.*;
import static template.HQTracker.*;
import static template.Map.*;
import static template.Nav.*;
import static template.Utils.*;


public class Muckraker extends Robot {

    // final constants

    final public static int CHASE_MEMORY = 10;

    // max distance from an hq, such that we can kill all slanderers spawned by it
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

    public static int lastReportSurroundRound = -1;

    // for when we are medium close to targetHQ
    public static boolean circleHQLeft;

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

        // UPDATED: 'targetHQ' updates such that it reflects changes in hq teams and stuff
        // move towards targetHQ
        if (targetHQLoc != null) {
            if (here.isWithinDistanceSquared(targetHQLoc, VERY_CLOSE_ENEMY_HQ_DIST)) {
                log("Very close to targetHQ");

                // read ally messages that are super close
                // this checks if an ally has already reported the 'surround' status
                RobotInfo[] veryCloseAllies = rc.senseNearbyRobots(targetHQLoc, VERY_CLOSE_ENEMY_HQ_DIST, us);
                // check if I need to report
                if (checkIfIAmReporter(veryCloseAllies, myID, veryCloseAllies.length)) {
                    tlog("[REPORTER]");
                    RobotInfo[] mediumCloseAllies = rc.senseNearbyRobots(targetHQLoc, MEDIUM_CLOSE_ENEMY_HQ_DIST, us);

                    int veryCloseMax = getMaxSurround(targetHQLoc, 1);
                    int mediumCloseMax = getMaxSurround(targetHQLoc, 2);
                    tlog("Very close " + veryCloseAllies.length + "/" + veryCloseMax);
                    tlog("Med close " + mediumCloseAllies.length + "/" + mediumCloseMax);

                    boolean wasSurrounded = checkIfSurrounded(targetHQIndex);
                    boolean isSurrounded = (1 + veryCloseAllies.length) >= veryCloseMax
                            || (1 + mediumCloseAllies.length) >= 0.8 * mediumCloseMax;

                    tlog("was/is " + wasSurrounded + " " + isSurrounded);

                    // update after reporting, to not affect the 'if' statement
                    updateHQSurroundRound(targetHQIndex, isSurrounded);

                    // report if status has changed, or it has been 10 rounds since last report
                    if (wasSurrounded != isSurrounded) {
                        writeReportSurrounded(targetHQLoc, isSurrounded);
                    } else if (hqSurroundRounds[targetHQIndex] > 0 && roundNum - lastReportSurroundRound > SURROUND_UPDATE_FREQUENCY) {
                        writeReportSurrounded(targetHQLoc, isSurrounded);
                    }
                }

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
                tryCircleHQ();
                return;
            } else {
                log("Moving towards targetHQ");
                moveLog(targetHQLoc);
                return;
            }
        }


        explore();
    }

    public static void updateEnemies() throws GameActionException {
        Muckraker.updateTargetHQ();
        updateEnemySlanderers();
    }

    public static void resetTargetHQ() throws GameActionException {
        targetHQIndex = -1;
        targetHQLoc = null;
        targetHQID = -1;
    }

    public static void updateTargetHQ() throws GameActionException {
        if (targetHQIndex != -1) { // reset if the target hq needs to be changed
            // reset if target hq team is not enemy/unknown
            if (hqTeams[targetHQIndex] != them && hqTeams[targetHQIndex] != null) {
                log("Resetting targetHQ, not enemy");
                resetTargetHQ();
            } else if (targetHQID > 0 && !rc.canGetFlag(targetHQID)) { // reset if target hq is dead
                log("Resetting targetHQ, dead");
                resetTargetHQ();
            } else {
                // reset if ignore flag has been triggered
                if (checkIfSurrounded(targetHQIndex)) {
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
        }

        if (targetHQIndex == -1) {
            int bestDist = P_INF;
            for (int i = knownHQCount; --i >= 0;) {
                // new targets must be enemy/unknown team and not surrounded
                if (hqTeams[i] == them || hqTeams[i] == null) {
                    if (!checkIfSurrounded(i)) {
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
            }
        }
        log("targetHQ: " + targetHQIndex + " " + targetHQID + " " + targetHQLoc);
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

    public static Direction tryCircleHQ() throws GameActionException {
        // try circling hq
        Direction relativeLeft = getCircleDirLeft(here, targetHQLoc);
        Direction relativeRight = getCircleDirRight(here, targetHQLoc);
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
}

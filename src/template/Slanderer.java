package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Map.*;

public class Slanderer extends Robot {

    // final constants

    final public static int FLEE_MEMORY = 50;

    // variables

    public static MapLocation closestMucker;
    public static int closestMuckerDist;
    public static MapLocation lastSeenMucker = null;
    public static int turnsSinceMucker = P_INF;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {

    }

    // code run each turn
    public static void turn() throws GameActionException {
        log("Generated influence: " + RobotType.SLANDERER.getPassiveInfluence(myInfluence, age, roundNum));
        tlog("Less: " + RobotType.SLANDERER.getPassiveInfluence(myInfluence - 1, age, roundNum));

        if (age == 0) {
            return;
        }

        if (age >= GameConstants.CAMOUFLAGE_NUM_ROUNDS) {
            log("CAMO ACTIVE");
            log("Type: " + rc.getType());
        } else {
            log("Turns to camo: " + (GameConstants.CAMOUFLAGE_NUM_ROUNDS - age));
        }

        if (!rc.isReady()) {
            return;
        }

        updateDanger();
        if (avoidDanger() != null) {
            return;
        }

        wander(SLANDERER_WANDER_RADIUS);
    }

    public static void updateDanger() throws GameActionException {
        closestMucker = null;
        closestMuckerDist = P_INF;
        for (RobotInfo ri: sensedEnemies) { // iterates from (len - 1) -> 0 inclusive
            if (ri.type == RobotType.MUCKRAKER) {
                int dist = here.distanceSquaredTo(ri.location);
                if (dist < closestMuckerDist) {
                    closestMucker = ri.location;
                    closestMuckerDist = dist;
                }
            }
        }

        if (closestMucker == null) {
            turnsSinceMucker++;
        } else {
            turnsSinceMucker = 0;
            lastSeenMucker = closestMucker;
        }
    }

    /*
    Return direction moved
     */
    public static Direction avoidDanger() throws GameActionException {
        if (closestMucker == null) {
            if (turnsSinceMucker <= FLEE_MEMORY) {
                log("Memory flee " + lastSeenMucker);
                return flee(lastSeenMucker);
            } else {
                log("No recent muckers");
                return null;
            }
        } else {
            if (closestMuckerDist <= RobotType.MUCKRAKER.actionRadiusSquared) {
                log("Close mucker " + closestMucker);
            } else {
                log("Far mucker " + closestMucker);
            }
            return flee(closestMucker);
        }
    }

    public static Direction flee(MapLocation dangerLoc) throws GameActionException {
        log("Fleeing from " + dangerLoc);

        int curDist = here.distanceSquaredTo(dangerLoc);

        Direction bestDir = null;
        double bestScore = N_INF;

        for (int i = 8; --i >= 0;) { // 7->0
            Direction dir = DIRS[i];
            if (isDirMoveable[dir2int(dir)]) {
                MapLocation adjLoc = rc.adjacentLocation(dir);
                int dist = dangerLoc.distanceSquaredTo(adjLoc);
                if (dist > curDist) {
                    double score = dist * rc.sensePassability(adjLoc);
                    if (score > bestScore) {
                        bestDir = dir;
                        bestScore = score;
                    }
                }
            }
        }

        if (bestDir != null) {
            Actions.doMove(bestDir);
        } else {
            tlog("Can't move");
        }
        return bestDir;
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
            return flee(wanderCenter);
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
        log("Init wanderDir " + wanderDir + " " + dir2center);

        for (int i = 0; i < 8; i++) {
            log("Considering " + i + " " + wanderDirs[i]);
            if (canWander(wanderDirs[i], radius)) {
                if (i >= 5) {
                    wanderLeft = !wanderLeft;
                }
                Actions.doMove(wanderDirs[i]);
                return wanderDirs[i];
            }
        }

        log("Cannot wander");
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

//    public static boolean isCrowded() {
//        // check if too close to spawn
//        if (here.isWithinDistanceSquared(wanderCenter, MIN_WANDER_RADIUS)) {
//            return true;
//        }
//
//        // check if crowded
//        MapLocation center = here.add(spawnDir);
//        RobotInfo[] closeAllies = rc.senseNearbyRobots(center, CROWD_RADIUS, us);
//
//        drawDot(center, BLACK);
//        drawLine(here, wanderCenter, GREEN);
//        log("center " + center);
//        log("spawnDir " + spawnDir);
//        log("crowd size " + closeAllies.length);
//
//        if (closeAllies.length >= CROWD_LIMIT) {
//            return true;
//        }
//
//        return false;
//    }
}

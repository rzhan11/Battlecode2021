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


    final public static int SLANDERER_WANDER_RADIUS = 8;
    final public static int POLITICIAN_WANDER_RADIUS = 13;

    public static boolean wanderLeft = true;

    public static boolean canWander(Direction dir, int radius) {
        MapLocation loc = rc.adjacentLocation(dir);
        return isDirMoveable[dir2int(dir)] && !loc.isWithinDistanceSquared(spawnLoc, radius);
    }

    public static Direction[] getWanderDirs(Direction dir) {
        Direction[] dirs = new Direction[8];
        dirs[0] = dir; // straight
        dirs[1] = wanderLeft ? dir.rotateLeft() : dir.rotateRight(); // in
        dirs[2] = wanderLeft ? dir.rotateRight() : dir.rotateLeft(); // out
        dirs[3] = wanderLeft ? dirs[1].rotateLeft() : dirs[1].rotateRight(); // in
        dirs[4] = wanderLeft ? dirs[2].rotateRight() : dirs[2].rotateLeft(); // out
        dirs[5] = dir.opposite(); // straight
        dirs[6] = wanderLeft ? dirs[5].rotateRight() : dirs[5].rotateLeft(); // in
        dirs[7] = wanderLeft ? dirs[5].rotateLeft() : dirs[5].rotateRight(); // in
        return dirs;
    }

    /*
    Circles around spawn point
     */
    public static Direction wander(int radius) throws GameActionException {
        log("Trying to wander");

        // check if too close to spawn
        if (here.isWithinDistanceSquared(spawnLoc, radius)) {
            tlog("Too close to spawn");
            return flee(spawnLoc);
        }

        // try to circle
        Direction spawnDir = here.directionTo(spawnLoc);
        Direction wanderDir;
        if (wanderLeft) {
            wanderDir = spawnDir.rotateLeft().rotateLeft();
        } else {
            wanderDir = spawnDir.rotateRight().rotateRight();
        }

        Direction[] wanderDirs = getWanderDirs(wanderDir);

        for (int i = 0; i < 8; i++) {
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

//    public static boolean isCrowded() {
//        // check if too close to spawn
//        if (here.isWithinDistanceSquared(spawnLoc, MIN_WANDER_RADIUS)) {
//            return true;
//        }
//
//        // check if crowded
//        MapLocation center = here.add(spawnDir);
//        RobotInfo[] closeAllies = rc.senseNearbyRobots(center, CROWD_RADIUS, us);
//
//        drawDot(center, BLACK);
//        drawLine(here, spawnLoc, GREEN);
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

package wander_defense;

import battlecode.common.*;

import static wander_defense.Debug.*;
import static wander_defense.Nav.*;
import static wander_defense.Comms.*;

public class Slanderer extends Robot {

    // final constants

    final public static int FLEE_MEMORY = 25;

    // variables

    public static MapLocation closestMucker;
    public static int closestMuckerDist;
    public static MapLocation lastSeenMucker = null;
    public static int turnsSinceMucker = P_INF;
    public static int slanderMaximumRadius = 18;
    public static boolean hasHomeLoc = false;
    public static MapLocation homeLoc = null;
    final public static int SLANDERER_WANDER_MINIMUM_RADIUS = 8;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {

    }

    // code run each turn
    public static void turn() throws GameActionException {
        log("Generated influence: " + RobotType.SLANDERER.getPassiveInfluence(myInfluence, age, roundNum));

//        if (age == 0) {
//            return;
//        }

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
        // todo temporarily commented
//        if(turnsSinceMucker == 0) {
//            // Found Attacker - Broadcast location
//            broadcastAttackMuckrakerLocation(closestMucker);
//        }
        if (avoidDanger() != null) {
            return;
        }
        if(hasHomeLoc) {
            Direction dir = moveLog(homeLoc);
            if(dir == null) return;
            Actions.doMove(dir);
            return;
        }

        wander(SLANDERER_WANDER_MINIMUM_RADIUS);
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
                return fuzzyAway(lastSeenMucker);
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
            return fuzzyAway(closestMucker);
        }
    }
}

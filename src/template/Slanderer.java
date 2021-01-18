package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Map.*;
import static template.Nav.*;
import static template.Utils.*;

public class Slanderer extends Robot {

    // final constants

    final public static int FLEE_MEMORY = 25;

    final public static int SLANDERER_MIN_LATTICE_DIST = 4;

    // variables

    public static MapLocation closestMucker;
    public static int closestMuckerDist;
    public static MapLocation lastSeenMucker = null;
    public static int turnsSinceMucker = P_INF;

//    public static MapLocation closestAlertLoc;
    public static MapLocation closestEnemyHQLoc;
    public static MapLocation prevHideLoc;


    public static int slanderMaximumRadius = 18;
    public static boolean hasHomeLoc = false;
    public static MapLocation homeLoc = null;
    final public static int SLANDERER_WANDER_MINIMUM_RADIUS = 8;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {

        // init default hide loc
        Direction masterDir = (myMasterLoc != null) ? here.directionTo(myMasterLoc) : Direction.NORTH;
        prevHideLoc = addDir(spawnLoc, masterDir, MAX_MAP_SIZE);

    }

    // code run each turn
    public static void turn() throws GameActionException {
        log("Generated influence: " + RobotType.SLANDERER.getPassiveInfluence(myInfluence, age, roundNum));

//        if (age == 0) {
//            return;
//        }

        CommManager.setStatus(1 << 3, false); // use (1<<3) bit to signal I am slan

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

        updateBannedLatticeDirs();
        // todo consider corner/edges

        // decide scaryLoc
        // if there is a nearby muckraker, go to opposite side of it
        updateClosestEnemyHQLoc();

        MapLocation centerLoc = (myMasterLoc != null) ? myMasterLoc : spawnLoc;
        MapLocation scaryLoc = null;

        if (alertEnemyMuckraker != null) {
            log("Hide closestAlertLoc " + alertEnemyMuckraker);
            scaryLoc = alertEnemyMuckraker;
        } else if (closestEnemyHQLoc != null) {
            log("Hide closestEnemyHQLoc " + closestEnemyHQLoc);
            scaryLoc = closestEnemyHQLoc;
        } else if (prevHideLoc != null) {
            log("Hide prevHideLoc " + prevHideLoc);
            scaryLoc = prevHideLoc;
        }


        if (scaryLoc != null) {
            prevHideLoc = scaryLoc;

            drawLine(here, scaryLoc, RED);
            drawDot(centerLoc, GREEN);

            Direction latticeDir = centerLoc.directionTo(scaryLoc);

            makeLattice(centerLoc, latticeDir, SLANDERER_MIN_LATTICE_DIST);
            return;
        } else {
            // hide near hq
            // else stay close to hq
            logi("WARNING: 'hide' bad logic, shouldn't be here");
        }





        // todo temporarily commented
//        if(turnsSinceMucker == 0) {
//            // Found Attacker - Broadcast location
//            broadcastAttackMuckrakerLocation(closestMucker);
//        }
//        if(hasHomeLoc) {
//            Direction dir = moveLog(homeLoc);
//            if(dir == null) return;
//            Actions.doMove(dir);
//            return;
//        }

//        wander(SLANDERER_WANDER_MINIMUM_RADIUS);
    }

    public static void updateDanger() throws GameActionException {
        closestMucker = null;
        closestMuckerDist = P_INF;
        for (int i = enemyMuckrakerCount; --i >= 0;) { // iterates from (len - 1) -> 0 inclusive
            RobotInfo ri = enemyMuckrakers[i];
            int dist = here.distanceSquaredTo(ri.location);
            if (dist < closestMuckerDist) {
                closestMucker = ri.location;
                closestMuckerDist = dist;
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
//            if (turnsSinceMucker <= FLEE_MEMORY) {
//                log("Memory flee " + lastSeenMucker);
//                return fuzzyAway(lastSeenMucker);
//            } else {
//                log("No recent muckers");
//                return null;
//            }
            log("No recent muckers");
            return null;
        } else {
            if (closestMuckerDist <= RobotType.MUCKRAKER.actionRadiusSquared) {
                log("Close mucker " + closestMucker);
            } else {
                log("Far mucker " + closestMucker);
            }
            return fuzzyAway(closestMucker);
        }
    }

    public static void updateClosestEnemyHQLoc() throws GameActionException {
        closestEnemyHQLoc = null;
        int bestDist = P_INF;
        for (int i = knownHQCount; --i >= 0;) {
            if (hqTeams[i] == them) {
                int dist = here.distanceSquaredTo(hqLocs[i]);
                if (dist < bestDist) {
                    closestEnemyHQLoc = hqLocs[i];
                    bestDist = dist;
                }
            }
        }
    }

    public static void updateBannedLatticeDirs() throws GameActionException {

    }


    public static void makeLattice(MapLocation centerLoc, Direction latticeDir, int minLatticeDist) throws GameActionException {
        log("makeLattice " + centerLoc + " " + latticeDir);


        int curSim = getDirSimilarity(latticeDir, here.directionTo(centerLoc));

        int curLatticeValue = (here.x + here.y) % 2;
        int curDist = centerLoc.distanceSquaredTo(here);

        if (curSim <= 1 && curDist > minLatticeDist) {
            // on the correct side, move to lattice
            Direction bestDir = null;
            int bestLatticeValue = P_INF;
            int bestDist = N_INF;
            {
                Direction[] possDirs = getClosestDirs(latticeDir);
                for (int i = possDirs.length; --i >= 0;) {
                    // make sure location is empty
                    Direction dir = possDirs[i];
                    if (isDirMoveable[dir2int(dir)]) {
                        MapLocation loc = rc.adjacentLocation(dir);
                        int sim = getDirSimilarity(latticeDir, loc.directionTo(centerLoc));
                        int dist = centerLoc.distanceSquaredTo(loc);
                        if (sim <= 1 && dist > minLatticeDist) {
                            int latticeValue = (loc.x + loc.y) % 2;
                            if (bestLatticeValue == 0) {
                                if (latticeValue == 0 && dist < bestDist) {
                                    bestDir = dir;
                                    bestLatticeValue = latticeValue;
                                    bestDist = dist;
                                }
                            } else if (latticeValue < bestLatticeValue || dist > bestDist) {
                                bestDir = dir;
                                bestLatticeValue = latticeValue;
                                bestDist = dist;
                            }
                        }
                    }
                }
            }

            // find worst friend
            MapLocation worstFriendLoc = null;
            int worstFriendLatticeValue = N_INF;
            int worstFriendDist = -1; // init value shouldn't matter
            for (int i = adjAllies.length; --i >= 0;) {
                RobotInfo ri = adjAllies[i];
                if (ri.type == RobotType.POLITICIAN
                        && ((rc.getFlag(ri.ID) & 8) > 0)) {
                    MapLocation loc = ri.location;
                    int sim = getDirSimilarity(latticeDir, loc.directionTo(centerLoc));
                    int dist = centerLoc.distanceSquaredTo(loc);
                    if (sim <= 1 && dist > minLatticeDist) {
                        int latticeValue = (loc.x + loc.y) % 2;
                        if (worstFriendLatticeValue == 1) {
                            if (latticeValue == 1 && dist < worstFriendDist) {
                                worstFriendLoc = loc;
                                worstFriendLatticeValue = latticeValue;
                                worstFriendDist = dist;
                            }
                        } else if (latticeValue > worstFriendLatticeValue || dist > worstFriendDist) {
                            worstFriendLoc = loc;
                            worstFriendLatticeValue = latticeValue;
                            worstFriendDist = dist;
                        }
                    }
                }
            }

//            log(bestLatticeValue + " " + bestDist);
//            log(curLatticeValue + " " + curDist);
//            log(worstFriendLatticeValue + " " + worstFriendDist);
//
//            if (bestDir != null) {
//                drawDot(rc.adjacentLocation(bestDir), BLACK);
//            } else {
//                drawDot(here, BLACK);
//            }
//            if (worstFriendLoc != null) {
//                drawDot(worstFriendLoc, RED);
//            }

            // if i found a better place than my current place
            if (bestDir != null) {
                if (bestLatticeValue < curLatticeValue
                        || (bestLatticeValue == 0 && bestDist < curDist)
                        || (bestLatticeValue == 1 && curLatticeValue == 1 && bestDist > curDist)) {
                    tlog("For me");
                    Actions.doMove(bestDir);
                    return;
                }
            }

            // if i found a better place than my worst friend
            if (worstFriendLoc != null && curLatticeValue == 0 && bestLatticeValue == 0) {
                if (worstFriendLatticeValue == 1 || bestDist < worstFriendDist) {
                    tlog("For friend");
                    Actions.doMove(bestDir);
                    return;
                }
            }

            log("No better place");
            return;
        } else {
            // on the wrong side
            if (latticeDir != Direction.CENTER) {
                MapLocation targetLoc = addDir(centerLoc, latticeDir.opposite(), MAX_MAP_SIZE);
                fuzzyTo(targetLoc);
                return;
            } else {
                fuzzyAway(centerLoc);
                return;
            }
        }
    }
}

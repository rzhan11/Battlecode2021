package template;

import battlecode.common.*;

import static template.Comms.*;
import static template.Debug.*;
import static template.HQTracker.*;
import static template.Map.*;
import static template.Nav.*;
import static template.Utils.*;


public class Politician extends Robot {

    // Final Constants

    final public static int MAX_EMPOWER = RobotType.POLITICIAN.actionRadiusSquared;
    final public static int[] EMPOWER_DISTS = new int[]{1, 2, 4, 5, 8, 9};
//    final public static int[][] ALL_EMPOWER_DISTS = new int[][] {{1, 2, 4, 5, 8, 9}, {1, 2, 4, 5, 8, 9}, {2, 4, 5, 8, 9}, {4, 5, 8, 9}, {4, 5, 8, 9}, {5, 8, 9}, {8, 9}, {8, 9}, {8, 9}, {9}};
//    final public static int[] ALL_EMPOWER_DISTS = new int[] {0, 1, 2, 4, 5, 8, 9};

    // Role Allocation
    final public static int ROLE_ATTACK = 1;
    final public static int ROLE_DEFEND = 2;
    final public static int ROLE_EXPLORE = 3;

    // Global Variables
    public static int myRole;
    public static int myDamage;

    public static int killHungryTarget; // if i can kill this enemy, then i will explode
    public static boolean extremeAggression; // if i can hit an enemy, then i will explode


    // attacker variables
    public static int noTargetHQTimer;


    // defender variables
    public static MapLocation closestEnemyMuckraker;
    public static MapLocation closestEnemy;
    public static RobotInfo[] empowerRangeEnemies;

    public static int targetHQIndex = -1;
    public static MapLocation targetHQLoc = null;
    public static int targetHQID = -1;

    // scout variables
    public static boolean isBugScout; // true = bug, false = fuzzy


    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        // init my role
        if (myMaster > 0) {
            int status = Comms.getStatusFromFlag(rc.getFlag(myMaster));
            boolean bit3 = (status & (1 << 3)) != 0;
            log("Master status " + status + " " + bit3);
            if (bit3) {
                myRole = ROLE_DEFEND;
            } else {
                myRole = ROLE_ATTACK;
            }
        } else { // default to attacker role
            myRole = ROLE_ATTACK;
        }

        // i am a scout
        if (rc.getInfluence() == 1) {
            myRole = ROLE_EXPLORE;
            isBugScout = (random() < 0.5);
        }

        initExploreTask();
    }

    // code run each turn
    public static void turn() throws GameActionException {
        // update damage
        myDamage = getDamage(myConviction, rc.getEmpowerFactor(us, 0));

        CommManager.setStatus(0, false); // use (1<<3) bit to signal I am poli

        // update myRole based on conviction
        switch(myRole) {
            case ROLE_ATTACK:
            case ROLE_DEFEND:
                if (myConviction <= 10) {
                    myRole = ROLE_EXPLORE;
                }
                break;
            case ROLE_EXPLORE:
                if (myConviction > 10) {
                    if (myConviction > 50) myRole = ROLE_ATTACK;
                    else myRole = ROLE_DEFEND;
                }
                break;
        }

        // print myRole
        switch(myRole) {
            case ROLE_ATTACK:
                log("[ROLE_ATTACK]");
                break;
            case ROLE_DEFEND:
                log("[ROLE_DEFEND]");
                break;
            case ROLE_EXPLORE:
                log("[ROLE_EXPLORE]");
                break;
        }
        if (wasSlanderer) {
            log("[SLAN2POLI]");
        }

        killHungryTarget = -1;
        extremeAggression = false;

        empowerRangeEnemies = rc.senseNearbyRobots(MAX_EMPOWER, them);

        updateExploreTask();
        Politician.updateTargetHQ();
        updateTargetMuckraker();
        updateEnemies();

        if (myRole == ROLE_EXPLORE) {
            updateSymmetryByPassability();
        }

        if (!rc.isReady()) {
            return;
        }

//        if (curAllyBuff >= 5 && myDamage >= 1000 && empowerRangeEnemies.length >= 8) {
//            log("[BIG BUFF EMPOWER]");
//            Actions.doEmpower(MAX_EMPOWER);
//        }
//
//        if (myDamage >= 100000 && empowerRangeEnemies.length >= 8) {
//            log("[WAVE CLEAR EMPOWER]");
//            Actions.doEmpower(MAX_EMPOWER);
//        }

        switch (myRole) {
            case ROLE_ATTACK:
                doAttackRole();
                break;


            case ROLE_DEFEND:
                doDefendRole();
                break;


            case ROLE_EXPLORE:
                explore(isBugScout);
                break;
        }
    }

    public static int getBestNeutralTarget() throws GameActionException {
        // update nearest neutral hq
        int bestIndex = -1;
        int bestDist = P_INF; // minimize
        for (int i = knownHQCount; --i >= 0;) {
            if(hqTeams[i] == neutral) {
                if (myDamage > hqInfluence[i]) {
                    int dist = here.distanceSquaredTo(hqLocs[i]);
                    if (dist < bestDist) {
                        bestIndex = i;
                        bestDist = dist;
                    }
                }
            }
        }
        log("bestNeutralIndex " + bestIndex);

        return bestIndex;
    }

    public static void updateTargetHQ() throws GameActionException {
        int bestNeutralIndex = getBestNeutralTarget();
        if (targetHQIndex != -1) {
            // reset if the target hq has changed to our team
            if (hqTeams[targetHQIndex] == us) {
                targetHQIndex = -1;
                targetHQLoc = null;
                targetHQID = -1;
            } else if (targetHQID > 0 && !rc.canGetFlag(targetHQID)) {
                // reset if target hq is dead
                targetHQIndex = -1;
                targetHQLoc = null;
                targetHQID = -1;
            } else if (hqTeams[targetHQIndex] == neutral && myDamage <= hqInfluence[targetHQIndex]) {
                // reset if cannot kill anymore
                targetHQIndex = -1;
                targetHQLoc = null;
                targetHQID = -1;
            } else if (hqTeams[targetHQIndex] != neutral && bestNeutralIndex != -1) {
                // reset if we can use new target
                targetHQIndex = -1;
                targetHQLoc = null;
                targetHQID = -1;
            }
        }

        if (targetHQIndex == -1) {
            if (bestNeutralIndex != -1) { // use neutral if possible
                targetHQIndex = bestNeutralIndex;
            } else { // use enemy if possible
                int bestDist = P_INF;
                for (int i = knownHQCount; --i >= 0;) {
                    if (hqTeams[i] == them) {
                        if (hqIDs[i] > 0) {
                            int dist = here.distanceSquaredTo(hqLocs[i]);
                            if (dist < bestDist) {
                                targetHQIndex = i;
                                bestDist = dist;
                            }
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

    public static void updateTargetMuckraker() throws GameActionException {
        closestEnemyMuckraker = null;
        if (myRole == ROLE_DEFEND) {
            RobotInfo ri = getClosest(here, enemyMuckrakers, enemyMuckrakerCount);
            if (ri != null) {
                closestEnemyMuckraker = ri.location;
            }
            log("Closest enemy: " + closestEnemyMuckraker);
        } else {
            // role == ROLE_ATTACK
            int bestDist = P_INF;
            double minConviction = 0.5 * (myConviction - GameConstants.EMPOWER_TAX);
            double maxConviction = 4 * (myConviction - GameConstants.EMPOWER_TAX);
            if (myConviction <= 20) {
                minConviction = 0;
            }
            for (int i = enemyMuckrakerCount; --i >= 0;) {
                RobotInfo ri = enemyMuckrakers[i];
                int killAmt = ri.conviction + 1;
                if (minConviction <= killAmt && killAmt <= maxConviction) {
                    int dist = here.distanceSquaredTo(ri.location);
                    if (dist < bestDist) {
                        closestEnemyMuckraker = ri.location;
                        bestDist = dist;
                    }
                }
            }
        }
        log("Closest muck: " + closestEnemyMuckraker);
    }

    public static void updateEnemies() throws GameActionException {
        RobotInfo ri = getClosest(here, sensedEnemies, sensedEnemies.length);
        if (ri != null) {
            closestEnemy = ri.location;
        } else {
            closestEnemy = null;
        }
        log("Closest enemy: " + closestEnemy);
    }

    public static void doAttackRole() throws GameActionException {

        // target hq
        if (targetHQIndex != -1) {
            noTargetHQTimer = 0;

            log("Trying attack");


            int dist = here.distanceSquaredTo(targetHQLoc);
            if (dist <= MAX_EMPOWER) {
                // check if can kill
                RobotInfo[] robots = rc.senseNearbyRobots(dist);
                int dmg = myDamage / robots.length;
                boolean canKillHQ = dmg > rc.senseRobot(targetHQID).conviction;
                boolean shouldEmpower = false;
                if (canKillHQ) {
                    shouldEmpower = true;
                }

                if (shouldEmpower) {
                    Actions.doEmpower(dist);
                    return;
                } else {
                    // try moving closer
                    log("Getting to better position");
                    if (dist == 1) {
                        // try rotating
                        Direction hqDir = here.directionTo(targetHQLoc);
                        Direction leftDir = hqDir.rotateLeft();
                        Direction rightDir = hqDir.rotateRight();
                        if (isDirMoveable[dir2int(leftDir)])
                            Actions.doMove(leftDir);
                        else if(isDirMoveable[dir2int(rightDir)])
                            Actions.doMove(rightDir);
                        return;
                    } else {
                        Direction dir = tryCircleApproach(targetHQLoc);
    //                moveLog(targetHQLoc);
                        return;
                    }
                }
            } else {
                log("Moving closer");
                Direction dir = fuzzyTo(targetHQLoc);
                return;
            }
        }

        noTargetHQTimer++;

        log("Aggression timer " + noTargetHQTimer);
        if (noTargetHQTimer >= 100) {
            tlog("EXTREME AGGRESSION");
            extremeAggression = true;
        }

        // target any enemies
        if (extremeAggression && closestEnemy != null) {
            tryAttackChase(closestEnemy, false); // just assume it is muckraker
            return;
        }

        // if no target
        explore(true);
        return;
    }

    public static void doDefendRole() throws GameActionException {
        // attack closest muckraker
        if (closestEnemyMuckraker != null) {
            killHungryTarget = rc.senseRobotAtLocation(closestEnemyMuckraker).ID;
            tryAttackChase(closestEnemyMuckraker, false);
            return;
        }

        // no seen muckrakers
        makePoliLattice(getCenterLoc(), 4, POLI_MIN_CORNER_DIST);
        return;
    }

    public static void tryAttackChase(MapLocation targetLoc, boolean useBug) throws GameActionException {
        if (tryAttack(targetLoc) == -1) {
            tryChase(targetLoc, useBug);
        }
    }

    public static int tryAttack(MapLocation targetLoc) throws GameActionException {
        log("Trying attack");
        if (targetLoc != null) {
            int dist = here.distanceSquaredTo(targetLoc);
            if (dist <= MAX_EMPOWER) {
                tlog("Empower dist " + dist);
                if (checkEmpower(dist)) {
                    Actions.doEmpower(dist);
                    return dist;
                } else {
                    ttlog("Inefficient");
                    return -1;
                }
            } else {
                tlog("Too far");
                return -1;
            }
        }
        tlog("No target");
        return -1;
    }

    public static Direction tryChase(MapLocation targetLoc, boolean useBug) throws GameActionException {
        log("Trying chasing");
        if (targetLoc != null) {
            // have not exploded, try chasing instead
            drawLine(here, targetLoc, PINK);
            tlog("Chasing " + targetLoc);
            if (useBug) {
                return moveLog(targetLoc);
            } else {
                return fuzzyTo(targetLoc);
            }
        }
        tlog("Could not chase");
        return null;
    }

    /*
    Simply checks if we should empower at a given distance
     */
    public static boolean checkEmpower(int dist) throws GameActionException {
        if (dist > MAX_EMPOWER) {
            return false;
        }

        boolean hurtsEnemy = false;

        RobotInfo[] hitRobots = rc.senseNearbyRobots(dist);
        int numHit = hitRobots.length;

        double totalScore = 0.0;
        int dmg = myDamage / numHit;
        if (dmg > 0) {
            for (int i = hitRobots.length; --i >= 0;) {
                RobotInfo ri = hitRobots[i];
                if (here.isWithinDistanceSquared(ri.location, dist)) {
                    if (ri.team == them) {
                        hurtsEnemy = true;
                    }
                    double score = getEmpowerScore(ri, dmg);
                    totalScore += score;
                }
            }
        }

        if (totalScore <= 0) {
            return false;
        }

        if (myConviction <= 25 && hurtsEnemy && dist <= 1) {
            log("Hurt condition met");
            return true;
        }

        // best score must be at least better than some threshold
        double threshold = 0.5 * (myConviction - GameConstants.EMPOWER_TAX);
        log("Score/Threshold: " + totalScore + " / " + threshold);
        if (totalScore >= threshold) {
            return true;
        } else {
            return false;
        }
    }

    /*
    Get best empower that hits at least 'dist' away
     */
    /*public static int getBestEmpower(int dist) throws GameActionException {
        if (dist > MAX_EMPOWER) {
            return -1;
        }

//        log("get best empower " + dist);

        int[] possDists = ALL_EMPOWER_DISTS[dist];
        RobotInfo[] hitRobots = rc.senseNearbyRobots(MAX_EMPOWER);
        int[] numHit = new int[possDists.length];

        for (RobotInfo ri: hitRobots) {
            for (int i = possDists.length; --i >= 0;) {
                if (here.isWithinDistanceSquared(ri.location, possDists[i])) {
                    numHit[i]++;
                } else {
                    break;
                }
            }
        }

//        Arrays.sort(hitRobots, new RobotCompare());

        double[] scores = new double[possDists.length];
        for (int i = possDists.length; --i >= 0;) {
            for (int j = hitRobots.length; --j >= 0;) {
                RobotInfo ri = hitRobots[j];
                if (here.isWithinDistanceSquared(ri.location, possDists[i])) {
                    int dmg = myDamage / numHit[i];
                    double score = getEmpowerScore(ri, dmg);
                    scores[i] += score;
                }
            }
        }

        int bestDist = -1;
        double bestScore = N_INF;
        for (int i = scores.length; --i >= 0;) {
            if (scores[i] > bestScore) {
                bestDist = possDists[i];
                bestScore = scores[i];
            }
        }

        if (bestScore == 0) {
            return -1;
        }


        // best score must be at least better than some threshold
        double threshold = 0.5 * (myConviction - GameConstants.EMPOWER_TAX);
        log("My score " + bestScore + " vs " + threshold);
        if (bestScore >= threshold) {
            return bestDist;
        } else {
            return -1;
        }
    }*/

    public static double getEmpowerScore(RobotInfo ri, int dmg) {
        double score = 0;
        if (ri.team == us) {
            switch (ri.type) {
                case ENLIGHTENMENT_CENTER:
                    score += dmg;
                    break;
                case POLITICIAN:
                    score += Math.min(dmg, ri.influence - ri.conviction);
                    break;
                default:
                    // don't increase if it is a muckraker
                    break;
            }
        } else if (ri.team == them) {
            switch (ri.type) {
                case ENLIGHTENMENT_CENTER:
                    if (dmg > ri.conviction) {
                        score += 2 * dmg;
                    } else {
                        score += dmg;
                    }
                    break;
                case MUCKRAKER:
                    if (dmg > ri.conviction) {
                        score += 2 * ri.conviction;
                    } else {
                        score += dmg;
                    }
                    break;
                case POLITICIAN:
                    if (dmg > ri.conviction) {
                        score += 2 * Math.min(dmg - ri.conviction, ri.influence + ri.conviction);
                    } else {
                        score += dmg;
                    }
                    break;
                default:
                    score += 0;
                    break;
            }
        } else {
            // neutral hq
            if (dmg > ri.conviction) {
                score += 10 * dmg;
            } else {
                score += 0.5 * dmg;
            }
        }

//        log("score " + extremeAggression + " " + killHungryTarget + " " + ri.ID);
        // modifiers
        if (extremeAggression && ri.team != us) {
//            log("extreme aggro");
            score += 1e6;
        }
        if (ri.ID == killHungryTarget && dmg > ri.conviction) {
//            log("kill hungry");
            score += 1e6;
        }
        return score;
    }

    final public static int POLITICIAN_WANDER_RADIUS = 8;
    final public static int POLI_MIN_CORNER_DIST = 25;

    public static Direction lockedLatticeDir = null;


    public static void makePoliLattice(MapLocation centerLoc, int minLatticeDist, int minCornerDist) throws GameActionException {
        log("poliLat " + centerLoc + " " + minLatticeDist);

        if (lockedLatticeDir != null) {
            if (isDirMoveable[dir2int(lockedLatticeDir)]) {
                tlog("Locked lattice");
                Actions.doMove(lockedLatticeDir);
                lockedLatticeDir = null;
                return;
            } else {
                lockedLatticeDir = null;
                // do not return, continue with method
            }
        }

        // get out of corner

        MapLocation avoidCornerLoc = avoidCorner(here, minCornerDist);
        if (avoidCornerLoc != null) {
            log("Corner move");
            fuzzyAway(avoidCornerLoc);
            return;
        }


//        int curSim = getDirSimilarity(latticeDir, centerLoc.directionTo(here));

        int curLatticeValue = (here.x % 4) + 4 * (here.y % 4);
        boolean curOnLattice = (curLatticeValue == 1 || curLatticeValue == 11);
        int curDist = centerLoc.distanceSquaredTo(here);

        // too close
        if (curDist <= minLatticeDist) {
            tlog("Too close");
            fuzzyAway(centerLoc);
            return;
        }

        if (curOnLattice) {
            // on the correct side, move to lattice
            Direction bestDir = null;
            int bestDist = P_INF;

            MapLocation worstFriendLoc = null;
            int worstFriendDist = N_INF;
            {
                // only look in diagonal dirs
                for (int i = DIAG_DIRS.length; --i >= 0; ) {
                    Direction dir = DIAG_DIRS[i];
                    MapLocation midLoc = rc.adjacentLocation(dir);
                    MapLocation loc = midLoc.add(dir);
                    int dist = centerLoc.distanceSquaredTo(loc);
                    if (rc.onTheMap(loc) && dist > minLatticeDist && avoidCorner(loc, minCornerDist) == null) {
                        if (rc.isLocationOccupied(loc)) {
                            // potential worstFriend
                            RobotInfo ri = rc.senseRobotAtLocation(loc);
                            int status = getStatusFromFlag(rc.getFlag(ri.ID));
                            if (ri.team == us && ri.type == RobotType.POLITICIAN && (status & 8) == 0) {
                                if (dist > worstFriendDist) {
                                    worstFriendDist = dist;
                                    worstFriendLoc = loc;
                                }
                            }
                        } else {
                            // available lattice loc
                            if (dist < bestDist && isDirMoveable[dir2int(dir)]) {
                                bestDir = dir;
                                bestDist = dist;
                            }
                        }
                    }
                }
            }

            log("best " + bestDir + " " + bestDist);
            log("cur " + curDist);
            log("worst " + worstFriendLoc + " " + worstFriendDist);

            if (bestDir != null) {
                drawDot(rc.adjacentLocation(bestDir), BLACK);
            } else {
                drawDot(here, BLACK);
            }
            if (worstFriendLoc != null) {
                drawDot(worstFriendLoc, RED);
            }

            if (bestDir != null) {
                // if i found a better place than my current place
                if (bestDist < curDist) {
                    tlog("For me");
                    Actions.doMove(bestDir);
                    return;
                }
                // if i found a better place than my worst friend
                if (worstFriendLoc != null) {
                    if (bestDist < worstFriendDist) {
                        tlog("For friend");
                        lockedLatticeDir = bestDir;
                        Actions.doMove(bestDir);
                        return;
                    }
                }
            }


            tlog("No better place");
            return;
        } else {
            Direction bestDir = null;
            int bestDist = P_INF;
            for (int i = DIRS.length; --i >= 0;) {
                if (isDirMoveable[i]) {
                    Direction dir = DIRS[i];
                    MapLocation loc = rc.adjacentLocation(dir);
                    int latticeValue = (loc.x % 4) + 4 * (loc.y % 4);
                    boolean onLattice = (latticeValue == 1 || latticeValue == 11);
                    int dist = centerLoc.distanceSquaredTo(loc);
                    if (onLattice && dist > minLatticeDist && avoidCorner(loc, minCornerDist) == null) {
                        if (dist < bestDist) {
                            bestDir = dir;
                            bestDist = dist;
                        }
                    }
                }
            }

            if (bestDir != null) {
                tlog("Go lattice");
                Actions.doMove(bestDir);
                return;
            } else {
                tlog("Wander for lattice");
                wander(minLatticeDist, minCornerDist, false);
                return;
            }
        }
    }
}

package newday;

import battlecode.common.*;

import static newday.Comms.*;
import static newday.Debug.*;
import static newday.HQTracker.*;
import static newday.Map.*;
import static newday.Nav.*;
import static newday.Utils.*;


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
    public static int targetHQMinDist;
    public static int targetHQCloserRound;

    // scout variables
    public static boolean isBugScout; // true = bug, false = fuzzy

    public static boolean isChaser;


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
            if (rc.getConviction() < 100) {
                myRole = ROLE_DEFEND;
            } else {
                myRole = ROLE_ATTACK;
            }
        }

        // i am a scout
        if (rc.getConviction() <= GameConstants.EMPOWER_TAX) {
            myRole = ROLE_EXPLORE;
            isBugScout = (random() < 0.5);
        }

        resetTargetHQ();

        initExploreTask();

        isChaser = randBoolean();
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
                    if (myConviction > 100) myRole = ROLE_ATTACK;
                    else myRole = ROLE_DEFEND;
                }
                break;
        }

        // print myRole
        switch(myRole) {
            case ROLE_ATTACK:
                log("[ROLE_ATTACK]");
                Debug.SILENCE_INDICATORS = false;
                drawDot(here, BLACK);
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
        updateAllySlanderer();
        updateTargetMuckraker();
        updateEnemies();

        if (myRole == ROLE_EXPLORE) {
            updateSymmetryByPassability();
        }

        if (age % 20 == 0) {
            isChaser = (random() < 0.5);
        }

        if (myRole == ROLE_ATTACK && targetHQIndex != -1) {
            drawLine(here, targetHQLoc, GREEN);
        }
        if (!rc.isReady()) {
            return;
        }

        log("Checking!");
        int dist = getBestEmpower();
        if (dist != -1) {
            log("Want to empower!");
            Actions.doEmpower(dist);
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
                explore();
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

    public static void resetTargetHQ() {
        targetHQIndex = -1;
        targetHQLoc = null;
        targetHQID = -1;
        targetHQMinDist = P_INF;
        targetHQCloserRound = -1;
    }

    public static void updateTargetHQ() throws GameActionException {
        int bestNeutralIndex = getBestNeutralTarget();
        if (targetHQIndex != -1) {
            if (hqTeams[targetHQIndex] == us) {
                // reset if the target hq has changed to our team
                resetTargetHQ();
            } else if (targetHQID > 0 && !rc.canGetFlag(targetHQID)) {
                // reset if target hq is dead
                resetTargetHQ();
            } else if (hqTeams[targetHQIndex] == neutral && myDamage <= hqInfluence[targetHQIndex]) {
                // reset if cannot kill anymore
                resetTargetHQ();
            } else if (hqTeams[targetHQIndex] != neutral && bestNeutralIndex != -1) {
                // reset if we can use new target
                resetTargetHQ();
            } else if (roundNum - targetHQCloserRound > 25) {
                // reset if we haven't gotten closer in a while
                hqIgnoreRounds[targetHQIndex] = roundNum; // ignoring the current target
                resetTargetHQ();
            }
            if (bestNeutralIndex != -1) {
                // reset if we have found a new hq much closer
                double bestNeutralDist = Math.sqrt(here.distanceSquaredTo(hqLocs[bestNeutralIndex]));
                double targetDist = Math.sqrt(here.distanceSquaredTo(targetHQLoc));
                if (bestNeutralDist < targetDist - 5) { // if this is substantially closer
                    resetTargetHQ();
                }
            }
        }

        if (targetHQIndex == -1) {
            if (bestNeutralIndex != -1) { // use neutral if possible
                targetHQIndex = bestNeutralIndex;
            } else { // use enemy if possible
                int bestDist = P_INF;
                for (int i = knownHQCount; --i >= 0;) {
                    if (hqTeams[i] == them) {
                        if (!checkHQIgnoreStatus(i)) {
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
            }
            // if we found a targethq, assign other relevant variables
            if (targetHQIndex != -1) {
                targetHQLoc = hqLocs[targetHQIndex];
                targetHQID = hqIDs[targetHQIndex];
                targetHQMinDist = here.distanceSquaredTo(targetHQLoc);
                targetHQCloserRound = roundNum;
            }
        }

        if (targetHQIndex != -1) {
            int curDist = here.distanceSquaredTo(targetHQLoc);
            if (curDist < targetHQMinDist) {
                targetHQMinDist = curDist;
                targetHQCloserRound = roundNum;
            }
        }
        log("targetHQ: " + targetHQIndex + " " + targetHQID + " " + targetHQLoc);
    }

    public static int killBonus = 21;
    public static double minEmpowerScoreRatio = 0.75;
    public static MapLocation closestAllySlanderer = null;

    public static void updateAllySlanderer() throws GameActionException {
        int bestDist = P_INF;
        closestAllySlanderer = null;
        for (int i = sensedAllies.length; --i >= 0;) {
            RobotInfo ri = sensedAllies[i];
            if (ri.type == RobotType.POLITICIAN // checks if it is slanderer
                    && (getStatusFromFlag(rc.getFlag(ri.ID)) & 8) > 0) {
                int dist = here.distanceSquaredTo(ri.location);
                if (dist < bestDist) {
                    closestAllySlanderer = ri.location;
                    bestDist = dist;
                }
            }
        }
    }

    public static void updateTargetMuckraker() throws GameActionException {
        closestEnemyMuckraker = null;
//        if (myRole == ROLE_DEFEND) {
//            RobotInfo ri = getClosest(here, enemyMuckrakers, enemyMuckrakerCount);
//            if (ri != null) {
//                closestEnemyMuckraker = ri.location;
//            }
//            log("Closest enemy: " + closestEnemyMuckraker);
//        } else {
            // role == ROLE_ATTACK
        int bestDist = P_INF;
        double minConviction = minEmpowerScoreRatio * (myConviction - killBonus);
        double maxConviction = 5 * (myConviction - GameConstants.EMPOWER_TAX);
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
//        }
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
                Direction dir = smartMove(targetHQLoc);
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
            tryAttackChase(closestEnemy); // just assume it is muckraker
            return;
        }

        // if no target
        explore();
        return;
    }

    public static void doDefendRole() throws GameActionException {
        // attack closest muckraker
        if (closestEnemyMuckraker != null) {
            killHungryTarget = rc.senseRobotAtLocation(closestEnemyMuckraker).ID;
            tryAttackChase(closestEnemyMuckraker);
            return;
        }

        // no sensed muckrakers
        if (isChaser) {
            if (alertEnemyMuckraker != null && roundNum - alertEnemyMuckrakerRound < WRITE_ENEMY_MUCKRAKER_FREQ) {
                int dist = here.distanceSquaredTo(alertEnemyMuckraker);
                if (MAX_EMPOWER < dist) {
                    fuzzyTo(alertEnemyMuckraker);
                    return;
                }
            }

            wander(8, 9, false);
        } else {
            wander(30, 9, true);
//            makePoliLattice(getCenterLoc(), 4, POLI_MIN_CORNER_DIST);
        }
        return;
    }

    public static void tryAttackChase(MapLocation targetLoc) throws GameActionException {
        if (tryAttack(targetLoc) == -1) {
            tryChase(targetLoc);
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

    public static Direction tryChase(MapLocation targetLoc) throws GameActionException {
        log("Trying chasing");
        if (targetLoc != null) {
            // have not exploded, try chasing instead
            drawLine(here, targetLoc, PINK);
            tlog("Chasing " + targetLoc);
            smartMove(targetLoc);
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

//        boolean hurtsEnemy = false;

        RobotInfo[] hitRobots = rc.senseNearbyRobots(dist);

        double totalScore = getEmpowerScoreForArray(hitRobots);

        if (totalScore <= 0) {
            return false;
        }

//        if (myConviction <= 25 && hurtsEnemy && dist <= 1) {
//            log("Hurt condition met");
//            return true;
//        }

        // best score must be at least better than some threshold
        double threshold = minEmpowerScoreRatio * myConviction;
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
    public static int getBestEmpower() throws GameActionException {
//        log("get best empower " + dist);

        RobotInfo[][] empowerRobots = new RobotInfo[][]{
            rc.senseNearbyRobots(1),
            rc.senseNearbyRobots(2),
            rc.senseNearbyRobots(4),
            rc.senseNearbyRobots(5),
            rc.senseNearbyRobots(8),
            rc.senseNearbyRobots(9),
        };

        double[] scores = new double[EMPOWER_DISTS.length];
        for (int i = EMPOWER_DISTS.length; --i >= 0;) {
            scores[i] = getEmpowerScoreForArray(empowerRobots[i]);
        }

        int bestDist = -1;
        double bestScore = N_INF;
        for (int i = scores.length; --i >= 0;) {
            if (scores[i] > bestScore) {
                bestDist = EMPOWER_DISTS[i];
                bestScore = scores[i];
            }
        }

        if (bestScore == 0) {
            return -1;
        }


        // best score must be at least better than some threshold
        double threshold = 0.75 * myConviction;
        log("My score " + bestScore + " vs " + threshold);
        if (bestScore >= threshold) {
            return bestDist;
        } else {
            return -1;
        }
    }

    public static int origDmg;
    public static int buffedDmg;

    public static double getEmpowerScoreForArray(RobotInfo[] robots) throws GameActionException {
        int len = robots.length;
        if (len == 0) return 0.0;

        origDmg = (rc.getConviction() - GameConstants.EMPOWER_TAX) / len;
        if (origDmg == 0) return 0.0;

        buffedDmg = (int) (origDmg * curAllyBuff);

        double total = 0.0;
        for (int i = len; --i >= 0;) {
            total += getEmpowerScore(robots[i]);
        }

        if (isStuck) {
            total *= 10;
        }
        return total;
    }

    /*
    Make sure you set origDmg and buffedDmg before calling this method
     */
    public static double getEmpowerScore(RobotInfo ri) throws GameActionException {
        double score = 0;

        if (ri.team == us) {
            switch (ri.type) {
                case ENLIGHTENMENT_CENTER:
                    score += 0.25 * origDmg;
                    break;
                case POLITICIAN:
                    score += Math.min(buffedDmg, ri.influence - ri.conviction);
                    break;
                case MUCKRAKER:
                    score += Math.min(buffedDmg, Math.ceil(ri.influence * 0.7) - ri.conviction);
                    break;
            }
        } else if (ri.team == them) {
            // bonus for killing enemies
            if (buffedDmg > ri.conviction) {
                score += killBonus; // 1 + 2 * GameConstants.EMPOWER_TAX;
            }
            switch (ri.type) {
                case ENLIGHTENMENT_CENTER:
                    // kills
                    if (buffedDmg > ri.conviction) {
                        score += ri.conviction + (buffedDmg - ri.conviction) / curAllyBuff;
                    } else {
                        score += buffedDmg;
                    }
                    break;
                case MUCKRAKER:
                    if (buffedDmg > ri.conviction) {
                        score += ri.conviction;
                    } else {
                        score += buffedDmg;
                    }
                    // be more likely to empower if enemy muck is close to base/ally slanderer
                    boolean urgent = closestAllySlanderer != null
                            || (myMasterLoc != null && myMasterLoc.isWithinDistanceSquared(ri.location, 30));
                    if (urgent) {
                        score *= 2;
                    }
                    break;
                case POLITICIAN:
                    if (buffedDmg > ri.conviction) {
                        score += Math.min(buffedDmg, ri.influence + ri.conviction);
                    } else {
                        score += buffedDmg;
                    }
                    break;
                default:
                    score += 0;
                    break;
            }
        } else {
            // kills
            if (buffedDmg > ri.conviction) {
                score += 2 * (ri.conviction + (buffedDmg - ri.conviction) / curAllyBuff);
            } else {
                score += 0.1 * buffedDmg;
            }
        }

//        log("score " + extremeAggression + " " + killHungryTarget + " " + ri.ID);
        // modifiers
        if (extremeAggression && ri.team != us) {
//            log("extreme aggro");
            score += 1e6;
        }
        if (ri.ID == killHungryTarget && buffedDmg > ri.conviction) {
//            log("kill hungry");
            score += 1e6;
        }

//        if (myID == 10950 && roundNum == 118) {
//            log(origDmg + " " + buffedDmg + " " + score + " " + ri.getID());
//        }
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

package muckspam;

import battlecode.common.*;

import static muckspam.Debug.*;
import static muckspam.Nav.*;
import static muckspam.Utils.*;


public class Politician extends Robot {

    // Final Constants

    final public static int MAX_EMPOWER = RobotType.POLITICIAN.actionRadiusSquared;
    final public static int[] EMPOWER_DISTS = new int[]{1, 2, 4, 5, 8, 9};
//    final public static int[][] ALL_EMPOWER_DISTS = new int[][] {{1, 2, 4, 5, 8, 9}, {1, 2, 4, 5, 8, 9}, {2, 4, 5, 8, 9}, {4, 5, 8, 9}, {4, 5, 8, 9}, {5, 8, 9}, {8, 9}, {8, 9}, {8, 9}, {9}};
//    final public static int[] ALL_EMPOWER_DISTS = new int[] {0, 1, 2, 4, 5, 8, 9};

    // Role Allocation
    final public static int ROLE_ATTACK = 1;
    final public static int ROLE_DEFEND = 2;

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

    public static int targetHQIndex = -1;
    public static MapLocation targetHQLoc = null;
    public static int targetHQID = -1;

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

        initExploreLoc();
    }

    // code run each turn
    public static void turn() throws GameActionException {
        killHungryTarget = -1;
        extremeAggression = false;

        updateExploreLoc();
        updateTargetHQ();
        updateTargetMuckraker();
        updateEnemies();

        if (myRole == ROLE_ATTACK) {
            log("[ROLE_ATTACK]");
        } else if (myRole == ROLE_DEFEND) {
            log("[ROLE_DEFEND]");
        }

        if (wasSlanderer) {
            log("[SLAN2POLI]");
        }

        // update damage
        myDamage = (int) (myConviction * rc.getEmpowerFactor(us, 0) - GameConstants.EMPOWER_TAX);

        if (!rc.isReady()) {
            return;
        }

        if(myRole == ROLE_ATTACK) {
            // target hq
            if (targetHQIndex != -1) {
                noTargetHQTimer = 0;
                tryAttackChase(targetHQLoc, true);
                return;
            }

            noTargetHQTimer++;

            log("Aggression timer " + noTargetHQTimer);
            if (noTargetHQTimer >= 100) {
                tlog("EXTREME AGGRESSION");
                extremeAggression = true;
            }

            // target enemy muckrakers
            if (closestEnemyMuckraker != null) {
                tryAttackChase(closestEnemyMuckraker, false);
                return;
            } else {
                noTargetHQTimer++;
            }

            // target any enemies
            if (extremeAggression && closestEnemy != null) {
                tryAttackChase(closestEnemy, false);
                return;
            }

            // if no target
            explore();
            return;
        }
        else if (myRole == ROLE_DEFEND) {
            if (closestEnemyMuckraker != null) {
                killHungryTarget = rc.senseRobotAtLocation(closestEnemyMuckraker).getID();
                tryAttackChase(closestEnemyMuckraker, false);
                return;
            }
            // no seen muckrakers
            wander(POLITICIAN_WANDER_RADIUS);
            return;
        }
    }

    public static void updateTargetHQ() throws GameActionException {
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
            }
        }

        if (targetHQIndex == -1) {
            int bestDist = P_INF;
            for (int i = knownHQCount; --i >= 0;) {
                if (hqTeams[i] == them || hqTeams[i] == neutral) {
                    if (hqIDs[i] > 0) {
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
                if (shouldEmpower(dist)) {
                    ttlog("Attacking " + dist);
                    Actions.doEmpower(dist);
                    return dist;
                } else {
                    ttlog("Attack is inefficient");
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
    public static boolean shouldEmpower(int dist) throws GameActionException {
        if (dist > MAX_EMPOWER) {
            return false;
        }

        RobotInfo[] hitRobots = rc.senseNearbyRobots(dist);
        int numHit = hitRobots.length;

        double totalScore = 0.0;
        for (int i = hitRobots.length; --i >= 0;) {
            RobotInfo ri = hitRobots[i];
            if (here.isWithinDistanceSquared(ri.location, dist)) {
                int dmg = myDamage / numHit;
                double score = getEmpowerScore(ri, dmg);
                totalScore += score;
            }
        }

        if (totalScore <= 0) {
            return false;
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

//        log("score " + extremeAggression + " " + killHungryTarget + " " + ri.getID());
        // modifiers
        if (extremeAggression && ri.team != us) {
//            log("extreme aggro");
            score += 1e6;
        }
        if (ri.getID() == killHungryTarget && dmg > ri.conviction) {
//            log("kill hungry");
            score += 1e6;
        }
        return score;
    }
}

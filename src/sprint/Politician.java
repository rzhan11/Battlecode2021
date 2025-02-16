package sprint;

import battlecode.common.*;

import java.util.Arrays;

import static sprint.Debug.*;
import static sprint.Nav.*;
import static sprint.Utils.getClosest;


public class Politician extends Robot {

    // Final Constants

    final public static int MAX_EMPOWER = RobotType.POLITICIAN.actionRadiusSquared;
    final public static int[] EMPOWER_DISTS = new int[]{1, 2, 4, 5, 8, 9};
    final public static int[][] ALL_EMPOWER_DISTS = new int[][] {{1, 2, 4, 5, 8, 9}, {1, 2, 4, 5, 8, 9}, {2, 4, 5, 8, 9}, {4, 5, 8, 9}, {4, 5, 8, 9}, {5, 8, 9}, {8, 9}, {8, 9}, {8, 9}, {9}};

    // Role Allocation
    final public static int ROLE_ATTACK = 1;
    final public static int ROLE_DEFEND = 2;

    // Global Variables
    public static int myRole;
    public static int myDamage;

    public static int killHungryTarget; // if i can kill this enemy, then i will explode
    public static boolean extremeAggression; // if i can hit an enemy, then i will explode


    // attacker variables
    public static int noEnemyHQTimer;


    // defender variables
    public static MapLocation closestEnemyMuckraker;
    public static MapLocation closestEnemy;


    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        // init my role
        if (myMaster != -1) {
            int status = Comms.getStatusFromFlag(rc.getFlag(myMaster));
            boolean bit3 = (status & (1 << 3)) != 0;
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
        updateEnemies();

        if (myRole == ROLE_ATTACK) {
            log("[ROLE_ATTACK]");
        } else if (myRole == ROLE_DEFEND) {
            log("[ROLE_DEFEND]");
        }

        // update damage
        myDamage = (int) (myConviction * rc.getEmpowerFactor(us, 0) - GameConstants.EMPOWER_TAX);

        if (!rc.isReady()) {
            return;
        }

        if(myRole == ROLE_ATTACK) {
            // target enemy hq
            if (targetEnemyHQLoc != null) {
                noEnemyHQTimer = 0;
                tryAttackChase(targetEnemyHQLoc);
                return;
            }

            noEnemyHQTimer++;

            log("Aggression timer " + noEnemyHQTimer);
            if (noEnemyHQTimer >= 25) {
                tlog("EXTREME AGGRESSION");
                extremeAggression = true;
            }

            // target enemy muckrakers
            if (closestEnemyMuckraker != null) {
                tryAttackChase(closestEnemyMuckraker);
                return;
            } else {
                noEnemyHQTimer++;
            }

            // target any enemies
            if (noEnemyHQTimer > 10 && closestEnemy != null) {
                extremeAggression = true;
                tryAttackChase(closestEnemy);
                return;
            }

            // if no target
            explore();
            return;
        }
        else if (myRole == ROLE_DEFEND) {
            if (closestEnemyMuckraker != null) {
                killHungryTarget = rc.senseRobotAtLocation(closestEnemyMuckraker).getID();
                tryAttackChase(closestEnemyMuckraker);
                return;
            }
            // no seen muckrakers
            Slanderer.wander(Slanderer.POLITICIAN_WANDER_RADIUS);
            return;
        }
    }

    public static void updateEnemies() throws GameActionException {
        RobotInfo ri = getClosest(here, enemyMuckrakers, enemyMuckrakerCount);
        if (ri != null) {
            closestEnemyMuckraker = ri.location;
        } else {
            closestEnemyMuckraker = null;
        }
        log("Closest muck: " + closestEnemyMuckraker);

        ri = getClosest(here, sensedEnemies, sensedEnemies.length);
        if (ri != null) {
            closestEnemy = ri.location;
        } else {
            closestEnemy = null;
        }
        log("Closest enemy: " + closestEnemy);
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
            int ed = getBestEmpower(dist);
            log("Empower dist " + dist + " " + ed);
            if (ed != -1) {
                tlog("Attacking " + ed);
                Actions.doEmpower(ed);
                return ed;
            } else {
                tlog("Attack is inefficient");
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
            return moveLog(targetLoc);
        }
        tlog("Could not chase");
        return null;
    }

    /*
    Get best empower that hits at least 'dist' away
     */
    public static int getBestEmpower(int dist) throws GameActionException {
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
    }

    public static double getEmpowerScore(RobotInfo ri, int dmg) {
        double score = 0;
        if (ri.team == us) {
            if (ri.type == RobotType.POLITICIAN) {
                score += Math.min(dmg, ri.influence - ri.conviction);
            } else {
                score += 0;
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

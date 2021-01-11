package template;

import battlecode.common.*;

import java.util.Arrays;

import static template.Debug.*;
import static template.Nav.*;
import static template.Utils.getClosest;


public class Politician extends Robot {

    public static void run() throws GameActionException {
        // turn 1
        try {
            updateTurnInfo();
            firstTurnSetup();
            loghalf(); turn(); loghalf();
        } catch (Exception e) {
            e.printStackTrace();
        }
        endTurn();

        // turn 2+
        while (true) {
            try {
                updateTurnInfo();
                loghalf(); turn(); loghalf();
            } catch (Exception e) {
                e.printStackTrace();
            }
            endTurn();
        }
    }

    // Final Constants

    final public static int MAX_EMPOWER = RobotType.POLITICIAN.actionRadiusSquared;
    final public static int[] EMPOWER_DISTS = new int[]{1, 2, 4, 5, 8, 9};
    final public static int[][] ALL_EMPOWER_DISTS = new int[][] {{1, 2, 4, 5, 8, 9}, {1, 2, 4, 5, 8, 9}, {2, 4, 5, 8, 9}, {4, 5, 8, 9}, {4, 5, 8, 9}, {5, 8, 9}, {8, 9}, {8, 9}, {8, 9}, {9}};

    // Role Allocation
    final public static int ROLE_ATTACK = 1;
    final public static int ROLE_DEFEND = 2;

    // Variables
    // Global Variables
    public static int role;

    public static int myDamage;

    public static MapLocation closestEnemyMuckraker;

    // Role-Specific Variables
    public static MapLocation target_initial_location;
    public static int estimated_target_id = -1;
    public static MapLocation estimated_target_location;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        // init my role
        if (myMaster != -1) {
            int status = Comms.getStatusFromFlag(rc.getFlag(myMaster));
            boolean bit3 = (status & (1 << 3)) != 0;
            if (bit3) {
                role = ROLE_DEFEND;
            } else {
                role = ROLE_ATTACK;
            }
        } else { // default to attacker role
            role = ROLE_ATTACK;
        }

        initExploreLoc();

    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateExploreLoc();
        updateEnemies();

        // update damage
        myDamage = (int) (myConviction * rc.getEmpowerFactor(us, 0) - GameConstants.EMPOWER_TAX);

        target_initial_location = targetEnemyHQLoc;

        if (!rc.isReady()) {
            return;
        }

        if(role == ROLE_ATTACK) {
            if (target_initial_location != null) {
                int ed = tryAttack(closestEnemyMuckraker);
                if (ed != -1) {
                    return;
                } else {
                    // did not atk
                    tryChase(closestEnemyMuckraker);
                    return;
                }
            }
            // if no target
            explore();
            return;
        }
        else if (role == ROLE_DEFEND) {
            if (closestEnemyMuckraker != null) {
                int ed = tryAttack(closestEnemyMuckraker);
                if (ed != -1) {
                    return;
                } else {
                    // did not atk
                    tryChase(closestEnemyMuckraker);
                    return;
                }
            }
            // no seen muckrakers
            explore();
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
    }

    public static int tryAttack(MapLocation targetLoc) throws GameActionException {
        if (targetLoc != null) {
            int dist = here.distanceSquaredTo(targetLoc);
            int ed = getBestEmpower(dist);
            log("Empower dist " + dist + " " + ed);
            if (ed != -1) {
                Actions.doEmpower(ed);
                return ed;
            } else {
                return -1;
            }
        }
        return -1;
    }

    public static Direction tryChase(MapLocation targetLoc) throws GameActionException {
        if (targetLoc != null) {
            // have not exploded, try chasing instead
            drawLine(here, targetLoc, PINK);
            log("Chasing " + targetLoc);
            return moveLog(targetLoc);
        }
        return null;
    }

    public static int getBestEmpower(int dist) throws GameActionException {
        if (dist > MAX_EMPOWER) {
            return -1;
        }

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

        Arrays.sort(hitRobots, new RobotCompare());

        double[] scores = new double[possDists.length];
        for (int i = possDists.length; --i >= 0;) {
            for (int j = hitRobots.length; --j >= 0;) {
                RobotInfo ri = hitRobots[j];
                if (here.isWithinDistanceSquared(ri.location, possDists[i])) {
                    int dmg = myDamage / numHit[i];
                    scores[i] += getEmpowerScore(ri, dmg);
                } else {
                    break;
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

        // best score must be at least better than some threshold
        double maxScore = myConviction - GameConstants.EMPOWER_TAX;
        if (bestScore >= 0.5 * maxScore) {
            return bestDist;
        } else {
            return -1;
        }
    }

    public static double getEmpowerScore(RobotInfo ri, int dmg) {
        if (ri.team == us) {
            if (ri.type == RobotType.POLITICIAN) {
                return Math.min(dmg, ri.influence - ri.conviction);
            } else {
                return 0;
            }
        } else if (ri.team == them) {
            switch (ri.type) {
                case ENLIGHTENMENT_CENTER:
                    if (dmg > ri.conviction) {
                        return 2 * dmg;
                    } else {
                        return dmg;
                    }
                case MUCKRAKER:
                    if (dmg > ri.conviction) {
                        return 2 * ri.conviction;
                    } else {
                        return dmg;
                    }
                case POLITICIAN:
                    if (dmg > ri.conviction) {
                        return 2 * Math.min(dmg - ri.conviction, ri.influence + ri.conviction);
                    } else {
                        return dmg;
                    }
                default:
                    return 0;
            }
        } else {
            // neutral hq
            if (dmg > ri.conviction) {
                return 10 * dmg;
            } else {
                return 0.5 * dmg;
            }
        }
    }
}

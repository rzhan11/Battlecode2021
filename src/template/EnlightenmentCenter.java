package template;

import battlecode.common.*;

import static template.Comms.*;
import static template.Debug.*;
import static template.HQTracker.*;
import static template.Map.*;
import static template.Role.*;
import static template.Utils.*;

public class EnlightenmentCenter extends Robot {
    // constants

    public static int[] SLANDERER_COSTS;
    final public static int MIN_SLANDERER_COST = 21;
    final public static int BEST_SLANDERER_COST = 41;
    final public static int MAX_SLANDERER_COST = 497;

    final public static int EARLY_EXPLORER_COUNT = 4;


    // the priority of directions to explore
    public static Direction[] EXPLORE_DIRS;

    public static int processMessageIndex = 0;

    public static int scoutCount = 0;

    public static int mySafetyBudget;
    public static int enemyPoliticianDanger = 0;

    public static int enemyMuckrakerDanger = 0;
    public static MapLocation closestEnemyMuckraker;

    public static int lastBid = 1;
    public static int lastTurnsVotes = 0;
    public static boolean wonLastVote = false;
    final public static double bidIncreaseScalingFactor = 1.5;
    final public static double bidDecreaseScalingFactor = 1.1;

    public static int MUCK_CAP = 100;
    public static int SLAN_CAP = 50;

    // if we are in the corner, it is essential that we have a lot of politicians, so that our slanderers dont get pushed in
    final public static double CORNER_DEFENSE_POLI_MULTIPLIER = 1.5;
    final public static double EDGE_DEFENSE_POLI_MULTIPLIER = 1.25;


    // num rounds before sending again
    public static int[] hqAttackDelays;

    public static int targetNeutralHQIndex;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        Comms.writeXBounds();
        Comms.writeYBounds();

        // choose directions to explore, based on map edges that we can see
        EXPLORE_DIRS = getExploreDirs();

        SLANDERER_COSTS = new int[] {-1, 21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282, 310, 339, 368, 399, 431, 463, 497, 532, 568, 605, 643, 683, 724, 766, 810, 855, 902, 949};

        int dx = getWallXDist(here.x);
        int dy = getWallYDist(here.y);

        if (dx <= Slanderer.MIN_WALL_DIST && dy <= Slanderer.MIN_WALL_DIST) {
            DEFENSE_POLI_ROLE.ratio *= CORNER_DEFENSE_POLI_MULTIPLIER;
            log("Corner multiplier " + DEFENSE_POLI_ROLE.ratio);
        } else if (dx <= Slanderer.MIN_WALL_DIST || dy <= Slanderer.MIN_WALL_DIST) {
            DEFENSE_POLI_ROLE.ratio *= EDGE_DEFENSE_POLI_MULTIPLIER;
            log("Edge multiplier " + DEFENSE_POLI_ROLE.ratio);
        }

        // poli
        hqAttackDelays = new int[MAX_HQ_COUNT];

        initRoles();
    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateRoleCounts();
        processMessages();

        // after processMessages
        broadcastHQSurround();

        updateEnemies();

        updateNeutrals();

        // after updateEnemies
        updateMaxBudget();

        // make a slanderer on the first turn
        if (roundNum == 1) {
            makeSlanderer();
            return;
        }

        // todo add saving
        // todo improve bidding strategy
        // bidding is OKish now, games are not normally won by bidding so its fine
        if (rc.getTeamVotes() < GameConstants.GAME_MAX_NUMBER_OF_ROUNDS / 2) {
            if (roundNum > 250 && mySafetyBudget > 100) {
                tryBid();
                // recalculate max budget AFTER making bets
                updateMaxBudget();
            }
        }

        logi("Budget: " + mySafetyBudget + " = " + rc.getInfluence() + " - " + enemyPoliticianDanger);

        if (!rc.isReady()) {
            return;
        }

        //When we have an empower buff, use it to duplicate influence
        if (buildKillPoliticians(11)) {
            //80 is chosen so that we always make a profit
            if (rc.getInfluence() < 0.5 * GameConstants.ROBOT_INFLUENCE_LIMIT) {
                if (mySafetyBudget >= 80) {
                    //System.out.println("Building self empower0");
                    makeSuicidePolitician();
                    return;
                } else {
                    //save up to make use of the buff
                    //System.out.println("Waiting to self empower.");
                    return;
                }
            }
        }

        // spawn muck scouts
        if (scoutCount < EXPLORE_DIRS.length) {
            Direction dir = makeMuckraker(true);
            return;
        }

        // must be after updateNeutrals
        updateRoleScores();



        if (enemyMuckrakerCount > 0) {
            log("Emergency defense");
            Direction dir = makeDefendPolitician();
            return;
        }

        if (DEFENSE_POLI_ROLE.count >= 4 && SLAN_ROLE.count >= 4) {
            if (EXPLORE_POLI_ROLE.count < 4) {
                log("Explore poli");
                makeExplorePolitician();
                return;
            }
            if (MUCK_ROLE.count < 5) {
                log("muck spam");
                makeMuckraker(true);
                return;
            }
        }

        // no visible enemy muckrakers
        if (DEFENSE_POLI_ROLE.score < SLAN_ROLE.score) {
            // 2/3 of politicans are defend
            // 1/3 of politicians are attack
            // todo TESTING CHANGE 0.66 -> 0.66
            Direction dir = makeDefendPolitician();
            if (dir != null) {
                return;
            }
//            if (random() < 0.66) {
//                Direction dir = makeDefendPolitician();
//                if (dir != null) {
//                    return;
//                }
//            } else {
//                Direction dir = makeAttackPolitician();
//                if (dir != null) {
//                    return;
//                }
//            }
        } else { // consider muckraker vs slanderer
            // check to make attack politicians
            do {
                if (ATTACK_POLI_ROLE.score < SLAN_ROLE.score) {
                    Direction dir = makeAttackPolitician();
                    if (dir != null) {
                        return;
                    }

                    if (targetNeutralHQIndex != -1) {
                        int income = mySlanTotalEarn + RobotType.ENLIGHTENMENT_CENTER.getPassiveInfluence(rc.getInfluence(), age, roundNum);
                        int diff = getAttackPoliCost() - mySafetyBudget;
                        int waitRounds = (int) Math.ceil(1.0 * diff / income);
                        log("income " + income + " " + diff + " " + waitRounds);
                        if (waitRounds < 5) {
                            log("Saving");
                            STOP_BID = true;
                            break;
                        }
                    } else {} // do nothing continue with normal build
                }

                if (MUCK_ROLE.score < SLAN_ROLE.score) {
                    Direction dir = makeMuckraker(false);
                    if (dir != null) {
                        return;
                    }
                } else {
                    Direction dir = makeSlanderer();
                    if (dir != null) {
                        return;
                    }
                }
            } while(false);
        }

        // build a cheap muckraker if i can't do anything else
        if (getNumOpenDirs() >= 2) {
            log("Killing time");
            makeMuckraker(true);
            return;
        }
    }

    // for hqs only
    public static Direction[] getExploreDirs() {
        if (XMIN != -1) {
            if (YMIN != -1) {
                return new Direction[] {Direction.NORTHEAST, Direction.NORTH, Direction.EAST};
            } else if (YMAX != -1) {
                return new Direction[] {Direction.SOUTHEAST, Direction.SOUTH, Direction.EAST};
            } else {
                return new Direction[] {Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.NORTHEAST, Direction.SOUTHEAST};
            }
        } else if (XMAX != -1) {
            if (YMIN != -1) {
                return new Direction[] {Direction.NORTHWEST, Direction.NORTH, Direction.WEST};
            } else if (YMAX != -1) {
                return new Direction[] {Direction.SOUTHWEST, Direction.SOUTH, Direction.WEST};
            } else {
                return new Direction[] {Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.SOUTHWEST, Direction.NORTHWEST};
            }
        } else {
            if (YMIN != -1) {
                return new Direction[] {Direction.NORTH, Direction.EAST, Direction.WEST, Direction.NORTHWEST, Direction.NORTHEAST};
            } else if (YMAX != -1) {
                return new Direction[] {Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTHWEST};
            } else {
                return new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.SOUTHWEST, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.NORTHWEST};
            }
        }
    }

    final public static int MIN_MESSAGE_BYTECODE = 5000;

    public static void processMessages() throws GameActionException {
        for (Role role: ROLE_ORDER) {
            int[] ids = role.ids;
            int count = role.count;


            if (count == 0) {
                log("Processed 0/0 " + role);
                continue;
            }

            processMessageIndex = processMessageIndex % count;
            int numProcessed = count;

//        Debug.SILENCE_LOGS = true;
            // first part
            for (int i = processMessageIndex; --i >= 0;) {
                if (Clock.getBytecodesLeft() > MIN_MESSAGE_BYTECODE) {
                    if ((rc.getFlag(ids[i]) & IGNORE_UNIT2UNIT_MASK) == 0) continue; // if it is a unit broadcast
                    Comms.readMessage(ids[i]);
                } else {
                    numProcessed = processMessageIndex - 1 - i;
                    break;
                }
            }
            if (Clock.getBytecodesLeft() > MIN_MESSAGE_BYTECODE) {
                // second part
                for (int i = count; --i >= 0;) { // intentionally i >= 0 to save bytecode
                    if (Clock.getBytecodesLeft() > MIN_MESSAGE_BYTECODE) {
                        if ((rc.getFlag(ids[i]) & IGNORE_UNIT2UNIT_MASK) == 0) continue; // if it is a unit broadcast
                        Comms.readMessage(ids[i]);
                    } else {
                        numProcessed = processMessageIndex + count - 1 - i;
                        break;
                    }
                }
            }
            if (numProcessed > count) {
                numProcessed = count;
            }
//        Debug.SILENCE_LOGS = false;

            log("Processed " + numProcessed + "/" + count + " " + role);
            if (numProcessed >= count) {
                processMessageIndex = 0;
            } else {
                processMessageIndex = (processMessageIndex - numProcessed + count) % count;
            }
        }
    }

    public static void updateEnemies() throws GameActionException {
        // calculate closest enemymuckraker
        RobotInfo ri = getClosest(here, enemyMuckrakers, enemyMuckrakerCount);
        if (ri != null) {
            closestEnemyMuckraker = ri.location;
        } else {
            closestEnemyMuckraker = null;
        }
//        log("Mucker " + closestEnemyMuckraker);

        // calculate enemyMuckrakerDanger
        enemyMuckrakerDanger = 0;
        for (int i = enemyMuckrakerCount; --i >= 0;) {
            // u have to deal conviction + 1 damage to kill
            enemyMuckrakerDanger += 1 + enemyMuckrakers[i].conviction;
        }

        // calculate enemyPolitician danger
        double enemyRatio = rc.getEmpowerFactor(them, 0);
        enemyPoliticianDanger = 0;
        for (int i = enemyPoliticianCount; --i >= 0;) {
            // u have to deal conviction + 1 damage to kill
            enemyPoliticianDanger += Math.max(enemyPoliticians[i].conviction * enemyRatio - GameConstants.EMPOWER_TAX, 0);
        }
    }

    public static void updateNeutrals() throws GameActionException {
        // update delay
        for (int i = knownHQCount; --i >= 0;) {
            hqAttackDelays[i] = Math.max(0, hqAttackDelays[i] - 1);
        }

        // update nearest neutral hq
        targetNeutralHQIndex = -1;
//        int bestNeutralDist = P_INF;
        int bestNeutralCost = P_INF;
        for (int i = knownHQCount; --i >= 0;) {
            if(hqTeams[i] == neutral && hqAttackDelays[i] <= 0) {
                int dist = here.distanceSquaredTo(hqLocs[i]);
                if (hqInfluence[i] < bestNeutralCost) {
                    targetNeutralHQIndex = i;
                    bestNeutralCost = hqInfluence[i];
//                    bestNeutralDist = dist;
                }
            }
        }
        log("targetNeutral " + targetNeutralHQIndex);
    }

    public static void updateMaxBudget() {
        mySafetyBudget = rc.getInfluence() - enemyPoliticianDanger;
    }

    public static boolean STOP_BID = false;

    public static void tryBid() throws GameActionException {
        if (STOP_BID) {
            STOP_BID = false;
            return;
        }

        wonLastVote = (rc.getTeamVotes() > lastTurnsVotes);
        lastTurnsVotes = rc.getTeamVotes();

        int bidBudget = mySafetyBudget / 10;
        int bidAmount;
        if (wonLastVote) {
            bidAmount = (int) Math.floor(lastBid / bidDecreaseScalingFactor);
        } else {
            bidAmount = (int) Math.ceil(lastBid * bidIncreaseScalingFactor);
        }
        if (roundNum > 500) {
            bidAmount = Math.max(bidAmount, mySafetyBudget / 50);
        }
        bidAmount = Math.max(1, bidAmount);

        if (bidAmount <= bidBudget) {
            log("Bidding " + bidAmount);
            rc.bid(bidAmount);
            lastBid = bidAmount;
        } else {
            lastBid = bidAmount / 2;
        }
    }

    public static int lastBigMuckrakerRound = -100;
    final public static int BIG_MUCK_FREQ = 50;

    final public static int MAX_BIG_MUCK_COST = 1000;;
    final public static int MIN_BIG_MUCK_BUDGET = 400;
    final public static double BIG_MUCK_BUDGET_RATIO = 0.25;

    public static Direction makeMuckraker(boolean cheap) throws GameActionException {
        log("Trying to build muckraker");

        if (mySafetyBudget < 1) {
            return null;
        }

        int cost = 1;

        // make increasingly expensive muckers over time
        // if we can afford it, 50% chance we make an "expensive" muckraker
        if (!cheap) {
            int targetConviction = (int) Math.ceil(1.0 * (age + 1) / 25);
            int targetCost = RobotType.MUCKRAKER.getInfluenceCostForConviction(targetConviction);
            if (targetCost < mySafetyBudget) {
                if (random() < 0.5) {
                    cost = targetCost;
                }
            }

            // if our empower factor is somewhat low
            if (roundNum - lastBigMuckrakerRound > BIG_MUCK_FREQ && rc.getEmpowerFactor(us, 0) < 4) {
                if (mySafetyBudget >= MIN_BIG_MUCK_BUDGET) { // we can build it
                    tlog("[BIG MUCK]");
                    cost = (int) Math.min(mySafetyBudget * BIG_MUCK_BUDGET_RATIO, MAX_BIG_MUCK_COST);
                    int bigConv = (int) Math.ceil(cost * 0.7);
                    cost = RobotType.MUCKRAKER.getInfluenceCostForConviction(bigConv);
                }
            }
        }

        Direction scoutDir = EXPLORE_DIRS[scoutCount % EXPLORE_DIRS.length];
        int status = dir2int(scoutDir);
        CommManager.setStatus(status, true);

        Direction buildDir = tryBuild(RobotType.MUCKRAKER, scoutDir, cost, MUCK_ROLE);
        if (buildDir != null) {
            scoutCount++;
        }
        return buildDir;
    }

    public static Direction makeSlanderer() throws GameActionException {
        log("Trying to build slanderer");

        // determine cost of slanderer
        int budget = mySafetyBudget;
        int cost = -1;
        if (budget >= MAX_SLANDERER_COST) {
            cost = MAX_SLANDERER_COST;
        } else {
            for (int i = 1; i < SLANDERER_COSTS.length; i++) {
                if (budget < SLANDERER_COSTS[i]) {
                    cost = SLANDERER_COSTS[i - 1];
                    break;
                }
            }
        }

        if (cost > mySafetyBudget) {
            return null;
        }

        // check for min cost
        double minValue = mySlanTotalValue / 10.0;
        double value = cost * GameConstants.EMBEZZLE_NUM_ROUNDS;
        if (value >= minValue || cost == MAX_SLANDERER_COST) {
        // todo testing
//        if (true) {
            Direction buildDir = tryBuild(RobotType.SLANDERER, EXPLORE_DIRS[0].opposite(), cost, SLAN_ROLE);
            return buildDir;
        } else {
            return null;
        }

    }

    public static Direction makeExplorePolitician() throws GameActionException {
        log("Trying to build explore politician");

        int cost = 1;
        if (cost > mySafetyBudget) {
            return null;
        }

        Direction scoutDir = EXPLORE_DIRS[scoutCount % EXPLORE_DIRS.length];
        int status = dir2int(scoutDir);
        CommManager.setStatus(status, true);

        Direction buildDir = tryBuild(RobotType.POLITICIAN, scoutDir, cost, EXPLORE_POLI_ROLE);
        if (buildDir != null) {
            scoutCount++;
        }
        return buildDir;
    }

    public static Direction makeDefendPolitician() throws GameActionException {
        log("Trying to build defensive politician");

        int minCost = GameConstants.EMPOWER_TAX + 8;


        int cost = GameConstants.EMPOWER_TAX + enemyMuckrakerDanger;
        cost = Math.max(minCost, cost);
        tlog("Min cost is " + cost);
        if (cost > mySafetyBudget) {
            ttlog("Cannot afford");
            return null;
        }

        Direction scoutDir;
        if (closestEnemyMuckraker != null) {
            scoutDir = here.directionTo(closestEnemyMuckraker);
        } else if (alertEnemyMuckraker != null) {
            scoutDir = here.directionTo(alertEnemyMuckraker);
        } else {
            scoutDir = getRandomDir();
        }

        int status = dir2int(scoutDir) + (1 << 3);
        CommManager.setStatus(status, true);

        Direction buildDir = tryBuild(RobotType.POLITICIAN, scoutDir, cost, DEFENSE_POLI_ROLE);
        return buildDir;
    }

    public static int getAttackPoliCost() throws GameActionException {
        int cost;
        if (targetNeutralHQIndex != -1 && hqTeams[targetNeutralHQIndex] == neutral) { // try to make it at least 50 more
            cost = 25 + hqInfluence[targetNeutralHQIndex] + GameConstants.EMPOWER_TAX;
        } else { // team = enemy
            cost = (int) Math.max(200, 0.5 * mySafetyBudget); // at least 200
        }
        return cost;
    }

    public static Direction makeAttackPolitician() throws GameActionException {
        log("Trying to build attack politician");

        log("targetIndex " + targetNeutralHQIndex);

        int cost;
        Direction scoutDir;
        if (targetNeutralHQIndex != -1 && hqTeams[targetNeutralHQIndex] == neutral) { // try to make it at least 50 more
            cost = 25 + hqInfluence[targetNeutralHQIndex] + GameConstants.EMPOWER_TAX;
            if (cost > 0.8 * mySafetyBudget) return null;

            // update attack delay
            hqAttackDelays[targetNeutralHQIndex] = (int) Math.ceil(2 * Math.sqrt(here.distanceSquaredTo(hqLocs[targetNeutralHQIndex]))
                    + RobotType.POLITICIAN.initialCooldown);
            scoutDir = here.directionTo(hqLocs[targetNeutralHQIndex]);

        } else { // team = enemy
            cost = (int) Math.max(200, 0.5 * mySafetyBudget); // at least 200
            if (cost > 0.8 * mySafetyBudget) return null;

            scoutDir = EXPLORE_DIRS[scoutCount % EXPLORE_DIRS.length];
        }

        int status = dir2int(scoutDir);
        CommManager.setStatus(status, true);


        Direction buildDir = tryBuild(RobotType.POLITICIAN, scoutDir, cost, ATTACK_POLI_ROLE);
        if (buildDir != null) {
            scoutCount++;
        }
        return buildDir;
    }

    //@rz please check this
    // looks good -rz
    public static Direction makeSuicidePolitician() throws GameActionException {
        int cost = (int) Math.min(mySafetyBudget,
                GameConstants.ROBOT_INFLUENCE_LIMIT / (4 * rc.getEmpowerFactor(us, 11)));

        log("Trying to build a suicide politician");
        for (Direction dir: CARD_DIRS) {
            MapLocation adjLoc = here.add(dir);
            if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                CommManager.setStatus(dir2int(dir), true);
                Actions.doBuildRobot(RobotType.POLITICIAN, dir, cost);
//                addKnownAlly(dir);
                log("Made suicide politician");
                return dir;
            }
        }
        return null;
    }

    public static Direction tryBuild(RobotType rt, Direction bestDir, int cost, Role role) throws GameActionException {
        Direction[] checkDirs = getClosestDirs(bestDir);
        // find direction to build
        for (Direction dir: checkDirs) {
            MapLocation adjLoc = here.add(dir);
            if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                Actions.doBuildRobot(rt, dir, cost);
                addRole(role, rc.senseRobotAtLocation(adjLoc));
                return dir;
            }
        }
        return null;

    }
}
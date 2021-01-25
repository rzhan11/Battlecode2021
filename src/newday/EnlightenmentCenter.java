package newday;

import battlecode.common.*;

import static newday.Comms.*;
import static newday.CommManager.*;
import static newday.Debug.*;
import static newday.HQTracker.*;
import static newday.Map.*;
import static newday.Role.*;
import static newday.Utils.*;

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
    public static MapLocation closestEnemyPolitician;


    public static int enemyMuckrakerDanger = 0;
    public static MapLocation closestEnemyMuckraker;
    public static int lastSeenMuckrakerRound = -100;

    public static int lastBid = 1;
    public static int lastTurnsVotes = 0;
    public static boolean wonLastVote = false;
    final public static double bidIncreaseScalingFactor = 1.5;
    final public static double bidDecreaseScalingFactor = 1.1;

    public static int myIncome;

    public static int MUCK_CAP = 100;
//    public static int SLAN_CAP = 50;
//    public static int DEFENSE_POLI_CAP = 50;

    // if we are in the corner, it is essential that we have a lot of politicians, so that our slanderers dont get pushed in
    final public static double CORNER_DEFENSE_POLI_MULTIPLIER = 1.5;
    final public static double EDGE_DEFENSE_POLI_MULTIPLIER = 1.25;


    // num rounds before sending again
    public static int[] hqAttackDelays;

    public static int targetNeutralHQIndex;

    public static int movesMade = 1;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        logi("v.newday.25.1432");

        Comms.writeXBounds();
        Comms.writeYBounds();

        // choose directions to explore, based on map edges that we can see
        EXPLORE_DIRS = getExploreDirs();

        SLANDERER_COSTS = new int[] {-1, 21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282, 310, 339, 368, 399, 431, 463, 497, 532, 568, 605, 643, 683, 724, 766, 810, 855, 902, 949};

        int dx = getWallXDist(here.x);
        int dy = getWallYDist(here.y);

//        if (dx <= Slanderer.MIN_WALL_DIST && dy <= Slanderer.MIN_WALL_DIST) {
//            DEFENSE_POLI_ROLE.ratio *= CORNER_DEFENSE_POLI_MULTIPLIER;
//            log("Corner multiplier " + DEFENSE_POLI_ROLE.ratio);
//        } else if (dx <= Slanderer.MIN_WALL_DIST || dy <= Slanderer.MIN_WALL_DIST) {
//            DEFENSE_POLI_ROLE.ratio *= EDGE_DEFENSE_POLI_MULTIPLIER;
//            log("Edge multiplier " + DEFENSE_POLI_ROLE.ratio);
//        }

        // poli
        hqAttackDelays = new int[MAX_HQ_COUNT];

        initRoles();
    }

    // code run each turn
    public static void turn() throws GameActionException {
        //
        updateRoleCounts();
        processMessages();

        // after processMessages
        broadcastHQSurround();

        updateEnemies();

        myIncome = mySlanTotalEarn + RobotType.ENLIGHTENMENT_CENTER.getPassiveInfluence(rc.getInfluence(), age, roundNum);


        updateNeutrals();

        // after updateEnemies
        updateMaxBudget();

        // bidding
        if (rc.getTeamVotes() < GameConstants.GAME_MAX_NUMBER_OF_ROUNDS / 2) {
            if (roundNum > 100 && mySafetyBudget > 100) {
                tryBid();
                // recalculate max budget AFTER making bets
                updateMaxBudget();
            }
        }

        logi("Budget: " + mySafetyBudget + " = " + rc.getInfluence() + " - " + enemyPoliticianDanger);

        if (!rc.isReady()) {
            return;
        }
//        //TODO TESTING REMOVE THIS
//        makeMuckraker(true);
//        if (true) return;

        // make a slanderer on the first turn
        if (spawnRound == 1 && movesMade <= MAX_EARLY_GAME_ROUND) {
            doEarlyGame();
            movesMade++;
            return;
        }

        if (spawnRound != 1 && movesMade <= MAX_CAPTURE_START_ROUND) {
            doCaptureStart();
            movesMade++;
            return;
        }

        // must be after updateNeutrals, updateEnemies
        updateRoleScores();

        // make a muckraker if we can't do anything else
        if (mySafetyBudget == 0 && rc.getInfluence() > 0) {
            makeMuckraker(true);
            return;
        }

        if (enemyMuckrakerCount > 0) {
            log("Emergency defense");
            Direction dir = makeDefendPolitician();
            if (dir != null) {
                return;
            } else {
                makeMuckraker(true);
                return;
            }
        }

//        if (DEFENSE_POLI_ROLE.count >= 8) {
//            if (EXPLORE_POLI_ROLE.count < 2) {
//                log("Explore poli");
//                makeExplorePolitician();
//                return;
//            }
//            if (MUCK_ROLE.count < 5) {
//                log("muck spam");
//                makeMuckraker(true);
//                return;
//            }
//        }

        // no visible enemy muckrakers
        if (DEFENSE_POLI_ROLE.score < SLAN_ROLE.score) {
            Direction dir = makeDefendPolitician();
            if (dir != null) {
                return;
            }
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
                        int diff = getAttackPoliCost(targetNeutralHQIndex) - mySafetyBudget;
                        int waitRounds = (int) Math.ceil(1.0 * diff / income);
                        if (waitRounds <= 3) {
                            tlog("Saving for attack");
                            STOP_BID = true;
                            break;
                        } else {
                            tlog("Skipped");
                        }
                    }
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


    public static int MAX_EARLY_GAME_ROUND = 9;

    public static void doEarlyGame() throws GameActionException {
        switch(movesMade) {
            case 1:
                makeSlanderer();
                return;
            case 2:
                makeExplorePolitician();
                return;
            case 3:
                makeExplorePolitician();
                return;
            case 4:
                makeExplorePolitician();
                return;
            case 5:
                makeSlanderer();
                return;
            case 6:
                makeDefendPolitician();
                return;
            case 7:
                makeSlanderer();
                return;
            case 8:
                makeExplorePolitician();
                return;
            case 9:
                makeSlanderer();
                return;
        }
    }

    public static int MAX_CAPTURE_START_ROUND = 6;

    public static void doCaptureStart() throws GameActionException {
        switch(movesMade) {
            case 1:
            case 2:
                makeMuckraker(true);
                return;
            case 3:
            case 4:
                makeDefendPolitician();
                return;
            case 5:
            case 6:
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
            lastSeenMuckrakerRound = roundNum;
        } else {
            closestEnemyMuckraker = null;
        }
//        log("Mucker " + closestEnemyMuckraker);

        ri = getClosest(here, enemyPoliticians, enemyPoliticianCount);
        if (ri != null) {
            closestEnemyPolitician = ri.location;
        } else {
            closestEnemyMuckraker = null;
        }

        // calculate enemyMuckrakerDanger
        long tempSum = 0;
        for (int i = enemyMuckrakerCount; --i >= 0;) {
            // u have to deal conviction + 1 damage to kill
//            tempSum += 1 + enemyMuckrakers[i].conviction;
            tempSum = Math.max(tempSum, 1 + enemyMuckrakers[i].conviction);
        }
        enemyMuckrakerDanger = (int) Math.min(GameConstants.ROBOT_INFLUENCE_LIMIT, tempSum);

        // calculate enemyPolitician danger
        double enemyRatio = rc.getEmpowerFactor(them, 0);
        enemyPoliticianDanger = 0;
        for (int i = enemyPoliticianCount; --i >= 0;) {
            // u have to deal conviction + 1 damage to kill
            enemyPoliticianDanger += getDamage(enemyPoliticians[i].conviction, enemyRatio);
        }
    }

    public static void updateNeutrals() throws GameActionException {
        // update delay
        for (int i = knownHQCount; --i >= 0;) {
            hqAttackDelays[i] = Math.max(0, hqAttackDelays[i] - 1);
        }

        // update nearest neutral hq
        targetNeutralHQIndex = -1;
//        int bestCost = P_INF;
//        int bestDist = P_INF;
        double bestScore = P_INF;
        for (int i = knownHQCount; --i >= 0;) {
            if(hqTeams[i] == neutral && hqAttackDelays[i] <= 0) {
                int dist = here.distanceSquaredTo(hqLocs[i]);
                int cost = getAttackPoliCost(i);
                double score = Math.sqrt(dist);
                score += Math.max((cost - mySafetyBudget) / myIncome, 0);
                score += cost / 25.0;
                if (score < bestScore) {
                    targetNeutralHQIndex = i;
//                    bestCost = hqInfluence[i];
//                    bestDist = dist;
                    bestScore = score;
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

        int bidAmount;
        if (wonLastVote) {
            bidAmount = (int) Math.floor(lastBid / bidDecreaseScalingFactor);
        } else {
            bidAmount = (int) Math.ceil(lastBid * bidIncreaseScalingFactor);
        }
        if (roundNum > 500) {
            bidAmount = Math.max(bidAmount, mySafetyBudget / 1000);
        }
        bidAmount = Math.max(1, bidAmount);

        int bidBudget = mySafetyBudget / 10;
        if (bidAmount <= bidBudget) {
            log("Bidding " + bidAmount);
            rc.bid(bidAmount);
            lastBid = bidAmount;
        } else {
            lastBid = bidAmount / 2;
        }
    }

    public static int lastBigMuckrakerRound = -100;
    final public static int BIG_MUCK_FREQ = 20;
    final public static int BIG_MUCK_COST = 143;

    public static int lastMedMuckrakerRound = -100;
    final public static int MED_MUCK_FREQ = 20;
    final public static int MED_MUCK_COST = 33;

    public static Direction makeMuckraker(boolean cheap) throws GameActionException {
        log("Trying to build muckraker");

        if (rc.getInfluence() == 0) {
            return null;
        }

        int cost = 1;

        // make increasingly expensive muckers over time
        if (!cheap && roundNum > 50) {
            // if our empower factor is somewhat low
            if (roundNum - lastBigMuckrakerRound > BIG_MUCK_FREQ
                    && BIG_MUCK_COST < 0.33 * mySafetyBudget) {
                cost = BIG_MUCK_COST;
            }
            if (cost == 1) {
                if (roundNum - lastMedMuckrakerRound > MED_MUCK_FREQ
                        && MED_MUCK_COST < 0.5 * mySafetyBudget) {
                    cost = MED_MUCK_COST;
                }
            }
        }

        Direction scoutDir = EXPLORE_DIRS[scoutCount % EXPLORE_DIRS.length];
        if (mySafetyBudget == 0 && closestEnemyPolitician != null) {
            scoutDir = here.directionTo(closestEnemyPolitician);
        }

        int status = dir2int(scoutDir);
        CommManager.setStatus(status, true);

        Direction buildDir = tryBuild(RobotType.MUCKRAKER, scoutDir, cost, MUCK_ROLE);
        if (buildDir != null) {
            if (cost == BIG_MUCK_COST) {
                lastBigMuckrakerRound = roundNum;
            } else if (cost == MED_MUCK_COST) {
                lastMedMuckrakerRound = roundNum;
            }
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

        // todo testing
//        if (true) {
        if (value >= minValue || cost == MAX_SLANDERER_COST) {
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

        int minCost = (int) Math.max(GameConstants.EMPOWER_TAX + 8, 0.01 * mySafetyBudget);
        if (mySafetyBudget > 100 && random() < 0.1) {
            minCost = 60;
        }


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

    public static int getAttackPoliCost(int index) throws GameActionException {
        int cost;
        if (index != -1 && hqTeams[index] == neutral) { // try to make it at least 50 more
            cost = 25 + hqInfluence[index] + GameConstants.EMPOWER_TAX;
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
            cost = (int) Math.max(61, 0.5 * mySafetyBudget); // at least 200
//            cost = Math.min(cost, 10000);
            if (cost > 0.8 * mySafetyBudget) return null;

            scoutDir = EXPLORE_DIRS[scoutCount % EXPLORE_DIRS.length];
        }

        int status = dir2int(scoutDir);
        CommManager.setStatus(status, true);


        Direction buildDir = tryBuild(RobotType.POLITICIAN, scoutDir, cost, ATTACK_POLI_ROLE);
        if (buildDir != null) {
            if (targetNeutralHQIndex != -1) {
                queueNormalMessage(hqBroadcasts[targetNeutralHQIndex], true);
                log("hi " + targetNeutralHQIndex + " " + hqBroadcasts[targetNeutralHQIndex].toString());
                if (CommManager.msgQueueCount == 1) { // if it is in the front of the queue, delay by 1
                    CommManager.useRepeatQueue = true;
                }
            }
            scoutCount++;
        }
        return buildDir;
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
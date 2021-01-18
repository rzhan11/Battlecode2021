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

    public static double muckrakerRatio = 0.75;
    public static double politicianRatio = 1.0;
    public static double slandererRatio = 1.0;


//    public static int enemyHQIndex = 0;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        Comms.writeXBounds();
        Comms.writeYBounds();

        // choose directions to explore, based on map edges that we can see
        EXPLORE_DIRS = getExploreDirs();

        SLANDERER_COSTS = new int[] {-1, 21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282, 310, 339, 368, 399, 431, 463, 497, 532, 568, 605, 643, 683, 724, 766, 810, 855, 902, 949};

        initRoles();
    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateRoleCounts();
        processMessages();

        updateMaxBudget();
        updateEnemies();
        broadcastHQSurround();

        // make a slanderer on the first turn
        if (roundNum == 1) {
            makeSlanderer();
            return;
        }

        // todo improve bidding strategy
        // bidding is OKish now, games are not normally won by bidding so its fine
        if (rc.getTeamVotes() < GameConstants.GAME_MAX_NUMBER_OF_ROUNDS / 2) {
            if (mySafetyBudget > 100) {
                tryBid();
            }
        }

        // calculate max budget AFTER making bets
        updateMaxBudget();
        logi("Budget: " + mySafetyBudget + " = " + rc.getInfluence() + " - " + enemyPoliticianDanger);

        if (!rc.isReady()) {
            return;
        }

//        if (false) {
//            makeExplorePolitician();
//            return;
//        }

        // spawn muck scouts
        if (scoutCount < EXPLORE_DIRS.length) {
            Direction dir = makeMuckraker(true);
            return;
        }

        double muckrakerScore = MUCK_ROLE.count / muckrakerRatio;
        double politicianScore = DEFENSE_POLI_ROLE.count / politicianRatio;
        double slandererScore = SLAN_ROLE.count / slandererRatio;

        log("BUILD SCORES");
        log("Muckraker: " + muckrakerScore);
        log("Politician: " + politicianScore);
        log("Slanderer: " + slandererScore);

        if (enemyMuckrakerCount > 0) {
            log("Emergency defense");
            Direction dir = makeDefendPolitician();
            return;
        }

        if (DEFENSE_POLI_ROLE.count > 3 && SLAN_ROLE.count > 3) {
            if (EXPLORE_POLI_ROLE.count < 2) {
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
        if (politicianScore < slandererScore) {
            // 2/3 of politicans are defend
            // 1/3 of politicians are attack
            if (random() < 0.66) {
                Direction dir = makeDefendPolitician();
                if (dir != null) {
                    return;
                }
            } else {
                Direction dir = makeAttackPolitician();
                if (dir != null) {
                    return;
                }
            }
        } else { // consider muckraker vs slanderer
            if (muckrakerScore < slandererScore) {
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
        }

        // build a cheap muckraker if i can't do anything else
        if (getNumOpenDirs() >= 2) {
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
        log("Mucker " + closestEnemyMuckraker);

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

    public static void updateMaxBudget() {
        mySafetyBudget = rc.getInfluence() - enemyPoliticianDanger;
    }

    public static void tryBid() throws GameActionException {
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

    public static Direction makeMuckraker(boolean cheap) throws GameActionException {
        log("Trying to build muckraker");

        if (mySafetyBudget < 1) {
            return null;
        }

        int cost = 1;

        // make increasingly expensive muckers over time
        // if we can afford it, 50% chance we make an "expensive" muckraker
        int targetConviction = (int) Math.ceil(1.0 * (age + 1) / 100);
        int targetCost = RobotType.MUCKRAKER.getInfluenceCostForConviction(targetConviction);
        if (!cheap && targetCost < mySafetyBudget) {
            if (random() < 0.5) {
                cost = targetCost;
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

        int minCost = GameConstants.EMPOWER_TAX + 4;
        minCost += Math.min(8, 8 * roundNum / GameConstants.GAME_MAX_NUMBER_OF_ROUNDS);


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
        } else {
            scoutDir = getRandomDir();
        }

        int status = dir2int(scoutDir) + (1 << 3);
        CommManager.setStatus(status, true);

        Direction buildDir = tryBuild(RobotType.POLITICIAN, scoutDir, cost, DEFENSE_POLI_ROLE);
        return buildDir;
    }

    public static Direction makeAttackPolitician() throws GameActionException {
        log("Trying to build attack politician");

        // todo make cost based on neutral hq costs
        // 177 = poli can kill neutral hq of 500 in 3 hits
        int cost = (int) Math.max(177, 0.5 * mySafetyBudget);
        if (cost > 0.75 * mySafetyBudget) { // if the cost is more than 75% of our budget, dont spend
            return null;
        }

        Direction scoutDir = EXPLORE_DIRS[scoutCount % EXPLORE_DIRS.length];
        int status = dir2int(scoutDir);
        CommManager.setStatus(status, true);

        Direction buildDir = tryBuild(RobotType.POLITICIAN, scoutDir, cost, ATTACK_POLI_ROLE);
        if (buildDir != null) {
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
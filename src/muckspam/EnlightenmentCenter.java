package muckspam;

import battlecode.common.*;

import static muckspam.Comms.*;
import static muckspam.Debug.*;
import static muckspam.Map.*;
import static muckspam.Utils.*;

public class EnlightenmentCenter extends Robot {
    // constants

    final public static int MAX_KNOWN_ALLIES = 300;

    public static int[] SLANDERER_COSTS;
    final public static int MIN_SLANDERER_COST = 21;
    final public static int BEST_SLANDERER_COST = 41;
    final public static int MAX_SLANDERER_COST = 399;

    final public static int EARLY_MUCKRAKERS_COUNT = 4;

    // variables

    public static int[] myMuckrakers;
    public static int myMuckrakerCount = 0;

    public static int[] myPoliticians;
    public static int myPoliticianCount = 0;

    public static int[] mySlanderers;
    public static int[] mySlandererSpawnRounds;
    public static int[] mySlandererEarns; // amt of influence genned per turn
    public static int mySlandererCount = 0;

    // total amount of influence in slanderers
    public static int mySlandererInfluence = 0;
    // amount of influence generated by slanderers per turn
    public static int mySlandererTotalEarn = 0;
    // total amount of influence that can be generated by slanderers
    public static int mySlandererTotalValue = 0;

    public static int myUnitCount = 0; // myMuckrakersCount + myPoliticianCount + mySlandererCount

    public static int processMessageIndex = 0;

    public static int scoutCount = 0;

    public static int mySafetyBudget;
    public static int enemyPoliticianDanger = 0;

    public static int enemyMuckrakerDanger = 0;
    public static MapLocation closestEnemyMuckraker;

    public static double muckrakerRatio = 2.0;
    public static double politicianRatio = 3.0;
    public static double slandererRatio = 1.0;



//    public static int enemyHQIndex = 0;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        Comms.writeXBounds();
        Comms.writeYBounds();

        SLANDERER_COSTS = new int[] {-1, 21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282, 310, 339, 368, 399, 431, 463, 497, 532, 568, 605, 643, 683, 724, 766, 810, 855, 902, 949};

        myMuckrakers = new int[MAX_KNOWN_ALLIES];
        myPoliticians = new int[MAX_KNOWN_ALLIES];

        mySlanderers = new int[MAX_KNOWN_ALLIES];
        mySlandererSpawnRounds = new int[MAX_KNOWN_ALLIES];
        mySlandererEarns = new int[MAX_KNOWN_ALLIES];

    }

    // todo different behavior when we have a significant number of units
    // e.g. units > 500
    // code run each turn
    public static void turn() throws GameActionException {
        updateKnownAllies();
        processMessages(myMuckrakers, myMuckrakerCount, "muck");
        processMessages(myPoliticians, myPoliticianCount, "poli");
        processMessages(mySlanderers, mySlandererCount, "slan");
        log(myMuckrakerCount + " " + myPoliticianCount + " " + mySlandererCount);

        updateMaxBudget();
        updateEnemies();

        // make a slanderer on the first turn
        if (roundNum == 1) {
            makeSlanderer();
            return;
        }

        // TODO: make better bidding strategy
        // crude bidding based on num rounds left
        if (rc.getTeamVotes() < GameConstants.GAME_MAX_NUMBER_OF_ROUNDS / 2) {
            if (roundNum >= 300) {
                int roundsLeft = GameConstants.GAME_MAX_NUMBER_OF_ROUNDS - roundNum + 1;
                int amt = mySafetyBudget/ Math.min(15, roundsLeft);
                if (amt > 0) {
                    rc.bid(amt);
                }
            }
        }

        // calculate max budget AFTER making bets
        updateMaxBudget();
        logi("budget " + mySafetyBudget + " = " + rc.getInfluence() + " - " + enemyPoliticianDanger);

        if (!rc.isReady()) {
            return;
        }

        // spawn muck scouts
        if (scoutCount < EARLY_MUCKRAKERS_COUNT) {
            Direction dir = makeMuckraker(true);
            if (dir != null) {
                scoutCount++;
            }
            return;
        }

        double muckrakerScore = myMuckrakerCount / muckrakerRatio;
        double politicianScore = myPoliticianCount / politicianRatio;
        double slandererScore = mySlandererCount / slandererRatio;

        log("BUILD SCORES");
        log("Muckraker: " + muckrakerScore);
        log("Politician: " + politicianScore);
        log("Slanderer: " + slandererScore);

        if (enemyMuckrakerCount > 0) {
            log("Emergency defense");
            Direction dir = makeDefendPolitician();
            return;
        }

        if (myMuckrakerCount < 5 && myPoliticianCount > 3 && rc.getRoundNum() > 50) {
            log("muck spam");
            makeMuckraker(true);
            return;
        }

        // no visible enemy muckrakers
        if (politicianScore < slandererScore) {
            // 2/3 of politicans are defend
            // 1/3 of politicians are attack
            if (Math.random() < 0.66) {
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

    // when we hit the unit cap, delete from the front
    public static int muckrakerFrontDelete = 0;
    public static int politicianFrontDelete = 0;
    public static int slandererFrontDelete = 0;

    public static void addKnownAlly(Direction dir) throws GameActionException {
        RobotInfo ri = rc.senseRobotAtLocation(rc.adjacentLocation(dir));
        switch(ri.type) {
            case MUCKRAKER:
                addKnownMuckraker(ri.getID());
                return;
            case POLITICIAN:
                addKnownPolitician(ri.getID());
                return;
            case SLANDERER:
                addKnownSlanderer(ri.getID(), ri.influence);
                return;
        }
    }

    public static void addKnownMuckraker(int id) {
        if (myMuckrakerCount < MAX_KNOWN_ALLIES) {
            myMuckrakers[myMuckrakerCount] = id;
            myMuckrakerCount++;
        } else {
            muckrakerFrontDelete = (muckrakerFrontDelete + 1) % MAX_KNOWN_ALLIES;
            myMuckrakers[muckrakerFrontDelete] = id;
        }
    }

    public static void addKnownPolitician(int id) {
        if (myPoliticianCount < MAX_KNOWN_ALLIES) {
            myPoliticians[myPoliticianCount] = id;
            myPoliticianCount++;
        } else {
            politicianFrontDelete = (politicianFrontDelete + 1) % MAX_KNOWN_ALLIES;
            myPoliticians[politicianFrontDelete] = id;
        }
    }

    public static void addKnownSlanderer(int id, int influence) {
        if (mySlandererCount < MAX_KNOWN_ALLIES) {
            mySlanderers[mySlandererCount] = id;
            mySlandererSpawnRounds[mySlandererCount] = roundNum + 1;
            mySlandererEarns[mySlandererCount] = HardCode.getPassiveInfluence(influence);
            mySlandererCount++;
        } else {
            slandererFrontDelete = (slandererFrontDelete + 1) % MAX_KNOWN_ALLIES;
            mySlanderers[slandererFrontDelete] = id;
            mySlandererSpawnRounds[slandererFrontDelete] = roundNum + 1;
            mySlandererEarns[slandererFrontDelete] = HardCode.getPassiveInfluence(influence);
        }
    }

    public static void updateKnownAllies() throws GameActionException {
        for (int i = myMuckrakerCount; --i >= 0;) {
            if (!rc.canGetFlag(myMuckrakers[i])) {
                // delete dead muckrakers
                myMuckrakerCount--;
                myMuckrakers[i] = myMuckrakers[myMuckrakerCount];
            }
        }

        for (int i = myPoliticianCount; --i >= 0;) {
            if (!rc.canGetFlag(myPoliticians[i])) {
                // delete dead politicians
                myPoliticianCount--;
                myPoliticians[i] = myPoliticians[myPoliticianCount];
            }
        }

        mySlandererInfluence = 0;
        mySlandererTotalEarn = 0;
        mySlandererTotalValue = 0;
        for (int i = mySlandererCount; --i >= 0;) {
            if (!rc.canGetFlag(mySlanderers[i])) {
                // delete dead slanderers
                mySlandererCount--;
                mySlanderers[i] = mySlanderers[mySlandererCount];
                mySlandererSpawnRounds[i] = mySlandererSpawnRounds[mySlandererCount];
                mySlandererEarns[i] = mySlandererEarns[mySlandererCount];
            } else if (roundNum - mySlandererSpawnRounds[i] >= GameConstants.CAMOUFLAGE_NUM_ROUNDS) {
                // checks if slanderers have turned to politicians
                // if so, delete from slanderer array and add to politician array
                addKnownPolitician(mySlanderers[i]);
                mySlandererCount--;
                mySlanderers[i] = mySlanderers[mySlandererCount];
                mySlandererSpawnRounds[i] = mySlandererSpawnRounds[mySlandererCount];
                mySlandererEarns[i] = mySlandererEarns[mySlandererCount];
            } else {
                // count value of sladnerers
                int earn = mySlandererEarns[i];
                int earnRounds = GameConstants.EMBEZZLE_NUM_ROUNDS - roundNum + mySlandererSpawnRounds[i];
                mySlandererInfluence += SLANDERER_COSTS[earn];
                if (earnRounds > 0) {
                    mySlandererTotalEarn += earn;
                    mySlandererTotalValue += earn * earnRounds;
                }
            }
        }
        log("total influence " + mySlandererInfluence);
        log("total earn " + mySlandererTotalEarn);
        log("total value " + mySlandererTotalValue);

        myUnitCount = myMuckrakerCount + myPoliticianCount + mySlandererCount;
    }

    final public static int MIN_MESSAGE_BYTECODE = 5000;

    public static void processMessages(int[] ids, int length, String str) throws GameActionException {
        if (length == 0) {
            logi("No " + str + " messages");
            return;
        }

        processMessageIndex = processMessageIndex % length;
        int count = length;

//        Debug.SILENCE_LOGS = true;
        // first part
        for (int i = processMessageIndex; --i >= 0;) {
            if (Clock.getBytecodesLeft() > MIN_MESSAGE_BYTECODE) {
                if ((rc.getFlag(ids[i]) & TYPE_MASK) == 0) continue; // if it is a unit broadcast
                Comms.readMessage(ids[i]);
            } else {
                count = processMessageIndex - 1 - i;
                break;
            }
        }
        if (Clock.getBytecodesLeft() > MIN_MESSAGE_BYTECODE) {
            // second part
            for (int i = length; --i >= 0;) { // intentionally i >= 0 to save bytecode
                if (Clock.getBytecodesLeft() > MIN_MESSAGE_BYTECODE) {
                    if ((rc.getFlag(ids[i]) & TYPE_MASK) == 0) continue; // if it is a unit broadcast
                    Comms.readMessage(ids[i]);
                } else {
                    count = processMessageIndex + length - 1 - i;
                    break;
                }
            }
        }
        if (count > length) {
            count = length;
        }
//        Debug.SILENCE_LOGS = false;

        logi("Processed " + count + "/" + length + " " + str + " messages");
        if (count >= length) {
            processMessageIndex = 0;
        } else {
            processMessageIndex = (processMessageIndex - count + length) % length;
        }
    }

    public static void updateMaxBudget() {
        mySafetyBudget = rc.getInfluence() - enemyPoliticianDanger;
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
            if (Math.random() < 0.5) {
                cost = targetCost;
            }
        }

        int scoutDirIndex = scoutCount % 8;
        Direction scoutDir = DIRS[scoutDirIndex];

        int status = scoutDirIndex;
        CommManager.setStatus(status);

        Direction buildDir = tryBuild(RobotType.MUCKRAKER, scoutDir, cost);
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
        double minValue = mySlandererTotalValue / 2.0;
        double value = cost * GameConstants.EMBEZZLE_NUM_ROUNDS;
        if (value >= minValue || cost == MAX_SLANDERER_COST) {
            Direction buildDir = tryBuild(RobotType.SLANDERER, Direction.NORTH, cost);
            return buildDir;
        } else {
            return null;
        }

    }

    public static Direction makeDefendPolitician() throws GameActionException {
        log("Trying to build defensive politician");

        int minCost = GameConstants.EMPOWER_TAX + 4;

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
        int scoutDirIndex = dir2int(scoutDir);

        int status = scoutDirIndex + (1 << 3);
        CommManager.setStatus(status);

        Direction buildDir = tryBuild(RobotType.POLITICIAN, scoutDir, cost);
        return buildDir;
    }

    public static Direction makeAttackPolitician() throws GameActionException {
        log("Trying to build attack politician");

        int cost = Math.min(250, Math.max(roundNum, GameConstants.EMPOWER_TAX + 4));
        if (cost > 0.5 * mySafetyBudget) {
            return null;
        }

        int scoutDirIndex = scoutCount % 8;
        Direction scoutDir = DIRS[scoutDirIndex];

        int status = scoutDirIndex;
        CommManager.setStatus(status);

        Direction buildDir = tryBuild(RobotType.POLITICIAN, scoutDir, cost);
        if (buildDir != null) {
            scoutCount++;
        }
        return buildDir;
    }

    public static Direction tryBuild(RobotType rt, Direction bestDir, int cost) throws GameActionException {
        Direction[] checkDirs = getClosestDirs(bestDir);
        // find direction to build
        for (Direction dir: checkDirs) {
            MapLocation adjLoc = here.add(dir);
            if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                Actions.doBuildRobot(rt, dir, cost);
                addKnownAlly(dir);
                return dir;
            }
        }
        return null;

    }
}
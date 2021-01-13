package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Map.*;
import static template.Utils.*;

public class EnlightenmentCenter extends Robot {
    // final constants

    final public static int MAX_KNOWN_ALLIES = 300;

    final public static int[] SLANDERER_COSTS = new int[] {21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282, 310, 339, 368, 399};
    final public static int MIN_SLANDERER_COST = 21;
    final public static int BEST_SLANDERER_COST = 41;
    final public static int MAX_SLANDERER_COST = 399;

    final public static int EARLY_MUCKRAKERS_COUNT = 4;

    // variables

    public static int[] myMuckrakers = new int[MAX_KNOWN_ALLIES];
    public static int myMuckrakerCount = 0;

    public static int[] myPoliticians = new int[MAX_KNOWN_ALLIES];
    public static int myPoliticianCount = 0;

    public static int[] mySlanderers = new int[MAX_KNOWN_ALLIES];
    public static int[] mySlandererSpawnRounds = new int[MAX_KNOWN_ALLIES];
    public static int mySlandererCount = 0;


    public static int processMessageIndex = 0;

    public static int scoutCount = 0;

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

    }

    // todo different behavior when we have a significant number of units
    // e.g. units > 1000
    // code run each turn
    public static void turn() throws GameActionException {
        updateKnownAllies();
        processMessages(myMuckrakers, myMuckrakerCount);
        processMessages(myPoliticians, myPoliticianCount);
        processMessages(mySlanderers, mySlandererCount);


        updateEnemies();

        // make a slanderer on the first turn
        if (roundNum == 1) {
            makeSlanderer();
            return;
        }

        // TODO: make better bidding strategy
        // crude bidding based on num rounds left
        if (rc.getTeamVotes() < GameConstants.GAME_MAX_NUMBER_OF_ROUNDS / 2) {
            if (roundNum >= 500) {
                int roundsLeft = GameConstants.GAME_MAX_NUMBER_OF_ROUNDS - roundNum + 1;
                int amt = rc.getInfluence() / Math.min(10, roundsLeft);
                if (amt > 0) {
                    rc.bid(amt);
                }
            }
        }

        // TESTING PURPOSES ONLY
//        if (roundNum >= 400) {
//            log("RESIGNING");
//            rc.resign();
//        }

        if (!rc.isReady()) {
            return;
        }

        // spawn muck scouts
        if (scoutCount < EARLY_MUCKRAKERS_COUNT) {
            Direction dir = makeMuckraker();
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

        if (rc.getRobotCount() > 600) {
            log("Greed");
            makeSlanderer();
            return;
        }

        if (enemyMuckrakerCount > 0) {
            log("Emergency defense");
            makeDefendPolitician();
            return;
        }

        // no visible enemy muckrakers
        if (politicianScore < slandererScore) {
            // 2/3 of politicans are defend
            // 1/3 of politicians are attack
            if (Math.random() < 2.0 / 3) {
                makeDefendPolitician();
                return;
            } else {
                makeAttackPolitician();
                return;
            }
        } else { // consider muckraker vs slanderer
            if (muckrakerScore < slandererScore) {
                makeMuckraker();
                return;
            } else {
                makeSlanderer();
                return;
            }
        }
    }

    // when we hit the unit cap, delete from the front
    public static int muckrakerFrontDelete = 0;
    public static int politicianFrontDelete = 0;
    public static int slandererFrontDelete = 0;

    public static void addKnownAlly(Direction dir) throws GameActionException {
        RobotInfo ri = rc.senseRobotAtLocation(rc.adjacentLocation(dir));
        addKnownAlly(ri.getID(), ri.type);
    }

    public static void addKnownAlly(int id, RobotType rt) throws GameActionException {
        switch(rt) {
            case MUCKRAKER:
                if (myMuckrakerCount < MAX_KNOWN_ALLIES) {
                    myMuckrakers[myMuckrakerCount] = id;
                    myMuckrakerCount++;
                } else {
                    muckrakerFrontDelete = (muckrakerFrontDelete + 1) % MAX_KNOWN_ALLIES;
                    myMuckrakers[muckrakerFrontDelete] = id;
                }
                break;

            case POLITICIAN:
                if (myPoliticianCount < MAX_KNOWN_ALLIES) {
                    myPoliticians[myPoliticianCount] = id;
                    myPoliticianCount++;
                } else {
                    politicianFrontDelete = (politicianFrontDelete + 1) % MAX_KNOWN_ALLIES;
                    myPoliticians[politicianFrontDelete] = id;
                }
                break;

            case SLANDERER:
                if (mySlandererCount < MAX_KNOWN_ALLIES) {
                    mySlanderers[mySlandererCount] = id;
                    mySlandererSpawnRounds[mySlandererCount] = roundNum + 1;
                    mySlandererCount++;
                } else {
                    slandererFrontDelete = (slandererFrontDelete + 1) % MAX_KNOWN_ALLIES;
                    mySlanderers[slandererFrontDelete] = id;
                    mySlandererSpawnRounds[slandererFrontDelete] = roundNum + 1;
                }
                break;

            default:
                return;
        }
    }

    // todo rename known allies to "my children"
    // todo have slanderers send messages when they get converted to politicians
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

        for (int i = mySlandererCount; --i >= 0;) {
            if (!rc.canGetFlag(mySlanderers[i])) {
                // delete dead slanderers
                mySlandererCount--;
                mySlanderers[i] = mySlanderers[mySlandererCount];
                mySlandererSpawnRounds[i] = mySlandererSpawnRounds[mySlandererCount];
            } else if (roundNum - mySlandererSpawnRounds[i] >= GameConstants.CAMOUFLAGE_NUM_ROUNDS) {
                // checks if slanderers have turned to politicians
                // if so, delete from slanderer array and add to politician array
                addKnownAlly(mySlanderers[i], RobotType.POLITICIAN);
                mySlandererCount--;
                mySlanderers[i] = mySlanderers[mySlandererCount];
                mySlandererSpawnRounds[i] = mySlandererSpawnRounds[mySlandererCount];
            }
        }
    }

    public static void processMessages(int[] ids, int length) throws GameActionException {
        if (length == 0) {
            return;
        }

        processMessageIndex = processMessageIndex % length;
        int count = length;

        Debug.SILENCE_LOGS = true;
        for (int i = 0; i < length; i++) {
            if (Clock.getBytecodesLeft() > 5000) {
                Comms.readMessage(ids[(i + processMessageIndex) % length]);
            } else {
                count = i;
                break;
            }
        }
        Debug.SILENCE_LOGS = false;

        logi("Processed " + count + "/" + length + " messages");
        if (count == length) {
            processMessageIndex = 0;
        } else {
            processMessageIndex = (processMessageIndex + count) % length;
        }
    }

    public static void updateEnemies() throws GameActionException {
        RobotInfo ri = getClosest(here, enemyMuckrakers, enemyMuckrakerCount);
        if (ri != null) {
            closestEnemyMuckraker = ri.location;
        } else {
            closestEnemyMuckraker = null;
        }
        log("Mucker " + closestEnemyMuckraker);

        enemyMuckrakerDanger = 0;
        for (int i = enemyMuckrakerCount; --i >= 0;) {
            enemyMuckrakerDanger += enemyMuckrakers[i].conviction;
        }
    }

    public static Direction makeMuckraker() throws GameActionException {
        log("Trying to build muckraker");

        if (rc.getInfluence() < 1) {
            return null;
        }

        // make increasingly expensive muckers over time
        // cap at 33, since this is the max cost of muckeres that allows for positive trades against politicians
        // 33 influence = 24 conviction = politician of 34 has to kill
        int scalingConviction = (int) Math.ceil(1.0 * (age + 1) / 50);
        int targetConviction = Math.min(33, scalingConviction);
        int cost = RobotType.MUCKRAKER.getInfluenceCostForConviction(targetConviction);
        // if we can't make the muckraker at the cost we want, then just make a super cheap one
        if (cost > rc.getInfluence()) {
            cost = 1;
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
        int budget = rc.getInfluence();
        int cost = -1;
        if (budget >= MAX_SLANDERER_COST) {
            cost = MAX_SLANDERER_COST;
        } else {
            if (budget < SLANDERER_COSTS[0]) {
                return null;
            }
            for (int i = 1; i < SLANDERER_COSTS.length; i++) {
                if (budget < SLANDERER_COSTS[i]) {
                    cost = SLANDERER_COSTS[i - 1];
                    break;
                }
            }
        }

        Direction buildDir = tryBuild(RobotType.SLANDERER, Direction.NORTH, cost);
        return buildDir;
    }

    public static Direction makeDefendPolitician() throws GameActionException {
        log("Trying to build defensive politician");

        int cost = GameConstants.EMPOWER_TAX + Math.max((int) Math.ceil(enemyMuckrakerDanger * 1.25), 4);
        if (cost > rc.getInfluence()) {
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

        int cost = Math.min(250, Math.max(roundNum, GameConstants.EMPOWER_TAX + 1));
        if (cost > 0.5 * rc.getInfluence()) {
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

package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Map.*;
import static template.Utils.*;

public class EnlightenmentCenter extends Robot {
    // final constants

    final public static int MAX_KNOWN_ALLIES = 1000;

    final public static int[] SLANDERER_COSTS = new int[] {21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282, 310, 339, 368, 399};
    final public static int MIN_SLANDERER_COST = 21;
    final public static int BEST_SLANDERER_COST = 41;
    final public static int MAX_SLANDERER_COST = 399;

    final public static int EARLY_MUCKRAKERS_COUNT = 4;

    // variables

    public static int[] knownAllies = new int[MAX_KNOWN_ALLIES];
    public static RobotType[] knownAlliesType = new RobotType[MAX_KNOWN_ALLIES];
    public static int knownAlliesCount = 0;
    public static int processMessageIndex = 0;

    public static int scoutCount = 0;

    public static int slanderersMade;

    public static int liveMuckrakers = 0;
    public static int liveSlanderers = 0;
    public static int livePoliticians = 0;

    public static int enemyMuckrakerDanger = 0;
    public static MapLocation closestEnemyMuckraker;

    public static double muckrakerRatio = 4.0;
    public static double politicianRatio = 4.0;
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
        processMessages();


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
                int amt = rc.getInfluence() / Math.min(25, roundsLeft);
                if (amt > 0) {
                    rc.bid(amt);
                }
            }
        }

        // TESTING PURPOSES ONLY
        if (roundNum >= 400) {
            log("RESIGNING");
            rc.resign();
        }

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

        double muckrakerScore = liveMuckrakers / muckrakerRatio;
        double politicianScore = livePoliticians / politicianRatio;
        double slandererScore = liveSlanderers / slandererRatio;

        log("BUILD SCORES");
        log("Muckraker: " + muckrakerScore);
        log("Politician: " + politicianScore);
        log("Slanderer: " + slandererScore);

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

    public static int frontDelete = 0;

    public static void addKnownAlly(Direction dir) throws GameActionException {
        RobotInfo ri = rc.senseRobotAtLocation(rc.adjacentLocation(dir));
        addKnownAlly(ri.getID(), ri.type);
    }

    public static void addKnownAlly(int id, RobotType rt) throws GameActionException {
        int i;
        if (knownAlliesCount < MAX_KNOWN_ALLIES) {
            i = knownAlliesCount;
            knownAlliesCount++;
        } else { // start deleting from the front
            i = frontDelete;
            frontDelete = (frontDelete + 1) % MAX_KNOWN_ALLIES;
        }
        knownAllies[i] = id;
        knownAlliesType[i] = rt;
    }

    // todo rename known allies to "my children"
    // todo have slanderers send messages when they get converted to politicians
    public static void updateKnownAllies() throws GameActionException {
        liveMuckrakers = 0;
        livePoliticians = 0;
        liveSlanderers = 0;
        for (int i = knownAlliesCount; --i >= 0;) {
            if (!rc.canGetFlag(knownAllies[i])) {
                // delete dead allies
                knownAlliesCount--;
                knownAllies[i] = knownAllies[knownAlliesCount];
                knownAlliesType[i] = knownAlliesType[knownAlliesCount];
            } else { // ally is alive
                switch (knownAlliesType[i]) {
                    case MUCKRAKER:
                        liveMuckrakers++;
                        break;
                    case POLITICIAN:
                        livePoliticians++;
                        break;
                    case SLANDERER:
                        liveSlanderers++;
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public static void processMessages() throws GameActionException {
        if (knownAlliesCount == 0) {
            return;
        }

        processMessageIndex = processMessageIndex % knownAlliesCount;
        int count = knownAlliesCount;

        Debug.SILENCE_LOGS = true;
        for (int i = 0; i < knownAlliesCount; i++) {
            if (Clock.getBytecodesLeft() > 5000) {
                Comms.readMessage(knownAllies[(i + processMessageIndex) % knownAlliesCount]);
            } else {
                count = i;
                break;
            }
        }
        Debug.SILENCE_LOGS = false;

        logi("Processed " + count + "/" + knownAlliesCount + " messages");
        if (count == knownAlliesCount) {
            processMessageIndex = 0;
        } else {
            processMessageIndex = (processMessageIndex + count) % knownAlliesCount;
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

        int targetConviction = (int) Math.ceil(1.0 * (age + 1) / 25);
        int cost = RobotType.MUCKRAKER.getInfluenceCostForConviction(targetConviction);

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

package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Map.*;
import static template.Utils.*;

public class EnlightenmentCenter extends Robot {

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

    // final constants


    final public static int MAX_KNOWN_ALLIES = 1000;

    final public static int[] SLANDERER_COSTS = new int[] {21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282, 310, 339, 368, 399};
    final public static int MIN_SLANDERER_COST = 21;
    final public static int BEST_SLANDERER_COST = 41;
    final public static int MAX_SLANDERER_COST = 399;

    final public static int EARLY_MUCKRAKERS_COUNT = 8;

    // variables

    public static int[] knownAllies = new int[MAX_KNOWN_ALLIES];
    public static RobotType[] knownAlliesType = new RobotType[MAX_KNOWN_ALLIES];
    public static int knownAlliesCount = 0;

    public static int scoutCount = 0;

    public static int slanderersMade;

    public static int liveMuckrakers = 0;
    public static int liveSlanderers = 0;
    public static int livePoliticians = 0;

//    public static int enemyHQIndex = 0;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        Comms.writeXBounds();
        Comms.writeYBounds();

    }

    // TODO add bidding
    // code run each turn
    public static void turn() throws GameActionException {
        updateKnownAllies();
        processMessages();

        // make a slanderer on the first turn
        if (roundNum == 1) {
            makeSlanderer();
            return;
        }

        // TODO: make better bidding strategy
        // crude bidding based on num rounds left
        if (roundNum >= 500) {
            int roundsLeft = GameConstants.GAME_MAX_NUMBER_OF_ROUNDS - roundNum + 1;
            int amt = rc.getInfluence() / Math.min(10, roundsLeft);
            if (amt > 0) {
                rc.bid(amt);
            }
        }

        // TESTING PURPOSES ONLY
//        if (roundNum >= 1000) {
//            rc.resign();
//        }

        if (!rc.isReady()) {
            return;
        }

        // spawn muck scouts
        if (scoutCount < EARLY_MUCKRAKERS_COUNT && rc.getInfluence() >= 1) {
            for (Direction dir: getClosestDirs(DIRS[scoutCount])) {
                MapLocation adjLoc = here.add(dir);
                if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                    CommManager.setStatus(scoutCount % 8);
                    Actions.doBuildRobot(RobotType.MUCKRAKER, dir, 1);
                    addKnownAlly(dir);
                    scoutCount++;
                    return;
                }
            }
            return;
        }


        if (roundNum % 50 == 0) {
            slanderersMade = Math.max(slanderersMade - 5, 0);
        }
        if (slanderersMade < 10 && rc.getInfluence() >= MIN_SLANDERER_COST) {
            Direction dir = makeSlanderer();
            if (dir != null) {
                slanderersMade++;
            }
            return;
        }

        if (rc.getInfluence() > 200) {
            makePolitician();
            return;
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

    public static void updateKnownAllies() throws GameActionException {
        // delete dead allies
        for (int i = knownAlliesCount; --i >= 0;) {
            if (!rc.canGetFlag(knownAllies[i])) {
                knownAlliesCount--;
                knownAllies[i] = knownAllies[knownAlliesCount];
                knownAlliesType[i] = knownAlliesType[knownAlliesCount];
            }
        }
    }

    public static void processMessages() throws GameActionException {
//        log("Processing " + knownAlliesCount + " messages");
        for (int i = knownAlliesCount; --i >= 0;) {
//            tlog(knownAllies[i] + " " + knownAlliesType[i]);
            Comms.readMessage(knownAllies[i]);
        }
    }

    public static Direction makeSlanderer() throws GameActionException {
        // determine cost of slanderer
        int budget = rc.getInfluence();
        int cost = -1;
        if (budget >= MAX_SLANDERER_COST) {
            cost = MAX_SLANDERER_COST;
        } else {
            for (int i = 0; i < SLANDERER_COSTS.length; i++) {
                if (budget < SLANDERER_COSTS[i]) {
                    cost = SLANDERER_COSTS[i - 1];
                    break;
                }
            }
        }
        // find direction to build slanderer
        for (Direction dir: DIRS) {
            MapLocation adjLoc = here.add(dir);
            if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                Actions.doBuildRobot(RobotType.SLANDERER, dir, cost);
                addKnownAlly(dir);
                return dir;
            }
        }
        return null;
    }

    public static Direction makePolitician() throws GameActionException {
        // determine cost of politician
        int cost = rc.getInfluence() * 9 / 10;
        // find direction to build slanderer
        for (Direction dir: DIRS) {
            MapLocation adjLoc = here.add(dir);
            if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                tlog("Scout " + scoutCount % 8);
                CommManager.setStatus(scoutCount % 8);
                Actions.doBuildRobot(RobotType.POLITICIAN, dir, cost);
                addKnownAlly(dir);
                return dir;
            }
        }
        return null;
    }
}

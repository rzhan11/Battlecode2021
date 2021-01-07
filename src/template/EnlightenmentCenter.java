package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Map.*;

public class EnlightenmentCenter extends Robot {

    public static void run() throws GameActionException {
        // turn 1
        try {
            updateTurnInfo();
            postUpdateInit();
            firstTurnSetup();
            turn();
        } catch (Exception e) {
            e.printStackTrace();
        }
        endTurn();

        // turn 2+
        while (true) {
            try {
                updateTurnInfo();
                turn();
            } catch (Exception e) {
                e.printStackTrace();
            }
            endTurn();
        }
    }

    // final constants

    final public static int MAX_SCOUTS = 8;

    // variables

    public static int[] scoutIDs = new int[MAX_SCOUTS];
    public static int scoutCount = 0;

//    public static int enemyHQIndex = 0;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {

    }

    // code run each turn
    public static void turn() throws GameActionException {

        // make a slanderer on the first turn
        if (roundNum == 1) {
            makeSlanderer();
            return;
        }

        // read messages from scouts
        for (int i = 0; i < scoutCount; i++) {
            int id = scoutIDs[i];
            if (rc.canGetFlag(id)) {
                Comms.readMessage(id);
            } else { // delete scout
                // swap with last
                scoutIDs[i] = scoutIDs[scoutCount - 1];
                scoutIDs[scoutCount - 1] = 0;
                // decrement indices
                i--;
                scoutCount--;
            }
        }

        // broadcast enemy HQ locations
        if (enemyHQCount > 0) {
            // broadcast first enemy hq loc
            Comms.writeAllTargetEnemyHQ(enemyHQLocs[0]);
        }

        if (!rc.isReady()) {
            return;
        }

        // spawn mucks
        if (scoutCount < MAX_SCOUTS && rc.getInfluence() >= 1) {
            for (Direction dir: getClosestDirs(DIRS[scoutCount])) {
                MapLocation adjLoc = here.add(dir);
                if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                    rc.setFlag(scoutCount);
                    Actions.doBuildRobot(RobotType.MUCKRAKER, dir, 1);
                    scoutIDs[scoutCount] = rc.senseRobotAtLocation(adjLoc).getID();
                    scoutCount++;
                    return;
                }
            }
            return;
        }

        if (rc.getInfluence() >= MIN_SLANDERER_COST) {
            makeSlanderer();
        }
    }

    public static Direction makeSlanderer() throws GameActionException {
        for (Direction dir: DIRS) {
            MapLocation adjLoc = here.add(dir);
            if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                int cost = rc.getInfluence() / MIN_SLANDERER_COST * MIN_SLANDERER_COST;
                Actions.doBuildRobot(RobotType.SLANDERER, dir, cost);
                return dir;
            }
        }
        return null;
    }
}

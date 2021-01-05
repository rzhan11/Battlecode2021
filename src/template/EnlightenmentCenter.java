package template;

import battlecode.common.*;

import static template.Map.*;

public class EnlightenmentCenter extends Robot {

    public static void run() throws GameActionException {
        // turn 1
        try {
            updateTurnInfo();
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

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {

    }

    // code run each turn
    public static void turn() throws GameActionException {

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
            for (Direction dir: DIRS) {
                MapLocation adjLoc = here.add(dir);
                if (rc.onTheMap(adjLoc) && !rc.isLocationOccupied(adjLoc)) {
                    int cost = rc.getInfluence() / MIN_SLANDERER_COST * MIN_SLANDERER_COST;
                    Actions.doBuildRobot(RobotType.SLANDERER, dir, cost);
                    return;
                }
            }
        }
    }
}

package template;

import battlecode.common.*;


public class Muckraker extends Robot {

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

    final public static int A_CONSTANT = 1;

    // variables

    public static MapLocation[] detectedLocs;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {

    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateDetectedLocs();

        if (!rc.isReady()) {
            return;
        }


        // expose an enemy slanderer if possible
        MapLocation exposeLoc = getBestExpose();
        if (exposeLoc != null) {
            rc.expose(exposeLoc);
            return;
        }


    }

    public static MapLocation getBestExpose() {
        RobotInfo bestExpose = null;
        double bestValue = -1;
        for (RobotInfo ri: sensedEnemies) {
            if (ri.getType() == RobotType.SLANDERER) {
                double value = GameConstants.PASSIVE_INFLUENCE_RATIO_SLANDERER * ri.getInfluence();
                if (value > bestValue) {
                    bestExpose = ri;
                    bestValue = value;
                }
            }
        }
        if (bestExpose == null) {
            return null;
        } else {
            return bestExpose.getLocation();
        }
    }

    public static void updateDetectedLocs() {
        MapLocation[] allDetectedLocs = rc.detectNearbyRobots();
        int count = 0;
        for (MapLocation loc: allDetectedLocs) {
            if (here.distanceSquaredTo(loc) > RobotType.MUCKRAKER.sensorRadiusSquared) {
                count++;
            }
        }
        detectedLocs = new MapLocation[count];
        count = 0;
        for (MapLocation loc: allDetectedLocs) {
            if (here.distanceSquaredTo(loc) > RobotType.MUCKRAKER.sensorRadiusSquared) {
                detectedLocs[count++] = loc;
            }
        }
    }
}

package template;

import battlecode.common.*;

import static template.Map.*;
import static template.Nav.*;


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

    public static MapLocation spawnLoc;

    // only contains information about detected locations that it cannot sense
    public static MapLocation[] detectedLocs;

    public static Direction exploreDir;
    public static MapLocation exploreLoc;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        spawnLoc = here;

        // default exploreDir is randomized
        exploreDir = DIRS[myID % 8];
        for (Direction dir: DIRS) {
            MapLocation adjLoc = here.add(dir);
            RobotInfo ri = rc.senseRobotAtLocation(adjLoc);
            if (ri != null) {
                if (ri.getType() == RobotType.ENLIGHTENMENT_CENTER && ri.getTeam() == us) {
                    exploreDir = DIRS[rc.getFlag(ri.getID())];
                    break;
                }
            }
        }

        exploreLoc = addDir(spawnLoc, exploreDir, MAX_MAP_SIZE);
    }

    // code run each turn
    public static void turn() throws GameActionException {
        updateDetectedLocs();
        updateExploreLoc();

        if (!rc.isReady()) {
            return;
        }


        // expose an enemy slanderer if possible
        MapLocation exposeLoc = getBestExpose();
        if (exposeLoc != null) {
            rc.expose(exposeLoc);
            return;
        }

        // move towards explore loc
        Direction moveDir = moveLog(exploreLoc);
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
            if (!rc.canSenseLocation(loc)) {
                count++;
            }
        }
        detectedLocs = new MapLocation[count];
        count = 0;
        for (MapLocation loc: allDetectedLocs) {
            if (!rc.canSenseLocation(loc)) {
                detectedLocs[count++] = loc;
            }
        }
    }

    public static void updateExploreLoc() {
        exploreLoc = convertToKnownBounds(exploreLoc);
        if (rc.canSenseLocation(exploreLoc)) {
            exploreDir = exploreDir.rotateLeft();
            exploreLoc = convertToKnownBounds(addDir(spawnLoc, exploreDir, MAX_MAP_SIZE));
        }
    }
}

package template;

import battlecode.common.*;

import java.util.Random;

import static template.Comms.*;
import static template.Debug.*;
import static template.HQTracker.*;
import static template.Map.*;
import static template.Nav.*;

public abstract class Robot extends Constants {

    /*
    True constants
     */
    final public static Direction[] DIRS = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST}; // 8 directions
    final public static Direction[] ALL_DIRS = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST, Direction.CENTER}; // includes center
    final public static Direction[] CARD_DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}; // four cardinal directions
    final public static Direction[] DIAG_DIRS = {Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHWEST}; // four diagonals

    // the priority of directions to explore
    final public static Direction[] EXPLORE_DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.SOUTHWEST, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.NORTHWEST};

    final public static Team neutral = Team.NEUTRAL;

    final public static int MAX_HQ_COUNT = 12;

    /*
    Variables that will never change (once set)
    */
    public static RobotController rc;

    public static int myID;
    public static RobotType myType;
    public static RobotInfo me;

    public static int spawnRound; // the first round this robot was called through RobotPlayer.java
    public static MapLocation spawnLoc;

    public static int myActionRadius;
    public static int mySensorRadius;
    public static int myDetectionRadius;

    public static Team us;
    public static Team them;

    public static int[][] senseDirections = null; // stores (dx, dy, magnitude) of locations that can be sensed
    public static int maxSensedUnits;
//    public static int mapWidth;
//    public static int mapHeight;

    public static Random rand;

    public static void init(RobotController theRC) throws GameActionException {
        rc = theRC;

        myID = rc.getID();
        myType = rc.getType();
        me = rc.senseRobot(myID);

        spawnRound = rc.getRoundNum();
        spawnLoc = rc.getLocation();

        myActionRadius = myType.actionRadiusSquared;
        mySensorRadius = myType.sensorRadiusSquared;
        myDetectionRadius = myType.detectionRadiusSquared;

        us = rc.getTeam();
        them = us.opponent();

        rand = new Random(myID);

        // after simple updates, since log needs myType

        Debug.clearBuffer();
        log("INIT ROBOT");

        Comms.initBaseCoords(rc.getLocation());
        CommManager.initQueues();

        HardCode.initHardCode();
        switch(myType) {
            case ENLIGHTENMENT_CENTER:
                maxSensedUnits = 128;
                break;
            case MUCKRAKER:
                maxSensedUnits = 96;
                break;
            case POLITICIAN:
                maxSensedUnits = 80;
                break;
            case SLANDERER:
                maxSensedUnits = 68;
                break;
        }

        enemyMuckrakers = new RobotInfo[maxSensedUnits];
        enemyPoliticians = new RobotInfo[maxSensedUnits];
        enemySlanderers = new RobotInfo[maxSensedUnits];

        for (int i = 0; i < hqLocs.length; i++) {
            hqIDs[i] = -1;
        }

        Debug.printBuffer();
    }

    /*
    Variables that (may) change each turn
     */

    public static MapLocation here;
    public static int roundNum;
    public static int age;
    public static boolean wasSlanderer = false;

    public static double myPassability;
    public static int myInfluence;
    public static int myConviction;

    public static RobotInfo[] sensedRobots;
    public static RobotInfo[] sensedAllies;
    public static RobotInfo[] sensedEnemies;
    public static RobotInfo[] sensedNeutrals;

    public static RobotInfo[] enemyMuckrakers;
    public static RobotInfo[] enemyPoliticians;
    public static RobotInfo[] enemySlanderers;
    public static int enemyMuckrakerCount;
    public static int enemyPoliticianCount;
    public static int enemySlandererCount;
    public static RobotInfo[] sensedEnemyHQs = new RobotInfo[MAX_HQ_COUNT];
    public static int sensedEnemyHQCount;

    public static RobotInfo[] adjAllies;

    public static boolean[] isDirMoveable = new boolean[8];

    public static int myMaster = -1;
    public static MapLocation myMasterLoc = null;

    public static MapLocation[] hqLocs = new MapLocation[MAX_HQ_COUNT];
    public static Message[] hqBroadcasts = new Message[MAX_HQ_COUNT]; // only used if myType == HQ
    // not guaranteed to be accurate, however if hqIDs[i] is known, then hqTeams[i] should be accurate
    public static Team[] hqTeams = new Team[MAX_HQ_COUNT];
    public static int[] hqIDs = new int[MAX_HQ_COUNT];
    public static int knownHQCount = 0;

    // ALLY hq ids that we know and want to read from
    // but we don't know their locs
    public static int[] extraAllyHQs = new int[MAX_HQ_COUNT];
    public static int extraAllyHQCount = 0;

    public static Direction exploreDir;
    public static MapLocation exploreLoc;
    public static MapLocation exploreCornerLoc;


    public static void updateTurnInfo() throws GameActionException {
        // TESTING PURPOSES ONLY
//        if (roundNum >= 400) {
//            log("RESIGNING");
//            rc.resign();
//        }

        CommManager.resetFlag();

        // independent
        updateBasicInfo();

        // independent
        sortEnemyTypes();
        updateIsDirMoveable();

        // TODO symmetry checks
        // TODO use symmetry to determine hq locs
        updateMapBounds();

        // after updateMapBounds & symmetry stuff
        updateKnownHQs();

        // after updateKnownHQs
        updateMaster();

        // after updateMaster
        readMasterComms();

        // after readMasterComms, updateKnownHQs
        readHQComms();

        // map info
        log("MAP X " + new MapLocation(XMIN, XMAX));
        log("MAP Y " + new MapLocation(YMIN, YMAX));

        // hq info
        log("HQs: " + knownHQCount);
        for (int i = 0; i < knownHQCount; i++) {
            tlog(hqLocs[i] + " " + hqIDs[i] + " " + hqTeams[i]);
        }

        printBuffer();
    }

    /*
    These updates should be cheap and independent of other updates
     */
    public static void updateBasicInfo() throws GameActionException {
        here = rc.getLocation();
        roundNum = rc.getRoundNum();
        age = roundNum - spawnRound;

        myPassability = rc.sensePassability(here);
        myInfluence = rc.getInfluence();
        myConviction = rc.getConviction();

        sensedRobots = rc.senseNearbyRobots();
        sensedAllies = rc.senseNearbyRobots(-1, us);
        sensedEnemies = rc.senseNearbyRobots(-1, them);
        sensedNeutrals = rc.senseNearbyRobots(-1, neutral);

        adjAllies = rc.senseNearbyRobots(2, us);

        // print basic info
        printMyInfo();
        printBuffer();
    }

    public static void sortEnemyTypes() throws GameActionException {
        enemyMuckrakerCount = 0;
        enemyPoliticianCount = 0;
        enemySlandererCount = 0;
        sensedEnemyHQCount = 0;

        for (int i = sensedEnemies.length; --i >= 0;) {
//        for (RobotInfo ri: sensedEnemies) {
            RobotInfo ri = sensedEnemies[i];
            switch(ri.type) {
                case MUCKRAKER:
                    enemyMuckrakers[enemyMuckrakerCount++] = ri;
                    break;

                case POLITICIAN:
                    enemyPoliticians[enemyPoliticianCount++] = ri;
                    break;

                case SLANDERER:
                    enemySlanderers[enemySlandererCount++] = ri;
                    break;

                case ENLIGHTENMENT_CENTER:
                    sensedEnemyHQs[sensedEnemyHQCount++] = ri;
                    break;

                default:
                    break;
            }
        }
    }

    public static void updateMaster() throws GameActionException {
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            return;
        }

        // check if master is dead
        if (!rc.canGetFlag(myMaster)) {
            myMaster = -1;
            myMasterLoc = null;
        }

        // if no master, try to find new master
        if (myMaster < 0) {
            // updates myMaster and myMasterLoc
            int bestDist = P_INF;
            for (int i = knownHQCount; --i >= 0;) {
                int id = hqIDs[i];
                // checks if this is an ally hq with known id
                if (id > 0 && hqTeams[i] == us) {
                    int dist = here.distanceSquaredTo(hqLocs[i]);
                    if (dist < bestDist) {
                        myMaster = id;
                        myMasterLoc = hqLocs[i];
                        bestDist = dist;
                    }
                }
            }
            // if found a new master, broadcast this master
            if (myMaster > 0) {
                writeBroadcastMyMaster(true);
            }
        }

        log("Master: " + myMaster + "@" + myMasterLoc);
    }

    public static void readMasterComms() throws GameActionException {
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            return;
        }
        if (myMaster > 0) {
            Comms.readMessage(myMaster);
        }
    }

    public static void readHQComms() throws GameActionException {
        // should be safe, no need for "rc.canGetFlag"
        for (int i = knownHQCount; --i >= 0;) {
            int id = hqIDs[i];
            if (hqTeams[i] == us && id > 0) {
                if (id != myID && id != myMaster) {
                    Comms.readMessage(id);
                }
            }
        }
        for (int i = extraAllyHQCount; --i >= 0;) {
            int id = extraAllyHQs[i];
            if (id != myID && id != myMaster) {
                Comms.readMessage(id);
            }
        }
    }

    public static void readUnitComms() throws GameActionException {
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            return;
        }
        log("Reading Unit Comms");

        // must reset here
        sensedAllies = rc.senseNearbyRobots(-1, us);
        if (sensedAllies.length == 0) {
            return;
        }

        int count = sensedAllies.length;

        Debug.SILENCE_LOGS = true;
        for (int i = sensedAllies.length; --i >= 0;) {
            if (Clock.getBytecodesLeft() > 1000 && roundNum == rc.getRoundNum()) {
                Comms.readMessage(sensedAllies[i].getID());
            } else {
                count = sensedAllies.length - 1 - i;
                break;
            }
        }
        Debug.SILENCE_LOGS = false;

        tlog("Processed " + count + "/" + sensedAllies.length + " messages");
    }

    /*
    Exploration code
     */

    public static void initExploreLoc() throws GameActionException {
        // default exploreDir is randomized
        exploreDir = DIRS[myID % 8];
        if (myMaster > 0) {
            int status = Comms.getStatusFromFlag(rc.getFlag(myMaster));
            exploreDir = DIRS[status % 8];
        }

        exploreCornerLoc = convertToKnownBounds(addDir(spawnLoc, exploreDir, MAX_MAP_SIZE));
        exploreLoc = processExploreLoc(exploreCornerLoc);
    }

    public static void updateExploreLoc() {
        exploreCornerLoc = convertToKnownBounds(exploreCornerLoc);
        exploreLoc = processExploreLoc(exploreCornerLoc);
        if (rc.canSenseLocation(exploreCornerLoc)) {
            // chose new exploreDir, either rotate 1 or 3
            if ((rc.getID() & 8) == 0) { //initial directions is ID%8, so this is independent
                exploreDir = exploreDir.rotateLeft();
            } else {
                exploreDir = exploreDir.rotateLeft().rotateLeft().rotateLeft();
            }

            int xmid, ymid;
            if (isMapXKnown()) xmid = XMIN + XLEN / 2;
            else xmid = spawnLoc.x;
            if (isMapYKnown()) ymid = YMIN + YLEN / 2;
            else ymid = spawnLoc.y;

            MapLocation mapCenter = new MapLocation(xmid, ymid);

            exploreCornerLoc = convertToKnownBounds(addDir(mapCenter, exploreDir, MAX_MAP_SIZE));
            exploreLoc = processExploreLoc(exploreCornerLoc);
            log("Exploring " + exploreLoc + " " + exploreDir + " " + mapCenter);
        }
    }

    public static void explore() throws GameActionException {
        // move towards explore loc
        rc.setIndicatorLine(here, exploreLoc, PURPLE[0], PURPLE[1], PURPLE[2]);
        log("Exploring: " + exploreLoc);
        moveLog(exploreLoc);
    }

    /*
    Run at the end of each turn
    Checks if we exceeded the bytecode limit
     */
    public static void endTurn() throws GameActionException {
        log("Using " + (CommManager.useRepeatQueue ? "repeat queue" : "normal queue"));
        CommManager.printMessageQueue();
        CommManager.printRepeatQueue();
        CommManager.updateMessageCount();

        // reads unit comms at destination
        readUnitComms();

        // check if we went over the bytecode limit
        int endTurn = rc.getRoundNum();
        if (roundNum != endTurn) {
            printMyInfo();
            logi("BYTECODE LIMIT EXCEEDED");
            int bytecodeOver = Clock.getBytecodeNum();
            int turns = endTurn - roundNum;
            tlogi("Overused bytecode: " + (bytecodeOver + (turns - 1) * myType.bytecodeLimit));
            tlogi("Skipped turns: " + turns);
        }

        printBuffer();
        // print endTurn information
        if (!NO_TURN_LOGS) {
            logline();
            log("END TURN");
            tlog("Flag Status: " + CommManager.getStatus());
            tlog(CommManager.getMessage().toString());
            tlog("Bytecode left: " + Clock.getBytecodesLeft());
            logline();
            printBuffer();
        }
        Clock.yield();
    }

    final public static int cooldownRounding = 100;
    final public static double cooldownRoundingDouble = (double) cooldownRounding;

    public static void printMyInfo () {
        if (NO_TURN_LOGS) return;
        logline();
        log(myType + " " + myID);
        log("Loc: " + here + ". R: " + roundNum);
        log("Conv/Infl: " + rc.getConviction() + "/" + rc.getInfluence());
        log("Cooldown: " + (int) (rc.getCooldownTurns() * cooldownRounding) / cooldownRoundingDouble);
        logline();
    }
}

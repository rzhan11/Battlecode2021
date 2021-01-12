package sprint;

import battlecode.common.*;

import java.util.Random;

import static sprint.Comms.*;
import static sprint.Debug.*;
import static sprint.Map.*;
import static sprint.Nav.*;
import static sprint.Utils.*;

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

        Comms.initBaseCoords(rc.getLocation());

        HardCode.initHardCode();
        switch(myType) {
            case ENLIGHTENMENT_CENTER:
                senseDirections = HardCode.BFS40;
                break;
            case POLITICIAN:
                senseDirections = HardCode.BFS25;
                break;
            case SLANDERER:
                senseDirections = HardCode.BFS20;
                break;
            case MUCKRAKER:
                senseDirections = HardCode.BFS30;
                break;
        }
        maxSensedUnits = senseDirections.length - 1;

        enemyMuckrakers = new RobotInfo[maxSensedUnits];
        enemyPoliticians = new RobotInfo[maxSensedUnits];
        enemySlanderers = new RobotInfo[maxSensedUnits];
    }

    /*
    Variables that (may) change each turn
     */

    public static MapLocation here;
    public static int roundNum;
    public static int age;

    public static double myPassability;
    public static int myInfluence;
    public static int myConviction;

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

//    public static MapLocation[] hqLocs = new MapLocation[MAX_HQ_COUNT];
//    // not guaranteed to be accurate, however if hqIDs[i] is known, then hqTeams[i] should be accurate
//    public static Team[] hqTeams = new Team[MAX_HQ_COUNT];
//    public static int[] hqIDs = new int[MAX_HQ_COUNT];
//    public static int hqCount = 0;

    public static MapLocation[] enemyHQLocs = new MapLocation[MAX_HQ_COUNT];
    // for enemyHQIDs, if negative, it represents the value of the robot that signalled the corresponding location
    public static int[] enemyHQIDs = new int[MAX_HQ_COUNT];
    public static int enemyHQCount = 0;

    public static MapLocation targetEnemyHQLoc;
    public static int targetEnemyHQID;

    public static Direction exploreDir;
    public static MapLocation exploreLoc;


    public static void updateTurnInfo() throws GameActionException {
        Debug.clearBuffer();

        // independent, always first
        updateBasicInfo();

        sortEnemyTypes();

        // independent
        updateIsDirMoveable();

        // TODO symmetry checks
        // TODO use symmetry to determine hq locs
        // independent
        updateMapBounds();

        // independent
        CommManager.updateQueuedMessage();

        // after updateMapBounds
        updateEnemyHQs();

        // independent
        updateMaster();

        // after updateMaster
        // before readMasterComms
        printMyInfo();
        printBuffer();

        // after updateMaster, updateEnemyHQs, updateMapBounds
        readMasterComms();

        // after readMasterComms, updateEnemyHQs
        if (myType != RobotType.ENLIGHTENMENT_CENTER) {
            reportEnemyHQs();
        }

        updateTargetEnemyHQ();

        // independent
        CommManager.updateQueuedMessage();

        // map info
        log("MAP X " + new MapLocation(XMIN, XMAX));
        log("MAP Y " + new MapLocation(YMIN, YMAX));

        // enemy hq info
        log("Enemy HQs " + enemyHQCount);
        for (int i = 0; i < enemyHQCount; i++) {
            tlog(enemyHQLocs[i] + " " + enemyHQIDs[i]);
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

        sensedAllies = rc.senseNearbyRobots(-1, us);
        sensedEnemies = rc.senseNearbyRobots(-1, them);
        sensedNeutrals = rc.senseNearbyRobots(-1, neutral);

        adjAllies = rc.senseNearbyRobots(2, us);

        CommManager.resetFlag();
    }

    public static void sortEnemyTypes() throws GameActionException {
        enemyMuckrakerCount = 0;
        enemyPoliticianCount = 0;
        enemySlandererCount = 0;
        sensedEnemyHQCount = 0;

        for (RobotInfo ri: sensedEnemies) {
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
        if (myMaster == -1) {
            int bestDist = P_INF;
            for (RobotInfo ri: sensedAllies) {
                if (ri.type == RobotType.ENLIGHTENMENT_CENTER) {
                    int dist = here.distanceSquaredTo(ri.location);
                    if (dist < bestDist) {
                        myMaster = ri.getID();
                        myMasterLoc = ri.location;
                        bestDist = dist;
                    }
                    return;
                }
            }
        }
    }

    public static void readMasterComms() throws GameActionException {
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            return;
        }
        if (myMaster != -1) {
            Comms.readMessage(myMaster);
        }
    }

    public static void readAllComms() throws GameActionException {
        // TODO: maybe make an exception for slanderers, due to low bytecode
//        if (myType == RobotType.SLANDERER) {
//            return;
//        }
        //
        for (RobotInfo ri: sensedAllies) {
            Comms.readMessage(ri.getID());
        }
    }

    /*
    Enemy hq code
     */

    public static void updateEnemyHQs() throws GameActionException {
        // delete dead enemies
        for (int i = enemyHQCount; --i >= 0;) {
            int id = enemyHQIDs[i];
            MapLocation loc = enemyHQLocs[i];
            if (id > 0 && !rc.canGetFlag(id)) {
                enemyHQCount--;
                swap(enemyHQIDs, i, enemyHQCount);
                swap(enemyHQLocs, i, enemyHQCount);
            } else if (rc.canSenseLocation(loc)) {
                if (rc.senseRobotAtLocation(loc).team != them) {
                    enemyHQCount--;
                    swap(enemyHQIDs, i, enemyHQCount);
                    swap(enemyHQLocs, i, enemyHQCount);
                }
            }
        }

        // MOVED: adding seen enemy hq's is done in the 'reportEnemyHQs' method
    }

    public static void reportEnemyHQs() throws GameActionException {
        for (int i = sensedEnemyHQCount; --i >= 0;) {
            RobotInfo ri = sensedEnemyHQs[i];
            int id = ri.getID();
            if (!inArray(enemyHQIDs, id, enemyHQCount)) {
                // add to array
                enemyHQLocs[enemyHQCount] = ri.location;
                enemyHQIDs[enemyHQCount] = id;
                enemyHQCount++;
                // add messages to queue
                writeEnemyHQLoc(ri.location, false);
                writeEnemyHQID(id, false);
            }
        }
    }

    public static void updateTargetEnemyHQ() throws GameActionException {
        // update targetEnemyHQLoc
        if (enemyHQCount > 0) {
            targetEnemyHQLoc = enemyHQLocs[0];
            targetEnemyHQID = enemyHQIDs[0];
        } else {
            targetEnemyHQLoc = null;
            targetEnemyHQID = -1;
        }
        log("targetEnemyHQ " + targetEnemyHQLoc + " " + targetEnemyHQID);
    }

    /*
    Exploration code
     */

    public static void initExploreLoc() throws GameActionException {
        // default exploreDir is randomized
        exploreDir = DIRS[myID % 8];
        if (myMaster != -1) {
            int status = Comms.getStatusFromFlag(rc.getFlag(myMaster));
            exploreDir = DIRS[status % 8];
        }

        exploreLoc = addDir(spawnLoc, exploreDir, MAX_MAP_SIZE);
    }

    public static void updateExploreLoc() {
        exploreLoc = processExploreLoc(exploreLoc);
        if (rc.canSenseLocation(exploreLoc)) {
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

            exploreLoc = processExploreLoc(addDir(mapCenter, exploreDir, MAX_MAP_SIZE));
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
        CommManager.printMessages();
        CommManager.updateMessageCount();

        int status = CommManager.getStatus();
        Message msg = CommManager.getMessage();

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

    public static void printMyInfo () {
        if (NO_TURN_LOGS) return;
        logline();
        log("Robot: " + myType);
        log("roundNum: " + roundNum);
        log("ID: " + myID);
        log("Master: " + myMaster + "@" + myMasterLoc);
        log("*Location: " + here);
        log("*Conv/Infl: " + rc.getInfluence() + "/" + rc.getConviction());
        log("*Cooldown: " + rc.getCooldownTurns());
        logline();
    }
}

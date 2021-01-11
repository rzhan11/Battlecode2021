package template;

import battlecode.common.*;

import java.util.Random;

import static template.Comms.*;
import static template.Debug.*;
import static template.Map.*;
import static template.Nav.*;
import static template.Utils.*;

// TODO URGENT TEST CODE

public abstract class Robot extends Constants {

    /*
    True constants
     */
    final public static Direction[] DIRS = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST}; // 8 directions
    final public static Direction[] ALL_DIRS = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST, Direction.CENTER}; // includes center
    final public static Direction[] CARD_DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}; // four cardinal directions
    final public static Direction[] DIAG_DIRS = {Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHWEST}; // four diagonals

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

//    public static int[][] senseDirections = null; // stores (dx, dy, magnitude) of locations that can be sensed
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

        HardCode.initHardCode();

        Comms.initBaseCoords(rc.getLocation());
//        Nav.resetHistory();
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

    public static RobotInfo[] adjAllies;

    public static boolean[] isDirMoveable = new boolean[8];

    public static int myMaster = -1;
    public static MapLocation myMasterLoc = null;

    public static MapLocation[] enemyHQLocs = new MapLocation[MAX_HQ_COUNT];
    // for enemyHQIDs, if negative, it represents the value of the robot that signalled the corresponding location
    public static int[] enemyHQIDs = new int[MAX_HQ_COUNT];
    public static int enemyHQCount = 0;

    public static MapLocation targetEnemyHQLoc;
    public static int targetEnemyHQID;

    public static Direction exploreDir;
    public static MapLocation exploreLoc;


    public static void updateTurnInfo() throws GameActionException {
        // independent, always first
        updateBasicInfo();

        // independent
        updateIsDirMoveable();

        // TODO symmetry checks
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

        // after updateMaster, updateEnemyHQs, updateMapBounds
        readMasterComms();

        // after readMasterComms, updateEnemyHQs
        if (myType != RobotType.ENLIGHTENMENT_CENTER) {
            reportEnemyHQs();
        }

        // after updateMapBounds, readMasterComms
        reportMapBounds();

        // map info
        log("MAP X " + new MapLocation(XMIN, XMAX));
        log("MAP Y " + new MapLocation(YMIN, YMAX));

        // enemy hq info
        log("Enemy HQs " + enemyHQCount);
        for (int i = 0; i < enemyHQCount; i++) {
            tlog(enemyHQLocs[i] + " " + enemyHQIDs[i]);
        }
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
        // TODO merge with politician/muckraker targetting
        // delete dead enemies
        for (int i = enemyHQCount; --i >= 0;) {
            if (enemyHQIDs[i] > 0 && !rc.canGetFlag(enemyHQIDs[i])) {
                enemyHQCount--;
                enemyHQIDs[i] = enemyHQIDs[enemyHQCount];
                enemyHQIDs[enemyHQCount] = -1;
                swap(enemyHQLocs, i, enemyHQCount);
            }
        }

        // MOVED:
        // adding seen enemy hq's is done in the 'reportEnemyHQs' method

        // update targetEnemyHQLoc
        if (enemyHQCount > 0) {
            targetEnemyHQLoc = enemyHQLocs[0];
            targetEnemyHQID = enemyHQIDs[0];
        } else {
            targetEnemyHQLoc = null;
            targetEnemyHQID = -1;
        }
    }

    public static void reportEnemyHQs() throws GameActionException {
        for (RobotInfo ri: sensedEnemies) {
            if (ri.type == RobotType.ENLIGHTENMENT_CENTER) {
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
    }

    /*
    Exploration code
     */

    public static void initExploreLoc() throws GameActionException {
        // default exploreDir is randomized
        exploreDir = DIRS[myID % 8];
        if (myMaster != -1) {
            int flag = rc.getFlag(myMaster);
            exploreDir = DIRS[Comms.getStatusFromFlag(flag)];
        }

        exploreLoc = addDir(spawnLoc, exploreDir, MAX_MAP_SIZE);
    }

    public static void updateExploreLoc() {
        exploreLoc = convertToKnownBounds(exploreLoc);
        if (rc.canSenseLocation(exploreLoc)) {
            exploreDir = exploreDir.rotateLeft();
            //this makes some mucks cross the center instead of sticking to the outside
            if ((rc.getID()&8) == 0) { //initial directions is ID%8, so this is independent
                exploreDir = exploreDir.rotateLeft();
                exploreDir = exploreDir.rotateLeft();
            }
            log("explore " + spawnLoc + " " + exploreDir + " " + MAX_MAP_SIZE);
            exploreLoc = convertToKnownBounds(addDir(spawnLoc, exploreDir, MAX_MAP_SIZE));
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
        if (!NO_TURN_LOGS) {
            logline();
            log("END TURN");
            tlog("Flag Status: " + CommManager.getStatus());
            tlog(CommManager.getMessage().toString());
            log("Bytecode left: " + Clock.getBytecodesLeft());
            logline();
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

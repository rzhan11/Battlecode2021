package template;

import battlecode.common.*;

import java.util.Random;

import static template.Debug.*;
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

    final public static Team neutral = Team.NEUTRAL;

    final public static int MAX_HQ_COUNT = 32;

    /*
    Variables that will never change (once set)
    */
    public static RobotController rc;

    public static int myID;
    public static RobotType myType;

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
    public static int XMIN = -1;
    public static int YMIN = -1;
    public static int XMAX = -1;
    public static int YMAX = -1;
    public static int XLEN = -1;
    public static int YLEN = -1;

    public static Random rand;

    public static void init(RobotController theRC) throws GameActionException {
        rc = theRC;

        myID = rc.getID();
        myType = rc.getType();

        spawnRound = rc.getRoundNum();
        spawnLoc = rc.getLocation();

        myActionRadius = myType.actionRadiusSquared;
        mySensorRadius = myType.sensorRadiusSquared;
        myDetectionRadius = myType.detectionRadiusSquared;

        us = rc.getTeam();
        them = us.opponent();

//        mapWidth = rc.getMapWidth();
//        mapHeight = rc.getMapHeight();

        rand = new Random(myID);

        HardCode.initHardCode();

        Comms.initBaseCoords(rc.getLocation());
//        Nav.resetHistory();
    }

    public static int myMaster = -1;
    public static MapLocation myMasterLoc = null;

    /*
    Performs updates for first turn that must be done after 'updateTurnInfo'
     */
    public static void postUpdateInit() {
        // find master
        if (myType != RobotType.ENLIGHTENMENT_CENTER) {
            RobotInfo[] adjAllies = rc.senseNearbyRobots(2, us);
            for (RobotInfo ri: adjAllies) {
                if (ri.type == RobotType.ENLIGHTENMENT_CENTER) {
                    myMaster = ri.getID();
                    myMasterLoc = ri.location;
                }
            }
        }
    }

    /*
    Variables that (may) change each turn
     */

    public static MapLocation here;
    public static int roundNum;
    public static int age;

    public static double myPassability;

    public static RobotInfo[] sensedAllies;
    public static RobotInfo[] sensedEnemies;
    public static RobotInfo[] sensedNeutrals;

    public static boolean[] isDirMoveable = new boolean[8];

    public static MapLocation[] enemyHQLocs = new MapLocation[MAX_HQ_COUNT];
    public static int enemyHQCount = 0;


    public static void updateTurnInfo() throws GameActionException {
        updateBasicInfo();

        Comms.resetFlag();

        printMyInfo();

        updateMapBounds();
        log("Bounds:");
        tlog("X " + XMIN + " " + XMAX);
        tlog("Y " + YMIN + " " + YMAX);

        updateIsDirMoveable();

        updateMaster();
        readMasterComms();
    }

    /*
    These updates should be cheap and independent of other updates
     */
    public static void updateBasicInfo() throws GameActionException {
        here = rc.getLocation();
        roundNum = rc.getRoundNum();
        age = roundNum - spawnRound;
        myPassability = rc.sensePassability(here);

        sensedAllies = rc.senseNearbyRobots(-1, us);
        sensedEnemies = rc.senseNearbyRobots(-1, them);
        sensedNeutrals = rc.senseNearbyRobots(-1, neutral);
    }

    public static void updateMaster() throws GameActionException {
        if (!rc.canGetFlag(myMaster)) {
            myMaster = -1;
            myMasterLoc = null;
        }
        if (myMaster == -1) {
            // try to find a new master
            for (RobotInfo ri: sensedAllies) {
                if (ri.type == RobotType.ENLIGHTENMENT_CENTER) {
                    myMaster = ri.getID();
                    myMasterLoc = ri.location;
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

    /*
    Run at the end of each turn
    Checks if we exceeded the bytecode limit
     */
    public static void endTurn() throws GameActionException {
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
            log("------------------------------\n");
            log("END TURN");
            log("Bytecode left: " + Clock.getBytecodesLeft());
            log("------------------------------\n");
        }
        Clock.yield();
    }

    public static void printMyInfo () {
        if (NO_TURN_LOGS) return;
        log("------------------------------\n");
        log("Robot: " + myType);
        log("roundNum: " + roundNum);
        log("ID: " + myID);
        log("Master: " + myMaster + "@" + myMasterLoc);
        log("*Location: " + here);
        log("*Conv/Infl: " + rc.getInfluence() + "/" + rc.getConviction());
        log("*Cooldown: " + rc.getCooldownTurns());
        log("------------------------------\n");
    }
}

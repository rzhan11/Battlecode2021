package template;

import battlecode.common.*;

import java.util.Random;

import static template.Debug.*;
import static template.Map.*;


public abstract class Robot extends Constants {

    /*
    True constants
     */
    final public static Direction[] DIRS = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST}; // 8 directions
    final public static Direction[] ALL_DIRS = {Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST}; // includes center
    final public static Direction[] CARD_DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}; // four cardinal directions
    final public static Direction[] DIAG_DIRS = {Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHWEST}; // four diagonals

    final public static Team neutral = Team.NEUTRAL;

    /*
    Variables that will never change (once set)
    */
    public static RobotController rc;
//    public static int spawnRound; // the first round this robot was called through RobotPlayer.java
    public static int myID;
    public static RobotType myType;

    public static Team us;
    public static Team them;

//    public static int[][] senseDirections = null; // stores (dx, dy, magnitude) of locations that can be sensed
//    public static int mapWidth;
//    public static int mapHeight;

    public static Random rand;

    public static void init(RobotController theRC) {
        rc = theRC;
//        spawnRound = rc.getRoundNum();

        myID = rc.getID();
        myType = rc.getType();

        us = rc.getTeam();
        them = us.opponent();

//        mapWidth = rc.getMapWidth();
//        mapHeight = rc.getMapHeight();

        rand = new Random(myID);
    }

    /*
    Variables that (may) change each turn
     */

    public static MapLocation here;
    public static int roundNum;
    public static double myPassability;

    public static RobotInfo[] sensedAllies;
    public static RobotInfo[] sensedEnemies;
    public static RobotInfo[] sensedNeutrals;

    public static boolean[] isDirMoveable = new boolean[8];

    public static void updateTurnInfo() throws GameActionException {
        updateBasicInfo();

        printMyInfo();

        updateIsDirMoveable();
    }

    /*
    These updates should be cheap and independent of other updates
     */
    public static void updateBasicInfo() throws GameActionException {
        here = rc.getLocation();
        roundNum = rc.getRoundNum();
        myPassability = rc.sensePassability(here);

        sensedAllies = rc.senseNearbyRobots(-1, us);
        sensedEnemies = rc.senseNearbyRobots(-1, them);
        sensedNeutrals = rc.senseNearbyRobots(-1, neutral);
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
        log("*Location: " + here);
        log("*Cooldown: " + rc.getCooldownTurns());
        log("------------------------------\n");
    }

}

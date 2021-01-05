package template;

import battlecode.common.*;

import java.util.Random;


public abstract class Robot extends Constants {

    /*
    True constants
     */
    final public static Direction[] DIRS = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST}; // 8 directions
    final public static Direction[] ALL_DIRS = {Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST}; // includes center
    final public static Direction[] CARD_DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}; // four cardinal directions
    final public static Direction[] DIAG_DIRS = {Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHWEST}; // four diagonals

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
    public static int mapWidth;
    public static int mapHeight;

    public static Random rand;

    public static void init(RobotController theRC) {
        rc = theRC;
//        spawnRound = rc.getRoundNum();

        myID = rc.getID();
        myType = rc.getType();

        us = rc.getTeam();
        them = us.opponent();

        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();

        rand = new Random(myID);
    }

    /*
    Variables that (may) change each turn
     */

    public static MapLocation here;

    public static void updateTurnInfo() throws GameActionException {
        here = rc.getLocation();
    }

    // run once at the beginning (before turn 1)
    public abstract static void firstTurnSetup() throws GameActionException;

    // run each turn
    public abstract static void turn() throws GameActionException;

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
        Clock.yield();
    }

    public static void run() throws GameActionException {
        // turn 1
        try {
            updateTurnInfo();
            firstTurnSetup();
            r.turn();
        } catch (Exception e) {
            e.printStackTrace();
        }
        r.endTurn();

        // turn 2+
        while (true) {
            try {
                r.updateTurnInfo();
                r.turn();
            } catch (Exception e) {
                e.printStackTrace();
            }
            r.endTurn();
        }
    }
}

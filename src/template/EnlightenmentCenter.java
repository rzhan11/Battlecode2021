package template;

import battlecode.common.*;


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

    final public static int A_CONSTANT = 1;

    // variables

    public static int myVar;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {

    }

    // code run each turn
    public static void turn() throws GameActionException {


    }
}

package newday;

import battlecode.common.*;

import static newday.Debug.*;
import static newday.Robot.*;

public strictfp class RobotPlayer {
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        boolean firstChange = true;

        while (true) {
            try {
                // changed is true on the first turn
                // or when slanderers turn to politicians
                boolean changed = (myType != rc.getType());
                if (changed) {
                    if (firstChange) firstChange = false;
                    else System.out.println("\nCONVERTED TO POLITICIAN");
                    Robot.init(rc);
                }
                updateTurnInfo();
                if (changed) firstTurnSetup();

                turn();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                endTurn();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void firstTurnSetup() throws GameActionException {
        switch (myType) {
            case ENLIGHTENMENT_CENTER:
                EnlightenmentCenter.firstTurnSetup();
                break;
            case POLITICIAN:
                Politician.firstTurnSetup();
                break;
            case SLANDERER:
                Slanderer.firstTurnSetup();
                break;
            case MUCKRAKER:
                Muckraker.firstTurnSetup();
                break;
            default:
                logi("WARNING: 'firstTurnSetup' Unknown unit!");
        }
    }

    private static void turn() throws GameActionException {
        switch (myType) {
            case ENLIGHTENMENT_CENTER:
                EnlightenmentCenter.turn();
                break;
            case POLITICIAN:
                Politician.turn();
                break;
            case SLANDERER:
                Slanderer.turn();
                break;
            case MUCKRAKER:
                Muckraker.turn();
                break;
            default:
                logi("WARNING: 'turn' Unknown unit!");
        }
        printBuffer();
    }
}

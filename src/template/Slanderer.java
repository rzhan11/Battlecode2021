package template;

import battlecode.common.*;

import static template.Map.*;

public class Slanderer extends Robot {

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
        if (!rc.isReady()) {
            return;
        }

        int randomDir = (int) (Math.random() * 8);
        for (int i = 0; i < 8; i++) {
            int j = (i + randomDir) % 8;
            if (isDirMoveable[j]) {
                Actions.doMove(DIRS[j]);
                return;
            }
        }
    }
}

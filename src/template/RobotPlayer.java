package template;

import battlecode.common.*;

public strictfp class RobotPlayer {
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        try {
            Robot.init(rc);
        } catch (Exception e) {
            e.printStackTrace();
        }

        switch (rc.getType()) {
            case ENLIGHTENMENT_CENTER:
                EnlightenmentCenter.run();
                break;
            case POLITICIAN:
                Politician.run();
                break;
            case SLANDERER:
                Slanderer.run();
                break;
            case MUCKRAKER:
                Muckraker.run();
                break;
            default:
                log("[EXCEPTION]: Unknown unit!");
        }
    }
}

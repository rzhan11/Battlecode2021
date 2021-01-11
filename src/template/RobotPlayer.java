package template;

import battlecode.common.*;

import static template.Debug.*;

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
                // if this is reached, then convert to politician
                Politician.run();
                break;
            case MUCKRAKER:
                Muckraker.run();
                break;
            default:
                log("WARNING: Unknown unit!");
        }
    }
}

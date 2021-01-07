package template;

import battlecode.common.*;

import static template.Robot.*;
import static template.Debug.*;

public class Actions {
    public static void doMove (Direction dir) throws GameActionException {
        log("MOVING " + dir);
        drawLine(here, rc.adjacentLocation(dir), YELLOW);
        rc.move(dir);
    }

    public static void doExpose (MapLocation loc) throws GameActionException {
        log("EXPOSING " + loc);
        drawLine(here, loc, ORANGE);
        rc.expose(loc);
    }

    public static void doBuildRobot (RobotType type, Direction dir, int i) throws GameActionException {
        drawLine(here, rc.adjacentLocation(dir), CYAN);
        log("BUILDING " + type + " " + dir);
        rc.buildRobot(type, dir, i);
    }
}

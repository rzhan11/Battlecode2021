package wander_defense;

import battlecode.common.*;

import static wander_defense.Robot.*;
import static wander_defense.Debug.*;

public class Actions {
    public static void doMove(Direction dir) throws GameActionException {
        log("MOVING " + dir);
        drawLine(here, rc.adjacentLocation(dir), YELLOW);
        rc.move(dir);
    }

    public static void doExpose(MapLocation loc) throws GameActionException {
        log("EXPOSING " + loc);
        drawLine(here, loc, ORANGE);
        rc.expose(loc);
    }

    public static void doEmpower(int dist) throws GameActionException {
        log("EMPOWERING " + dist);
        int len = HardCode.BFS9.length;
        for (int i = 0; i < len; i++) {
            if (HardCode.BFS9[i][2] <= dist) {
                drawDot(here.translate(HardCode.BFS9[i][0], HardCode.BFS9[i][1]), BLACK);
            }
        }
        printBuffer();
        rc.empower(dist);
    }

    public static void doBuildRobot(RobotType type, Direction dir, int i) throws GameActionException {
        drawLine(here, rc.adjacentLocation(dir), CYAN);
        log("BUILDING " + type + " " + dir);
        rc.buildRobot(type, dir, i);
    }
}

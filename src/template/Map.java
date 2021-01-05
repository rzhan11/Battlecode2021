package template;

import battlecode.common.*;

import static template.Robot.*;
import static template.Debug.*;

public class Map {
    public static void updateIsDirMoveable() throws GameActionException {
        // add information about if direction is moveable
        for (int i = 0; i < DIRS.length; i++) {
            MapLocation adjLoc = rc.adjacentLocation(DIRS[i]);
            isDirMoveable[i] = rc.onTheMap(adjLoc) && rc.senseRobotAtLocation(adjLoc) == null;
        }
    }
}

package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Map.*;
import static template.Nav.*;


public class Politician extends Robot {

    public static void run() throws GameActionException {
        // turn 1
        try {
            updateTurnInfo();
            postUpdateInit();
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

    // Final Constants

    final public static int A_CONSTANT = 1;

    // Role Allocation
    final public static int ROLE_TARGET = 1;
    final public static int ROLE_BOMB = 2;

    // Variables
    // Global Variables
    public static int role;

    // Role-Specific Variables
    public static MapLocation target_initial_location;
    public static int estimated_target_id = -1;
    public static MapLocation estimated_target_location;

    // things to do on turn 1 of existence
    public static void firstTurnSetup() throws GameActionException {
        role = ROLE_TARGET;
        // spawnLocation replaced by spawnLoc (in Robot.init)
    }

    // stuff to do every turn
    public static void turn_setup() throws GameActionException {
        // visible_allies replaced by sensedAllies (in Robot.updateBasicInfo)
        // visible_enemies replaced by sensedEnemies (in Robot.updateBasicInfo)
    }

    // code run each turn
    public static void turn() throws GameActionException {
        turn_setup();

        if (!rc.isReady()) {
            return;
        }

        if(role == ROLE_TARGET) {
            if (target_initial_location == null) {
                log("No target_initial_location");
                return;
            }
            int distance_to_target = target_initial_location.distanceSquaredTo(rc.getLocation());
            if(rc.canEmpower(distance_to_target)) {
                rc.empower(distance_to_target);
                return;
                /*if(estimated_target_id == -1 || !rc.canSenseRobot(estimated_target_id)) {
                    // Search for Good Target
                    int curmax = -1;
                    int curid = -1;
                    int best = rc.getConviction()-10;
                    for(RobotInfo ri : sensedEnemies) {
                        // Idea 1: Find the Best Enemy we can kill.
                        if(ri.conviction <= best && ri.conviction > curmax) {
                            curmax = ri.conviction;
                            curid = ri.ID;
                        }
                    }
                    estimated_target_id = curid;
                    estimated_target_location = rc.senseRobot(curid).location;
                }
                estimated_target_location = rc.senseRobot(estimated_target_id).location;
                // TODO: IMPROVE THIS BASIC FUNCTIONALITY
                if(rc.canEmpower(rc.getLocation().distanceSquaredTo(estimated_target_location))) {
                    rc.empower(rc.getLocation().distanceSquaredTo(estimated_target_location));
                    return;
                }
                else {
                    moveLog(estimated_target_location);
                    return;
                }*/
            }
            else {
                moveLog(target_initial_location);
                return;
            }
        }
        else if (role == ROLE_BOMB) {
            // TODO: DO BOMBER STUFF
        }
    }
}

package template;

import battlecode.common.*;

import static template.CommManager.*;
import static template.Comms.*;
import static template.Debug.*;
import static template.Robot.*;
import static template.Message.*;

public class HQTracker {
    // todo have all units broadcast hq locations
    // todo have all units receive from all hq locations

    public static void updateKnownHQs() throws GameActionException {
        // loop through sensed hqs, update their info
        for (RobotInfo ri: sensedRobots) {
            if (ri.type == RobotType.ENLIGHTENMENT_CENTER) {
                boolean isKnown = false;
                for (int i = knownHQCount; --i >= 0;) {
                    if (hqLocs[i].equals(ri.location)) {
                        // this is a known location
                        isKnown = true;
                        if (hqIDs[i] != ri.getID()) {
                            // its info is old, needs to be replaced
                            reportHQInfo(i, ri.getID(), ri.getTeam());
                        }
                        break;
                    }
                }
                // adds new hq loc if not known
                if (!isKnown) {
                    reportNewHQLoc(ri.location);
                    reportHQInfo(knownHQCount - 1, ri.getID(), ri.getTeam());
                }
            }
        }

        // deletes dead hqs
        for (int i = knownHQCount; --i >= 0;) {
            int id = hqIDs[i];
            if (id > 0 && !rc.canGetFlag(id)) {
                stopBroadcastHQInfo(i);
                // reset hqIDs to default value of -1
                hqIDs[i] = -1;
                // flip the team of the killed hq
                hqTeams[i] = hqTeams[i].opponent(); // assume that the team has flipped
            }
        }
    }

    public static void updateTargetEnemyHQ() throws GameActionException {
        // update targetEnemyHQLoc
        targetEnemyHQLoc = null;
        targetEnemyHQID = -1;
        for (int i = knownHQCount; --i >= 0;) {
            if (hqTeams[i] == them) {
                targetEnemyHQLoc = hqLocs[i];
                targetEnemyHQID = hqIDs[i];
                break;
            }
        }
        log("targetEnemyHQ " + targetEnemyHQLoc + " " + targetEnemyHQID);
    }

    public static void reportNewHQLoc(MapLocation loc) throws GameActionException {
        log("Reporting New HQ Loc");
        knownHQCount++;
        hqLocs[knownHQCount - 1] = loc;
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            broadcastHQLoc(knownHQCount - 1);
        } else {
            writeHQLoc(loc, false);
        }
    }

    public static void reportHQInfo(int index, int hqid, Team team) throws GameActionException {
        log("Reporting HQ Info");
        hqIDs[index] = hqid;
        hqTeams[index] = team;
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            broadcastHQInfo(index);
        } else {
            writeHQInfo(hqid, team, false);
        }
    }

    /*
    Broadcast methods are used by HQs
     */
    public static void broadcastHQLoc(int index) throws GameActionException {
        log("Broadcasting HQ Loc");

        Message msg = getHQLocMsg(hqLocs[index], true);
        tlog(msg.toString());

        hqLocMsgs[index] = msg;
        queueMessage(msg, false);
    }

    public static void broadcastHQInfo(int index) throws GameActionException {
        log("Broadcasting HQ Info");

        Message oldMsg = hqLocMsgs[index].next;
        Message newMsg = getHQInfoMsg(hqIDs[index], hqTeams[index], true);
        tlog(newMsg.toString());

        if (oldMsg == null) {
            chainMessages(hqLocMsgs[index], newMsg);
        } else {
            oldMsg.type = newMsg.type;
            oldMsg.info = newMsg.info;
            oldMsg.repeat = newMsg.repeat;
        }
    }

    public static void stopBroadcastHQInfo(int index) throws GameActionException {
        log("Stop broadcasting HQ Info " + hqLocs[index]);
        hqLocMsgs[index].next = null;
    }
}

package template;

import battlecode.common.*;

import static template.CommManager.*;
import static template.Comms.*;
import static template.Debug.*;
import static template.Robot.*;
import static template.Message.*;
import static template.Utils.*;

public class HQTracker {



    public static void updateKnownHQs() throws GameActionException {
        // add myself to hqinfo
        if (age == 0 && myType == RobotType.ENLIGHTENMENT_CENTER) {
            saveHQLoc(here);
            saveHQInfo(knownHQCount - 1, myID, us);
            updateHQBroadcast(knownHQCount - 1);
        }

        // loop through sensed hqs, update their info
        for (RobotInfo ri : sensedRobots) {
            if (ri.type == RobotType.ENLIGHTENMENT_CENTER) {
                boolean isKnown = false;
                for (int i = knownHQCount; --i >= 0; ) {
                    if (hqLocs[i].equals(ri.location)) {
                        // this is a known location
                        isKnown = true;
                        if (hqIDs[i] != ri.getID()) {
                            // we have found new information, save and report
                            saveHQInfo(i, ri.getID(), ri.getTeam());
                            reportHQ(i);
                        }
                        break;
                    }
                }
                // adds new hq loc if not known
                if (!isKnown) {
                    saveHQLoc(ri.location);
                    saveHQInfo(knownHQCount - 1, ri.getID(), ri.getTeam());
                    reportHQ(knownHQCount - 1);
                }
            }
        }

        // deletes dead hqs
        for (int i = knownHQCount; --i >= 0; ) {
            int id = hqIDs[i];
            if (id > 0 && !rc.canGetFlag(id)) {
                if (myType == RobotType.ENLIGHTENMENT_CENTER) {
                    updateHQBroadcast(i);
                }
                // reset hqIDs to default value of -1
                hqIDs[i] = -1;
                // flip the team of the killed hq
                hqTeams[i] = hqTeams[i].opponent(); // assume that the team has flipped
            }
        }

        // deletes dead extraAllyHQs
        // deletes extraAllyHQs that are in hqIDs
        for (int i = extraAllyHQCount; --i >= 0;) {
            if (!rc.canGetFlag(extraAllyHQs[i]) || inArray(hqIDs, extraAllyHQs[i], knownHQCount)) {
                extraAllyHQCount--;
                extraAllyHQs[i] = extraAllyHQs[extraAllyHQCount];
            }
        }
    }

    public static void saveHQLoc(MapLocation loc) throws GameActionException {
        hqLocs[knownHQCount++] = loc;
    }

    public static void saveHQInfo(int index, int hqid, Team team) throws GameActionException {
        hqIDs[index] = hqid;
        hqTeams[index] = team;
    }

    /*
    HQ loc is known, but hq id/team has changed
     */
    public static void reportHQ(int index) throws GameActionException {
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            updateHQBroadcast(index);
        } else {
            if (hqIDs[index] > 0) {
                Message locMsg = getHQLocMsg(hqLocs[index], true, false);
                queueMessage(locMsg, false);

                Message infoMsg = getHQInfoMsg(hqIDs[index], hqTeams[index], false);
                chainMessages(locMsg, infoMsg);

                if (hqTeams[index] == us) {
                    writeUnitBroadcastAllyHQ(hqIDs[index], true);
                }
            } else {
                writeHQLocSolo(hqLocs[index], false);
            }
        }
    }

    //broadcastHQLoc broadcastHQInfo
    /*
    Broadcast methods are used by HQs
     */
    public static void updateHQBroadcast(int index) throws GameActionException {
        log("Updating HQ Broadcast " + index);

        boolean paired = (hqIDs[index] > 0);

        if (hqBroadcasts[index] == null) {
            tlog("Initializing");
            hqBroadcasts[index] = getHQLocMsg(hqLocs[index], paired, true);
            queueMessage(hqBroadcasts[index], false);
        }

        if (paired) {
            tlog("Paired");
            Message infoMsg = getHQInfoMsg(hqIDs[index], hqTeams[index], true);
            chainMessages(hqBroadcasts[index], infoMsg);
        } else {
            tlog("Solo");
            hqBroadcasts[index].next = null;
            // do not alter prev of infoMsg, allows for backtracking
        }
    }
}

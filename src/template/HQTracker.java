package template;

import battlecode.common.*;

import static template.CommManager.*;
import static template.Comms.*;
import static template.Debug.*;
import static template.Robot.*;
import static template.Message.*;
import static template.Utils.*;

public class HQTracker {

    final public static int SURROUND_MEMORY = 50;
    final public static int DEFAULT_SURROUND = -100;
    final public static int SURROUND_UPDATE_FREQ = 5;

    final public static int IGNORE_MEMORY = 100;
    final public static int DEFAULT_IGNORE = -100;

    public static void updateKnownHQs() throws GameActionException {
        // add myself to hqinfo
        if (age == 0 && myType == RobotType.ENLIGHTENMENT_CENTER) {
            saveHQLoc(here);
            saveHQInfo(knownHQCount - 1, myID, us);
        }

        // loop through sensed hqs, update their info
        for (int i = sensedRobots.length; --i >= 0;) {
            RobotInfo ri = sensedRobots[i];
            if (ri.type == RobotType.ENLIGHTENMENT_CENTER) {
                boolean isKnown = false;
                for (int j = knownHQCount; --j >= 0; ) {
                    if (hqLocs[j].equals(ri.location)) {
                        // this is a known location
                        isKnown = true;
                        if (hqIDs[j] != ri.ID) {
                            // we have found new information, save and report
                            saveHQInfo(j, ri.ID, ri.getTeam());
                            if (myType != RobotType.ENLIGHTENMENT_CENTER) {
                                reportHQ(j);
                            }
                        }
                        break;
                    }
                }
                // adds new hq loc if not known
                if (!isKnown) {
                    saveHQLoc(ri.location);
                    saveHQInfo(knownHQCount - 1, ri.ID, ri.getTeam());
                    if (myType != RobotType.ENLIGHTENMENT_CENTER) {
                        reportHQ(knownHQCount - 1);
                    }
                }
            }
        }

        // deletes dead hqs
        for (int i = knownHQCount; --i >= 0; ) {
            int id = hqIDs[i];
            if (id > 0 && !rc.canGetFlag(id)) {
                updateHQDead(i);
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

    // reset hqIDs to default value of -1
    // flip the team of the killed hq, (if team was NEUTRAL, now it's unknown)
    public static void updateHQDead(int index) throws GameActionException {
        saveHQInfo(index, -1, (hqTeams[index] == Team.NEUTRAL) ? null: hqTeams[index].opponent());
    }

    public static void saveHQLoc(MapLocation loc) throws GameActionException {
        knownHQCount++;
        hqLocs[knownHQCount - 1] = loc;
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            updateHQBroadcast(knownHQCount - 1);
        }
    }

    public static void saveHQInfo(int index, int hqid, Team team) throws GameActionException {
        hqIDs[index] = hqid;
        hqTeams[index] = team;
        hqSurroundRounds[index] = DEFAULT_SURROUND;
        hqReportSurroundRounds[index] = DEFAULT_SURROUND;
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            updateHQBroadcast(index);
        }
    }

    /*
    HQ loc is known, but hq id/team has changed
     */
    public static void reportHQ(int index) throws GameActionException {
        if (hqIDs[index] > 0) {
            Message locMsg = getHQLocMsg(hqLocs[index], true, false);
            queueMessage(locMsg);

            Message infoMsg = getHQInfoMsg(hqIDs[index], hqTeams[index], false);
            chainMessages(locMsg, infoMsg);
        } else {
            writeHQLocSolo(hqLocs[index], false);
        }
    }

    //broadcastHQLoc broadcastHQInfo
    /*
    Broadcast methods are used by HQs
     */
    public static void updateHQBroadcast(int index) throws GameActionException {
//        log("Updating HQ Broadcast " + index);

        boolean paired = (hqIDs[index] > 0);

        if (hqBroadcasts[index] == null) {
//            tlog("Initializing");
            hqBroadcasts[index] = getHQLocMsg(hqLocs[index], paired, true);
            queueMessage(hqBroadcasts[index]);
        }

        if (paired) {
//            tlog("Paired");
            hqBroadcasts[index].type = HQ_LOC_PAIRED_MSG;
            Message infoMsg = getHQInfoMsg(hqIDs[index], hqTeams[index], true);
            chainMessages(hqBroadcasts[index], infoMsg);
        } else {
//            tlog("Solo");
            hqBroadcasts[index].next = null;
            // do not alter prev of infoMsg, allows for backtracking
        }
    }

    public static void updateHQSurroundRound(int index, boolean isSurrounded) throws GameActionException {
        if (isSurrounded) {
            hqSurroundRounds[index] = roundNum;
//            hqReportSurroundRounds[index] = DEFAULT_SURROUND;
        } else {
            hqSurroundRounds[index] = DEFAULT_SURROUND;
            hqReportSurroundRounds[index] = DEFAULT_SURROUND;
        }
    }

    public static boolean checkHQSurroundStatus(int index) {
        return (roundNum - hqSurroundRounds[index] <= SURROUND_MEMORY);
    }

    public static void broadcastHQSurround() throws GameActionException {
        for (int i = knownHQCount; --i >= 0;) {
            if (checkHQSurroundStatus(i)) {
                if (roundNum - hqReportSurroundRounds[i] > SURROUND_MEMORY / 2) {
                    log("Broadcasting surround");
                    writeReportSurrounded(i, true);
                }
            }
        }
    }

    public static boolean checkHQIgnoreStatus(int index) {
        return (roundNum - hqIgnoreRounds[index] <= IGNORE_MEMORY);
    }
}

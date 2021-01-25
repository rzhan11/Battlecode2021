package newday;

import battlecode.common.*;

import static newday.CommManager.*;
import static newday.Comms.*;
import static newday.Debug.*;
import static newday.Robot.*;
import static newday.Message.*;
import static newday.Utils.*;

public class HQTracker {

    final public static int SURROUND_MEMORY = 50;
    final public static int DEFAULT_SURROUND = -100;
    final public static int SURROUND_UPDATE_FREQ = 5;

    final public static int IGNORE_MEMORY = 150;
    final public static int DEFAULT_IGNORE = -1000;


    public static MapLocation[] hqLocs = new MapLocation[MAX_HQ_COUNT];
    public static Message[] hqBroadcasts = new Message[MAX_HQ_COUNT]; // only used if myType == HQ
    // not guaranteed to be accurate, however if hqIDs[i] is known, then hqTeams[i] should be accurate
    public static Team[] hqTeams = new Team[MAX_HQ_COUNT];
    public static int[] hqIDs = new int[MAX_HQ_COUNT];
    public static int[] hqInfluence = new int[MAX_HQ_COUNT];
    public static int[] hqSurroundRounds = new int[MAX_HQ_COUNT];
    public static int[] hqReportSurroundRounds = new int[MAX_HQ_COUNT];
    public static int[] hqIgnoreRounds = new int[MAX_HQ_COUNT];
    public static int knownHQCount = 0;

    public static MapLocation[] symHQLocs = new MapLocation[3 * MAX_HQ_COUNT];
    public static Symmetry[] symHQType = new Symmetry[3 * MAX_HQ_COUNT];
    public static int symHQCount = 0;

    // ALLY hq ids that we know and want to read from
    // but we don't know their locs
    public static int[] extraAllyHQs = new int[MAX_HQ_COUNT];
    public static int extraAllyHQCount = 0;

    public static void updateKnownHQs() throws GameActionException {
        // add myself to hqinfo
        if (age == 0 && myType == RobotType.ENLIGHTENMENT_CENTER) {
            saveHQLoc(here);
            saveHQInfo(knownHQCount - 1, myID, us, -1);
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
                            saveHQInfo(j, ri.ID, ri.team, ri.influence);
                            if (myType != RobotType.ENLIGHTENMENT_CENTER) {
                                reportHQ(j);
                            }
                        } else if(hqTeams[j] == Team.NEUTRAL) {
                            saveHQInfo(j, ri.ID, ri.team, ri.influence);
                        }
                        break;
                    }
                }
                // adds new hq loc if not known
                if (!isKnown) {
                    saveHQLoc(ri.location);
                    saveHQInfo(knownHQCount - 1, ri.ID, ri.team, ri.influence);
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
        saveHQInfo(index, -1, (hqTeams[index] == Team.NEUTRAL) ? null: hqTeams[index].opponent(), -1);
    }

    public static void saveHQLoc(MapLocation loc) throws GameActionException {
        knownHQCount++;
        hqLocs[knownHQCount - 1] = loc;
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            updateHQBroadcast(knownHQCount - 1);
        }
    }

    public static void saveHQInfo(int index, int hqid, Team team, int influence) throws GameActionException {
        hqIDs[index] = hqid;
        hqTeams[index] = team;
        hqInfluence[index] = influence;

        hqSurroundRounds[index] = DEFAULT_SURROUND;
        hqReportSurroundRounds[index] = DEFAULT_SURROUND;
        hqIgnoreRounds[index] = DEFAULT_IGNORE;

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

            Message infoMsg = getHQInfoMsg(hqIDs[index], hqTeams[index], hqInfluence[index], false);
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
            Message infoMsg = getHQInfoMsg(hqIDs[index], hqTeams[index], hqInfluence[index], true);
            chainMessages(hqBroadcasts[index], infoMsg);
        } else {
//            tlog("Solo");
            hqBroadcasts[index].type = HQ_LOC_SOLO_MSG;
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
//            hqReportSurroundRounds[index] = DEFAULT_SURROUND;
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

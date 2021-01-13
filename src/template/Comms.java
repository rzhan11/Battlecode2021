package template;

import battlecode.common.*;

import static template.CommManager.*;
import static template.Debug.*;
import static template.HQTracker.*;
import static template.Map.*;
import static template.Robot.*;


public class Comms {

    /*
    How to make a new message
    2. Add a "read" and "write" method
    1. Add it to the switch statement in 'readMessage()'
    3. Add it to the switch statement in 'checkRepeat()' (if you want to do custom things for repeated msgs)
    4. Add it to the switch statement in 'Message.toString()' for nicer printing :)
     */

    /** TERMINOLOGY (Subject to change)
     *
     * Flag = the whole 24 bits, made up of the status and message
     * Status = just the 4 bits corresponding to status
     * Message = the 6 bits corresponding to message type and 14 bits of extra detail (20 bits total)
     */

    /*
    FLAG FORMAT:
    Description: [messageInfo] [messageType] [status]
    # of bits:    14            6             4
     */

    // constants for flag portioning
    final public static int FLAG_BITS = 24; // number of bits in a flag
    final public static int STATUS_BITS = 4;
    final public static int TYPE_BITS = 6; // 64 message types allowed
    final public static int INFO_BITS = FLAG_BITS - STATUS_BITS - TYPE_BITS;

    // offsets
    final public static int TYPE_OFFSET = STATUS_BITS;
    final public static int INFO_OFFSET = STATUS_BITS + TYPE_BITS;

    // masks
    final public static int STATUS_MASK = (1 << STATUS_BITS) - 1;
    final public static int TYPE_MASK = ((1 << TYPE_BITS) - 1) << TYPE_OFFSET;
    final public static int INFO_MASK = ((1 << INFO_BITS) - 1) << INFO_OFFSET;

    final public static int MAX_STATUS = (1 << STATUS_BITS) - 1;

    // MESSAGE TYPE CONSTANTS
    final public static int EMPTY_MSG = 0;

    final public static int HQ_LOC_SOLO_MSG = 1;
    final public static int HQ_LOC_PAIRED_MSG = 2;
    final public static int ALLY_HQ_INFO_MSG = 3;
    final public static int ENEMY_HQ_INFO_MSG = 4;
    final public static int NEUTRAL_HQ_INFO_MSG = 5;

    final public static int UNIT_BROADCAST_ALLY_HQ_MSG = 6;

    final public static int XBOUNDS_MSG = 7;
    final public static int XMIN_MSG = 8;
    final public static int XMAX_MSG = 9;
    final public static int XNONE_MSG = 10;
    final public static int YBOUNDS_MSG = 11;
    final public static int YMIN_MSG = 12;
    final public static int YMAX_MSG = 13;
    final public static int YNONE_MSG = 14;

//    final public static int ALL_TARGET_LOC_MSG = ;
//    final public static int MUCKRAKER_TARGET_LOC_MSG = ;


    // constants for coordinates
    final public static int COORD_BITS = 7;
    final public static int COORD_MASK = (1 << COORD_BITS) - 1;

    // used in determining MapLocations with 7 bits
    // these can be any valid coordinates on the map
    public static int BASE_X_COORD;
    public static int BASE_Y_COORD;
    public static int BASE_X_COORD_PART;
    public static int BASE_Y_COORD_PART;

    public static void initBaseCoords(MapLocation baseLoc) {
        BASE_X_COORD = baseLoc.x;
        BASE_Y_COORD = baseLoc.y;
        BASE_X_COORD_PART = BASE_X_COORD & (~COORD_MASK);
        BASE_Y_COORD_PART = BASE_Y_COORD & (~COORD_MASK);
    }

    public static int getStatusFromFlag(int flag) {
        return flag & STATUS_MASK;
    }

    public static Message getMessageFromFlag(int flag) {
        return new Message((flag & TYPE_MASK) >>> TYPE_OFFSET, (flag & INFO_MASK) >>> INFO_OFFSET);
    }

    /*
    Converts a MapLocation to 14-bit representation
     */
    public static int loc2bits(MapLocation loc) {
        return ((loc.y & COORD_MASK) << COORD_BITS) + (loc.x & COORD_MASK);
    }


    /*
    Converts 14 bits to a MapLocation
    -----
    bits = msgInfo
     */
    public static MapLocation bits2loc(int bits) {
        int x = BASE_X_COORD_PART + (bits & COORD_MASK);
        int y = BASE_Y_COORD_PART + ((bits & (COORD_MASK << COORD_BITS)) >>> COORD_BITS);
        if (x+64<=BASE_X_COORD) x+=128;
        if (x-64>=BASE_X_COORD) x-=128;
        if (y+64<=BASE_Y_COORD) y+=128;
        if (y-64>=BASE_Y_COORD) y-=128;
        return new MapLocation(x, y);
    }

    /*
    "Read" method header:

    (Rightmost/smallest bits at the top)

    [# OF BITS] | [MEANING]
    -----------------------
    4 | status (default)
    6 | message.type (default)
    7 | loc.x (last 14 bits are customizable)
    7 | loc.y

    (Leftmost/largest bits at the bottom)
     */

    /*
    Make sure to add your new messages to this switch statement
     */
    public static void readMessage(int id) throws GameActionException {
        int flag = rc.getFlag(id);
        Message msg = getMessageFromFlag(flag);
        if (msg.type == EMPTY_MSG) return;

        log("Reading message from " + id);
        tlog(msg.toString());
        switch(msg.type) {
            case EMPTY_MSG:
                readBlank(msg.info, id);
                break;
            case XBOUNDS_MSG:
            case XMIN_MSG:
            case XMAX_MSG:
            case XNONE_MSG:
                readXBounds(msg.info, msg.type);
                break;
            case YBOUNDS_MSG:
            case YMIN_MSG:
            case YMAX_MSG:
            case YNONE_MSG:
                readYBounds(msg.info, msg.type);
                break;
            case HQ_LOC_SOLO_MSG:
            case HQ_LOC_PAIRED_MSG:
                readHQLoc(msg.info, msg.type, id);
                break;
            case ALLY_HQ_INFO_MSG:
            case ENEMY_HQ_INFO_MSG:
            case NEUTRAL_HQ_INFO_MSG:
                readHQInfo(msg.info, msg.type, id);
                break;
            case UNIT_BROADCAST_ALLY_HQ_MSG:
                readUnitBroadcastAllyHQ(msg.info);
                break;

//            case ALL_TARGET_LOC_MSG:
//                readAllTargetLoc(msg.info);
//                break;
//            case MUCKRAKER_TARGET_LOC_MSG:
//                readMuckrakerTargetLoc(msg.info);
//                break;

            default:
                logi("ERROR: Unknown msg.type " + msg.type + " from id " + id);
                break;
        }
    }

    public static boolean checkRepeat(Message msg) {
        if (!msg.repeat) return false;
        switch(msg.type) {
            case XBOUNDS_MSG:
            case XMIN_MSG:
            case XMAX_MSG:
            case XNONE_MSG: {
                msg.type = getBoundsMsgType(true);
                msg.info = loc2bits(new MapLocation(XMIN, XMAX));
                return true;
            }

            case YBOUNDS_MSG:
            case YMIN_MSG:
            case YMAX_MSG:
            case YNONE_MSG: {
                msg.type = getBoundsMsgType(false);
                msg.info = loc2bits(new MapLocation(YMIN, YMAX));
                return true;
            }

            case UNIT_BROADCAST_ALLY_HQ_MSG:
                return rc.canGetFlag(msg.info + MIN_ID);

            default:
                return true;
        }
    }

    public static void readBlank(int msgInfo, int id) {
        /*
        NOTE: THIS DOES NOT WORK IF YOU WRITE CODE HERE
        LMK IF YOU ACC NEED THIS, YOU PROB DONT
         */
    }

//    /*
//    14 | ENEMY HQ LOC
//     */
//    public static void writeAllTargetEnemyHQ(MapLocation loc) throws GameActionException {
//        log("Writing 'All Target Enemy HQ' message");
//        tlog("Loc: " + loc);
//
//        Message msg = new Message(ALL_TARGET_LOC_MSG, loc2bits(loc));
//        queueMessage(msg, false);
//    }
//
//    public static void readAllTargetLoc(int msgInfo) throws GameActionException {
//        switch (myType) {
//            case MUCKRAKER: break;
//            case POLITICIAN: break;
//            default: return;
//        }
//
//        MapLocation loc = bits2loc(msgInfo);
//        tlog("Target HQ loc: " + loc);
//
//        targetEnemyHQLoc = loc;
//    }
//
//    /*
//    14 | ENEMY HQ LOC
//     */
//    public static void writeMuckrakerTargetEnemyHQ(MapLocation loc) throws GameActionException {
//        log("Writing 'Muckraker Target Enemy HQ' message");
//        tlog("Loc: " + loc);
//
//        Message msg = new Message(MUCKRAKER_TARGET_LOC_MSG, loc2bits(loc));
//        queueMessage(msg, false);
//    }
//
//    public static void readMuckrakerTargetLoc(int msgInfo) throws GameActionException {
//        if (myType != RobotType.MUCKRAKER) {
//            return;
//        }
//
//        MapLocation loc = bits2loc(msgInfo);
//        tlog("Target HQ loc: " + loc);
//
//        targetEnemyHQLoc = loc;
//    }

    /*
    Converts 14 bits into a MapLocation(XMIN, XMAX) or MapLocation(YMIN, YMAX)
     */
    public static MapLocation bits2bounds(int bits, boolean isX) {
        int baseCoordPart = isX ? BASE_X_COORD_PART : BASE_Y_COORD_PART;
        int baseCoord = isX ? BASE_X_COORD : BASE_Y_COORD;
        int x = baseCoordPart + (bits & COORD_MASK);
        int y = baseCoordPart + ((bits & (COORD_MASK << COORD_BITS)) >>> COORD_BITS);
        if (x+64<=baseCoord) x+=128;
        if (x-64>=baseCoord) x-=128;
        if (y+64<=baseCoord) y+=128;
        if (y-64>=baseCoord) y-=128;
        return new MapLocation(x, y);
    }

    public static int getBoundsMsgType(boolean isX) {
        if (isX) {
            if (XMIN != -1) {
                if (XMAX != -1) return XBOUNDS_MSG; // both known
                else return XMIN_MSG; // xmin known
            } else {
                if (XMAX != -1) return XMAX_MSG; // xmax known
                else return XNONE_MSG; // neither known
            }
        } else {
            if (YMIN != -1) {
                if (YMAX != -1) return YBOUNDS_MSG; // both known
                else return YMIN_MSG; // ymin known
            } else {
                if (YMAX != -1) return YMAX_MSG; // ymax known
                else return YNONE_MSG; // neither known
            }
        }
    }

    /*
    7 | XMIN
    7 | XMAX
     */
    public static void writeXBounds() throws GameActionException {
        int msgType = getBoundsMsgType(true);
        MapLocation loc = new MapLocation(XMIN, XMAX);

        log("Writing 'X Bounds' " + msgType + " " + loc);
        Message msg = new Message(msgType, loc2bits(loc), myType == RobotType.ENLIGHTENMENT_CENTER);
        queueMessage(msg, false);
    }

    public static void readXBounds(int msgInfo, int msgType) throws GameActionException {
        MapLocation loc = bits2bounds(msgInfo, true);
        // min
        if (XMIN == -1) {
            if (msgType == XBOUNDS_MSG || msgType == XMIN_MSG) {
                XMIN = loc.x;
            }
        }
        // max
        if (XMAX == -1) {
            if (msgType == XBOUNDS_MSG || msgType == XMAX_MSG) {
                XMAX = loc.y;
            }
        }
    }

    /*
    7 | YMIN
    7 | YMAX
     */
    public static void writeYBounds() throws GameActionException {
        int msgType = getBoundsMsgType(false);
        MapLocation loc = new MapLocation(YMIN, YMAX);

        log("Writing 'Y Bounds' " + msgType + " " + loc);
        Message msg = new Message(msgType, loc2bits(loc), myType == RobotType.ENLIGHTENMENT_CENTER);
        queueMessage(msg, false);
    }

    public static void readYBounds(int msgInfo, int msgType) throws GameActionException {
        MapLocation loc = bits2bounds(msgInfo, false);
        // min
        if (YMIN == -1) {
            if (msgType == YBOUNDS_MSG || msgType == YMIN_MSG) {
                YMIN = loc.x;
            }
        }
        // max
        if (YMAX == -1) {
            if (msgType == YBOUNDS_MSG || msgType == YMAX_MSG) {
                YMAX = loc.y;
            }
        }
    }

    /*
    14 | HQ LOCATION
     */
    public static Message getHQLocMsg(MapLocation loc, boolean paired, boolean repeat) {
        return new Message(paired?HQ_LOC_PAIRED_MSG:HQ_LOC_SOLO_MSG, loc2bits(loc), repeat);
    }

    public static void writeHQLocSolo(MapLocation loc, boolean repeat) throws GameActionException {
        log("Writing 'HQ Loc' " + loc);

        Message msg = getHQLocMsg(loc, false, repeat);
        queueMessage(msg, false);
    }

    public static void readHQLoc(int msgInfo, int msgType, int id) throws GameActionException {
        boolean paired = (msgType == HQ_LOC_PAIRED_MSG);
        // todo make this able to receive from any hq
        // only receive paired messages from my master
        if (myType != RobotType.ENLIGHTENMENT_CENTER && id != myMaster) {
            paired = false;
        }

        MapLocation loc = bits2loc(msgInfo);
        // check if this hq is known
        for (int i = knownHQCount; --i >= 0;) {
            if (loc.equals(hqLocs[i])) {
                // this hq is known
                if (paired && hqIDs[i] < 0) { // if the hqid is unknown, setup receiving
                    tlog("Known loc " + loc + " will receive " + id);
                    hqIDs[i] = -id;
                }
                return;
            }
        }

        // by this point, we know that this is a new HQ loc
        tlog("New HQ " + loc);
        saveHQLoc(loc);

        if (paired) {
            tlog("Receiving from " + id);
            hqIDs[knownHQCount - 1] = -id;
        } else {
            if (myType == RobotType.ENLIGHTENMENT_CENTER) {
                updateHQBroadcast(knownHQCount - 1);
            }
        }
    }

    /*
    Assumes MAX_ID = 2^14 + 10000
    14 | ENEMY HQ ID
     */

    public static Message getHQInfoMsg(int id, Team team, boolean repeat) {
        int msgType;
        if (team == us) {
            msgType = ALLY_HQ_INFO_MSG;
        } else if (team == them) {
            msgType = ENEMY_HQ_INFO_MSG;
        } else if (team == neutral) {
            msgType = NEUTRAL_HQ_INFO_MSG;
        } else {
            // should never reach here
            logi("WARNING: 'writeEnemyHQID' for unknown team " + team);
            return null;
        }

        int value = id - MIN_ID;
        if (!(0 <= value && value < (1 << 14))) {
            logi("WARNING: 'writeEnemyHQID' for " + value + " is too big");
        }

        return new Message(msgType, value, repeat);
    }

    public static void writeHQInfo(int id, Team team, boolean repeat) throws GameActionException {
        log("Writing 'HQ Info' message");
        tlog("ID: " + id);
        tlog("Team: " + team);

        Message msg = getHQInfoMsg(id, team, repeat);
        queueMessage(msg, false);
    }

    public static void readHQInfo(int msgInfo, int msgType, int id) throws GameActionException {
        int hqid = msgInfo + MIN_ID;
        Team team;
        switch (msgType) {
            case ALLY_HQ_INFO_MSG:
                team = us;
                break;
            case ENEMY_HQ_INFO_MSG:
                team = them;
                break;
            case NEUTRAL_HQ_INFO_MSG:
                team = neutral;
                break;
            default:
                logi("WARNING: Unknown msgType in 'readHQInfo' " + msgType);
                return;
        }

        for (int i = knownHQCount; --i >= 0;) {
            if (hqIDs[i] == -id) {
                saveHQInfo(i, hqid, team);
                tlog("Receiving from " + id);
                tlog("HQ " + hqLocs[i] + " " + hqIDs[i] + " " + hqTeams[i]);

                if (myType == RobotType.ENLIGHTENMENT_CENTER) {
                    updateHQBroadcast(i);
                }
                return;
            } else if (hqIDs[i] == hqid) {
                tlog("Info is already known");
                return;
            }
        }

        tlog("No receiving ID found");
    }

    /*
    Assumes MAX_ID = 2^14 + 10000
    14 | ENEMY HQ ID
    Note: this message should only be written by non-hq robots (aka units)
     */
    public static void writeUnitBroadcastAllyHQ(int id, boolean repeat) throws GameActionException {
        log("Writing 'Unit Broadcast Ally HQ' message " + id);

        Message msg = new Message(UNIT_BROADCAST_ALLY_HQ_MSG, id - MIN_ID, repeat);
        queueMessage(msg, false);
    }

    public static void readUnitBroadcastAllyHQ(int msgInfo) throws GameActionException {
        int hqid = msgInfo + MIN_ID;

        // check if hq id is still alive
        if (!rc.canGetFlag(hqid)) {
            tlog("Ally HQ is dead");
            return;
        }

        for (int i = knownHQCount; --i >= 0;) {
            if (hqid == hqIDs[i]) {
                return;
            }
        }

        for (int i = extraAllyHQCount; --i >= 0;) {
            if (hqid == extraAllyHQs[i]) {
                return;
            }
        }

        tlog("Saving extra ally hq");
        extraAllyHQCount++;
        extraAllyHQs[extraAllyHQCount - 1] = hqid;
        if (myType != RobotType.ENLIGHTENMENT_CENTER) {
            writeUnitBroadcastAllyHQ(hqid, true);
        }
    }
}
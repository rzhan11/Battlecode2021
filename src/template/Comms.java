package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Robot.*;


public class Comms {

    /*
     FRIENDLY REMINDER:
     IF YOUR NEW READ/WRITE ISN'T WORKING,
     MAKE SURE YOU ADDED IT TO THE SWITCH STATEMENT IN 'readMessage'
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
    static final int FLAG_BITS = 24; // number of bits in a flag
    static final int STATUS_BITS = 4;
    static final int TYPE_BITS = 6;
    static final int INFO_BITS = FLAG_BITS - STATUS_BITS - TYPE_BITS;

    // offsets
    static final int TYPE_OFFSET = STATUS_BITS;
    static final int INFO_OFFSET = STATUS_BITS + TYPE_BITS;

    // masks
    static final int STATUS_MASK = (1 << STATUS_BITS) - 1;
    static final int TYPE_MASK = ((1 << TYPE_BITS) - 1) << TYPE_OFFSET;
    static final int INFO_MASK = ((1 << INFO_BITS) - 1) << INFO_OFFSET;


    // add message types here
    static final int BLANK_MESSAGE_TYPE = 0;
    static final int ENEMY_HQ_MESSAGE_TYPE = 1;


    // constants for coordinates
    static final int COORD_BITS = 7;
    static final int COORD_MASK = (1 << COORD_BITS) - 1;

    // used in determining MapLocations with 7 bits
    // these can be any valid coordinates on the map
    static int BASE_X_COORD;
    static int BASE_Y_COORD;
    static int BASE_X_COORD_PART;
    static int BASE_Y_COORD_PART;

    public static void initBaseCoords(MapLocation baseLoc) {
        BASE_X_COORD = baseLoc.x;
        BASE_Y_COORD = baseLoc.y;
        BASE_X_COORD_PART = BASE_X_COORD & (~COORD_MASK);
        BASE_Y_COORD_PART = BASE_Y_COORD & (~COORD_MASK);
    }

    static int myStatus;
    static int myMessageType;
    static int myMessageInfo;

    /*
    Called at the beginning of each turn to reset the flags
     */
    public static void resetFlag() {
        myStatus = 0;
        myMessageType = 0;
        myMessageInfo = 0;
    }

    /*
    Whenever a change is made to the status or message, the flag is updated accordingly
     */
    public static void updateFlag() throws GameActionException {
        rc.setFlag((myMessageInfo << INFO_OFFSET) + (myMessageType << TYPE_OFFSET) + myStatus);
    }

    public static int getStatus(int flag) {
        return flag & STATUS_MASK;
    }

    public static int getMessageType(int flag) {
        return (flag & TYPE_MASK) >>> TYPE_OFFSET;
    }

    public static int getMessageInfo(int flag) {
        return (flag & INFO_MASK) >>> INFO_OFFSET;
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
    bits = messageInfo
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
        int msgType = getMessageType(flag);
        int msgInfo = getMessageInfo(flag);
//        tlog("flag " + flag);
//        tlog("type " + msgType);
//        tlog("info " + msgInfo);
        switch(msgType) {
            case BLANK_MESSAGE_TYPE:
                readBlank(msgInfo, id);
                break;
            case ENEMY_HQ_MESSAGE_TYPE:
                readFoundEnemyHQ(msgInfo, id);
                break;
            default:
                logi("ERROR: Unknown messageType of " + msgType + " from id " + id);
                break;
        }
    }

    public static void readBlank(int info, int id) {
//        log("Reading 'Blank' message from " + id);
    }

    /*
    14 | ENEMY HQ LOC
     */
    public static void writeFoundEnemyHQ(MapLocation loc) throws GameActionException {
        log("Writing 'Found Enemy HQ' message");
        tlog("Loc: " + loc);
        myMessageType = ENEMY_HQ_MESSAGE_TYPE;

        myMessageInfo = loc2bits(loc);

        updateFlag();
    }

    public static void readFoundEnemyHQ(int msgInfo, int id) {
        log("Reading 'Found Enemy HQ' message from " + id);

        if (myType != RobotType.ENLIGHTENMENT_CENTER) {
            return;
        }

        MapLocation loc = bits2loc(msgInfo);
        // check if enemy HQ has been previously found
        for (int i = enemyHQCount; --i >= 0;) {
            if (loc.equals(enemyHQLocs[i])) {
                tlog("Known loc: " + loc);
                return;
            }
        }

        // by this point, we know that this is a new enemy HQ
        if (enemyHQCount == MAX_HQ_COUNT) { // safety check
            logi("ERROR: enemyHQCount reached MAX_HQ_COUNT limit");
            return;
        }

        // saves to array
        enemyHQLocs[enemyHQCount++] = loc;
        tlog("New loc: " + loc);
    }

    /*
    14 | ENEMY HQ LOC
     */
//    public static void writeMuckrakerTargetEnemyHQ(MapLocation loc) throws GameActionException {
//        log("Writing 'Muckraker Target Enemy HQ' message");
//        tlog("Loc: " + loc);
//        myMessageType = ENEMY_HQ_MESSAGE_TYPE;
//
//        myMessageInfo = loc2bits(loc);
//
//        updateFlag();
//    }
//
//    public static void readMuckrakerAttackHQ(MapLocation loc) throws GameActionException {
//
//    }
}

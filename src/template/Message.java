package template;

import battlecode.common.*;

import static template.Comms.*;
import static template.Constants.*;
import static template.Debug.*;

public class Message {
    int type;
    int info;
    boolean repeat;

    public Message(int msgType, int msgInfo) {
        this.type = msgType;
        this.info = msgInfo;
        this.repeat = false;
    }

    public Message(int msgType, int msgInfo, boolean repeat) {
        this.type = msgType;
        this.info = msgInfo;
        this.repeat = repeat;
    }

    @Override
    public String toString() {
        if (USE_BASIC_MESSAGES) {
            return type + " " + info + " " + repeat;
        }

        String str = "";
        boolean valid = !repeat || checkRepeat(this);
        if (!valid) {
            str += "INVALID ";
        }

        switch (type) {
            case EMPTY_MSG:
                str += "[BLANK MESSAGE]"; break;

            case ENEMY_HQ_LOC_MSG:
                str += "[ENEMY HQ LOC] " + bits2loc(info); break;
            case ENEMY_HQ_ID_MSG:
                str += "[ENEMY HQ ID] " + (info + MIN_ID); break;

            case ALL_TARGET_LOC_MSG:
                str += "[ALL TARGET LOC] " + bits2loc(info); break;
            case MUCKRAKER_TARGET_LOC_MSG:
                str += "[MUCKRAKER TARGET LOC]" + bits2loc(info); break;

            case XBOUNDS_MSG:
                str += "[X]" + bits2loc(info); break;
            case XMIN_MSG:
                str += "[X]" + new MapLocation(bits2loc(info).x, -1); break;
            case XMAX_MSG:
                str += "[X]" + new MapLocation(-1, bits2loc(info).y); break;
            case XNONE_MSG:
                str += "[X]" + new MapLocation(-1, -1); break;

            case YBOUNDS_MSG:
                str += "[Y]" + bits2loc(info); break;
            case YMIN_MSG:
                str += "[Y]" + new MapLocation(bits2loc(info).x, -1); break;
            case YMAX_MSG:
                str += "[Y]" + new MapLocation(-1, bits2loc(info).y); break;
            case YNONE_MSG:
                str += "[Y]" + new MapLocation(-1, -1); break;

            default:
                str += type + " " + info + " " + repeat; break;
        }
        return str;
    }
}
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

        switch (type) {
            case EMPTY_MSG:
                return "[BLANK MESSAGE]";

            case ENEMY_HQ_LOC_MSG:
                return "[ENEMY HQ LOC] " + bits2loc(info);
            case ENEMY_HQ_ID_MSG:
                return "[ENEMY HQ ID] " + (info + MIN_ID);

            case ALL_TARGET_LOC_MSG:
                return "[ALL TARGET LOC] " + bits2loc(info);
            case MUCKRAKER_TARGET_LOC_MSG:
                return "[MUCKRAKER TARGET LOC]" + bits2loc(info);

            case XBOUNDS_MSG:
                return "[X]" + bits2loc(info);
            case XMIN_MSG:
                return "[X]" + new MapLocation(bits2loc(info).x, -1);
            case XMAX_MSG:
                return "[X]" + new MapLocation(-1, bits2loc(info).y);
            case XNONE_MSG:
                return "[X]" + new MapLocation(-1, -1);

            case YBOUNDS_MSG:
                return "[Y]" + bits2loc(info);
            case YMIN_MSG:
                return "[Y]" + new MapLocation(bits2loc(info).x, -1);
            case YMAX_MSG:
                return "[Y]" + new MapLocation(-1, bits2loc(info).y);
            case YNONE_MSG:
                return "[Y]" + new MapLocation(-1, -1);

            default:
                return type + " " + info + " " + repeat;
        }
    }
}
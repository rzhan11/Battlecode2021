package template;

import battlecode.common.*;

import static template.Comms.*;
import static template.Constants.*;
import static template.Debug.*;

public class Message {
    int type;
    int info;
    boolean repeat;
    Message prev; // null by default
    Message next; // null by default

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

//            case ENEMY_HQ_LOC_MSG:
//                return "[ENEMY HQ LOC] " + bits2loc(info);
//            case OLD_ENEMY_HQ_ID_MSG:
//                return "[ENEMY HQ ID] " + (info + MIN_ID);

//            case ALL_TARGET_LOC_MSG:
//                return "[ALL TARGET LOC] " + bits2loc(info);
//            case MUCKRAKER_TARGET_LOC_MSG:
//                return "[MUCKRAKER TARGET LOC]" + bits2loc(info);

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

            case HQ_LOC_MSG:
                return "[HQ LOC] " + bits2loc(info);

            case ALLY_HQ_INFO_MSG:
                return "[HQ INFO] ALLY " + (info + MIN_ID);
            case ENEMY_HQ_INFO_MSG:
                return "[HQ INFO] ENEMY " + (info + MIN_ID);
            case NEUTRAL_HQ_INFO_MSG:
                return "[HQ INFO] NEUTRAL " + (info + MIN_ID);

            default:
                return type + " " + info + " " + repeat;
        }
    }

    public Message getMessageFront() {
        Message cur = this;
        while (cur.prev != null) {
            cur = cur.prev;
        }
        return cur;
    }

    public Message getMessageBack() {
        Message cur = this;
        while (cur.next != null) {
            cur = cur.next;
        }
        return cur;
    }

    public String getFullString() {
        // go to front of message chain
        Message cur = getMessageFront();

        StringBuilder str = new StringBuilder();
        while (cur != null) {
            str.append("* ");
            if (this == cur) {
                str.append("[CUR] ");
            }
            str.append(cur);
            if (cur.next != null) {
                str.append("\n");
            }
            cur = cur.next;
        }

        return str.toString();
    }

    public boolean isChained() {
        return prev != null || next != null;
    }

    public static void chainMessages(Message m1, Message m2) {
        m1.prev = null;
        m1.next = m2;
        m2.prev = m1;
        m2.next = null;
    }

    public static void chainMessages(Message[] msgs) {
        msgs[0].prev = null;
        msgs[msgs.length - 1].next = null;
        for (int i = 0; i < msgs.length - 1; i++) {
            msgs[i].next = msgs[i + 1];
            msgs[i + 1].prev = msgs[i];
        }
    }
}
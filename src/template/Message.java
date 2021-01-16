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

    // assumes m1 and m2 != null
    public static boolean hasSameContent(Message m1, Message m2) {
        return m1.type == m2.type && m1.info == m2.info;
    }

    @Override
    public String toString() {
        if (USE_BASIC_MESSAGES) {
            return type + " " + info + " " + repeat;
        }

        String str = "";
        if (repeat) {
            if (!checkRepeat(this)) {
                str += "INVALID ";
            }
            str += "~";
        }

        switch (type) {
            case EMPTY_MSG:
                str += "[BLANK]"; break;

            case BROADCAST_MY_MASTER_MSG:
                str += "[MY MASTER] " + (info + MIN_ID); break;
            case ECHO_SURROUNDED_MSG:
                str += "[ECHO SURROUND] " + bits2loc(info); break;
            case ECHO_NOT_SURROUNDED_MSG:
                str += "[ECHO NOT SURROUND] " + bits2loc(info); break;

            case HQ_LOC_SOLO_MSG:
                str += "[HQ LOC 1] " + bits2loc(info); break;
            case HQ_LOC_PAIRED_MSG:
                str += "[HQ LOC 2] " + bits2loc(info); break;

            case ALLY_HQ_INFO_MSG:
                str += "[HQ ID ALLY] " + (info + MIN_ID); break;
            case ENEMY_HQ_INFO_MSG:
                str += "[HQ ID ENEMY] " + (info + MIN_ID); break;
            case NEUTRAL_HQ_INFO_MSG:
                str += "[HQ ID NEUTRAL] " + (info + MIN_ID); break;

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

            case SYMMETRY_MSG:
                str += "[SYM] ";
                str += (info & 1);
                str += (info & 2) >> 1;
                str += (info & 4) >> 2;
                break;


            case REPORT_NON_MASTER_MSG:
                str += "[NON MASTER] " + (info + MIN_ID); break;
            case REPORT_SURROUNDED_MSG:
                str += "[REP SURROUND] " + bits2loc(info); break;
            case REPORT_NOT_SURROUNDED_MSG:
                str += "[REP NOT SURROUND] " + bits2loc(info); break;


            default:
                str += type + " " + info + " " + repeat; break;
        }
        return str;
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
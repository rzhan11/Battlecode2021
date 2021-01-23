package newday;

import battlecode.common.*;

import static newday.Comms.*;
import static newday.Debug.*;
import static newday.Robot.*;

public class CommManager {
    public static int QUEUE_LEN; // different for each RobotType

    public static Message[] msgQueue;
    public static int msgQueueIndex = 0;
    public static int msgQueueCount = 0;

    public static Message[] repeatQueue;
    public static int repeatQueueIndex = 0;
    public static int repeatQueueCount = 0;

    public static boolean useRepeatQueue;

    // private variables - use getter/setter methods

    // status is delayed by 1, due to turn order stuff
    private static int nextStatus;
    private static int myStatus;
    private static Message myMessage;

    public static void initQueues() {
        if (msgQueue != null) {
            return;
        }
        switch(myType) {
            case ENLIGHTENMENT_CENTER:
                QUEUE_LEN = 50;
                break;
            case MUCKRAKER:
            case POLITICIAN:
            case SLANDERER:
                QUEUE_LEN = 25;
                break;
        }
        msgQueue = new Message[QUEUE_LEN];
        repeatQueue = new Message[QUEUE_LEN];
    }

    public static int getStatus() {
        return myStatus;
    }

    public static Message getMessage() {
        return myMessage;
    }

    // delay is used if you want an HQ to set status for a troop that it just spawned
    public static void setStatus(int status, boolean delay) throws GameActionException {
        if (!(0 <= status && status <= MAX_STATUS)) {
            logi("WARNING: Tried to set invalid status " + status);
            return;
        }
        if (delay) {
            nextStatus = status;
        } else {
            myStatus = status;
        }
        updateFlag();
    }

    // leave this private, use queueMessage instead
    private static void setMessage(Message msg) throws GameActionException {
        myMessage = msg;
        updateFlag();
    }

    /*
    Called at the beginning of each turn to reset the flags
     */
    public static void resetFlag() throws GameActionException {
        myStatus = nextStatus;
        nextStatus = 0;
        myMessage = new Message(EMPTY_MSG, 0);
        updateMyMessage();
        updateFlag();
    }

    /*
    Run at the end of a turn
    Updates relevant variables
     */
    public static void updateMessageCount() throws GameActionException {
        if (msgQueueCount + repeatQueueCount == 0) {
            return;
        }
        if (useRepeatQueue) {
            repeatQueue[repeatQueueIndex] = myMessage.next;
            if (myMessage.next == null) {
                // readd to queue
                repeatQueue[(repeatQueueIndex + repeatQueueCount) % QUEUE_LEN] = myMessage.getMessageFront();
                repeatQueueIndex = (repeatQueueIndex + 1) % QUEUE_LEN;
                useRepeatQueue = false; // switch
            } else {} // do nothing
        } else {
            msgQueue[msgQueueIndex] = myMessage.next;
            if (myMessage.next == null) {
                // delete from queue
                msgQueueIndex = (msgQueueIndex + 1) % QUEUE_LEN;
                msgQueueCount--;
                useRepeatQueue = true;
            } else {} // do nothing
        }
    }

    /*
    Whenever a change is made to the status or message, the flag is updated accordingly
     */
    private static void updateFlag() throws GameActionException {
        rc.setFlag(myMessage.info + myMessage.type + (myStatus << STATUS_OFFSET));
    }

    public static void updateMyMessage() throws GameActionException {
        // clear invalid repeat messages
        while (repeatQueueCount > 0) {
            Message msg = repeatQueue[repeatQueueIndex];
            if (checkRepeat(msg)) {
                break;
            } else {
                repeatQueueCount--;
                repeatQueueIndex = (repeatQueueIndex + 1) % QUEUE_LEN;
            }
        }

        if (msgQueueCount + repeatQueueCount == 0) {
            return;
        }

        if (useRepeatQueue) {
            if (repeatQueueCount == 0) {
                useRepeatQueue = false;
            }
        } else {
            if (msgQueueCount == 0) {
                useRepeatQueue = true;
            }
        }

        if (useRepeatQueue) {
            setMessage(repeatQueue[repeatQueueIndex]);
        } else {
            setMessage(msgQueue[msgQueueIndex]);
        }
    }

    /*
    Repeated messages default to urgent
    Non-repeated messages deafult to not urgent
     */
    public static void queueMessage(Message msg) throws GameActionException {
        queueMessage(msg, msg.repeat);
    }

    public static void queueMessage(Message msg, boolean urgent) throws GameActionException {
        if (msg.repeat) {
            queueRepeatMessage(msg, urgent);
        } else {
            queueNormalMessage(msg, urgent);
        }
    }

    public static void queueNormalMessage(Message msg, boolean urgent) throws GameActionException {
        if (msgQueueCount >= QUEUE_LEN) {
            logi("WARNING: msgQueueCount reached MSG_QUEUE_LEN");
            return;
        }

        if (urgent && msgQueueCount > 0) {
            int index = (msgQueueIndex - 1 + QUEUE_LEN) % QUEUE_LEN;
            msgQueue[index] = msgQueue[msgQueueIndex];
            msgQueue[msgQueueIndex] = msg;
            msgQueueIndex = index;
        } else {
            int index = (msgQueueIndex + msgQueueCount) % QUEUE_LEN;
            msgQueue[index] = msg;
        }
        msgQueueCount++;
        updateMyMessage();
    }

    public static void queueRepeatMessage(Message msg, boolean urgent) throws GameActionException {
        if (repeatQueueCount >= QUEUE_LEN) {
            logi("WARNING: repeatQueueCount reached MSG_QUEUE_LEN");
            return;
        }

        if (urgent && repeatQueueCount > 0) {
            int index = (repeatQueueIndex - 1 + QUEUE_LEN) % QUEUE_LEN;
            repeatQueue[index] = repeatQueue[repeatQueueIndex];
            repeatQueue[repeatQueueIndex] = msg;
            repeatQueueIndex = index;
        } else {
            int index = (repeatQueueIndex + repeatQueueCount) % QUEUE_LEN;
            repeatQueue[index] = msg;
        }
        repeatQueueCount++;
        updateMyMessage();
    }

    public static void printMessageQueue() throws GameActionException {
        log("Queued Normal Messages: " + msgQueueCount);
        int numPrint = Math.min(5, msgQueueCount);
        for (int i = 0; i < numPrint; i++) {
            int index = (msgQueueIndex + i) % QUEUE_LEN;
            if (msgQueue[index] != null) {
                tlog(msgQueue[index].toString());
                if (msgQueue[index].isChained()) {
                    log(msgQueue[index].getFullString());
                }
            } else {
                tlog(null);
            }
            log();
        }
    }

    public static void printRepeatQueue() throws GameActionException {
        log("Queued Repeat Messages: " + repeatQueueCount);
        int numPrint = Math.min(5, repeatQueueCount);
        for (int i = 0; i < numPrint; i++) {
            int index = (repeatQueueIndex + i) % QUEUE_LEN;
            if (repeatQueue[index] != null) {
                tlog(repeatQueue[index].toString());
                if (repeatQueue[index].isChained()) {
                    log(repeatQueue[index].getFullString());
                }
            } else {
                tlog(null);
            }
            log();
        }
    }
}
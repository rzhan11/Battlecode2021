package template;

import battlecode.common.*;

import static template.Comms.*;
import static template.Debug.*;
import static template.Robot.*;

public class CommManager {
    final public static int MSG_QUEUE_LEN = 100;

    public static Message[] msgQueue = new Message[MSG_QUEUE_LEN];

    public static int msgQueueIndex = 0;
    public static int msgQueueCount = 0;

    // private variables - use getter/setter methods

    // status is delayed by 1, due to turn order stuff
    private static int nextStatus;
    private static int myStatus;
    private static Message myMessage;

    public static int getStatus() {
        return myStatus;
    }

    public static Message getMessage() {
        return myMessage;
    }

    public static void setStatus(int status) throws GameActionException {
        if (!(0 <= status && status <= MAX_STATUS)) {
            logi("WARNING: Tried to set invalid status " + status);
            return;
        }
        nextStatus = status;
        updateFlag();
    }

    public static void setMessage(Message msg) throws GameActionException {
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
        updateFlag();
    }

    /*
    Run at the end of a turn
    Updates relevant variables
     */
    public static void updateMessageCount() throws GameActionException {
        if (msgQueueCount > 0) {
            msgQueue[msgQueueIndex] = myMessage.next;
            if (myMessage.next == null) {
                // if repeated, readd it to the queue
                if (checkRepeat(myMessage)) {
//                log("YES REPEAT");
                    msgQueue[(msgQueueIndex + msgQueueCount) % MSG_QUEUE_LEN] = myMessage.getMessageFront();
                } else {
//                log("NO REPEAT");
                    msgQueueCount--;
                }
                msgQueueIndex = (msgQueueIndex + 1) % MSG_QUEUE_LEN;
            }
        }
    }

    /*
    Whenever a change is made to the status or message, the flag is updated accordingly
     */
    private static void updateFlag() throws GameActionException {
        rc.setFlag((myMessage.info << INFO_OFFSET) + (myMessage.type << TYPE_OFFSET) + myStatus);
    }

    public static void updateQueuedMessage() throws GameActionException {
        while (msgQueueCount > 0) {
            Message msg = msgQueue[msgQueueIndex];
            boolean valid = !msg.repeat || checkRepeat(msg);
            if (valid) {
                setMessage(msg);
                break;
            } else {
                msgQueueCount--;
                msgQueueIndex = (msgQueueIndex + 1) % MSG_QUEUE_LEN;
            }
        }
    }

    public static void queueMessage(Message msg, boolean urgent) throws GameActionException {
        if (msgQueueCount >= MSG_QUEUE_LEN) {
            logi("WARNING: msgQueueCount reached MSG_QUEUE_LEN");
            return;
        }

        int index;
        // todo chained messages vs urgent messages
        // todo add 2 queues, 1 for repeated messages, 1 for one-time message
        if (urgent) {
            index = (msgQueueIndex - 1 + MSG_QUEUE_LEN) % MSG_QUEUE_LEN;
            msgQueueIndex = index;
        } else {
            index = (msgQueueIndex + msgQueueCount) % MSG_QUEUE_LEN;
        }
        msgQueue[index] = msg;

        msgQueueCount++;

        if (urgent || msgQueueCount == 1) { // if this is urgent or if its the only message in the queue
            updateQueuedMessage();
        }
    }

    public static void printMessageQueue() throws GameActionException {
        log("Queued Messages: " + msgQueueCount);
        for (int i = 0; i < msgQueueCount; i++) {
            int index = (msgQueueIndex + i) % MSG_QUEUE_LEN;
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
}
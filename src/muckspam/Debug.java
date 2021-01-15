package muckspam;

import battlecode.common.*;

import static muckspam.Robot.*;

public class Debug {

    public static boolean SILENCE_LOGS = false;
    final public static boolean SILENCE_INDICATORS = false;

    // Robot.endTurn and Robot.printMyInfo
    final public static boolean NO_TURN_LOGS = false;

    // Message.toString
    final public static boolean USE_BASIC_MESSAGES = false;

    private static StringBuilder buffer = new StringBuilder();

    /*
    Selectively turn off print logs for certain units
    NOTE: important messages will still be displayed
     */
    public static boolean isDisplayLogs() {
        if (SILENCE_LOGS) {
            return false;
        }
        switch (myType) {
            case ENLIGHTENMENT_CENTER: return true; // this unit prints debug logs
            case POLITICIAN:           return true; // change to 'return false' to not print debug logs
            case SLANDERER:            return true;
            case MUCKRAKER:            return true;
            default:
                logi("ERROR: Sanity check failed - unknown class " + myType);
                return false;
        }
    }

    /*
    Selectively turn off dots and lines for certain units
     */
    public static boolean isDisplayIndicators() {
        if (SILENCE_INDICATORS) {
            return false;
        }
        switch (myType) {
            case ENLIGHTENMENT_CENTER: return true;
            case POLITICIAN:           return true;
            case SLANDERER:            return true;
            case MUCKRAKER:            return true;
            default:
                logi("ERROR: Sanity check failed - unknown class " + myType);
                return false;
        }
    }

    public static void clearBuffer() {
        buffer = new StringBuilder();
    }

    public static void printBuffer() {
        if (buffer.length() > 0) {
            System.out.println(buffer);
            buffer = new StringBuilder();
        }
    }

    /*
    Prints a separator line, currently a single dash
    */
    public static void log() {
        if (isDisplayLogs()) {
            buffer.append("\n");
        }
    }

    /*
    Prints message
    Can be turned off by setting 'DISPLAY_LOGS' to false
    */
    public static void log(String str) {
        if (isDisplayLogs()) {
            buffer.append("\n").append(str);
        }
    }

    /*
    Prints message with a single tab in front
    Can be turned off by setting 'DISPLAY_LOGS' to false
    */
    public static void tlog(String str) {
        if (isDisplayLogs()) {
            buffer.append("\n- ").append(str);
        }
    }

    /*
    Prints message with a double tab in front
    Can be turned off by setting 'DISPLAY_LOGS' to true
    */
    public static void ttlog(String str) {
        if (isDisplayLogs()) {
            buffer.append("\n-- ").append(str);
        }
    }

    public static void logByte(String tag) {
        if (isDisplayLogs()) {
            buffer.append("\nBYTECODE LEFT - ").append(tag).append(": ").append(Clock.getBytecodesLeft());
        }
    }

    public static void loghalf() {
        if (isDisplayLogs()) {
            buffer.append("\n---------------\n---------------");
        }
    }

    public static void logline() {
        if (isDisplayLogs()) {
            buffer.append("\n------------------------------");
        }
    }

    /* (Log Important)
    Ignores the 'DISPLAY_LOGS' flag
    */
    public static void logi() {
        buffer.append("\n");
    }

    public static void logi(String str) {
        buffer.append("\n").append(str);
    }

    public static void tlogi(String str) {
        buffer.append("\n- ").append(str);
    }

    public static void ttlogi(String str) {
        buffer.append("\n-- ").append(str);
    }

    public static void drawLine(MapLocation loc1, MapLocation loc2, int[] color) {
        if (isDisplayIndicators()) {
            rc.setIndicatorLine(loc1, loc2, color[0], color[1], color[2]);
        }
    }

    public static void drawDot(MapLocation loc, int[] color) {
        if (isDisplayIndicators()) {
            rc.setIndicatorDot(loc, color[0], color[1], color[2]);
        }
    }
}

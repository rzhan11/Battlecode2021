package template;

import battlecode.common.*;

import static template.Robot.*;

public class Debug {

    final public static boolean SILENCE_LOGS = false;
    final public static boolean SILENCE_INDICATORS = false;

    // Robot.endTurn and Robot.printMyInfo
    final public static boolean NO_TURN_LOGS = false;

    // Message.toString
    final public static boolean USE_BASIC_MESSAGES = false;

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

    /*
    Prints a separator line, currently a single dash
    */
    public static void log() {
        if (isDisplayLogs()) {
            System.out.println("\n");
        }
    }

    /*
    Prints message
    Can be turned off by setting 'DISPLAY_LOGS' to false
    */
    public static void log(String str) {
        if (isDisplayLogs()) {
            System.out.println("\n" + str);
        }
    }

    /*
    Prints message with a single tab in front
    Can be turned off by setting 'DISPLAY_LOGS' to false
    */
    public static void tlog(String str) {
        if (isDisplayLogs()) {
            System.out.println("\n- " + str);
        }
    }

    /*
    Prints message with a double tab in front
    Can be turned off by setting 'DISPLAY_LOGS' to true
    */
    public static void ttlog(String str) {
        if (isDisplayLogs()) {
            System.out.println("\n-- " + str);
        }
    }

    public static void loghalf() {
        if (isDisplayLogs()) {
            System.out.println("\n---------------\n---------------\n");
        }
    }

    public static void logline() {
        if (isDisplayLogs()) {
            System.out.println("\n------------------------------\n");
        }
    }

    /* (Log Important)
    Ignores the 'DISPLAY_LOGS' flag
    */
    public static void logi() {
        System.out.println("\n");
    }

    public static void logi(String str) {
        System.out.println("\n" + str);
    }

    public static void tlogi(String str) {
        System.out.println("\n- " + str);
    }

    public static void ttlogi(String str) {
        System.out.println("\n-- " + str);
    }

    public static void logByte(String tag) {
        if (isDisplayLogs()) {
            System.out.println("\nBYTECODE LEFT - " + tag + ": " + Clock.getBytecodesLeft());
        }
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

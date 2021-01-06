package template;

import battlecode.common.*;


import static template.Nav.*;

public class Utils {

    public static boolean inArray(Object[] arr, Object item, int length) {
        for(int i = 0; i < length; i++) {
            if(arr[i].equals(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean inArray(int[] arr, int item, int length) {
        for(int i = 0; i < length; i++) {
            if(arr[i] == item) {
                return true;
            }
        }
        return false;
    }
    //format: [location y][location x][message type][status]
    //full loc:   7           7            6           4
    static final int statusBits = 4;
    static final int typeBits = 6;
    static final int coordBits = 7;

    //message types. Add your own
    static final int blankMessageType = 0;
    static final int enemyHQMessageType = 1;


    public static int getStatus(int flag) {
        return flag & ((1<<statusBits) - 1);
    }
    public static int getMessageType(int flag) {
        return (flag>>>statusBits) & ((1<<typeBits) - 1);
    }
    //onMap can be any location on the map
    public static MapLocation getMessageLocation(int flag, MapLocation onMap) {
        flag = flag>>>(statusBits + typeBits);
        int x = flag & ((1<<coordBits) - 1);
        flag = flag>>>coordBits;
        int y = flag & ((1<<coordBits) - 1);
        x+= onMap.x&0xffffff80; //drop the last 7 bits
        y+= onMap.y&0xffffff80; //drop the last 7 bits
        if (x+64<=onMap.x) x+=128;
        if (x-64>=onMap.x) x-=128;
        if (y+64<=onMap.y) y+=128;
        if (y-64>=onMap.y) y-=128;
        return new MapLocation(x,y);
    }
    public static int packMessage(MapLocation loc, int messageType, int status) {
        int flag = loc.y&127;
        flag = flag<<coordBits | loc.x&127;
        flag = flag<<typeBits | messageType;
        flag = flag<<statusBits | status;
        return flag;
    }
}

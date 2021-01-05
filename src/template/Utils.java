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
}

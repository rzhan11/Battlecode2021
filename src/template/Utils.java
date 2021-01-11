package template;

import battlecode.common.*;

import static template.Robot.*;
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

    public static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    public static void swap(Object[] arr, int i, int j) {
        Object temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    public static void delete(int[] arr, int i, int len) {
        arr[i] = arr[len - 1];
    }

    public static void delete(Object[] arr, int i, int len) {
        arr[i] = arr[len - 1];
    }

    public static Direction getRandomDir() {
        return DIRS[(int)(Math.random() * 8)];
    }
}

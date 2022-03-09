package org.arithmetic.linkedList;

import java.util.Scanner;

/**
 * @author jackzhengrui
 * @create 2022-02-12 上午11:51
 **/
public class Test {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String str = sc.nextLine();
        int start = 0;
        int end = str.length() - 1;
        while (start < end) {
            while (start < end && '*' == str.charAt(start)) {
                start ++;
            }
            while (start < end && '*' != str.charAt(end) ) {
                end --;
            }
            str = swap(str,start,end);
            start ++;
            end --;
        }
        // *c*m*b*n*t*
        System.out.println(str);
    }

    private static String swap(String str, int start, int end) {
        char[] chars = str.toCharArray();
        char temp = chars[start];
        chars[start] = chars[end];
        chars[end] = temp;
        return String.valueOf(chars);
    }
}

package org.arithmetic.linkedList.leetcode;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhengrui
 * @description
 * @date 2022-04-04 23:34
 */
public class RomanToInt {
    public RomanToInt() {
    }

    public int romanToInt(String s) {
        if(s == null || s.length() < 0) {
            return 0;
        }
        Map<Character,Integer> map = new HashMap<Character,Integer>();
        map.put('I',1);
        map.put('V',5);
        map.put('X',10);
        map.put('L',50);
        map.put('C',100);
        map.put('D',500);
        map.put('M',1000);
        int res = 0;
        for(int i = 0; i < s.length(); i++) {
            Character left = s.charAt(i);
            Character right = s.charAt(i + 1);
            if(left == 'I' && (right == 'V' || right == 'X')) {
                res -= 1;
            } else if(left == 'X' && (right == 'L' || right == 'C')) {
                res -= 10;
            } else if(left == 'X' && (right == 'L' || right == 'C')) {
                res -= 100;
            } else{
                res += map.get(left);
            }
        }
        return res;
    }
}

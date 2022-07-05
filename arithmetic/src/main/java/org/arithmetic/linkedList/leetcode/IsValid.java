package org.arithmetic.linkedList.leetcode;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhengrui
 * @description
 * @date 2022-04-11 15:33
 */
public class IsValid {
    public static boolean isValid(String s) {
        if(s == null || s.length() < 2) {
            return false;
        }
        Map<Character,Character> map = new HashMap<Character,Character>(){
            {
                put('(',')');
                put('[',']');
                put('{','}');
            }
        };
        ArrayDeque<Character> stack = new ArrayDeque<>();
        for(int i = 0; i < s.length(); i++) {
            if(stack.peekFirst() != null && s.charAt(i) == map.get(stack.peekFirst())) {
                stack.pop();
                continue;
            }
            stack.push(s.charAt(i));
        }
        return stack.isEmpty();
    }

    public static void main(String[] args) {
        String s = "{[]}";
        isValid(s);
    }
}

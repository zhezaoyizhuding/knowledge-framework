package org.arithmetic.linkedList.leetcode;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author zhengrui
 * @description 给你一个字符串 s 、一个字符串 t 。返回 s 中涵盖 t 所有字符的最小子串。如果 s 中不存在涵盖 t 所有字符的子串，则返回空字符串 "" 。
 *
 *  
 *
 * 注意：
 *
 * 对于 t 中重复字符，我们寻找的子字符串中该字符数量必须不少于 t 中该字符数量。
 * 如果 s 中存在这样的子串，我们保证它是唯一的答案。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/minimum-window-substring
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 * @date 2022-04-19 20:44
 */
public class Leetcode76 {
    public static String minWindow(String s, String t) {
        Map<Character,Integer> map = new HashMap<>();
        LinkedList<Character> window = new LinkedList<>();
        for(int i = 0; i < t.length(); i++) {
            map.put(t.charAt(i),map.getOrDefault(t.charAt(i),0) + 1);
        }
        String ans = "";
        int len = Integer.MAX_VALUE;
        for(int i = 0; i < s.length(); i++) {
            window.offer(s.charAt(i));
            while (isVaildWindow(map,window)) {
                if(window.size() < len) {
                    ans = toString(window);
                    len = ans.length();
                }
                window.poll();
                while(!window.isEmpty() && !map.containsKey(window.peekFirst())) {
                    window.poll();
                }
            }
        }
        return ans;
    }

    private static String toString(LinkedList<Character> window) {
        String s = "";
        for(Character ch : window) {
            s += String.valueOf(ch);
        }
        return s;
    }

    private static boolean isVaildWindow(Map<Character,Integer> map,LinkedList<Character> window) {
        if(window.isEmpty()) {
            return false;
        }
        Map<Character,Integer> temp = new HashMap<>(map);
        // for(Character ch : window) {
        //     temp.put(ch,temp.getOrDefault(ch,0) + 1);
        // }
        // for(Map.Entry<Character,Integer> entry : map.entrySet()) {
        //     Character key = entry.getKey();
        //     if(!temp.containsKey(key) || temp.get(key) < entry.getValue()) {
        //         return false;
        //     }
        // }
        for(Character ch : window) {
            if(temp.containsKey(ch)) {
                temp.put(ch,temp.get(ch) - 1);
                if(temp.get(ch) <= 0) {
                    temp.remove(ch);
                }
            }
        }
        if(temp.size() != 0) {
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        String s = "a";
        String t = "aa";
        System.out.println(minWindow(s,t));
    }

}

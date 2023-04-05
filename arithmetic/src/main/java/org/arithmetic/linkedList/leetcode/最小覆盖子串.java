package org.arithmetic.linkedList.leetcode;

import java.util.*;

/**
 * @author 参商
 * @date 2023/3/24 10:38
 * @description 最小覆盖子串
 */
public class 最小覆盖子串 {

    public static String minWindow(String s, String t) {
        Map<Character,Integer> sMap = new HashMap<>();
        Map<Character,Integer> tMap = new HashMap<>();
        for (int i = 0; i < t.length(); i++) {
            Character ch = t.charAt(i);
            tMap.put(ch,tMap.getOrDefault(ch,0) + 1);
        }
        Deque<Integer> queue = new LinkedList<>();
        int minlen = Integer.MAX_VALUE;
        int start = -1;
        int end = 0;
        for(int i = 0; i < s.length(); i++) {
            queue.offer(i);
            Character ch = s.charAt(i);
            if(tMap.containsKey(ch)) {
                sMap.put(ch,sMap.getOrDefault(ch,0) + 1);
            }
            while(check(sMap,tMap) && !queue.isEmpty()) {
                int size = queue.size();
                if(size < minlen) {
                    minlen = size;
                    start = queue.peekFirst();
                    end = queue.peekLast();
                }
                Character character = s.charAt(queue.poll());
                if(tMap.containsKey(character)) {
                    sMap.put(character,sMap.getOrDefault(character,0) - 1);
                }
            }
        }
        return start == -1 ? "" : s.substring(start,end + 1);
    }

    private static boolean check(Map<Character,Integer> sMap,Map<Character,Integer> tMap) {
        for(Map.Entry<Character,Integer> entry : tMap.entrySet()) {
            Character key = entry.getKey();
            Integer sValue = sMap.get(key);
            if(Objects.isNull(sValue) || sValue < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        String s = "ADOBECODEBANC";
        String t = "ABC";
        System.out.println(minWindow(s,t));;
    }
}

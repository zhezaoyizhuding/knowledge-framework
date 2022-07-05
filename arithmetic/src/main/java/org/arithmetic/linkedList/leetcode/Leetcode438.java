package org.arithmetic.linkedList.leetcode;

import java.util.*;

/**
 * @author zhengrui
 * @description
 * @date 2022-04-25 16:12
 */
public class Leetcode438 {
    public static List<Integer> findAnagrams(String s, String p) {
        Map<Character,Integer> map = new HashMap<>();
        for(Character ch : p.toCharArray()) {
            map.put(ch,map.getOrDefault(ch,0) + 1);
        }
        Deque<Integer> queue = new ArrayDeque<>();
        List<Integer> ans = new ArrayList<>();
        for(int i = 0; i < s.length(); i++) {
            queue.offer(i);
            if(queue.size() == p.length()) {
                String str = s.substring(queue.peekFirst(),queue.peekLast() + 1);
                if(check(str,map)) {
                    ans.add(queue.pollFirst());
                } else {
                    queue.pollFirst();
                }
            }
        }
        return ans;
    }

    private static boolean check(String s, Map<Character,Integer> map) {
        Map<Character,Integer> temp = new HashMap<>(map);
        for(Character ch : s.toCharArray()) {
            if(!temp.containsKey(ch) || temp.get(ch) == 0) {
                return false;
            }
            temp.put(ch,temp.get(ch) - 1);
        }
        return true;
    }

    public static void main(String[] args) {
        String s = "abab";
        String p = "ab";
        System.out.println(findAnagrams(s,p));
    }
}

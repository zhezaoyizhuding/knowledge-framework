package org.arithmetic.linkedList.tooffer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhengrui
 * @description 在字符串 s 中找出第一个只出现一次的字符。如果没有，返回一个单空格。 s 只包含小写字母。
 * @date 2022-03-18 17:03
 */
public class FirstUniqChar {
    public char firstUniqChar(String s) {
        Map<Character,Integer> map = new HashMap<>();
        for(int i = 0; i < s.length(); i++) {
            Character ch = s.charAt(i);
            if(map.containsKey(ch)) {
                map.put(ch,map.get(ch) + 1);
            } else {
                map.put(ch, 1);
            }
        }
        for(int i = 0; i < s.length(); i++) {
            Character ch = s.charAt(i);
            if(map.get(ch) == 1) {
                return ch;
            }
        }
        return ' ';
    }
}

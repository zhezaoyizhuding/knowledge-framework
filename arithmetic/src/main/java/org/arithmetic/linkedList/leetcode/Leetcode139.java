package org.arithmetic.linkedList.leetcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author zhengrui
 * @description
 * @date 2022-04-22 12:06
 */
public class Leetcode139 {
    public static boolean wordBreak(String s, List<String> wordDict) {
        if (s == null || s.length() == 0) {
            return false;
        }
        String str = "";
        for (int i = 0; i < s.length(); i++) {
            str += String.valueOf(s.charAt(i));
            if (wordDict.contains(str)) {
                str = "";
            }
        }
        return str.isEmpty();
    }

    public static void main(String[] args) {
        String s = "leetcode";
        List<String> wordDict = new ArrayList<String>(){
            {
                add("leet");
                add("code");
            }
        };
        System.out.println(wordBreak(s,wordDict));
    }
}

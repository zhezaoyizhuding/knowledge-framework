package org.arithmetic.linkedList;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;

/**
 * @author jackzhengrui
 * @create 2022-02-12 上午11:36
 **/
public class CountStr {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String str = sc.nextLine();
        Map<Character,Integer> map = new HashMap<Character,Integer>();
        for(int i = 0; i < str.length(); i++){
            Character ch = str.charAt(i);
            if(map.get(ch) != null ) {
                map.put(ch,map.get(ch) + 1);
            } else {
                map.put(ch,1);
            }
        }
        StringBuilder result = new StringBuilder();
        TreeSet<Character> keySet = new TreeSet<Character>(map.keySet());
        for (Character character : keySet) {
            result.append(character.charValue()).append(map.get(character));
        }
        System.out.println(result);
    }
}

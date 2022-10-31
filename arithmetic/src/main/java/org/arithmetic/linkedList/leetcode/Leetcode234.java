package org.arithmetic.linkedList.leetcode;

import org.arithmetic.linkedList.ListNode;

import java.util.HashSet;
import java.util.Set;

/**
 * @author zhengrui
 * @description
 * @date 2022-04-22 16:33
 */
public class Leetcode234 {

    public static boolean isPalindrome(ListNode head) {
        if(head == null) {
            return true;
        }
        Set<ListNode> set = new HashSet<>();
        while(head != null) {
            if(set.contains(head)) {
                set.remove(head);
            } else {
                set.add(head);
            }
            head = head.next;
        }
        return set.isEmpty();
    }

    public static void main(String[] args) {
        ListNode node1 = new ListNode(1);
        ListNode node2 = new ListNode(2);
        ListNode node3 = new ListNode(2);
        ListNode node4 = new ListNode(1);

    }
}

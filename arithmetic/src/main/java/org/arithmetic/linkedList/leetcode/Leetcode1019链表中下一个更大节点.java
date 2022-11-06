package org.arithmetic.linkedList.leetcode;

import org.arithmetic.linkedList.ListNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * @author zhengrui
 * @description
 * @date 2022-11-06 16:18
 */
public class Leetcode1019链表中下一个更大节点 {
    public int[] nextLargerNodes(ListNode head) {
        if(head == null) {
            return null;
        }
        int len = 0;
        Deque<Integer> stack = new ArrayDeque<>();
        List<Integer> list = new ArrayList<>();
        while(head != null) {
            int val = head.val;
            while(!stack.isEmpty() && val > stack.peek()) {
                stack.pop();
                list.add(val);
            }
            stack.push(head.val);
            head = head.next;
            len ++;
        }
        int[] ret = new int[len];
        for(int i = 0; i < list.size(); i++) {
            ret[i] = list.get(i);
        }
        return ret;
    }

    public static void main(String[] args) {
        ListNode node = new ListNode(2);
        ListNode node1 = new ListNode(7);
        ListNode node2 = new ListNode(4);
        ListNode node3 = new ListNode(3);
        ListNode node4 = new ListNode(5);
        node.next = node1;
        node1.next = node2;
        node2.next = node3;
        node3.next = node4;


        Leetcode1019链表中下一个更大节点 test = new Leetcode1019链表中下一个更大节点();
        int[] ret = test.nextLargerNodes(node);
        System.out.println(ret);
    }
}

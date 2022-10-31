package org.arithmetic.linkedList.leetcode;

import org.arithmetic.linkedList.ListNode;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 参商
 * @date 2022/10/30 15:20
 * @description 删除排序链表中的重复元素 II
 */
public class Leetcode82删除排序链表中的重复元素II {
    public ListNode deleteDuplicates(ListNode head) {
        ListNode newHead = new ListNode();
        ListNode cur = newHead;
        while(head!= null) {
            int count = 1;
            while(head.next !=null && head.val == head.next.val) {
                count++;
                head = head.next;
            }
            ListNode temp = head;
            head = head.next;
            if(count == 1) {
                temp.next = null;
                cur.next = temp;
                cur = cur.next;
            }
        }
        return newHead.next;
    }

    public static void main(String[] args) {
        ListNode node1 = new ListNode(1);
        ListNode node2 = new ListNode(2);
        ListNode node3 = new ListNode(3);
        ListNode node32 = new ListNode(3);
        ListNode node4 = new ListNode(4);
        ListNode node42 = new ListNode(4);
        ListNode node5 = new ListNode(5);
        node1.next = node2;
        node2.next = node3;
        node3.next = node32;
        node32.next = node4;
        node4.next = node42;
        node42.next = node5;
        Leetcode82删除排序链表中的重复元素II test = new Leetcode82删除排序链表中的重复元素II();
        ListNode ret = test.deleteDuplicates(node1);
        System.out.println(ret);
    }
}

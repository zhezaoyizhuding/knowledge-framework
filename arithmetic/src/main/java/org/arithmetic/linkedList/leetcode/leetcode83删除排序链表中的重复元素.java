package org.arithmetic.linkedList.leetcode;

import org.arithmetic.linkedList.ListNode;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 参商
 * @date 2022/10/30 14:48
 * @description 删除排序链表中的重复元素
 */
public class leetcode83删除排序链表中的重复元素 {
    public ListNode deleteDuplicates(ListNode head) {
        if(head == null) {
            return head;
        }
        ListNode cur = head;
        while(cur.next != null) {
            while(cur.val == cur.next.val) {
                cur.next = cur.next.next;
            }
            cur = cur.next;
        }
        return head;
    }

    public static void main(String[] args) {
        ListNode node1 = new ListNode(1);
        ListNode node2 = new ListNode(1);
        ListNode node3 = new ListNode(2);
        node1.next = node2;
        node2.next = node3;
        leetcode83删除排序链表中的重复元素 test = new leetcode83删除排序链表中的重复元素();
        ListNode ret = test.deleteDuplicates(node1);
        System.out.println(ret);
    }
}

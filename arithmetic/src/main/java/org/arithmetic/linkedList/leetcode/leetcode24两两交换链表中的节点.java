package org.arithmetic.linkedList.leetcode;

import org.arithmetic.linkedList.ListNode;

/**
 * @author 参商
 * @date 2022/10/29 15:48
 * @description 两两交换链表中的节点
 */
public class leetcode24两两交换链表中的节点 {
    public static ListNode swapPairs(ListNode head) {
        ListNode newHead = new ListNode();
        ListNode first = head;
        ListNode second = head.next;
        ListNode cur = newHead;
        cur.next = first;
        while(second != null) {
            first.next = second.next;
            second.next = first;
            ListNode temp = first;
            first = second;
            second = temp;
            cur.next = first;
            if(second.next == null || second.next.next == null) {
                break;
            }
            first = first.next.next;
            second = second.next.next;
            cur = cur.next.next;
        }
        return newHead.next;
    }

    public static void main(String[] args) {
        ListNode node1 = new ListNode(1);
//        ListNode node2 = new ListNode(2);
//        ListNode node3 = new ListNode(3);
//        ListNode node4 = new ListNode(4);
//        node1.next = node2;
//        node2.next = node3;
//        node3.next = node4;
        ListNode head = swapPairs(node1);
        System.out.println(head);
    }
}

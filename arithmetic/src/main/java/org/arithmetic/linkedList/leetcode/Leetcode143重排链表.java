package org.arithmetic.linkedList.leetcode;

import org.arithmetic.linkedList.ListNode;

/**
 * @author 参商
 * @date 2022/10/30 17:57
 * @description 重排链表
 */
public class Leetcode143重排链表 {
    public void reorderList(ListNode head) {
        if(head == null) {
            return;
        }
        ListNode pHead = new ListNode();
        pHead.next = head;
        ListNode slow = pHead;
        ListNode fast = pHead;
        while(fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        ListNode twoHead = slow.next;
        slow.next = null;
        // 反转链表二
        ListNode temp = new ListNode();
        ListNode twoCur = twoHead;
        while(twoHead != null) {
            twoHead = twoHead.next;
            twoCur.next = temp.next;
            temp.next = twoCur;
            twoCur = twoHead;
        }
        twoHead = temp.next;

        ListNode newHead = new ListNode();
        ListNode cur = newHead;
        while(twoHead != null) {
            cur.next = head;
            head = head.next;
            cur = cur.next;
            cur.next = twoHead;
            twoHead = twoHead.next;
            cur = cur.next;
            cur.next = null;
        }
        // 奇数
        if(head != null) {
            cur.next = head;
            head.next = null;
        }
        head = newHead.next;
    }

    public static void main(String[] args) {
        ListNode node1 = new ListNode(1);
        ListNode node2 = new ListNode(2);
        ListNode node3 = new ListNode(3);
        ListNode node4 = new ListNode(4);
        ListNode node5 = new ListNode(5);
        node1.next = node2;
        node2.next = node3;
        node3.next = node4;
        node4.next = node5;
        Leetcode143重排链表 test = new Leetcode143重排链表();
        test.reorderList(node1);
    }
}

package org.arithmetic.linkedList.leetcode;

import org.arithmetic.linkedList.ListNode;

/**
 * @author 参商
 * @date 2022/10/30 14:05
 * @description K 个一组翻转链表
 */
public class leetcode25K个一组翻转链表 {
    public ListNode reverseKGroup(ListNode head, int k) {
        ListNode newHead = new ListNode();
        ListNode curHead = newHead;
        while(head != null) {
            ListNode cur = head;
            ListNode last = head;
            int j = 0;
            while(head != null && j < k) {
                head = head.next;
                cur.next = curHead.next;
                curHead.next = cur;
                cur = head;
                j++;
            }
            if(j != k) {
                // 重新反转回来
                ListNode tempHead = curHead.next;
                curHead.next = null;
                ListNode tempCur = tempHead;
                while(tempHead != null) {
                    tempHead = tempHead.next;
                    tempCur.next = curHead.next;
                    curHead.next = tempCur;
                    tempCur = tempHead;
                }
                break;
            }
            curHead = last;
        }
        return newHead.next;
    }

    private void reverse(ListNode curHead, ListNode head) {
        ListNode cur = head;
        while(head != null) {
            head = head.next;
            cur.next = curHead.next;
            curHead.next = cur;
            cur = head;
        }
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
        leetcode25K个一组翻转链表 test = new leetcode25K个一组翻转链表();
        ListNode ret = test.reverseKGroup(node1,3);
        System.out.println(ret);
    }
}

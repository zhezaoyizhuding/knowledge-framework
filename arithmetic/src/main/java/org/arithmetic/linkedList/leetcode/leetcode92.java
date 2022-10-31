package org.arithmetic.linkedList.leetcode;

import org.arithmetic.linkedList.ListNode;

/**
 * @author zhengrui
 * @description
 * @date 2022-10-09 11:47
 */
public class leetcode92 {

    private ListNode rev(ListNode head, int low, int high) {
        if(head == null) {
            return head;
        }
        ListNode ret = new ListNode();
        ListNode cur = ret;
        for(int i = 0; i < low; i++) {
            cur.next = head;
            head = head.next;
            cur = cur.next;
        }
        reverse(cur,head,high - low);
        return null;
    }


    private ListNode reverse(ListNode cur, ListNode head, int length) {
        return null;
    }
}

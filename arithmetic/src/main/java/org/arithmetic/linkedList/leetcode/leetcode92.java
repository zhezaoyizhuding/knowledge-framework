package org.arithmetic.linkedList.leetcode;

/**
 * @author zhengrui
 * @description
 * @date 2022-10-09 11:47
 */
public class leetcode92 {

    private Leetcode234.ListNode rev(Leetcode234.ListNode head, int low, int high) {
        if(head == null) {
            return head;
        }
        Leetcode234.ListNode ret = new Leetcode234.ListNode();
        Leetcode234.ListNode cur = ret;
        for(int i = 0; i < low; i++) {
            cur.next = head;
            head = head.next;
            cur = cur.next;
        }
        reverse(cur,head,high - low);

    }


    private Leetcode234.ListNode reverse(Leetcode234.ListNode cur, Leetcode234.ListNode head, int length) {

    }
}

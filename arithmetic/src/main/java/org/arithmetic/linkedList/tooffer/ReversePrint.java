package org.arithmetic.linkedList.tooffer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhengrui
 * @description 输入一个链表的头节点，从尾到头反过来返回每个节点的值（用数组返回）。
 * @date 2022-03-17 14:21
 */
public class ReversePrint {
    public static int[] reversePrint(ListNode head) {
        if(head == null) {
            return new int[0];
        }
        ListNode newHead = new ListNode(0);
        while(head != null) {
            ListNode temp = head;
            head = head.next;
            temp.next = newHead.next;
            newHead.next = temp;
        }
        newHead = newHead.next;
        List<Integer> res = new ArrayList<>();
        while(newHead != null) {
            res.add(newHead.val);
            newHead = newHead.next;
        }
        int[] result = new int[res.size()];
        for(int i = 0; i < res.size(); i ++) {
            result[i] = res.get(i);
        }
        return result;
    }

    public static class ListNode {
      int val;
      ListNode next;
      ListNode(int x) { val = x; }
  }

    public static void main(String[] args) {
        ListNode head = new ListNode(1);
        ListNode second = new ListNode(3);
        ListNode three = new ListNode(2);
        head.next = second;
        second.next = three;
        reversePrint(head);
    }
}

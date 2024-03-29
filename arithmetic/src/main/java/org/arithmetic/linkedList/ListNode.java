package org.arithmetic.linkedList;


/**
 * @author 参商
 * @date 2022/10/26 20:05
 * @description 链表节点
 */
public class ListNode {
    public int val;
    public ListNode next;

    public ListNode() {
    }
    public ListNode(int val) {
        this.val = val;
        this.next = null;
    }
    public ListNode(int val, ListNode next) {
        this.val = val;
        this.next = next;
    }
}

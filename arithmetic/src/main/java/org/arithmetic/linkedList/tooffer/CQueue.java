package org.arithmetic.linkedList.tooffer;

import java.util.Stack;

/**
 * 用两个栈实现一个队列。队列的声明如下，请实现它的两个函数 appendTail 和 deleteHead ，分别完成在队列尾部插入整数和在队列头部删除整数的功能。(若队列中没有元素，deleteHead 操作返回 -1 )
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/yong-liang-ge-zhan-shi-xian-dui-lie-lcof
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class CQueue {

    private Stack<Integer> start;
    private Stack<Integer> end;

    public CQueue() {
        start = new Stack<>();
        end = new Stack<>();
    }

    public void appendTail(int value) {
        start.push(value);
    }

    public int deleteHead() {
        if(start.empty()) {
            return -1;
        }
        while(!start.empty()) {
            int value = start.pop();
            end.push(value);
        }
        int result = end.pop();
        while(!end.empty()) {
            start.push(end.pop());
        }
        return result;
    }
}

package org.arithmetic.linkedList.tooffer;

import java.util.Stack;

/**
 * @author zhengrui
 * @description 定义栈的数据结构，请在该类型中实现一个能够得到栈的最小元素的 min 函数在该栈中，调用 min、push 及 pop 的时间复杂度都是 O(1)。
 * @date 2022-03-17 12:31
 */
public class MinStack {
    private Stack<Integer> start;
    private Stack<Integer> end;
    /** initialize your data structure here. */
    public MinStack() {
        start = new Stack<>();
        end = new Stack<>();
    }

    public void push(int x) {
        start.push(x);
    }

    public void pop() {
        if(start.empty()) {
            return;
        }
        start.pop();
    }

    public int top() {
        return start.peek();
    }

    public int min() {
        int res = Integer.MAX_VALUE;
        while(!start.empty()) {
            int temp = start.pop();
            if(temp < res) {
                res = temp;
            }
            end.push(temp);
        }
        while (!end.empty()) {
            start.push(end.pop());
        }
        return res;
    }

    public static void main(String[] args) {
        MinStack minStack = new MinStack();
        minStack.push(-2);
        minStack.push(0);
        minStack.push(-3);
        minStack.min();
        minStack.pop();
        minStack.top();
        minStack.min();
    }
}

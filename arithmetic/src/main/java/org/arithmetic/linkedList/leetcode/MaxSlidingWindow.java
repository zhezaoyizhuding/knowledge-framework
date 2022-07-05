package org.arithmetic.linkedList.leetcode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.PriorityQueue;

/**
 * @author zhengrui
 * @description 给你一个整数数组 nums，有一个大小为 k 的滑动窗口从数组的最左侧移动到数组的最右侧。你只可以看到在滑动窗口内的 k 个数字。滑动窗口每次只向右移动一位。
 *
 * 返回 滑动窗口中的最大值 。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/sliding-window-maximum
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 * @date 2022-03-29 15:36
 */
public class MaxSlidingWindow {
    public static int[] maxSlidingWindow(int[] nums, int k) {
        if(nums.length == 0 || k == 0) {
            return new int[0];
        }
        Deque<Integer> queue = new ArrayDeque<>();
        int[] result = new int[nums.length - k + 1];
        for(int i = 0; i < nums.length; i++) {
            while(!queue.isEmpty() && nums[i] >= nums[queue.peekLast()]) {
                queue.peekLast();
            }
            queue.offer(i);
            if(queue.peekFirst() <= i - k) {
                queue.pollFirst();
            }
            if(i >= k - 1) {
                result[i - k + 1] = nums[queue.peekFirst()];
            }
        }
        return result;
    }

    public static int[] maxSlidingWindow2(int[] nums, int k) {
        if(nums.length == 0 || k == 0) {
            return new int[0];
        }
        PriorityQueue<ArrayList<Integer>> queue = new PriorityQueue<>(k,(o1, o2) -> o2.get(1) - o1.get(1));
        int[] result = new int[nums.length -k + 1];
        for(int i = 0; i < nums.length; i++) {
            while (!queue.isEmpty() && queue.peek().get(0) <= i - k) {
                queue.poll();
            }
            queue.offer(createList(i,nums[i]));
            if(i >= k - 1) {
                result[i - k + 1] = queue.peek().get(1);
            }
        }
        return result;
    }

    public static ArrayList<Integer> createList(int i, int val) {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(i);
        list.add(val);
        return list;
    }

    public static void main(String[] args) {
        int[] nums = new int[]{1,3,-1,-3,5,3,6,7};
        int k = 3;
        System.out.println(maxSlidingWindow2(nums,k).toString());
//        System.out.println(maxSlidingWindow(nums,k).toString());
    }
}

package org.arithmetic.linkedList.leetcode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author zhengrui
 * @description
 * @date 2022-04-13 11:05
 */
public class NextArrange2 {
    public static void main(String[] args) {
        int[] nums = {1,3,2};
        nextPermutation(nums);
    }

    public static void nextPermutation(int[] nums) {
        if(nums == null || nums.length == 0) {
            return;
        }
        for(int i = nums.length - 1; i > 0; i--) {
            for(int j = i - 1; j >= 0; j--) {
                if(nums[i] > nums[j]) {
                    swap(nums,i, j);
                    Arrays.sort(nums,j + 1,nums.length);
                    return;
                }
            }
        }
        Arrays.sort(nums);
        List<int[]> list = new ArrayList<>();
        int[] n = new int[2];
        list.add(n);
    }

    private static void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
}

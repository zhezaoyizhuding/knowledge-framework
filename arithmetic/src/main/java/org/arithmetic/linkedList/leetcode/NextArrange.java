package org.arithmetic.linkedList.leetcode;

import java.lang.reflect.Array;

/**
 * 下一个排列
 * 整数数组的一个 排列  就是将其所有成员以序列或线性顺序排列。
 *
 * 例如，arr = [1,2,3] ，以下这些都可以视作 arr 的排列：[1,2,3]、[1,3,2]、[3,1,2]、[2,3,1] 。
 * 整数数组的 下一个排列 是指其整数的下一个字典序更大的排列。更正式地，如果数组的所有排列根据其字典顺序从小到大排列在一个容器中，那么数组的 下一个排列 就是在这个有序容器中排在它后面的那个排列。如果不存在下一个更大的排列，那么这个数组必须重排为字典序最小的排列（即，其元素按升序排列）。
 *
 * 例如，arr = [1,2,3] 的下一个排列是 [1,3,2] 。
 * 类似地，arr = [2,3,1] 的下一个排列是 [3,1,2] 。
 * 而 arr = [3,2,1] 的下一个排列是 [1,2,3] ，因为 [3,2,1] 不存在一个字典序更大的排列。
 * 给你一个整数数组 nums ，找出 nums 的下一个排列。
 *
 * 必须 原地 修改，只允许使用额外常数空间。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/next-permutation
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 * @author jackzhengrui
 * @create 2022-03-16 下午4:46
 **/
public class NextArrange {
    public static void main(String[] args) {
        int[] nums = {3,1,4};
        nextPermutation(nums);
        for(int num : nums) {
            System.out.print(num);
        }
    }
    public static void nextPermutation(int[] nums) {
        int low = 0;
        int i = 0;
        // 第一步，从后往前找到第一个降序序列
        for(i = nums.length - 1; i > 0; i--) {
            if(nums[i] > nums[i - 1]) {
                low = i - 1;
                break;
            }
        }
        // 找不到的情况，即目标值就是最大值
        if(i == 0) {
            reverse(nums,0);
            return;
        }
        // 第二步，再从目标位置的后面，找到第一个比他大的值，进行交换
        for(int j = nums.length - 1; j > low; j--) {
            if(nums[j] > nums[low]) {
                swap(nums,j,low);
                
            }
        }
        // 第三步，对目标位置的后面序列进行降序
        reverse(nums,low + 1);
    }
    private static void swap(int[] nums, int left, int right) {
        int temp = nums[left];
        nums[left] = nums[right];
        nums[right] = temp;
    }

    private static void reverse(int[] nums, int start) {
        int left = start;
        int right = nums.length - 1;
        while(left < right) {
            swap(nums,left,right);
            left ++;
            right --;
        }
    }


}

package org.arithmetic.linkedList.leetcode;

/**
 * @author zhengrui
 * @description
 * @date 2022-04-15 11:18
 */
public class Search {
    public static int search(int[] nums, int target) {
        if(nums == null || nums.length == 0) {
            return -1;
        }
        int low = 0;
        int high = nums.length - 1;
        while(low < high && nums[low] > target && nums[low] > nums[high]) {
            low ++;
        }
        while(low < high && nums[high] < target && nums[low] > nums[high]) {
            high --;
        }
        while(low <= high) {
            int mid = (low + high) / 2;
            if(nums[mid] == target) {
                return mid;
            } else if(nums[mid] < target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] nums = new int[]{4,5,6,7,0,1,2};
        int targer = 0;
        System.out.println(search(nums,targer));
    }
}

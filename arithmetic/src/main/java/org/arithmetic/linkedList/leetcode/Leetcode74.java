package org.arithmetic.linkedList.leetcode;

/**
 * @author zhengrui
 * @description
 * @date 2023-04-15 16:50
 */
public class Leetcode74 {
    public static boolean searchMatrix(int[][] matrix, int target) {
        int low = 0;
        int high = matrix[0].length - 1;
        while(low > matrix.length && high > 0) {
            int mid = matrix[low][high];
            if(mid > target) {
                return search(matrix[low],target);
            } else if(mid < target) {
                low ++;
            } else{
                return true;
            }
        }
        return false;
    }

    private static boolean search(int[] nums, int target) {
        int low = 0;
        int high = nums.length - 1;
        while(low < high) {
            int mid = (low + high) / 2;
            if(nums[mid] > target) {
                high = mid;
            } else if(nums[mid] < target) {
                low = mid + 1;
            } else{
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        int[][] matrix = new int[][]{
            {1,3,5,7},{10,11,16,20}
        };
        searchMatrix(matrix,3);
    }
}

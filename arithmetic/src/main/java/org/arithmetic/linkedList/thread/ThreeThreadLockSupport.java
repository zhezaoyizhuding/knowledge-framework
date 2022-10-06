package org.arithmetic.linkedList.thread;

import java.util.concurrent.locks.LockSupport;

/**
 * @author zhengrui
 * @description
 * @date 2022-09-29 23:13
 */

public class ThreeThreadLockSupport {

    static int num = 1;
    static int count = 0;
    Thread t1 = new Thread();
    Thread t2 = new Thread();
    Thread t3 = new Thread();

    public void doPrint() {

        t1 = new Thread(new Runnable() {

            @Override
            public void run() {
                while (num < 65) {
                    while (count % 3 != 0) {
                        LockSupport.park();
                    }
                    printNum();
                    count++;
                    LockSupport.unpark(t2);
                }

            }
        }, "t1");
        t1.start();

        t2 = new Thread(new Runnable() {

            @Override
            public void run() {
                while (num < 65) {
                    while (count % 3 != 1) {
                        LockSupport.park();
                    }
                    printNum();
                    count++;
                    LockSupport.unpark(t3);
                }

            }
        }, "t2");
        t2.start();

        t3 = new Thread(new Runnable() {

            @Override
            public void run() {
                while (num < 65) {
                    while (count % 3 != 2) {
                        LockSupport.park();
                    }
                    printNum();
                    count = 0;
                    LockSupport.unpark(t1);
                }

            }
        }, "t3");
        t3.start();
    }

    private void printNum() {
        System.out.print("线程：" + Thread.currentThread().getName() + ": ");
        for (int i = 5; i > 0; i--, num++) {
            System.out.print(" " + num);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        ThreeThreadLockSupport lockSupport = new ThreeThreadLockSupport();
        lockSupport.doPrint();
    }
}

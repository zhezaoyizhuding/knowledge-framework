package org.arithmetic.linkedList.thread;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author zhengrui
 * @description
 * @date 2022-09-29 21:57
 */
public class LetterPrint {
    private int state;
    private int times;
    private Lock lock = new ReentrantLock();
    public LetterPrint(int times) {
        this.times = times;
    }

    private void threadPrintNum(String name) {
        for(int i = 1; i <= times;) {
            lock.lock();
            int j = 1;
            int num = i * j;
            for(j = 1; j <= 5; j++) {
                System.out.println("线程" + name + ": " + num);
            }
            i++;
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        LetterPrint print = new LetterPrint(5);
        new Thread(() -> {
            print.threadPrintNum("1");
        },"1").start();
        new Thread(() -> {
            print.threadPrintNum("2");
        },"2").start();
        new Thread(() -> {
            print.threadPrintNum("3");
        },"3").start();
    }
}

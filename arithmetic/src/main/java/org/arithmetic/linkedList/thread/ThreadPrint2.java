package org.arithmetic.linkedList.thread;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author zhengrui
 * @description
 * @date 2022-03-24 23:06
 */
public class ThreadPrint2 {
    private static Integer count = 1;
    private static final Object LOCK = new Object();


    static class Task implements Runnable {
        Integer threadId;

        public Task(Integer id) {
            threadId = id;
        }

        @Override
        public void run() {
            while (count < 100) {
                synchronized (LOCK) {
                    if(count % 2 != threadId) {
                        try {
                            LOCK.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if(threadId == 0) {
                        System.out.println("Thread2 - " + count);
                    } else {
                        System.out.println("Thread1 - " + count);
                    }
                    count++;
                    LOCK.notifyAll();
                }
            }
        }
    }

    public static void main(String[] args) {
        new Thread(new Task(0)).start();
        new Thread(new Task(1)).start();
    }
}

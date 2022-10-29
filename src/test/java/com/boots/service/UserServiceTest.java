package com.boots.service;


import org.junit.Test;

/// volatile https://www.cyberforum.ru/java-j2se/thread941208.html
public class UserServiceTest {

    class TrueOrFalse implements Runnable {
        volatile boolean state = true;
        volatile int timeToDie = 10;
        @Override
        public void run() {
            while ( timeToDie > 0 ) {
                System.out.println("Поток: " + Thread.currentThread().getName());
                if ( state ) {
                    state = false;
                    System.out.println("Была правда, стала кривда.");
                }
                else {
                    state = true;
                    System.out.println("Была кривда, стала правда.");
                }
                try {
                    Thread.sleep(500);
                }
                catch ( InterruptedException e ) {}
            }
        }

        int getCloserToDeath() {
            return --timeToDie;
        }

    }

    @Test
    public void saveUser() {
        TrueOrFalse tof = new TrueOrFalse();
        Thread t1 = new Thread(tof, "Первый");
        Thread t2 = new Thread(tof, "Второй");

        t1.start();
        t2.start();

        while ( tof.getCloserToDeath() > 0 ) {
            try {
                Thread.sleep(1000);
            }
            catch ( InterruptedException eslip ) {}
        }

        try {
            t1.join();
            t2.join();
        }
        catch ( InterruptedException eslip ) {}
    }
}
package com.vilenet.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Tester {

    static final String hostName = "54.193.49.146";
    static final int portNumber = 6112;
//    static final String hostName = "127.0.0.1";
//    static final int portNumber = 6112;

    static AtomicInteger i = new AtomicInteger();

    static class Bot implements Runnable {

        PrintWriter out;
        String name;

        Bot(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            try {


                Socket socket = new Socket(hostName, portNumber);
                out =
                        new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));

                out.write(3);
                out.write(4);
                send(name);
                send("pw");
                send("/j vile");
                System.out.println(i.addAndGet(1));
                String s;
                while ((s = in.readLine()) != null) {
                    //System.out.println(s);
                }
            } catch (Exception e) {

            }
        }

        public void send(String text) {
            out.write(text);
            out.write(0x0D);
            out.write(0x0A);
            out.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        int threads = 200;
        ExecutorService e = Executors.newFixedThreadPool(threads+1);
        for (int i = 0; i != threads; i++) {
            e.submit(new Bot(args[0] + i));
        }

        Scanner stdIn = new Scanner(new BufferedReader(new InputStreamReader(System.in)));
        String s;

        while ((s = stdIn.nextLine()) != null) {
            if (s.equals("q")) {
                System.exit(0);
            }
        }
    }
}

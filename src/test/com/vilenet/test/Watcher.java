package com.init6.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Watcher {

    //static final String hostName = "45.63.55.10";
    static final int  portNumber = 6112;
    //static final String hostName = "54.193.49.146";
    //static final int portNumber = 6113;
    static final String hostName = "127.0.0.1";
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
                send("hovno1000");
                send("pw");
                send("/j vile");
                String s;

                int joined =0;int left=0;

                while ((s = in.readLine()) != null) {
                    System.out.println(s);
                    if (s.startsWith("1002")) {
                        System.out.println("Joined: " + ++joined);
                    } else if (s.startsWith("1003")) {
                        System.out.println("Left: " + ++left);
                    } else if (s.contains("xab")) {
                        send("/uptime");
                    }
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
        ExecutorService e = Executors.newSingleThreadExecutor();
        e.submit(new Bot("Test12345"));

        Scanner stdIn = new Scanner(new BufferedReader(new InputStreamReader(System.in)));
        String s;

        while ((s = stdIn.nextLine()) != null) {
            if (s.equals("q")) {
                System.exit(0);
            }
        }
    }
}

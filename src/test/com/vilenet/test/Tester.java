package com.vilenet.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Tester {

    static PrintWriter out;

    static class Bot implements Runnable {
        String name;

        Bot(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            try {
                String hostName = "127.0.0.1";
                int portNumber = 6112;

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
                send("/j legacy");

                String s;
                while ((s = in.readLine()) != null) {
                    System.out.println(s);
                }
            } catch (Exception e) {

            }
        }
    }

    public static void send(String text) {
        out.write(text);
        out.write(0x0D);
        out.write(0x0A);
        out.flush();
    }

    public static void main(String[] args) throws Exception {
        ExecutorService e = Executors.newSingleThreadExecutor();
        e.submit(new Bot(args[0]));

        Scanner stdIn = new Scanner(new BufferedReader(new InputStreamReader(System.in)));
        String s;

        while ((s = stdIn.nextLine()) != null) {
            if (s.equals("q")) {
                System.exit(0);
            }

            send(s);
        }
    }
}

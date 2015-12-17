package com.vilenet.massbot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by filip on 12/8/15.
 */
public class Massbot {

    public static void main(String[] args) throws IOException {
        // arg[0] is Host:Port
        String name = args[0];
        String host = args[1].split(":")[0];
        int port = Integer.valueOf(args[1].split(":")[1]);
        int number = Integer.valueOf(args[2]);

        ExecutorService executor = Executors.newFixedThreadPool(number);
        Bot bot = null;

        for (int i = 0; i != number; i++) {
            bot = new Bot(host, port, name + (i + 1), i + 1 == number);
            executor.submit(bot);
        }

        Scanner stdIn = new Scanner(new BufferedReader(new InputStreamReader(System.in)));
        String s;
        while ((s = stdIn.nextLine()) != null) {
            if (s.equals("/quit")) {
                System.exit(0);
            } else {
                bot.send(s);
            }
        }
    }

    private static class Bot implements Runnable {

        final String host;
        final int port;
        final String name;
        final boolean showOutput;

        PrintWriter out;

        Bot(String host, int port, String name, boolean showOutput) {
            this.host = host;
            this.port = port;
            this.name = name;
            this.showOutput = showOutput;
        }

        public void run() {
            try {
                for (;;) {
                    Socket socket = new Socket(host, port);
                    out = new PrintWriter(socket.getOutputStream(), true);
//                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.write(3);
                    out.write(4);
//                    send("boatbawt");
//                    send("boatbawt#$&(*)!@vsdh9ai@$!Q^911");
                    send(name); send("1234");
                    send("/j vile");

                    Thread.sleep(100);
                    try {
                        socket.close();
                        Thread.sleep(10);
                    } catch (Exception e) {

                    }
                }

//                String s;
//                while ((s = in.readLine()) != null) {
//                    if (showOutput || s.startsWith("1004")) {
//                        output(s);
//                    }
//
//                    if (s.startsWith("1005") || s.startsWith("1004")) {
//                        String[] splt = s.split(" ", 5);
//                        String msg = splt[4].substring(1, splt[4].length() - 1);
//                        if (msg.startsWith("$")) {
//                            send(msg.substring(1));
//                        }
//                    }
//                }
            } catch (Exception e) {

            }
        }

        void output(String msg) {
            switch (msg.substring(0, 4)) {
                case "1001":
                    System.out.println("> In: " + msg.split(" ")[2]);
                    break;
                case "1002":
                    System.out.println("> Joined: " + msg.split(" ")[2]);
                    break;
                case "1003":
                    System.out.println("> Left: " + msg.split(" ")[2]);
                    break;
                case "1004":
                    String[] splt1 = msg.split(" ", 5);
                    System.out.println("> Whisper: " + splt1[2] + ": " + trim(splt1[4]));
                    break;
                case "1005":
                    String[] splt2 = msg.split(" ", 5);
                    System.out.println(splt2[2] + ": " + trim(splt2[4]));
                    break;
                case "1018":
                    System.out.println("> INFO: " + trim(msg.split(" ", 3)[2]));
                    break;
                case "1019":
                    System.out.println("> ERROR: " + trim(msg.split(" ", 3)[2]));
            }
        }

        String trim(String str) {
            return str.substring(1, str.length() - 1);
        }

        synchronized void send(String text) {
            out.write(text);
            out.write(0x0D);
            out.write(0x0A);
            out.flush();
        }
    }
}

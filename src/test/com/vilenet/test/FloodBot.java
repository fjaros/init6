import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.CharBuffer;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloodBot {

    //static final String hostName = "54.193.49.146";
    static final Random random = new Random(System.currentTimeMillis());
    static final int portNumber = 6112;
    static final String hostName = "127.0.0.1";
    static final int bots = 10;
    // static final String hostName = "158.69.231.163";
    //static final int portNumber = 7112;

    static class Bot implements Runnable {

        PrintWriter out;
        String name;
        int num;

        Bot(int num, String name) {
            this.num = num;
            this.name = name;
        }

        @Override
        public void run() {
            Socket socket = null;
            CharBuffer byteBuffer = CharBuffer.allocate(30);
            byteBuffer.put((char) 0x03);
            byteBuffer.put((char) 0x04);
            for (byte b : name.getBytes()) {
                byteBuffer.put((char) b);
            }
            byteBuffer.put((char) 0x0d);
            byteBuffer.put((char) 0x0a);
            byteBuffer.put(new char[]{'1', '2', '3', '4'});
            byteBuffer.put((char) 0x0d);
            byteBuffer.put((char) 0x0a);
            byteBuffer.put(new char[]{'/', 'j', ' ', 's'});
            byteBuffer.put((char) 0x0d);
            byteBuffer.put((char) 0x0a);
            char[] carr = byteBuffer.array();

            for (;;) {
                for (;;) {
                    try {
                        //System.out.println(name);
                        socket = new Socket(hostName, 6112);//num >= (bots / 2) ? 6113 : 6112);//portNumber + random.nextInt(2));
                        break;
                    } catch (Exception e) {
                        try {
                            Thread.sleep(10);
                        } catch (Exception e1) {
                            System.exit(0);
                        }
                    }
                }

                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.write(carr);
                    out.flush();

                    Thread.sleep(100 + random.nextInt(200));
                    out.close();
                    in.close();
                    socket.close();
                    String s;
                    //                while ((s = in.readLine()) != null) {
                    //                    //System.out.println(s);
                    //                    if (s.contains("$join")) {
                    //                        send("/j " + s.substring(s.indexOf("$join") + 5));
                    //                    }
                    //                }
                } catch (Exception e) {
                    System.exit(0);
                }
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
        ExecutorService e = Executors.newFixedThreadPool(bots + 1);
        for (int i = 1; i <= bots; i++) {
            e.submit(new Bot(i, "boat" + i));
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

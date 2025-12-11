import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

import Project.Common.ConnectionPayload;
import Project.Common.GameMode;
import Project.Common.GameModePayload;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.ReadyPayload;

public class TestMatch {
    static class PlayerSim implements Runnable {
        private final String name;
        private final String[] picks; // e.g. {"r","p","s"}
        private final int port = 3000;
        private final CountDownLatch readyLatch;

        public PlayerSim(String name, String[] picks, CountDownLatch readyLatch) {
            this.name = name;
            this.picks = picks;
            this.readyLatch = readyLatch;
        }

        @Override
        public void run() {
            try (Socket sock = new Socket("127.0.0.1", port);
                    ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(sock.getInputStream())) {

                // send connection payload
                ConnectionPayload cp = new ConnectionPayload();
                cp.setClientName(name);
                cp.setPayloadType(PayloadType.CLIENT_CONNECT);
                out.writeObject(cp);
                out.flush();

                // start reader thread
                Thread reader = new Thread(() -> {
                    try {
                        while (true) {
                            Object o = in.readObject();
                            if (o instanceof Payload) {
                                Payload p = (Payload) o;
                                System.out.println("[" + name + "] <- " + p.getPayloadType() + " : " + p);
                            } else {
                                System.out.println("[" + name + "] <- Object: " + o);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("[" + name + "] reader stopped: " + e.getMessage());
                    }
                });
                reader.setDaemon(true);
                reader.start();

                // small wait to let server assign id and join lobby
                Thread.sleep(500);

                // The first player will set the game mode to RPS_5
                if (name.equals("PlayerA")) {
                    GameModePayload gmp = new GameModePayload();
                    gmp.setGameMode(GameMode.RPS_5);
                    gmp.setCooldownEnabled(false);
                    gmp.setPayloadType(PayloadType.GAME_MODE);
                    out.writeObject(gmp);
                    out.flush();
                    System.out.println("[" + name + "] sent GAME_MODE RPS_5 cooldown=false");
                }

                // both players mark ready
                Thread.sleep(200);
                ReadyPayload rp = new ReadyPayload();
                rp.setClientId(-1); // server will set authoritative id
                rp.setReady(true);
                rp.setPayloadType(PayloadType.READY);
                out.writeObject(rp);
                out.flush();
                System.out.println("[" + name + "] sent READY");

                // wait for latch (synchronizes players)
                readyLatch.countDown();
                readyLatch.await();

                // send picks across 3 rounds
                for (int i = 0; i < picks.length; i++) {
                    Thread.sleep(500); // wait a bit each round
                    Payload pick = new Payload();
                    pick.setPayloadType(PayloadType.PLAYER_PICK);
                    pick.setMessage(picks[i]);
                    out.writeObject(pick);
                    out.flush();
                    System.out.println("[" + name + "] sent PICK " + picks[i]);
                }

                // wait a bit to receive GAME_OVER
                Thread.sleep(2000);

            } catch (Exception e) {
                System.out.println("[" + name + "] error: " + e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        Thread t1 = new Thread(new PlayerSim("PlayerA", new String[] {"r","p","s"}, latch));
        Thread t2 = new Thread(new PlayerSim("PlayerB", new String[] {"s","r","p"}, latch));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("TestMatch complete");
    }
}

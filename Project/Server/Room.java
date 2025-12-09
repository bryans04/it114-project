package Project.Server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RoomAction;
import Project.Common.TextFX;
import Project.Common.TextFX.Color;
import Project.Exceptions.DuplicateRoomException;
import Project.Exceptions.RoomNotFoundException;

// bs768, 11/24/2025, Room class handling game logic, session management, and client communication
public class Room implements AutoCloseable {
    private final String name;// unique name of the Room
    private volatile boolean isRunning = false;
    private final ConcurrentHashMap<Long, ServerThread> clientsInRoom = new ConcurrentHashMap<Long, ServerThread>();
    private final Set<Long> readyPlayers = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean isSessionActive = false;

    public final static String LOBBY = "lobby";

    private void info(String message) {
        LoggerUtil.INSTANCE.info(TextFX.colorize(String.format("Room[%s]: %s", name, message), Color.PURPLE));
    }

    public Room(String name) {
        this.name = name;
        isRunning = true;
        info("Created");
    }

    public String getName() {
        return this.name;
    }

    //bs768, 11/24/2025, addClient method for Room
    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        if (clientsInRoom.containsKey(client.getClientId())) {
            info("Attempting to add a client that already exists in the room");
            return;
        }
        clientsInRoom.put(client.getClientId(), client);
        client.setCurrentRoom(this);
        client.sendResetUserList();
        syncExistingClients(client);
        // notify clients of someone joining
        joinStatusRelay(client, true);

    }

    //bs768, 11/24/2025, removeClient method for Room
    protected synchronized void removeClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        if (!clientsInRoom.containsKey(client.getClientId())) {
            info("Attempting to remove a client that doesn't exist in the room");
            return;
        }
        ServerThread removedClient = clientsInRoom.get(client.getClientId());
        if (removedClient != null) {
            // notify clients of someone joining
            joinStatusRelay(removedClient, false);
            clientsInRoom.remove(client.getClientId());
            readyPlayers.remove(client.getClientId());
            autoCleanup();
        }
    }

    private void syncExistingClients(ServerThread incomingClient) {
        clientsInRoom.values().forEach(serverThread -> {
            if (serverThread.getClientId() != incomingClient.getClientId()) {
                boolean failedToSync = !incomingClient.sendClientInfo(serverThread.getClientId(),
                        serverThread.getClientName(), RoomAction.JOIN, true);
                // Sync points for existing clients to the incoming client
                boolean failedPointsSync = !incomingClient.sendPointsUpdate(serverThread.getClientId(),
                        serverThread.getPoints());
                if (failedToSync || failedPointsSync) {
                    LoggerUtil.INSTANCE.warning(
                            String.format("Removing disconnected %s from list", serverThread.getDisplayName()));
                    disconnect(serverThread);
                }
            }
        });
    }

    //bs768, 11/24/2025, joinStatusRelay method for Room
    private void joinStatusRelay(ServerThread client, boolean didJoin) {
        clientsInRoom.values().removeIf(serverThread -> {
            String formattedMessage = String.format("Room[%s] %s %s the room",
                    getName(),
                    client.getClientId() == serverThread.getClientId() ? "You"
                            : client.getDisplayName(),
                    didJoin ? "joined" : "left");
            final long senderId = client == null ? Constants.DEFAULT_CLIENT_ID : client.getClientId();
            // Share info of the client joining or leaving the room
            boolean failedToSync = !serverThread.sendClientInfo(client.getClientId(),
                    client.getClientName(), didJoin ? RoomAction.JOIN : RoomAction.LEAVE);
            // Send the server generated message to the current client
            boolean failedToSend = !serverThread.sendMessage(senderId, formattedMessage);
            if (failedToSend || failedToSync) {
                LoggerUtil.INSTANCE.warning(
                        String.format("Removing disconnected %s from list", serverThread.getDisplayName()));
                disconnect(serverThread);
            }
            return failedToSend;
        });
    }

    /**
     * Sends a basic String message from the sender to all connectedClients
     * Internally calls processCommand and evaluates as necessary.
     * Note: Clients that fail to receive a message get removed from
     * connectedClients.
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param message
     * @param sender  ServerThread (client) sending the message or null if it's a
     *                server-generated message
     */
    
    //bs768, 11/24/2025, relay method for Room
    protected synchronized void relay(ServerThread sender, String message) {
        if (!isRunning) { 
            return;
        }
        String senderString = sender == null ? String.format("Room[%s]", getName())
                : sender.getDisplayName();
        final long senderId = sender == null ? Constants.DEFAULT_CLIENT_ID : sender.getClientId();
        // Note: formattedMessage must be final (or effectively final) since outside
        // scope can't be changed inside a callback function (see removeIf() below)
        final String formattedMessage = String.format("%s: %s", senderString, message);

        // loop over clients and send out the message; remove client if message failed
        // to be sent
        // Note: this uses a lambda expression for each item in the values() collection,
        // it's one way we can safely remove items during iteration
        info(String.format("sending message to %s recipients: %s", clientsInRoom.size(), formattedMessage));

        clientsInRoom.values().removeIf(serverThread -> {
            boolean failedToSend = !serverThread.sendMessage(senderId, formattedMessage);
            if (failedToSend) {
                LoggerUtil.INSTANCE.warning(
                        String.format("Removing disconnected %s from list", serverThread.getDisplayName()));
                disconnect(serverThread);
            }
            return failedToSend;
        });
    }

    /**
     * Takes a ServerThread and removes them from the Server
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param client
     */
    private synchronized void disconnect(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        ServerThread disconnectingServerThread = clientsInRoom.remove(client.getClientId());
        if (disconnectingServerThread != null) {
            readyPlayers.remove(client.getClientId());
            clientsInRoom.values().removeIf(serverThread -> {
                if (serverThread.getClientId() == disconnectingServerThread.getClientId()) {
                    return true;
                }
                boolean failedToSend = !serverThread.sendClientInfo(disconnectingServerThread.getClientId(),
                        disconnectingServerThread.getClientName(), RoomAction.LEAVE);
                if (failedToSend) {
                    LoggerUtil.INSTANCE.warning(
                            String.format("Removing disconnected %s from list", serverThread.getDisplayName()));
                    disconnect(serverThread);
                }
                return failedToSend;
            });
            relay(null, disconnectingServerThread.getDisplayName() + " disconnected");
            disconnectingServerThread.disconnect();
        }
        autoCleanup();
    }

    protected synchronized void disconnectAll() {
        info("Disconnect All triggered");
        if (!isRunning) {
            return;
        }
        clientsInRoom.values().removeIf(client -> {
            disconnect(client);
            return true;
        });
        info("Disconnect All finished");
    }

    /**
     * Attempts to close the room to free up resources if it's empty
     */
    private void autoCleanup() {
        if (!Room.LOBBY.equalsIgnoreCase(name) && clientsInRoom.isEmpty()) {
            close();
        }
    }

    @Override
    public void close() {
        // attempt to gracefully close and migrate clients
        if (!clientsInRoom.isEmpty()) {
            relay(null, "Room is shutting down, migrating to lobby");
            info(String.format("migrating %s clients", clientsInRoom.size()));
            clientsInRoom.values().removeIf(client -> {
                try {
                    Server.INSTANCE.joinRoom(Room.LOBBY, client);
                } catch (RoomNotFoundException e) {
                    e.printStackTrace();
                    // TODO, fill in, this shouldn't happen though
                }
                return true;
            });
        }
        Server.INSTANCE.removeRoom(this);
        isRunning = false;
        clientsInRoom.clear();
        readyPlayers.clear();
        info(String.format("closed"));
    }

    // start handle methods
    protected void handleListRooms(ServerThread sender, String roomQuery) {
        sender.sendRooms(Server.INSTANCE.listRooms(roomQuery));
    }

    public void handleCreateRoom(ServerThread sender, String roomName) {
        try {
            Server.INSTANCE.createRoom(roomName);
            Server.INSTANCE.joinRoom(roomName, sender);
        } catch (RoomNotFoundException e) {
            info("Room wasn't found (this shouldn't happen)");
            e.printStackTrace();
        } catch (DuplicateRoomException e) {
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s already exists", roomName));
        }
    }

    public void handleJoinRoom(ServerThread sender, String roomName) {
        try {
            Server.INSTANCE.joinRoom(roomName, sender);
        } catch (RoomNotFoundException e) {
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s doesn't exist", roomName));
        }
    }

    protected synchronized void handleDisconnect(BaseServerThread sender) {
        handleDisconnect((ServerThread) sender);
    }

    /**
     * Expose access to the disconnect action
     * 
     * @param serverThread
     */
    protected synchronized void handleDisconnect(ServerThread sender) {
        disconnect(sender);
    }

    protected synchronized void handleReverseText(ServerThread sender, String text) {
        StringBuilder sb = new StringBuilder(text);
        sb.reverse();
        String rev = sb.toString();
        relay(sender, rev);
    }

    protected synchronized void handleMessage(ServerThread sender, String text) {
        relay(sender, text);
    }

    /**
     * Updates a player's points and syncs to all clients in the room
     * 
     * @param player       the ServerThread whose points are being updated
     * @param pointsChange the amount to add (positive) or subtract (negative)
     */
    protected synchronized void updatePlayerPoints(ServerThread player, int pointsChange) {
        if (!isRunning) {
            return;
        }
        int newPoints = player.getPoints() + pointsChange;
        player.setPoints(newPoints);
        syncPlayerPoints(player);
        info(String.format("%s points updated: %d (change: %+d)",
                player.getDisplayName(), newPoints, pointsChange));
    }

    /**
     * Syncs a player's current points to all clients in the room
     * 
     * @param player the ServerThread whose points should be synced
     */
    protected synchronized void syncPlayerPoints(ServerThread player) {
        if (!isRunning) {
            return;
        }
        final long playerId = player.getClientId();
        final int points = player.getPoints();

        clientsInRoom.values().removeIf(serverThread -> {
            boolean failedToSend = !serverThread.sendPointsUpdate(playerId, points);
            if (failedToSend) {
                LoggerUtil.INSTANCE.warning(
                        String.format("Removing disconnected %s from list", serverThread.getDisplayName()));
                disconnect(serverThread);
            }
            return failedToSend;
        });
    }

    /**
     * Handles incoming points update from a client (if clients can self-report)
     * 
     * @param sender the client sending the update
     * @param points the new points value
     */
    protected synchronized void handlePointsUpdate(ServerThread sender, int points) {
        if (!isRunning) {
            return;
        }
        sender.setPoints(points);
        syncPlayerPoints(sender);
    }
    // end handle methods

    private Timer roundTimer;
    private final long ROUND_DURATION_MS = 30_000; // 30 seconds for round duration
    private volatile boolean roundActive = false;

    private synchronized void startRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        roundTimer = new Timer();
        roundActive = true;

        roundTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                info("Round timer expired, ending round.");
                endRoundDueToTimeout();
            }
        }, ROUND_DURATION_MS);

        info("Round timer started for " + (ROUND_DURATION_MS / 1000) + " seconds.");
    }

    private synchronized void endRoundDueToTimeout() {
        if (!roundActive)
            return; // avoid multiple calls
        endRound();
    }

    // Game Logic

    public synchronized void handleReadyCheck(ServerThread client) {
        if (isSessionActive) {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, "Session already active.");
            return;
        }
        long clientId = client.getClientId();
        if (readyPlayers.contains(clientId)) {
            readyPlayers.remove(clientId);
            relay(null, client.getDisplayName() + " is not ready.");
        } else {
            readyPlayers.add(clientId);
            relay(null, client.getDisplayName() + " is ready.");
        }

        if (readyPlayers.size() == clientsInRoom.size() && clientsInRoom.size() >= 2) {
            sessionStart();
        } else if (readyPlayers.size() == clientsInRoom.size() && clientsInRoom.size() < 2) {
            relay(null, "Not enough players to start. Need at least 2.");
        }
    }

    //bs768, 11/24/2025, sessionStart method for Room
    private void sessionStart() {
        isSessionActive = true;
        relay(null, "All players ready! Session starting.");
        // Reset all players
        for (ServerThread client : clientsInRoom.values()) {
            client.setPoints(0);
            client.setEliminated(false);
            client.setChoice(null);
            syncPlayerPoints(client);
        }
        roundStart();
    }

    //bs768, 11/24/2025, roundStart method for Room
    private void roundStart() {
        if (!isSessionActive)
            return;

        // Reset choices for active players
        for (ServerThread client : clientsInRoom.values()) {
            if (!client.isEliminated()) {
                client.setChoice(null);
            }
        }

        Payload p = new Payload();
        p.setPayloadType(PayloadType.ROUND_START);
        p.setMessage("Round Started! Type /pick [r|p|s]");
        broadcast(p);

        startRoundTimer();
    }

    //bs768, 11/24/2025, handlePick method for Room
    public synchronized void handlePick(ServerThread client, String choice) {
        if (!roundActive || client.isEliminated()) {
            return;
        }
        if (choice == null
                || (!choice.equalsIgnoreCase("r") && !choice.equalsIgnoreCase("p") && !choice.equalsIgnoreCase("s"))) {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, "Invalid choice. Use r, p, or s.");
            return;
        }

        client.setChoice(choice.toLowerCase());
        relay(null, client.getDisplayName() + " picked their choice.");

        checkRoundEnd();
    }

    //bs768, 11/24/2025, checkRoundEnd method for Room
    private void checkRoundEnd() {
        boolean allPicked = true;
        for (ServerThread client : clientsInRoom.values()) {
            if (!client.isEliminated() && client.getChoice() == null) {
                allPicked = false;
                break;
            }
        }
        if (allPicked) {
            endRound();
        }
    }

    //bs768, 11/24/2025, endRound method for Room
    private synchronized void endRound() {
        if (!roundActive)
            return;
        roundActive = false;
        if (roundTimer != null) {
            roundTimer.cancel();
        }

        for (ServerThread client : clientsInRoom.values()) {
            if (!client.isEliminated() && client.getChoice() == null) {
                client.setEliminated(true);
                relay(null, client.getDisplayName() + " eliminated for not picking.");
            }
        }

        processBattles();
    }

    private void processBattles() {
        List<ServerThread> activePlayers = clientsInRoom.values().stream()
                .filter(c -> !c.isEliminated())
                .collect(Collectors.toList());

        if (activePlayers.size() < 2) {
            checkWinCondition();
            return;
        }

        StringBuilder battleResults = new StringBuilder();
        List<ServerThread> losers = new ArrayList<>();

        // bs768, 11/24/2025, processBattles method for Room
        for (int i = 0; i < activePlayers.size(); i++) {
            ServerThread p1 = activePlayers.get(i);
            ServerThread p2 = activePlayers.get((i + 1) % activePlayers.size());

            String c1 = p1.getChoice();
            String c2 = p2.getChoice();

            battleResults
                    .append(String.format("%s (%s) vs %s (%s): ", p1.getDisplayName(), c1, p2.getDisplayName(), c2));

            int result = compareChoices(c1, c2);
            if (result == 1) { // p1 wins
                updatePlayerPoints(p1, 1);
                losers.add(p2);
                battleResults.append(p1.getDisplayName() + " wins!\n");
            } else if (result == -1) { // p2 wins
                updatePlayerPoints(p2, 1);
                losers.add(p1);
                battleResults.append(p2.getDisplayName() + " wins!\n");
            } else {
                battleResults.append("Tie!\n");
            }
        }

        for (ServerThread loser : losers) {
            if (!loser.isEliminated()) { 
                loser.setEliminated(true);
            }
        }

        Payload resultPayload = new Payload();
        resultPayload.setPayloadType(PayloadType.BATTLE_RESULT);
        resultPayload.setMessage(battleResults.toString());
        broadcast(resultPayload);

        checkWinCondition();
    }

    //bs768, 11/24/2025, compareChoices method for Room
    private int compareChoices(String c1, String c2) {
        if (c1.equals(c2))
            return 0;
        if (c1.equals("r") && c2.equals("s"))
            return 1;
        if (c1.equals("s") && c2.equals("p"))
            return 1;
        if (c1.equals("p") && c2.equals("r"))
            return 1;
        return -1;
    }

    //bs768, 11/24/2025, checkWinCondition method for Room
    private void checkWinCondition() {
        List<ServerThread> remaining = clientsInRoom.values().stream()
                .filter(c -> !c.isEliminated())
                .collect(Collectors.toList());

        if (remaining.size() == 1) {
            sessionEnd(remaining.get(0));
        } else if (remaining.size() == 0) {
            sessionEnd(null); // Tie
        } else {
            roundStart();
        }
    }

    //bs768, 11/24/2025, sessionEnd method for Room
    private void sessionEnd(ServerThread winner) {
        isSessionActive = false;
        readyPlayers.clear();

        StringBuilder sb = new StringBuilder();
        if (winner != null) {
            sb.append("Game Over! Winner: ").append(winner.getDisplayName()).append("\n");
        } else {
            sb.append("Game Over! It's a Tie!\n");
        }

        List<ServerThread> sortedPlayers = new ArrayList<>(clientsInRoom.values());
        sortedPlayers.sort((p1, p2) -> Integer.compare(p2.getPoints(), p1.getPoints()));

        sb.append("Final Scores:\n");
        for (ServerThread p : sortedPlayers) {
            sb.append(p.getDisplayName()).append(": ").append(p.getPoints()).append("\n");
        }

        Payload p = new Payload();
        p.setPayloadType(PayloadType.SESSION_END);
        p.setMessage(sb.toString());
        broadcast(p);

        for (ServerThread client : clientsInRoom.values()) {
            client.setPoints(0);
            client.setEliminated(false);
            client.setChoice(null);
            syncPlayerPoints(client);
        }

        relay(null, "Session ended. Type /ready to start a new game.");
    }

    private void broadcast(Payload payload) {
        clientsInRoom.values().removeIf(serverThread -> {
            boolean failedToSend = !serverThread.sendToClient(payload);
            if (failedToSend) {
                LoggerUtil.INSTANCE.warning(
                        String.format("Removing disconnected %s from list", serverThread.getDisplayName()));
                disconnect(serverThread);
            }
            return failedToSend;
        });
    }
}

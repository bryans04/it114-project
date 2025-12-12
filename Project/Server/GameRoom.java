package Project.Server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import Project.Common.Constants;
import Project.Common.GameOverPayload;
import Project.Common.GameMode;
import Project.Common.GameModePayload;
import Project.Common.LoggerUtil;
import Project.Common.Phase;
import Project.Common.TimedEvent;
import Project.Common.TimerType;
import Project.Exceptions.MissingCurrentPlayerException;
import Project.Exceptions.NotPlayersTurnException;
import Project.Exceptions.NotReadyException;
import Project.Exceptions.PhaseMismatchException;
import Project.Exceptions.PlayerNotFoundException;

public class GameRoom extends BaseGameRoom {

    // used for general rounds (usually phase-based turns)
    private TimedEvent roundTimer = null;

    // used for granular turn handling (usually turn-order turns)
    private TimedEvent turnTimer = null;
    private List<ServerThread> turnOrder = new ArrayList<>();
    private long currentTurnClientId = Constants.DEFAULT_CLIENT_ID;
    private int round = 0;
    private GameMode gameMode = GameMode.RPS_3; // Default to RPS-3
    private boolean cooldownEnabled = false; // Track if cooldown is enabled
    private boolean gameStarted = false; // Track if this is the first round or a subsequent one

    public GameRoom(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientAdded(ServerThread sp) {
        // sync GameRoom state to new client

        syncCurrentPhase(sp);
        // sync only what's necessary for the specific phase
        // if you blindly sync everything, you'll get visual artifacts/discrepancies
        syncReadyStatus(sp);
        if (currentPhase != Phase.READY) {
            syncTurnStatus(sp); // turn/ready use the same visual process so ensure turn status is only called
                                // outside of ready phase
            syncPlayerPoints(sp);
        }

    }

    /** {@inheritDoc} */
    @Override
    protected void onClientRemoved(ServerThread sp) {
        // added after Summer 2024 Demo
        // Stops the timers so room can clean up
        LoggerUtil.INSTANCE.info("Player Removed, remaining: " + clientsInRoom.size());
        long removedClient = sp.getClientId();
        turnOrder.removeIf(player -> player.getClientId() == sp.getClientId());
        if (clientsInRoom.isEmpty()) {
            resetReadyTimer();
            resetTurnTimer();
            resetRoundTimer();
            onSessionEnd();
        } else if (removedClient == currentTurnClientId) {
            onTurnStart();
        }
    }

    // timer handlers
    private void startRoundTimer() {
        roundTimer = new TimedEvent(30, () -> onRoundEnd());
        roundTimer.setTickCallback((time) -> {
            System.out.println("Round Time: " + time);
            sendCurrentTime(TimerType.ROUND, time);
        });
    }

    private void resetRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
            sendCurrentTime(TimerType.ROUND, -1);
        }
    }

    private void startTurnTimer() {
        turnTimer = new TimedEvent(30, () -> onTurnEnd());
        turnTimer.setTickCallback((time) -> {
            System.out.println("Turn Time: " + time);
            sendCurrentTime(TimerType.TURN, time);
        });
    }

    private void resetTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
            sendCurrentTime(TimerType.TURN, -1);
        }
    }
    // end timer handlers

    // lifecycle methods

    /** {@inheritDoc} */
    @Override
    protected void onSessionStart() {
        LoggerUtil.INSTANCE.info("onSessionStart() start");
        gameStarted = true;
        changePhase(Phase.IN_PROGRESS);
        currentTurnClientId = Constants.DEFAULT_CLIENT_ID;

        clientsInRoom.values().forEach(p -> {
            if (!p.isReady()) {
                p.setSpectator(true);

                Project.Common.ConnectionPayload cp = new Project.Common.ConnectionPayload();
                cp.setClientId(p.getClientId());
                cp.setClientName(p.getDisplayName());
                cp.setSpectator(true);
                cp.setPayloadType(Project.Common.PayloadType.SYNC_CLIENT);
                clientsInRoom.values().forEach(client -> client.sendToClient(cp));

                sendGameEvent(String.format("%s is now spectating", p.getDisplayName()));
            }
        });

        clientsInRoom.values().forEach(p -> p.setPoints(0));
        setTurnOrder();
        round = 0;
        resetEliminationStatus();
        LoggerUtil.INSTANCE.info("onSessionStart() end");
        onRoundStart();
    }

    /** {@inheritDoc} */
    @Override
    protected void onRoundStart() {
        LoggerUtil.INSTANCE.info("onRoundStart() start");
        resetRoundTimer();
        resetTurnStatus();
        round++;
        sendGameEvent(String.format("Round %d has started", round));
        startRoundTimer(); // Start 30-second round timer

        // RPS is simultaneous - all players pick at once, no turns needed
        // Players can now make their choices immediately

        LoggerUtil.INSTANCE.info("onRoundStart() end");
    }

    /** {@inheritDoc} */
    @Override
    protected void onTurnStart() {
        LoggerUtil.INSTANCE.info("onTurnStart() start");
        resetTurnTimer();
        try {
            ServerThread currentPlayer = getNextPlayer();
            // relay(null, String.format("It's %s's turn", currentPlayer.getDisplayName()));
            sendGameEvent(String.format("It's %s's turn", currentPlayer.getDisplayName()));
        } catch (MissingCurrentPlayerException | PlayerNotFoundException e) {

            e.printStackTrace();
        }
        startTurnTimer();
        LoggerUtil.INSTANCE.info("onTurnStart() end");
    }

    // Note: logic between Turn Start and Turn End is typically handled via timers
    // and user interaction
    /** {@inheritDoc} */
    @Override
    protected void onTurnEnd() {
        LoggerUtil.INSTANCE.info("onTurnEnd() start");
        resetTurnTimer(); // reset timer if turn ended without the time expiring
        try {
            // optionally can use checkAllTookTurn();
            if (isLastPlayer()) {
                // if the current player is the last player in the turn order, end the round
                onRoundEnd();
            } else {
                onTurnStart();
            }
        } catch (MissingCurrentPlayerException | PlayerNotFoundException e) {

            e.printStackTrace();
        }
        LoggerUtil.INSTANCE.info("onTurnEnd() end");
    }

    // Note: logic between Round Start and Round End is typically handled via timers
    // and user interaction
    /** {@inheritDoc} */
    @Override
    protected void onRoundEnd() {
        LoggerUtil.INSTANCE.info("onRoundEnd() start");
        resetRoundTimer(); // reset timer if round ended without the time expiring
        resetReadyTimer(); // Reset ready timer so it doesn't expire with 0 ready players

        // NEW: Eliminate players who didn't make a choice
        eliminateNonPickers();

        // Determine winners and award points
        determineRoundWinnersAndAwardPoints();

        // Clear choices for next round (but keep players marked as ready)
        resetChoices();

        LoggerUtil.INSTANCE.info("onRoundEnd() end");

        // NEW: Check if game should end based on eliminations instead of fixed round
        // count
        if (shouldEndSession()) {
            onSessionEnd();
        } else {
            // Automatically go to next round without requiring players to mark ready again
            changePhase(Phase.IN_PROGRESS);
            onRoundStart();
        }
    }

    /**
     * Override checkReadyStatus to handle round transitions properly
     * For the first game start, call onSessionStart
     * For subsequent rounds, transition directly to IN_PROGRESS
     */
    @Override
    protected void checkReadyStatus() {
        long numReady = clientsInRoom.values().stream().filter(p -> p.isReady()).count();
        if (numReady >= MINIMUM_REQUIRED_TO_START) {
            resetReadyTimer();
            if (!gameStarted) {
                // First time: initialize the game
                onSessionStart();
            } else {
                // Subsequent rounds: just transition to IN_PROGRESS
                changePhase(Phase.IN_PROGRESS);
                onRoundStart();
            }
        } else {
            onSessionEnd();
        }
    }

    /**
     * Determines round winners and awards points using round-robin battles
     * Each player battles every other player
     * Compares all ready players' choices and determines winner(s)
     * Excludes spectators, away players, and eliminated players
     */
    private void determineRoundWinnersAndAwardPoints() {
        List<ServerThread> activePlayers = clientsInRoom.values().stream()
                .filter(p -> p.isReady() && !p.isSpectator() && !p.isEliminated() && !p.isAway())
                .collect(Collectors.toList());

        if (activePlayers.size() < 2) {
            sendGameEvent("Not enough active players to complete the round");
            return;
        }

        // Get all choices
        Map<ServerThread, String> playerChoices = new HashMap<>();
        for (ServerThread player : activePlayers) {
            String choice = player.getChoice();
            if (choice == null || choice.trim().isEmpty()) {
                // This shouldn't happen as eliminateNonPickers() runs first
                sendGameEvent(String.format("%s did not make a choice", player.getDisplayName()));
                return;
            }
            playerChoices.put(player, choice);
        }

        // Track wins for each player
        Map<ServerThread, Integer> wins = new HashMap<>();
        activePlayers.forEach(p -> wins.put(p, 0));

        // Round-robin battles: each player vs every other player
        for (int i = 0; i < activePlayers.size(); i++) {
            for (int j = i + 1; j < activePlayers.size(); j++) {
                ServerThread player1 = activePlayers.get(i);
                ServerThread player2 = activePlayers.get(j);
                String choice1 = playerChoices.get(player1);
                String choice2 = playerChoices.get(player2);

                int result = compareChoices(choice1, choice2);

                String battleMessage;
                if (result > 0) {
                    // Player 1 wins
                    wins.put(player1, wins.get(player1) + 1);
                    battleMessage = String.format("%s (%s) vs %s (%s) - %s wins!",
                            player1.getDisplayName(), gameMode.getDisplay(choice1),
                            player2.getDisplayName(), gameMode.getDisplay(choice2),
                            player1.getDisplayName());
                } else if (result < 0) {
                    // Player 2 wins
                    wins.put(player2, wins.get(player2) + 1);
                    battleMessage = String.format("%s (%s) vs %s (%s) - %s wins!",
                            player1.getDisplayName(), gameMode.getDisplay(choice1),
                            player2.getDisplayName(), gameMode.getDisplay(choice2),
                            player2.getDisplayName());
                } else {
                    // Tie
                    battleMessage = String.format("%s (%s) vs %s (%s) - Tie!",
                            player1.getDisplayName(), gameMode.getDisplay(choice1),
                            player2.getDisplayName(), gameMode.getDisplay(choice2));
                }

                sendGameEvent(battleMessage);
            }
        }

        // Find players with most wins
        int maxWins = wins.values().stream().max(Integer::compare).orElse(0);
        List<ServerThread> roundWinners = wins.entrySet().stream()
                .filter(e -> e.getValue() == maxWins && maxWins > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Award points to round winners
        for (ServerThread winner : roundWinners) {
            winner.changePoints(1);
            sendPlayerPoints(winner);
            sendGameEvent(String.format("%s wins the round and gets 1 point! (Total: %d)",
                    winner.getDisplayName(), winner.getPoints()));
        }

        // Eliminate losers (players with 0 wins)
        activePlayers.stream()
                .filter(p -> wins.get(p) == 0)
                .forEach(loser -> {
                    loser.setEliminated(true);
                    sendEliminationStatus(loser, true);
                    sendGameEvent(String.format("💀 %s has been eliminated!",
                            loser.getDisplayName()));
                });

        // Clear choices for next round
        resetChoices();
    }

    /**
     * Compares two Rock-Paper-Scissors choices
     * 
     * @return positive if choice1 wins, negative if choice2 wins, 0 if tie
     */
    private int compareChoices(String choice1, String choice2) {
        if (choice1.equals(choice2)) {
            return 0; // Tie
        }

        // RPS-3: Rock beats Scissors, Scissors beats Paper, Paper beats Rock
        // RPS-5: Rock beats Scissors & Lizard, Paper beats Rock & Spock,
        // Scissors beats Paper & Lizard, Lizard beats Spock & Paper,
        // Spock beats Scissors & Rock

        switch (choice1) {
            case "r": // Rock
                return (choice2.equals("s") || choice2.equals("l")) ? 1 : -1;
            case "p": // Paper
                return (choice2.equals("r") || choice2.equals("k")) ? 1 : -1;
            case "s": // Scissors
                return (choice2.equals("p") || choice2.equals("l")) ? 1 : -1;
            case "l": // Lizard
                return (choice2.equals("k") || choice2.equals("p")) ? 1 : -1;
            case "k": // Spock
                return (choice2.equals("s") || choice2.equals("r")) ? 1 : -1;
            default:
                return 0;
        }
    }

    /**
     * Clears all player choices for the next round
     */
    private void resetChoices() {
        clientsInRoom.values().forEach(p -> p.setChoice(null));
    }

    /** {@inheritDoc} */
    @Override
    protected void onSessionEnd() {
        LoggerUtil.INSTANCE.info("onSessionEnd() start");
        // ensure any pending ready timer is cancelled so it doesn't re-trigger
        resetReadyTimer();
        // also cancel any active round/turn timers to avoid stray callbacks
        resetRoundTimer();
        resetTurnTimer();
        // Determine session winner(s) before clearing state
        // Exclude spectators and away players from contenders
        java.util.List<ServerThread> contenders = clientsInRoom.values().stream()
                .filter(p -> !p.isSpectator() && !p.isAway())
                .collect(Collectors.toList());
        int topPoints = Integer.MIN_VALUE;
        for (ServerThread p : contenders) {
            if (p.getPoints() > topPoints) {
                topPoints = p.getPoints();
            }
        }
        java.util.List<String> winners = new ArrayList<>();
        if (topPoints > Integer.MIN_VALUE) {
            for (ServerThread p : contenders) {
                if (p.getPoints() == topPoints) {
                    winners.add(p.getDisplayName());
                }
            }
        }

        // Send game event message to announce winner(s) instead of popup
        String winnerMessage;
        if (winners.isEmpty()) {
            winnerMessage = "Game Over: No winners";
        } else if (winners.size() == 1) {
            winnerMessage = String.format("Game Over: %s wins with %d point(s)!", winners.get(0), topPoints);
        } else {
            winnerMessage = String.format("Game Over: Tie between %s with %d point(s)!", String.join(", ", winners),
                    topPoints);
        }
        sendGameEvent(winnerMessage);

        // NEW: Send final scoreboard
        sendFinalScoreboard();

        // Clear session state
        turnOrder.clear();
        currentTurnClientId = Constants.DEFAULT_CLIENT_ID;
        resetReadyStatus();
        resetTurnStatus();
        resetChoices(); // Clear any remaining choices
        resetEliminationStatus(); // NEW: Clear elimination status
        gameStarted = false; // Reset flag so next game starts from round 1
        changePhase(Phase.READY);
        LoggerUtil.INSTANCE.info("onSessionEnd() end");
    }
    // end lifecycle methods

    // send/sync data to ServerThread(s)
    private void syncPlayerPoints(ServerThread incomingClient) {
        clientsInRoom.values().forEach(serverUser -> {
            if (serverUser.getClientId() != incomingClient.getClientId()) {
                boolean failedToSync = !incomingClient.sendPlayerPoints(serverUser.getClientId(),
                        serverUser.getPoints());
                if (failedToSync) {
                    LoggerUtil.INSTANCE.warning(
                            String.format("Removing disconnected %s from list", serverUser.getDisplayName()));
                    disconnect(serverUser);
                }
            }
        });
    }

    private void sendPlayerPoints(ServerThread sp) {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendPlayerPoints(sp.getClientId(), sp.getPoints());
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }

    private void sendResetTurnStatus() {
        clientsInRoom.values().forEach(spInRoom -> {
            boolean failedToSend = !spInRoom.sendResetTurnStatus();
            if (failedToSend) {
                removeClient(spInRoom);
            }
        });
    }

    private void sendTurnStatus(ServerThread client, boolean tookTurn) {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendTurnStatus(client.getClientId(), client.didTakeTurn());
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }

    private void syncTurnStatus(ServerThread incomingClient) {
        clientsInRoom.values().forEach(serverUser -> {
            if (serverUser.getClientId() != incomingClient.getClientId()) {
                boolean failedToSync = !incomingClient.sendTurnStatus(serverUser.getClientId(),
                        serverUser.didTakeTurn(), true);
                if (failedToSync) {
                    LoggerUtil.INSTANCE.warning(
                            String.format("Removing disconnected %s from list", serverUser.getDisplayName()));
                    disconnect(serverUser);
                }
            }
        });
    }

    /**
     * Sends elimination status for a player to all clients
     * 
     * @param player     the player whose elimination status changed
     * @param eliminated true if eliminated, false if restored
     */
    private void sendEliminationStatus(ServerThread player, boolean eliminated) {
        clientsInRoom.values().forEach(spInRoom -> {
            boolean failedToSend = !spInRoom.sendEliminationStatus(player.getClientId(), eliminated);
            if (failedToSend) {
                removeClient(spInRoom);
            }
        });
    }

    /**
     * Eliminates all players who didn't make a choice before round ended
     */
    private void eliminateNonPickers() {
        clientsInRoom.values().stream()
                .filter(p -> p.isReady() && !p.isSpectator() && !p.isEliminated() && !p.isAway())
                .forEach(player -> {
                    String choice = player.getChoice();
                    if (choice == null || choice.trim().isEmpty()) {
                        player.setEliminated(true);
                        sendEliminationStatus(player, true);
                        sendGameEvent(String.format("💀 %s was eliminated for not making a choice",
                                player.getDisplayName()));
                    }
                });
    }

    /**
     * Checks if the session should end based on number of non-eliminated players
     * 
     * @return true if 1 or fewer players remain
     */
    private boolean shouldEndSession() {
        long nonEliminatedCount = clientsInRoom.values().stream()
                .filter(p -> p.isReady() && !p.isSpectator() && !p.isEliminated() && !p.isAway())
                .count();

        return nonEliminatedCount <= 1;
    }

    /**
     * Resets elimination status for all players (called on session end)
     */
    private void resetEliminationStatus() {
        clientsInRoom.values().forEach(p -> {
            if (p.isEliminated()) {
                p.setEliminated(false);
                sendEliminationStatus(p, false);
            }
        });
    }

    /**
     * Sends final scoreboard sorted by points (highest to lowest)
     */
    private void sendFinalScoreboard() {
        List<ServerThread> allPlayers = clientsInRoom.values().stream()
                .filter(p -> !p.isSpectator())
                .sorted((a, b) -> Integer.compare(b.getPoints(), a.getPoints()))
                .collect(Collectors.toList());

        sendGameEvent("═══════════════════════════");
        sendGameEvent("📊 FINAL SCOREBOARD");
        sendGameEvent("═══════════════════════════");

        int rank = 1;
        for (ServerThread player : allPlayers) {
            String status = player.isEliminated() ? " [ELIMINATED]" : "";
            sendGameEvent(String.format("%d. %s - %d point(s)%s",
                    rank++,
                    player.getDisplayName(),
                    player.getPoints(),
                    status));
        }

        sendGameEvent("═══════════════════════════");
    }

    // end send data to ServerThread(s)

    // misc methods
    private void resetTurnStatus() {
        clientsInRoom.values().forEach(sp -> {
            sp.setTookTurn(false);
        });
        sendResetTurnStatus();
    }

    /**
     * Sets `turnOrder` to a shuffled list of players who are ready.
     */
    private void setTurnOrder() {
        turnOrder.clear();
        // Exclude spectators and away players from the turn order
        turnOrder = clientsInRoom.values().stream()
                .filter(sp -> sp.isReady() && !sp.isSpectator() && !sp.isAway())
                .collect(Collectors.toList());
        Collections.shuffle(turnOrder);
    }

    /**
     * Gets the current player based on the `currentTurnClientId`.
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private ServerThread getCurrentPlayer() throws MissingCurrentPlayerException, PlayerNotFoundException {
        // quick early exit
        if (currentTurnClientId == Constants.DEFAULT_CLIENT_ID) {
            throw new MissingCurrentPlayerException("Current Player not set");
        }
        return turnOrder.stream()
                .filter(sp -> sp.getClientId() == currentTurnClientId)
                .findFirst()
                // this shouldn't occur but is included as a "just in case"
                .orElseThrow(() -> new PlayerNotFoundException("Current player not found in turn order"));
    }

    /**
     * Gets the next player in the turn order.
     * If the current player is the last in the turn order, it wraps around
     * (round-robin).
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private ServerThread getNextPlayer() throws MissingCurrentPlayerException, PlayerNotFoundException {
        int index = 0;
        if (currentTurnClientId != Constants.DEFAULT_CLIENT_ID) {
            index = turnOrder.indexOf(getCurrentPlayer()) + 1;
            if (index >= turnOrder.size()) {
                index = 0;
            }
        }
        ServerThread nextPlayer = turnOrder.get(index);
        currentTurnClientId = nextPlayer.getClientId();
        return nextPlayer;
    }

    /**
     * Checks if the current player is the last player in the turn order.
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private boolean isLastPlayer() throws MissingCurrentPlayerException, PlayerNotFoundException {
        // check if the current player is the last player in the turn order
        return turnOrder.indexOf(getCurrentPlayer()) == (turnOrder.size() - 1);
    }

    private void checkAllTookTurn() {
        int numReady = clientsInRoom.values().stream()
                .filter(sp -> sp.isReady())
                .toList().size();
        int numTookTurn = clientsInRoom.values().stream()
                // ensure to verify the isReady part since it's against the original list
                .filter(sp -> sp.isReady() && sp.didTakeTurn())
                .toList().size();
        if (numReady == numTookTurn) {
            // relay(null,
            // String.format("All players have taken their turn (%d/%d) ending the round",
            // numTookTurn, numReady));
            sendGameEvent(
                    String.format("All players have taken their turn (%d/%d) ending the round", numTookTurn, numReady));
            onRoundEnd();
        }
    }

    // start check methods
    private void checkCurrentPlayer(long clientId) throws NotPlayersTurnException {
        if (currentTurnClientId != clientId) {
            throw new NotPlayersTurnException("You are not the current player");
        }
    }

    // end check methods

    // receive data from ServerThread (GameRoom specific)

    /**
     * Handles the turn action from the client.
     * 
     * @param currentUser
     * @param exampleText (arbitrary text from the client, can be used for
     *                    additional actions or information)
     */
    protected void handleTurnAction(ServerThread currentUser, String exampleText) {
        // check if the client is in the room
        try {
            checkPlayerInRoom(currentUser);
            checkCurrentPhase(currentUser, Phase.IN_PROGRESS);
            checkCurrentPlayer(currentUser.getClientId());
            checkIsReady(currentUser);
            if (currentUser.didTakeTurn()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You have already taken your turn this round");
                return;
            }
            // example points
            int points = new Random().nextInt(4) == 3 ? 1 : 0;
            sendGameEvent(String.format("%s %s", currentUser.getDisplayName(),
                    points > 0 ? "gained a point" : "didn't gain a point"));
            if (points > 0) {
                currentUser.changePoints(points);
                sendPlayerPoints(currentUser);
            }
            currentUser.setTookTurn(true);
            // TODO handle example text possibly or other turn related intention from client
            sendTurnStatus(currentUser, currentUser.didTakeTurn());
            // finished processing the turn
            onTurnEnd();
        } catch (NotPlayersTurnException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "It's not your turn");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (NotReadyException e) {
            // The check method already informs the currentUser
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (PlayerNotFoundException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to do the ready check");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (PhaseMismatchException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID,
                    "You can only take a turn during the IN_PROGRESS phase");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        }
    }

    /**
     * Handles the player's Rock/Paper/Scissors choice
     * 
     * @param currentUser the player making the choice
     * @param choice      the choice ("r", "p", "s", or other game mode specific
     *                    choices)
     */
    protected void handlePlayerPick(ServerThread currentUser, String choice) {
        try {
            checkPlayerInRoom(currentUser);
            checkCurrentPhase(currentUser, Phase.IN_PROGRESS);
            checkIsReady(currentUser);

            // Store the choice in the user object
            currentUser.setChoice(choice);

            // Notify all players in the room that this player made a pick
            sendGameEvent(String.format("%s has selected their choice", currentUser.getDisplayName()));

            // Check if all ready players have made their picks
            if (allReadyPlayersHavePicked()) {
                onRoundEnd();
            }
        } catch (PlayerNotFoundException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to make a pick");
            LoggerUtil.INSTANCE.severe("handlePlayerPick exception", e);
        } catch (PhaseMismatchException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID,
                    "You can only make a pick during the IN_PROGRESS phase");
            LoggerUtil.INSTANCE.severe("handlePlayerPick exception", e);
        } catch (NotReadyException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be ready to make a pick");
            LoggerUtil.INSTANCE.severe("handlePlayerPick exception", e);
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handlePlayerPick exception", e);
        }
    }

    /**
     * Checks if all ready players have made their picks
     * 
     * @return true if all ready players have choices, false otherwise
     */
    private boolean allReadyPlayersHavePicked() {
        for (ServerThread player : clientsInRoom.values()) {
            if (player.isReady() && !player.isSpectator() && !player.isEliminated() && !player.isAway()) {
                String choice = player.getChoice();
                if (choice == null || choice.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Handles game mode change from session creator
     * Broadcasts the new game mode and cooldown settings to all clients
     * 
     * @param currentUser the player attempting to change the game mode
     * @param payload     the GameModePayload containing the new mode and cooldown
     *                    setting
     */
    protected void handleGameModeChange(ServerThread currentUser, GameModePayload payload) {
        try {
            checkPlayerInRoom(currentUser);

            // Only allow game mode changes during the READY phase
            if (currentPhase != Phase.READY) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID,
                        "Game mode can only be changed during the READY phase");
                return;
            }

            // Update the game mode and cooldown settings
            this.gameMode = payload.getGameMode();
            this.cooldownEnabled = payload.isCooldownEnabled();

            // Broadcast the change to all clients in the room
            broadcastGameModeChange(gameMode, cooldownEnabled);

            LoggerUtil.INSTANCE
                    .info(String.format("Game mode changed to %s (cooldown=%b)", gameMode.name(), cooldownEnabled));
        } catch (PlayerNotFoundException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in the GameRoom to change the game mode");
            LoggerUtil.INSTANCE.severe("handleGameModeChange exception", e);
        } catch (Exception e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "Error changing game mode");
            LoggerUtil.INSTANCE.severe("handleGameModeChange exception", e);
        }
    }

    /**
     * Broadcasts game mode change to all clients
     * 
     * @param gameMode        the new game mode
     * @param cooldownEnabled whether cooldown is enabled
     */
    private void broadcastGameModeChange(GameMode gameMode, boolean cooldownEnabled) {
        GameModePayload payload = new GameModePayload();
        payload.setGameMode(gameMode);
        payload.setCooldownEnabled(cooldownEnabled);
        clientsInRoom.values().forEach(client -> {
            try {
                client.sendToClient(payload);
            } catch (Exception e) {
                LoggerUtil.INSTANCE.severe("Error broadcasting game mode change", e);
            }
        });
    }

    // end receive data from ServerThread (GameRoom specific)
}
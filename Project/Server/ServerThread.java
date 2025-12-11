package Project.Server;

import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import Project.Common.TextFX.Color;
import Project.Common.TimerPayload;
import Project.Common.TimerType;
import Project.Common.ConnectionPayload;
import Project.Common.Constants;
import Project.Common.GameModePayload;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.Phase;
import Project.Common.PointsPayload;
import Project.Common.ReadyPayload;
import Project.Common.RoomAction;
import Project.Common.RoomResultPayload;
import Project.Common.TextFX;
import Project.Exceptions.RoomNotFoundException;

/**
 * A server-side representation of a single client
 */
public class ServerThread extends BaseServerThread {
    private Consumer<ServerThread> onInitializationComplete; // callback to inform when this object is ready

    /**
     * A wrapper method so we don't need to keep typing out the long/complex sysout
     * line inside
     * 
     * @param message
     */
    @Override
    protected void info(String message) {
        LoggerUtil.INSTANCE
                .info(TextFX.colorize(String.format("Thread[%s]: %s", this.getClientId(), message), Color.CYAN));
    }

    /**
     * Wraps the Socket connection and takes a Server reference and a callback
     * 
     * @param myClient
     * @param server
     * @param onInitializationComplete method to inform listener that this object is
     *                                 ready
     */
    protected ServerThread(Socket myClient, Consumer<ServerThread> onInitializationComplete) {
        Objects.requireNonNull(myClient, "Client socket cannot be null");
        Objects.requireNonNull(onInitializationComplete, "callback cannot be null");
        info("ServerThread created");
        // get communication channels to single client
        this.client = myClient;
        // this.clientId = this.threadId(); // An id associated with the thread
        // instance, used as a temporary identifier
        this.onInitializationComplete = onInitializationComplete;

    }

    // Start Send*() Methods
    /**
     * Syncs a specific client's points
     * 
     * @param clientId
     * @param points
     * @return
     */
    public boolean sendPlayerPoints(long clientId, int points) {
        PointsPayload rp = new PointsPayload();
        rp.setPoints(points);
        rp.setClientId(clientId);
        return sendToClient(rp);
    }

    public boolean sendGameEvent(String str) {
        return sendMessage(Constants.GAME_EVENT_CHANNEL, str);
    }

    /**
     * Syncs the current time of a specific TimerType
     * 
     * @param timerType
     * @param time
     * @return
     */
    public boolean sendCurrentTime(TimerType timerType, int time) {
        TimerPayload tp = new TimerPayload();
        tp.setTime(time);
        tp.setTimerType(timerType);
        return sendToClient(tp);
    }

    public boolean sendResetTurnStatus() {
        ReadyPayload rp = new ReadyPayload();
        rp.setPayloadType(PayloadType.RESET_TURN);
        return sendToClient(rp);
    }

    public boolean sendTurnStatus(long clientId, boolean didTakeTurn) {
        return sendTurnStatus(clientId, didTakeTurn, false);
    }

    public boolean sendTurnStatus(long clientId, boolean didTakeTurn, boolean quiet) {
        // NOTE for now using ReadyPayload as it has the necessary properties
        // An actual turn may include other data for your project
        ReadyPayload rp = new ReadyPayload();
        rp.setPayloadType(quiet ? PayloadType.SYNC_TURN : PayloadType.TURN);
        rp.setClientId(clientId);
        rp.setReady(didTakeTurn);
        return sendToClient(rp);
    }

    public boolean sendCurrentPhase(Phase phase) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.PHASE);
        p.setMessage(phase.name());
        return sendToClient(p);
    }

    public boolean sendResetReady() {
        ReadyPayload rp = new ReadyPayload();
        rp.setPayloadType(PayloadType.RESET_READY);
        return sendToClient(rp);
    }

    public boolean sendReadyStatus(long clientId, boolean isReady) {
        return sendReadyStatus(clientId, isReady, false);
    }

    /**
     * Sync ready status of client id
     * 
     * @param clientId who
     * @param isReady  ready or not
     * @param quiet    silently mark ready
     * @return
     */
    public boolean sendReadyStatus(long clientId, boolean isReady, boolean quiet) {
        ReadyPayload rp = new ReadyPayload();
        rp.setClientId(clientId);
        rp.setReady(isReady);
        if (quiet) {
            rp.setPayloadType(PayloadType.SYNC_READY);
        }
        return sendToClient(rp);
    }

    public boolean sendRooms(List<String> rooms) {
        RoomResultPayload rrp = new RoomResultPayload();
        rrp.setRooms(rooms);
        return sendToClient(rrp);
    }

    protected boolean sendDisconnect(long clientId) {
        Payload payload = new Payload();
        payload.setClientId(clientId);
        payload.setPayloadType(PayloadType.DISCONNECT);
        return sendToClient(payload);
    }

    protected boolean sendResetUserList() {
        return sendClientInfo(Constants.DEFAULT_CLIENT_ID, null, null, RoomAction.JOIN);
    }

    /**
     * Syncs Client Info (id, name, join status) to the client
     * 
     * @param clientId   use -1 for reset/clear
     * @param clientName
     * @param action     RoomAction of Join or Leave
     * @return true for successful send
     */
    protected boolean sendClientInfo(long clientId, String clientName, String roomName, RoomAction action) {
        return sendClientInfo(clientId, clientName, roomName, action, false);
    }

    /**
     * Syncs Client Info (id, name, join status) to the client
     * 
     * @param clientId   use -1 for reset/clear
     * @param clientName
     * @param action     RoomAction of Join or Leave
     * @param isSync     True is used to not show output on the client side (silent
     *                   sync)
     * @return true for successful send
     */
    protected boolean sendClientInfo(long clientId, String clientName, String roomName, RoomAction action,
            boolean isSync) {
        return sendClientInfo(clientId, clientName, roomName, action, false, isSync);
    }

    /**
     * Syncs Client Info (id, name, join status) to the client including spectator
     * flag and sync option
     */
    protected boolean sendClientInfo(long clientId, String clientName, String roomName, RoomAction action,
            boolean isSpectator, boolean isSync) {
        ConnectionPayload payload = new ConnectionPayload();
        switch (action) {
            case JOIN:
                payload.setPayloadType(PayloadType.ROOM_JOIN);
                break;
            case LEAVE:
                payload.setPayloadType(PayloadType.ROOM_LEAVE);
                break;
            default:
                break;
        }
        if (isSync) {
            payload.setPayloadType(PayloadType.SYNC_CLIENT);
        }
        payload.setClientId(clientId);
        payload.setClientName(clientName);
        payload.setMessage(roomName);
        payload.setSpectator(isSpectator);
        return sendToClient(payload);
    }

    /**
     * Sends this client's id to the client.
     * This will be a successfully connection handshake
     * 
     * @return true for successful send
     */
    protected boolean sendClientId() {
        ConnectionPayload payload = new ConnectionPayload();
        payload.setPayloadType(PayloadType.CLIENT_ID);
        payload.setClientId(getClientId());
        payload.setClientName(getClientName());// Can be used as a Server-side override of username (i.e., profanity
                                               // filter)
        return sendToClient(payload);
    }

    /**
     * Sends a message to the client
     * 
     * @param clientId who it's from
     * @param message
     * @return true for successful send
     */
    protected boolean sendMessage(long clientId, String message) {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.MESSAGE);
        payload.setMessage(message);
        payload.setClientId(clientId);
        return sendToClient(payload);
    }

    /**
     * Sends elimination status for a specific client
     * 
     * @param clientId   the client whose status changed
     * @param eliminated true if eliminated, false if restored
     * @return true for successful send
     */
    public boolean sendEliminationStatus(long clientId, boolean eliminated) {
        Project.Common.EliminationPayload ep = new Project.Common.EliminationPayload();
        ep.setClientId(clientId);
        ep.setEliminated(eliminated);
        return sendToClient(ep);
    }

    // End Send*() Methods
    @Override
    protected void processPayload(Payload incoming) {

        switch (incoming.getPayloadType()) {
            case CLIENT_CONNECT:
                setClientName(((ConnectionPayload) incoming).getClientName().trim());

                break;
            case DISCONNECT:
                currentRoom.handleDisconnect(this);
                break;
            case MESSAGE:
                currentRoom.handleMessage(this, incoming.getMessage());
                break;
            case REVERSE:
                currentRoom.handleReverseText(this, incoming.getMessage());
                break;
            case ROOM_CREATE:
                currentRoom.handleCreateRoom(this, incoming.getMessage());
                break;
            case ROOM_JOIN:
                currentRoom.handleJoinRoom(this, incoming.getMessage());
                break;
            case SPECTATOR_JOIN:
                // Client asked to join as a spectator; delegate to Server to mark spectator
                try {
                    Server.INSTANCE.joinRoom(incoming.getMessage(), this, true);
                } catch (RoomNotFoundException e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID,
                            String.format("Room %s doesn't exist", incoming.getMessage()));
                }
                break;
            case ROOM_LEAVE:
                currentRoom.handleJoinRoom(this, Room.LOBBY);
                break;
            case ROOM_LIST:
                currentRoom.handleListRooms(this, incoming.getMessage());
                break;
            case READY:
                // no data needed as the intent will be used as the trigger
                try {
                    // cast to GameRoom as the subclass will handle all Game logic
                    ((GameRoom) currentRoom).handleReady(this);
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to do the ready check");
                }
                break;
            case TURN:
                // no data needed as the intent will be used as the trigger
                try {
                    // cast to GameRoom as the subclass will handle all Game logic
                    ((GameRoom) currentRoom).handleTurnAction(this, incoming.getMessage());
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to do a turn");
                }
                break;
            case PLAYER_PICK:
                // Player sends their Rock/Paper/Scissors choice
                try {
                    ((GameRoom) currentRoom).handlePlayerPick(this, incoming.getMessage());
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to make a pick");
                }
                break;
            case GAME_MODE:
                // Session creator selecting game mode (RPS-3, RPS-5, etc)
                try {
                    ((GameRoom) currentRoom).handleGameModeChange(this, (GameModePayload) incoming);
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "Error setting game mode");
                }
                break;
            case AWAY:
                // Client toggled away status
                try {
                    if (currentRoom != null) {
                        currentRoom.handleAway(this, (Project.Common.AwayPayload) incoming);
                    }
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "Error setting away status");
                }
                break;
            default:
                LoggerUtil.INSTANCE.warning(TextFX.colorize("Unknown payload type received", Color.RED));
                break;
        }
    }

    // user state is exposed by BaseServerThread; no overrides here.

    @Override
    protected void onInitialized() {
        // once receiving the desired client name the object is ready
        onInitializationComplete.accept(this);
    }
}
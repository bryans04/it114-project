package Project.Client.Interfaces;

import java.util.List;

/**
 * Interface for handling room events.
 */
public interface IRoomEvents extends IClientEvents {
    /**
     * Received room list from server.
     *
     * @param rooms   List of rooms or null if error.
     * @param message A message related to the action, may be null (usually if
     *                rooms.length > 0).
     */
    void onReceiveRoomList(List<String> rooms, String message);

    /**
     * Receives the room name when the client is added to the room.
     *
     * @param clientId    The client ID.
     * @param roomName    The room name.
     * @param isJoin      True if joining, false if leaving.
     * @param isQuiet     True if this is a sync/quiet event.
     * @param isSpectator True if the client is a spectator.
     */
    void onRoomAction(long clientId, String roomName, boolean isJoin, boolean isQuiet, boolean isSpectator);
}
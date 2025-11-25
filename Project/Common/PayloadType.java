package Project.Common;

public enum PayloadType {
    CLIENT_CONNECT, // client requesting to connect to server (passing of initialization data
                    // [name])
    CLIENT_ID, // server sending client id
    SYNC_CLIENT, // silent syncing of clients in room
    DISCONNECT, // distinct disconnect action
    ROOM_CREATE,
    ROOM_JOIN,
    ROOM_LEAVE,
    REVERSE,
    MESSAGE, // sender and message,
    ROOM_LIST, // list of rooms

    ROUND_START,      // when a new round begins
    PLAYER_PICK,      // when player picks r/p/s
    POINTS_UPDATE,    // syncing points to clients
    ROUND_END,        // round finished
    SESSION_END,      // session finished
    ELIMINATION,      // player eliminated notification
    BATTLE_RESULT,    // results of battles in a round
    READY_CHECK       // ready check trigger
}
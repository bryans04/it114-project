package Project.Common;

/**
 * Represents the different phases of a game session.
 * Controls what actions clients and servers can perform.
 */
public enum Phase {
    READY,      // Players are preparing/waiting to start a session
    IN_PROGRESS // Game/session is actively running
}

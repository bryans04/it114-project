package Project.Client.Interfaces;

/**
 * Events related to player awareness changes (away status).
 */
public interface IAwarenessEvent extends IGameEvents {
    /**
     * Notifies that a player's away status changed.
     *
     * @param clientId the player id
     * @param isAway   true if player marked away
     */
    void onAwayStatusChanged(long clientId, boolean isAway);
}

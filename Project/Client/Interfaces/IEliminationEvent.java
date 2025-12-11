package Project.Client.Interfaces;

public interface IEliminationEvent {
    /**
     * Called when a player's elimination status changes
     * 
     * @param clientId   the client whose status changed
     * @param eliminated true if eliminated, false if restored
     */
    void onPlayerEliminated(long clientId, boolean eliminated);
}

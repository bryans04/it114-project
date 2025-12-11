package Project.Common;

public class ConnectionPayload extends Payload {
    private String clientName;
    private boolean spectator = false;

    /**
     * @return the clientName
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * @param clientName the clientName to set
     */
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }

    @Override
    public String toString() {
        return super.toString() +
            String.format(" ClientName: [%s] spectator=%b",
                getClientName(), spectator);
    }

}
package Project.Common;

public class EliminationPayload extends Payload {
    private long clientId;
    private boolean eliminated;

    public EliminationPayload() {
        setPayloadType(PayloadType.ELIMINATION);
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    @Override
    public String toString() {
        return String.format("EliminationPayload[clientId=%d, eliminated=%b]", clientId, eliminated);
    }
}

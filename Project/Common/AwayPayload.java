package Project.Common;

public class AwayPayload extends Payload {
    private boolean away = false;

    public AwayPayload() {
        setPayloadType(PayloadType.AWAY);
    }

    public boolean isAway() {
        return away;
    }

    public void setAway(boolean away) {
        this.away = away;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(" away=%b", away);
    }
}

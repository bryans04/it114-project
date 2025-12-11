package Project.Common;

public class ReadyPayload extends Payload {
    private boolean isReady;

    public ReadyPayload() {
        setPayloadType(PayloadType.READY);
    }

    /**
     * @return the isReady
     */
    public boolean isReady() {
        return isReady;
    }

    /**
     * @param isReady the isReady to set
     */
    public void setReady(boolean isReady) {
        this.isReady = isReady;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(" isReady=%b", isReady);
    }
}

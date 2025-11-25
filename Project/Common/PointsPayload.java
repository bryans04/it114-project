package Project.Common;

public class PointsPayload extends Payload {
    private int points;

    public PointsPayload(long clientId, int points) {
        setClientId(clientId);
        setPoints(points);
        setPayloadType(PayloadType.POINTS_UPDATE);
        setMessage("Points updated");
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    //bs768, 11/24/2025, toString method for PointsPayload
    @Override
    public String toString() {
        return String.format("PointsPayload[ClientId=%d, Points=%d]", getClientId(), points);
    }
}

package Project.Common;

public class PayloadTest {
    public static void main(String[] args) {
        PointsPayload payload = new PointsPayload(12345L, 10);
        System.out.println(payload.toString());
    }
}

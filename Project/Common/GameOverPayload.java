package Project.Common;

import java.util.List;

/**
 * Payload sent by the server when a game/session ends to announce the winner(s).
 */
public class GameOverPayload extends Payload {
    private List<String> winners;
    private String message;

    public GameOverPayload() {
        setPayloadType(PayloadType.GAME_OVER);
    }

    public List<String> getWinners() {
        return winners;
    }

    public void setWinners(List<String> winners) {
        this.winners = winners;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(" winners=%s message=%s", winners, message);
    }
}

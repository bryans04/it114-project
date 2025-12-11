package Project.Common;

public class GameModePayload extends Payload {
    private GameMode gameMode;
    private boolean cooldownEnabled = false;

    public GameModePayload() {
        setPayloadType(PayloadType.GAME_MODE);
    }

    /**
     * @return the gameMode
     */
    public GameMode getGameMode() {
        return gameMode;
    }

    /**
     * @param gameMode the gameMode to set
     */
    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    /**
     * @return whether the "no repeat" cooldown is enabled
     */
    public boolean isCooldownEnabled() {
        return cooldownEnabled;
    }

    /**
     * @param cooldownEnabled enable/disable the per-option cooldown
     */
    public void setCooldownEnabled(boolean cooldownEnabled) {
        this.cooldownEnabled = cooldownEnabled;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(" gameMode=%s cooldown=%b", gameMode, cooldownEnabled);
    }
}

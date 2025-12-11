package Project.Client.Interfaces;

import Project.Common.GameMode;

public interface IGameModeEvent extends IGameEvents {
    /**
     * Called when the session's game mode changes.
     *
     * @param gameMode the new game mode
     * @param cooldownEnabled whether per-option cooldown (no-repeat) is enabled
     */
    void onGameModeChange(GameMode gameMode, boolean cooldownEnabled);
}

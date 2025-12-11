package Project.Client;

public enum CardViewName {
    CONNECT, USER_INFO, READY, CHAT, ROOMS, CHAT_GAME_SCREEN, GAME_SCREEN;

    public static boolean viewRequiresConnection(CardViewName check) {
        return check.ordinal() >= CardViewName.READY.ordinal();
    }
}

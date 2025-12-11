package Project.Common;

public enum GameMode {
    RPS_3("rps3", new String[]{"r", "p", "s"}, new String[]{"🪨 Rock", "📄 Paper", "✂️ Scissors"}),
    RPS_5("rps5", new String[]{"r", "p", "s", "l", "k"}, new String[]{"🪨 Rock", "📄 Paper", "✂️ Scissors", "🦎 Lizard", "🖖 Spock"});

    private final String mode;
    private final String[] choices;
    private final String[] displays;

    GameMode(String mode, String[] choices, String[] displays) {
        this.mode = mode;
        this.choices = choices;
        this.displays = displays;
    }

    /**
     * @return the mode identifier
     */
    public String getMode() {
        return mode;
    }

    /**
     * @return the array of valid choice codes (r, p, s, l, k)
     */
    public String[] getChoices() {
        return choices;
    }

    /**
     * @return the array of display strings with emojis
     */
    public String[] getDisplays() {
        return displays;
    }

    /**
     * Get the number of options in this game mode
     */
    public int getOptionCount() {
        return choices.length;
    }

    /**
     * Get the display string for a given choice code
     */
    public String getDisplay(String choice) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].equals(choice)) {
                return displays[i];
            }
        }
        return choice;
    }

    /**
     * Check if a choice is valid in this game mode
     */
    public boolean isValidChoice(String choice) {
        for (String c : choices) {
            if (c.equals(choice)) {
                return true;
            }
        }
        return false;
    }
}

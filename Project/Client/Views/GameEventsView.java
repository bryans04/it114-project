package Project.Client.Views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.JCheckBox;

import Project.Client.Client;
import Project.Client.Interfaces.IGameModeEvent;
import Project.Client.Interfaces.ITurnEvent;
import Project.Client.Interfaces.IMessageEvents;
import Project.Client.Interfaces.IPhaseEvent;
import Project.Client.Interfaces.IReadyEvent;
import Project.Client.Interfaces.ITimeEvents;
import Project.Common.Constants;
import Project.Common.GameMode;
import Project.Common.LoggerUtil;
import Project.Common.Phase;
import Project.Common.TimerType;

public class GameEventsView extends JPanel
        implements IPhaseEvent, IReadyEvent, IMessageEvents, ITimeEvents, ITurnEvent, IGameModeEvent,
        Project.Client.Interfaces.IEliminationEvent {
    private final JPanel content;
    private final boolean debugMode = true; // Set this to false to disable debugging styling
    private final JLabel timerText;
    private final GridBagConstraints gbcGlue = new GridBagConstraints();
    private final JPanel buttonPanel;
    private boolean gameActive = false;
    private GameMode currentGameMode = GameMode.RPS_3;
    private boolean cooldownEnabled = false;
    private String lastLocalChoice = null;
    private boolean isEliminated = false;
    private boolean isSpectator = false;
    private final Map<String, JButton> choiceButtonMap = new HashMap<>();
    private JCheckBox awayCheckbox;

    public GameEventsView() {
        super(new BorderLayout(10, 10));

        // Top panel: Timer
        JPanel topPanel = new JPanel(new BorderLayout());
        timerText = new JLabel("Round timer: -- ");
        timerText.setFont(timerText.getFont().deriveFont(12f));
        timerText.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        timerText.setPreferredSize(new Dimension(200, 20));
        timerText.setBorder(new EmptyBorder(5, 5, 5, 5));
        topPanel.add(timerText, BorderLayout.CENTER);
        timerText.setVisible(false);
        // Away toggle allows player to mark themselves away (skips turns)
        awayCheckbox = new JCheckBox("Mark me away (skip turns)");
        awayCheckbox.addActionListener(evt -> {
            boolean isAway = awayCheckbox.isSelected();
            try {
                // optimistic local update
                Client.INSTANCE.sendAway(isAway);
                // disable own choice buttons when away
                for (JButton b : choiceButtonMap.values()) {
                    b.setEnabled(!isAway);
                }
            } catch (IOException e) {
                LoggerUtil.INSTANCE.severe("Error toggling away", e);
                awayCheckbox.setSelected(!isAway); // revert on error
            }
        });
        topPanel.add(awayCheckbox, BorderLayout.EAST);
        this.add(topPanel, BorderLayout.NORTH);

        // Center panel: Event log/battle log
        content = new JPanel(new GridBagLayout());
        if (debugMode) {
            content.setBorder(BorderFactory.createLineBorder(Color.RED));
            content.setBackground(new Color(240, 240, 240));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        if (debugMode) {
            scroll.setBorder(BorderFactory.createLineBorder(Color.GREEN));
        } else {
            scroll.setBorder(BorderFactory.createEmptyBorder());
        }
        this.add(scroll, BorderLayout.CENTER);

        // Add vertical glue to push messages to the top
        gbcGlue.gridx = 0;
        gbcGlue.gridy = GridBagConstraints.RELATIVE;
        gbcGlue.weighty = 1.0;
        gbcGlue.fill = GridBagConstraints.BOTH;
        content.add(Box.createVerticalGlue(), gbcGlue);

        // Bottom panel: RPS choice buttons (dynamically generated)
        buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        buttonPanel.setVisible(false); // Hidden until game is active

        regenerateButtons(GameMode.RPS_3);

        this.add(buttonPanel, BorderLayout.SOUTH);

        Client.INSTANCE.registerCallback(this);
    }

    /**
     * Regenerates choice buttons based on the current game mode
     */
    private void regenerateButtons(GameMode gameMode) {
        buttonPanel.removeAll();

        String[] displays = gameMode.getDisplays();
        String[] choices = gameMode.getChoices();

        // Use GridLayout for RPS-5 (2 rows), BoxLayout for RPS-3 (1 row)
        if (displays.length > 3) {
            // RPS-5: 2 rows, 3 columns
            buttonPanel.setLayout(new java.awt.GridLayout(2, 3, 10, 10));
        } else {
            // RPS-3: 1 row
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        }

        choiceButtonMap.clear();

        if (displays.length <= 3) {
            buttonPanel.add(Box.createHorizontalGlue());
        }

        for (int i = 0; i < displays.length; i++) {
            String display = displays[i];
            String choice = choices[i];

            JButton choiceButton = new JButton(display);
            choiceButton.setToolTipText("Click to choose " + display);
            choiceButton.addActionListener(_ -> onPickChoice(choice));
            // If cooldown is enabled and this is the last local choice, disable it for this
            // player
            if (cooldownEnabled && lastLocalChoice != null && lastLocalChoice.equals(choice)) {
                choiceButton.setEnabled(false);
            }
            choiceButtonMap.put(choice, choiceButton);
            buttonPanel.add(choiceButton);

            // honor away checkbox state: disable buttons if this player marked away
            if (awayCheckbox != null && awayCheckbox.isSelected()) {
                choiceButton.setEnabled(false);
            }

            // Add spacing between buttons for BoxLayout only
            if (displays.length <= 3 && i < displays.length - 1) {
                buttonPanel.add(Box.createHorizontalStrut(10));
            }
        }

        if (displays.length <= 3) {
            buttonPanel.add(Box.createHorizontalGlue());
        }

        buttonPanel.revalidate();
        buttonPanel.repaint();
    }

    /**
     * Handles RPS choice button click
     */
    private void onPickChoice(String choice) {
        try {
            if (isSpectator) {
                LoggerUtil.INSTANCE.warning("Cannot pick - player is spectator");
                return;
            }

            if (isEliminated) {
                LoggerUtil.INSTANCE.warning("Cannot pick - player is eliminated");
                return;
            }

            LoggerUtil.INSTANCE.info("Player chose: " + choice.toUpperCase());
            if (cooldownEnabled && lastLocalChoice != null && lastLocalChoice.equals(choice)) {
                LoggerUtil.INSTANCE.warning("Choice on cooldown: " + choice);
                return;
            }

            Client.INSTANCE.sendPick(choice);
            lastLocalChoice = choice;
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Error sending pick", e);
        }
    }

    public void addText(String text) {
        SwingUtilities.invokeLater(() -> {
            JEditorPane textContainer = new JEditorPane("text/plain", text);
            textContainer.setEditable(false);
            if (debugMode) {
                textContainer.setBorder(BorderFactory.createLineBorder(Color.BLUE));
                textContainer.setBackground(new Color(255, 255, 200));
            } else {
                textContainer.setBorder(BorderFactory.createEmptyBorder());
                textContainer.setBackground(new Color(0, 0, 0, 0));
            }
            textContainer.setText(text);
            int width = content.getWidth() > 0 ? content.getWidth() : 200;
            Dimension preferredSize = textContainer.getPreferredSize();
            textContainer.setPreferredSize(new Dimension(width, preferredSize.height));
            // Remove glue if present
            int lastIdx = content.getComponentCount() - 1;
            if (lastIdx >= 0 && content.getComponent(lastIdx) instanceof Box.Filler) {
                content.remove(lastIdx);
            }
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = content.getComponentCount() - 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 0, 5, 0);
            content.add(textContainer, gbc);
            content.add(Box.createVerticalGlue(), gbcGlue);
            content.revalidate();
            content.repaint();
            JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, content);
            if (parentScrollPane != null) {
                SwingUtilities.invokeLater(() -> {
                    JScrollBar vertical = parentScrollPane.getVerticalScrollBar();
                    vertical.setValue(vertical.getMaximum());
                });
            }
        });
    }

    @Override
    public void onReceivePhase(Phase phase) {
        gameActive = (phase == Phase.IN_PROGRESS);
        if (gameActive) {
            // Regenerate buttons with current game mode when game starts
            regenerateButtons(currentGameMode);
        }
        // Reset elimination state when returning to READY phase (new game starting)
        if (phase == Phase.READY) {
            isEliminated = false;
            lastLocalChoice = null; // Clear cooldown state for new game
            // Re-enable buttons for the new game
            SwingUtilities.invokeLater(() -> {
                choiceButtonMap.values().forEach(button -> button.setEnabled(true));
            });
        }
        buttonPanel.setVisible(gameActive);
        addText(String.format("--- Phase: %s ---", phase.name()));
    }

    @Override
    public void onReceiveReady(long clientId, boolean isReady, boolean isQuiet) {
        if (isQuiet) {
            return;
        }
        String displayName = Client.INSTANCE.getDisplayNameFromId(clientId);
        addText(String.format("%s is %s", displayName, isReady ? "ready" : "not ready"));
    }

    @Override
    public void onMessageReceive(long id, String message) {
        if (id == Constants.GAME_EVENT_CHANNEL) {
            addText(message);
        }
    }

    @Override
    public void onTimerUpdate(TimerType timerType, int time) {
        if (time >= 0) {
            String timerDisplay = String.format("⏱️  Round Timer: %02d seconds remaining", time);
            timerText.setText(timerDisplay);
            timerText.setVisible(true);
        } else {
            timerText.setText("");
            timerText.setVisible(false);
        }
    }

    @Override
    public void onGameModeChange(GameMode gameMode, boolean cooldown) {
        this.currentGameMode = gameMode;
        this.cooldownEnabled = cooldown;
        regenerateButtons(gameMode);
        addText(String.format("--- Game Mode Changed to: %s (%d options) ---",
                gameMode.name(), gameMode.getOptionCount()));
    }

    @Override
    public void onTookTurn(long clientId, boolean didtakeTurn) {
        // Server signals reset of turns with DEFAULT_CLIENT_ID + didtakeTurn=false
        if (clientId == Constants.DEFAULT_CLIENT_ID && !didtakeTurn) {
            // New round starting: re-enable all buttons first, then disable last choice if
            // cooldown enabled
            for (JButton btn : choiceButtonMap.values()) {
                btn.setEnabled(true);
            }

            // If cooldown is enabled, disable the last local choice
            if (cooldownEnabled && lastLocalChoice != null) {
                JButton btn = choiceButtonMap.get(lastLocalChoice);
                if (btn != null) {
                    btn.setEnabled(false);
                }
            }

            // Re-apply away status if player is marked away
            if (awayCheckbox != null && awayCheckbox.isSelected()) {
                for (JButton b : choiceButtonMap.values()) {
                    b.setEnabled(false);
                }
            }
        }
    }

    @Override
    public void onPlayerEliminated(long clientId, boolean eliminated) {
        // Check if this is the local player
        if (Client.INSTANCE.isMyClientId(clientId)) {
            isEliminated = eliminated;
            // Disable all choice buttons if eliminated
            SwingUtilities.invokeLater(() -> {
                choiceButtonMap.values().forEach(button -> {
                    button.setEnabled(!eliminated);
                });
                if (eliminated) {
                    addText("You have been eliminated! You can no longer make picks.");
                } else {
                    // Re-enable buttons (e.g., when new game starts)
                    addText("You are back in the game!");
                }
            });
        }
    }

    public void setSpectator(boolean spectator) {
        this.isSpectator = spectator;
        if (spectator) {
            for (JButton btn : choiceButtonMap.values()) {
                btn.setEnabled(false);
            }
            if (awayCheckbox != null) {
                awayCheckbox.setEnabled(false);
            }
        }
    }
}

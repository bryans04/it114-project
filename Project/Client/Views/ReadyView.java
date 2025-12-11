package Project.Client.Views;

import java.awt.BorderLayout;
import java.io.IOException;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import Project.Client.CardViewName;
import Project.Client.Client;
import Project.Client.Interfaces.ICardControls;
import Project.Client.Interfaces.IGameModeEvent;
import Project.Common.GameMode;
import Project.Common.LoggerUtil;
import Project.Common.TextFX;
import Project.Common.TextFX.Color;

public class ReadyView extends JPanel implements IGameModeEvent {
    private JButton readyButton;
    private JLabel statusLabel;
    private JComboBox<GameMode> gameModeCombo;
    private JLabel gameModeLabel;
    private JCheckBox cooldownCheckbox;
    private boolean isReady = false;
    private GameMode currentGameMode = GameMode.RPS_3;
    private boolean currentCooldown = false;
    private ICardControls controls;

    public ReadyView(ICardControls controls) {
        super(new BorderLayout(10, 10));
        this.controls = controls;

        // Set panel name and register for card navigation
        setName(CardViewName.READY.name());
        controls.registerView(CardViewName.READY.name(), this);

        // Main content panel
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title/status label
        statusLabel = new JLabel("Waiting for players...");
        statusLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        content.add(statusLabel);

        content.add(Box.createVerticalStrut(20));

        // Game mode selector (for session creator)
        JPanel gameModePanel = new JPanel();
        gameModePanel.setLayout(new BoxLayout(gameModePanel, BoxLayout.Y_AXIS));
        gameModePanel.setBorder(new TitledBorder("Game Mode (Session Creator Only)"));
        gameModePanel.setAlignmentX(JPanel.CENTER_ALIGNMENT);

        // Dropdown row
        JPanel dropdownRow = new JPanel();
        dropdownRow.setLayout(new BoxLayout(dropdownRow, BoxLayout.X_AXIS));
        dropdownRow.setAlignmentX(JPanel.CENTER_ALIGNMENT);
        dropdownRow.setMaximumSize(new java.awt.Dimension(300, 30));

        gameModeLabel = new JLabel("Select Mode:");
        gameModeCombo = new JComboBox<>(GameMode.values());
        gameModeCombo.setSelectedItem(GameMode.RPS_3);
        gameModeCombo.addActionListener(_ -> onGameModeChanged());
        gameModeCombo.setMaximumSize(new java.awt.Dimension(150, 25));

        dropdownRow.add(gameModeLabel);
        dropdownRow.add(Box.createHorizontalStrut(10));
        dropdownRow.add(gameModeCombo);

        cooldownCheckbox = new JCheckBox("Disable repeated picks (no-repeat / cooldown)");
        cooldownCheckbox.setSelected(false);
        cooldownCheckbox.setAlignmentX(JCheckBox.CENTER_ALIGNMENT);
        cooldownCheckbox.addActionListener(_ -> onGameModeChanged());

        gameModePanel.add(dropdownRow);
        gameModePanel.add(Box.createVerticalStrut(10));
        gameModePanel.add(cooldownCheckbox);

        content.add(gameModePanel);
        content.add(Box.createVerticalStrut(20));

        // Ready button
        readyButton = new JButton("Mark Ready");
        readyButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
        readyButton.addActionListener(_ -> onReadyButtonClicked());
        content.add(readyButton);

        add(content, BorderLayout.CENTER);
        // Register for incoming game mode updates so UI can stay in sync
        Client.INSTANCE.registerCallback(this);
    }

    /**
     * Handles the Ready button click: toggles ready status and sends to server
     */
    private void onReadyButtonClicked() {
        try {
            isReady = !isReady;
            Client.INSTANCE.sendReady();
            updateReadyUI();
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Error sending ready status", e);
            isReady = !isReady; // revert on error
            updateReadyUI();
        }
    }

    /**
     * Updates button and label appearance based on ready status
     */
    private void updateReadyUI() {
        if (isReady) {
            readyButton.setText("Not Ready");
            statusLabel.setText("You are ready. Waiting for other players...");
        } else {
            readyButton.setText("Mark Ready");
            statusLabel.setText("Waiting for players...");
        }
    }

    /**
     * Resets ready status when a new session starts or when returning from game
     */
    public void resetReady() {
        isReady = false;
        currentGameMode = GameMode.RPS_3;
        currentCooldown = false;
        gameModeCombo.setSelectedItem(GameMode.RPS_3);
        updateReadyUI();
    }

    /**
     * Handles game mode selection change
     */
    private void onGameModeChanged() {
        try {
            GameMode selectedMode = (GameMode) gameModeCombo.getSelectedItem();
            boolean cooldown = cooldownCheckbox.isSelected();
            // send to server only if the mode or cooldown changed
            if (selectedMode != currentGameMode || cooldown != currentCooldown) {
                currentGameMode = selectedMode;
                currentCooldown = cooldown;
                Client.INSTANCE.sendGameMode(selectedMode, cooldown);
            }
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Error sending game mode", e);
            // revert to previous selection
            gameModeCombo.setSelectedItem(currentGameMode);
        }
    }

    @Override
    public void onGameModeChange(GameMode gameMode, boolean cooldownEnabled) {
        // Update UI to reflect authoritative session settings
        this.currentGameMode = gameMode;
        SwingUtilities.invokeLater(() -> {
            gameModeCombo.setSelectedItem(gameMode);
            cooldownCheckbox.setSelected(cooldownEnabled);
        });
    }
}

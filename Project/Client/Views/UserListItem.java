package Project.Client.Views;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * UserListItem represents a user entry in the user list.
 * Displays: username#id | status indicator | points | elimination/pending
 * badges
 */
public class UserListItem extends JPanel {
    private final JEditorPane textContainer;
    private final JPanel statusIndicator;
    private final JEditorPane pointsPanel;
    private final JLabel statusBadge;
    private final String displayName;
    private final long clientId;
    private boolean isEliminated = false;
    private boolean isPendingPick = false;
    private boolean isAway = false;

    /**
     * Constructor to create a UserListItem.
     *
     * @param clientId    The ID of the client.
     * @param displayName The name of the client.
     */
    public UserListItem(long clientId, String displayName) {
        this.displayName = displayName;
        this.clientId = clientId;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Name line with ID
        textContainer = new JEditorPane("text/html", this.displayName);
        textContainer.setName(Long.toString(clientId));
        textContainer.setEditable(false);
        textContainer.setBorder(new EmptyBorder(0, 0, 0, 0));
        textContainer.setOpaque(false);
        textContainer.setBackground(new Color(0, 0, 0, 0));
        add(textContainer);

        // Second line: status indicator + points + badge
        JPanel rowPanel = new JPanel();
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        rowPanel.setOpaque(false);

        // Status indicator (color dot)
        statusIndicator = new JPanel();
        statusIndicator.setPreferredSize(new Dimension(10, 10));
        statusIndicator.setMinimumSize(statusIndicator.getPreferredSize());
        statusIndicator.setMaximumSize(statusIndicator.getPreferredSize());
        statusIndicator.setOpaque(true);
        statusIndicator.setVisible(true);
        rowPanel.add(statusIndicator);
        rowPanel.add(Box.createHorizontalStrut(8));

        // Points display
        pointsPanel = new JEditorPane("text/html", "");
        pointsPanel.setEditable(false);
        pointsPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        pointsPanel.setOpaque(false);
        pointsPanel.setBackground(new Color(0, 0, 0, 0));
        rowPanel.add(pointsPanel);
        rowPanel.add(Box.createHorizontalStrut(15));

        // Status badge (Eliminated / Pending Pick)
        statusBadge = new JLabel("");
        statusBadge.setFont(statusBadge.getFont().deriveFont(9f));
        rowPanel.add(statusBadge);

        add(rowPanel);
        setPoints(-1);

        // Set maximum height to prevent vertical stretching
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
    }

    /**
     * Sets the ready/turn status indicator color
     * Mostly used to trigger a reset, but if used for a true value, it'll apply
     * Color.GREEN
     * 
     * @param didTakeTurn true if the user took their turn
     */
    public void setTurn(boolean didTakeTurn) {
        setTurn(didTakeTurn, Color.GREEN);
    }

    /**
     * Sets the indicator and color based on turn status
     * 
     * @param didTakeTurn if true, applies trueColor; otherwise applies transparent
     * @param trueColor   Color to apply when true
     */
    public void setTurn(boolean didTakeTurn, Color trueColor) {
        statusIndicator.setBackground(didTakeTurn ? trueColor : new Color(0, 0, 0, 0));
        statusIndicator.revalidate();
        statusIndicator.repaint();
        this.revalidate();
        this.repaint();
    }

    /**
     * Sets the points display for this user.
     * 
     * @param points the number of points, or <0 to hide
     */
    public void setPoints(int points) {
        if (points < 0) {
            pointsPanel.setText("0");
            pointsPanel.setVisible(false);
        } else {
            pointsPanel.setText("Pts: " + points);
            if (!pointsPanel.isVisible()) {
                pointsPanel.setVisible(true);
            }
        }
        repaint();
    }

    /**
     * Marks this player as eliminated
     */
    public void setEliminated(boolean eliminated) {
        this.isEliminated = eliminated;
        updateStatusBadge();
        if (eliminated) {
            textContainer.setForeground(Color.GRAY);
            // Strike through effect via HTML
            textContainer.setText("<html><strike>" + displayName + "</strike></html>");
        } else {
            textContainer.setForeground(Color.BLACK);
            textContainer.setText(displayName);
        }
        revalidate();
        repaint();
    }

    /**
     * Marks this player as away. Visual representation is grayed out and badge
     * shown.
     */
    public void setAway(boolean away) {
        this.isAway = away;
        updateStatusBadge();
        if (away) {
            textContainer.setForeground(Color.LIGHT_GRAY);
            textContainer.setText("<html><i>" + displayName + "</i></html>");
        } else {
            textContainer.setForeground(Color.BLACK);
            textContainer.setText(displayName);
        }
        revalidate();
        repaint();
    }

    /**
     * Marks this player as pending their pick
     */
    public void setPendingPick(boolean pending) {
        this.isPendingPick = pending;
        updateStatusBadge();
    }

    /**
     * Updates the status badge display based on current state
     */
    private void updateStatusBadge() {
        if (isEliminated) {
            statusBadge.setText("[ELIMINATED]");
            statusBadge.setForeground(Color.RED);
        } else if (isAway) {
            statusBadge.setText("[SITTING OUT]");
            statusBadge.setForeground(Color.GRAY);
        } else if (isPendingPick) {
            statusBadge.setText("[PENDING]");
            statusBadge.setForeground(Color.ORANGE);
        } else {
            statusBadge.setText("");
        }
        repaint();
    }

    /**
     * Returns the client ID for this user item
     */
    public long getClientId() {
        return clientId;
    }

    /**
     * Returns whether this player is eliminated
     */
    public boolean isEliminated() {
        return isEliminated;
    }

    /**
     * Returns the points for this user (used for sorting)
     */
    public int getPoints() {
        String text = pointsPanel.getText();
        if (text.isEmpty() || text.equals("0")) {
            return 0;
        }
        try {
            return Integer.parseInt(text.replace("Pts: ", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Returns the display name for this user (used for sorting)
     */
    public String getDisplayName() {
        return displayName;
    }
}
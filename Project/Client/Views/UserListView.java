package Project.Client.Views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.List;

import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import Project.Client.Client;
import Project.Client.Interfaces.IConnectionEvents;
import Project.Client.Interfaces.IPointsEvent;
import Project.Client.Interfaces.IReadyEvent;
import Project.Client.Interfaces.IRoomEvents;
import Project.Client.Interfaces.ITurnEvent;
import Project.Common.Constants;
import Project.Common.LoggerUtil;

/**
 * UserListView represents a UI component that displays a list of users.
 */
public class UserListView extends JPanel
        implements IConnectionEvents, IRoomEvents, IReadyEvent, IPointsEvent, ITurnEvent,
        Project.Client.Interfaces.IAwarenessEvent, Project.Client.Interfaces.IEliminationEvent {
    private final JPanel userListArea;
    private final GridBagConstraints lastConstraints; // Keep track of the last constraints for the glue
    private final HashMap<Long, UserListItem> userItemsMap; // Maintain a map of client IDs to UserListItems

    public UserListView() {
        super(new BorderLayout(10, 10));
        userItemsMap = new HashMap<>();

        JPanel content = new JPanel(new GridBagLayout());
        userListArea = content;

        JScrollPane scroll = new JScrollPane(userListArea);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        this.add(scroll, BorderLayout.CENTER);

        // Add vertical glue to push items to the top
        lastConstraints = new GridBagConstraints();
        lastConstraints.gridx = 0;
        lastConstraints.gridy = GridBagConstraints.RELATIVE;
        lastConstraints.weighty = 1.0;
        lastConstraints.fill = GridBagConstraints.VERTICAL;
        userListArea.add(Box.createVerticalGlue(), lastConstraints);
        Client.INSTANCE.registerCallback(this);
    }

    /**
     * Marks a player as away or not away
     *
     * @param clientId the player id
     * @param away     true to mark away
     */
    @Override
    public void onAwayStatusChanged(long clientId, boolean away) {
        if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.get(clientId).setAway(away);
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error updating away status", e);
                }
            });
        }
    }

    /**
     * Adds a user to the list.
     */
    private void addUserListItem(long clientId, String clientName) {
        SwingUtilities.invokeLater(() -> {
            if (userItemsMap.containsKey(clientId)) {
                LoggerUtil.INSTANCE.warning("User already in the list: " + clientName);
                return;
            }
            LoggerUtil.INSTANCE.info("Adding user to list: " + clientName);
            UserListItem userItem = new UserListItem(clientId, clientName);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = userListArea.getComponentCount() - 1;
            gbc.weightx = 1;
            gbc.weighty = 0; // Don't stretch vertically
            gbc.anchor = GridBagConstraints.NORTHWEST; // Align to top-left
            gbc.fill = GridBagConstraints.HORIZONTAL; // Only fill horizontally
            gbc.insets = new Insets(0, 0, 5, 5);
            // Remove the last glue component if it exists
            if (lastConstraints != null) {
                int index = userListArea.getComponentCount() - 1;
                if (index > -1) {
                    userListArea.remove(index);
                }
            }
            userListArea.add(userItem, gbc);
            userListArea.add(Box.createVerticalGlue(), lastConstraints);
            userItemsMap.put(clientId, userItem);
            userListArea.revalidate();
            userListArea.repaint();
        });
    }

    /**
     * Removes a user from the list.
     */
    private void removeUserListItem(long clientId) {
        SwingUtilities.invokeLater(() -> {
            LoggerUtil.INSTANCE.info("Removing user list item for id " + clientId);
            try {
                UserListItem item = userItemsMap.remove(clientId);
                if (item != null) {
                    userListArea.remove(item);
                    userListArea.revalidate();
                    userListArea.repaint();
                }
            } catch (Exception e) {
                LoggerUtil.INSTANCE.severe("Error removing user list item", e);
            }
        });
    }

    /**
     * Resorts the user list by points (descending) then by name (ascending)
     */
    private void resortUserList() {
        SwingUtilities.invokeLater(() -> {
            LoggerUtil.INSTANCE.info("Resorting user list");
            try {
                userListArea.removeAll();

                java.util.List<UserListItem> sortedItems = userItemsMap.values().stream()
                        .sorted((a, b) -> {
                            int pointCompare = Integer.compare(b.getPoints(), a.getPoints());
                            if (pointCompare != 0) {
                                return pointCompare;
                            }
                            return a.getDisplayName().compareTo(b.getDisplayName());
                        })
                        .collect(java.util.stream.Collectors.toList());

                // Add items with sequential gridy values
                int gridy = 0;
                for (UserListItem userItem : sortedItems) {
                    GridBagConstraints gbc = new GridBagConstraints();
                    gbc.gridx = 0;
                    gbc.gridy = gridy++;
                    gbc.weightx = 1;
                    gbc.weighty = 0; // Don't stretch vertically
                    gbc.anchor = GridBagConstraints.NORTHWEST; // Align to top-left
                    gbc.fill = GridBagConstraints.HORIZONTAL; // Only fill horizontally
                    gbc.insets = new Insets(0, 0, 2, 5); // Reduced vertical spacing
                    userListArea.add(userItem, gbc);
                }

                // Re-add the glue at the end to push items to top
                GridBagConstraints glueGbc = new GridBagConstraints();
                glueGbc.gridx = 0;
                glueGbc.gridy = gridy;
                glueGbc.weighty = 1.0;
                glueGbc.fill = GridBagConstraints.VERTICAL;
                userListArea.add(Box.createVerticalGlue(), glueGbc);

                userListArea.revalidate();
                userListArea.repaint();
            } catch (Exception e) {
                LoggerUtil.INSTANCE.severe("Error resorting user list", e);
            }
        });
    }

    /**
     * Clears the user list.
     */
    private void clearUserList() {
        SwingUtilities.invokeLater(() -> {
            LoggerUtil.INSTANCE.info("Clearing user list");
            try {
                userItemsMap.clear();
                userListArea.removeAll();
                userListArea.revalidate();
                userListArea.repaint();
            } catch (Exception e) {
                LoggerUtil.INSTANCE.severe("Error clearing user list", e);
            }
        });
    }

    @Override
    public void onReceiveRoomList(List<String> rooms, String message) {
        // unused
    }

    @Override
    public void onRoomAction(long clientId, String roomName, boolean isJoin, boolean isQuiet, boolean isSpectator) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            clearUserList();
            return;
        }
        String displayName = Client.INSTANCE.getDisplayNameFromId(clientId);
        if (isJoin) {
            addUserListItem(clientId, displayName);
            // Set spectator visual if applicable
            if (isSpectator && userItemsMap.containsKey(clientId)) {
                SwingUtilities.invokeLater(() -> {
                    userItemsMap.get(clientId).setSpectator(true);
                });
            }
        } else {
            removeUserListItem(clientId);
        }
    }

    @Override
    public void onClientDisconnect(long clientId) {
        removeUserListItem(clientId);
    }

    @Override
    public void onReceiveClientId(long id) {
        // unused
    }

    @Override
    public void onTookTurn(long clientId, boolean didtakeCurn) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            SwingUtilities.invokeLater(() -> {
                // Reset all turn statuses
                userItemsMap.values().forEach(u -> u.setTurn(false));
                // When turn status resets, everyone is pending their pick
                if (!didtakeCurn) {
                    userItemsMap.values().forEach(u -> u.setPendingPick(true));
                }
            });
        } else if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                userItemsMap.get(clientId).setTurn(didtakeCurn);
                // Clear pending status when player takes their turn
                if (didtakeCurn) {
                    userItemsMap.get(clientId).setPendingPick(false);
                }
            });
        }
    }

    @Override
    public void onPointsUpdate(long clientId, int points) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.values().forEach(u -> u.setPoints(-1));// reset all
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error resetting user items", e);
                }
            });
        } else if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.get(clientId).setPoints(points);
                    resortUserList(); // Resort after points change
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error setting user item", e);
                }

            });
        }
    }

    @Override
    public void onReceiveReady(long clientId, boolean isReady, boolean isQuiet) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.values().forEach(u -> u.setTurn(false));// reset all
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error resetting user items", e);
                }
            });
        } else if (userItemsMap.containsKey(clientId)) {

            SwingUtilities.invokeLater(() -> {
                try {
                    LoggerUtil.INSTANCE.info("Setting user item ready for id " + clientId + " to " + isReady);
                    userItemsMap.get(clientId).setTurn(isReady, Color.GRAY);
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error setting user item", e);
                }
            });
        }
    }

    /**
     * Interface method from IEliminationEvent
     * Called when a player's elimination status changes
     */
    @Override
    public void onPlayerEliminated(long clientId, boolean eliminated) {
        if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.get(clientId).setEliminated(eliminated);
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error updating elimination status", e);
                }
            });
        }
    }

    /**
     * Marks a player as eliminated (called during game phase)
     * 
     * @param clientId the player who is eliminated
     */
    public void markPlayerEliminated(long clientId) {
        if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.get(clientId).setEliminated(true);
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error marking player eliminated", e);
                }
            });
        }
    }

    /**
     * Marks a player as pending their pick (waiting for them to make a choice)
     * 
     * @param clientId the player who is pending
     * @param pending  true if pending, false if already picked
     */
    public void markPlayerPendingPick(long clientId, boolean pending) {
        if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.get(clientId).setPendingPick(pending);
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error marking player pending pick", e);
                }
            });
        }
    }

    /**
     * Clears all elimination and pending markers
     */
    public void resetPlayerStatus() {
        SwingUtilities.invokeLater(() -> {
            try {
                userItemsMap.values().forEach(u -> {
                    u.setEliminated(false);
                    u.setPendingPick(false);
                });
            } catch (Exception e) {
                LoggerUtil.INSTANCE.severe("Error resetting player status", e);
            }
        });
    }
}

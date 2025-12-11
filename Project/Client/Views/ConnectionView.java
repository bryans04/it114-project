package Project.Client.Views;

import java.awt.BorderLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import Project.Client.CardViewName;
import Project.Client.Interfaces.ICardControls;

public class ConnectionView extends JPanel {
    // Stores the host, port, and username entered by the user
    private String host;
    private int port;
    private String username;
    // UI components for user input and error display
    private final JTextField usernameField = new JTextField("");
    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("3000");
    private final JLabel usernameError = new JLabel();
    private final JLabel hostError = new JLabel();
    private final JLabel portError = new JLabel();

    /**
     * Sets up the connection view UI for entering username, host and port.
     * Registers this view with the card controls for navigation.
     */
    public ConnectionView(ICardControls controls) {
        super(new BorderLayout(10, 10));

        // Set panel name and register
        setName(CardViewName.CONNECT.name());
        controls.registerView(CardViewName.CONNECT.name(), this);

        // Main content panel with vertical layout and padding
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Username input section
        content.add(new JLabel("Username:"));
        usernameField.setToolTipText("Enter your desired username");
        content.add(usernameField);
        usernameError.setVisible(false);
        content.add(usernameError);

        // Host input section
        content.add(new JLabel("Host:"));
        hostField.setToolTipText("Enter the host address");
        content.add(hostField);
        hostError.setVisible(false);
        content.add(hostError);

        // Port input section
        content.add(new JLabel("Port:"));
        portField.setToolTipText("Enter the port number");
        content.add(portField);
        portError.setVisible(false);
        content.add(portError);

        // Next button for proceeding to the next view
        JButton nextButton = new JButton("Next");
        nextButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
        nextButton.addActionListener(_ -> onNext(controls));
        content.add(Box.createVerticalStrut(10));
        content.add(nextButton);

        add(content, BorderLayout.CENTER);
    }

    /**
     * Handles the Next button click: validates username, host, and port input,
     * then navigates to the next view if all are valid.
     */
    private void onNext(ICardControls controls) {
        boolean valid = true;
        
        // Validate username
        username = usernameField.getText().trim();
        if (username.isEmpty()) {
            usernameError.setText("Username cannot be empty");
            usernameError.setVisible(true);
            valid = false;
        } else {
            usernameError.setVisible(false);
        }
        
        // Validate host
        host = hostField.getText().trim();
        if (host.isEmpty()) {
            hostError.setText("Host cannot be empty");
            hostError.setVisible(true);
            valid = false;
        } else {
            hostError.setVisible(false);
        }
        
        // Validate port
        try {
            port = Integer.parseInt(portField.getText());
            if (port < 0 || port > 65535) {
                portError.setText("Port must be between 0 and 65535");
                portError.setVisible(true);
                valid = false;
            } else {
                portError.setVisible(false);
            }
        } catch (NumberFormatException ex) {
            portError.setText("Invalid port value, must be a number");
            portError.setVisible(true);
            valid = false;
        }
        
        if (valid) {
            controls.connect();
        }
    }

    // Getters for the username, host and port values entered by the user
    public String getUsername() {
        return username;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
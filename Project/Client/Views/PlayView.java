package Project.Client.Views;

import javax.swing.JPanel;

import Project.Common.Phase;

public class PlayView extends JPanel {
    private final JPanel buttonPanel = new JPanel();

    public PlayView(String name){
        this.setName(name);
        this.add(buttonPanel);
    }
    public void changePhase(Phase phase){
        if (phase == Phase.READY) {
            buttonPanel.setVisible(false);
        } else if (phase == Phase.IN_PROGRESS) {
            buttonPanel.setVisible(true);
        }
    }
    
}

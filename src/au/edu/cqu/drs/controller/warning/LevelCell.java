package au.edu.cqu.drs.controller.warning;

import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningLevel;
import javafx.scene.control.TableCell;

/**
 * A table cell that shows a warning level by name on the level's colour.
 */
final class LevelCell extends TableCell<Warning, WarningLevel> {

    /**
     * Shows the level, replacing any colour from a previous row.
     *
     * @param level the level, or null
     * @param empty whether the row is empty
     */
    @Override
    protected void updateItem(WarningLevel level, boolean empty) {
        super.updateItem(level, empty);
        getStyleClass().removeIf(style -> style.startsWith("level-"));
        if (empty || level == null) {
            setText(null);
            return;
        }
        setText(level.getDisplayName());
        getStyleClass().add(WarningCalls.levelStyle(level));
    }

    /**
     * Describes the cell.
     *
     * @return the level shown
     */
    @Override
    public String toString() {
        return "LevelCell: " + getText();
    }
}

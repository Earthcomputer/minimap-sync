package net.earthcomputer.minimapsync.ducks;

import net.earthcomputer.minimapsync.model.Model;

public interface IHasModel {
    Model minimapsync_model();
    void minimapsync_setModel(Model model);
}

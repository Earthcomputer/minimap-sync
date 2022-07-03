package com.mamiyaotaru.voxelmap.interfaces;

import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import net.minecraft.world.level.Level;

public interface IDimensionManager {
    DimensionContainer getDimensionContainerByWorld(Level world);
}

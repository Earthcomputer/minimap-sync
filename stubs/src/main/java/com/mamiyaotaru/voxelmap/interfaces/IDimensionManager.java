package com.mamiyaotaru.voxelmap.interfaces;

import com.mamiyaotaru.voxelmap.util.DimensionContainer;

public interface IDimensionManager {
    DimensionContainer getDimensionContainerByIdentifier(String identifier);
}

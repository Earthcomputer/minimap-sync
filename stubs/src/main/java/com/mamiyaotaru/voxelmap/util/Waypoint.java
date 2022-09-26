package com.mamiyaotaru.voxelmap.util;

import java.util.TreeSet;

public class Waypoint {
    public String name;
    public String imageSuffix;
    public TreeSet<DimensionContainer> dimensions;
    public int x;
    public int y;
    public int z;
    public float red;
    public float green;
    public float blue;
    public boolean inDimension = true;

    public Waypoint(
        String name,
        int x,
        int z,
        int y,
        boolean enabled,
        float red,
        float green,
        float blue,
        String suffix,
        String world,
        TreeSet<DimensionContainer> dimensions
    ) {
    }
}

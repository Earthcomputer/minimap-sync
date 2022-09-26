package journeymap.client.waypoint;

import net.minecraft.resources.ResourceLocation;

public class Waypoint {
    protected String icon;
    protected Integer iconColor;

    public Waypoint(Waypoint original) {
    }

    public String getName() {
        return "";
    }


    public Waypoint setIcon(ResourceLocation iconResource) {
        return null;
    }

    public void setIconColor(Integer iconColor) {
    }

    public Integer getIconColor() {
        return null;
    }

    public ResourceLocation getIcon() {
        return new ResourceLocation("");
    }

    public Type getType() {
        return Type.Death;
    }

    public enum Type {
        Death
    }
}

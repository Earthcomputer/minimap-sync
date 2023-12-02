package net.earthcomputer.minimapsync;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class MixinConfigPlugin implements IMixinConfigPlugin {
    private String mixinPackage;

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage + ".";
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(this.mixinPackage)) {
            return true;
        }
        String mixinClass = mixinClassName.substring(this.mixinPackage.length());
        int dotIndex = mixinClass.indexOf('.');
        if (dotIndex == -1) {
            return true;
        }
        String modId = mixinClass.substring(0, dotIndex);
        return FabricLoader.getInstance().isModLoaded(modId) || getAliases(modId).stream().anyMatch(FabricLoader.getInstance()::isModLoaded);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static List<String> getAliases(String modid) {
        if ("journeymap".equals(modid)) {
            return List.of("journeymap-fabric");
        } else {
            return Collections.emptyList();
        }
    }
}

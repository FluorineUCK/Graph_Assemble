package dev.graphassembly.compat.jei;

import dev.graphassembly.GraphAssembly;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.file.*;

final class ViewPreference {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("graph-assembly-view.txt");
    private static boolean sphere = load();
    private ViewPreference() {}
    private static boolean load() {
        try { return Files.exists(FILE) && Files.readString(FILE).trim().equals("sphere"); }
        catch (IOException e) { return false; }
    }
    static boolean sphere() { return sphere; }
    static void set(boolean value) {
        sphere = value;
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, value ? "sphere" : "plane"); }
        catch (IOException e) { GraphAssembly.LOGGER.warn("Could not save graph view preference", e); }
    }
}

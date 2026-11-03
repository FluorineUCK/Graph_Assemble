package dev.graphassembly.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModLogoResourcesTest {
    @Test void modMetadataPointsToADecodablePngLogo() throws Exception {
        var resources = Path.of("src/main/resources");
        var metadata = Files.readString(resources.resolve("META-INF/neoforge.mods.toml"));
        var logo = Pattern.compile("(?m)^logoFile\\s*=\\s*\"([^\"]+)\"").matcher(metadata);
        assertTrue(logo.find(), "Declare the mod-list icon in logoFile");
        assertTrue(logo.group(1).endsWith(".png"), "Use a PNG resource rather than an ICO container");
        var image = ImageIO.read(resources.resolve(logo.group(1)).toFile());
        assertNotNull(image, "The declared icon must decode");
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
        assertTrue(image.getColorModel().hasAlpha(), "Preserve the artist's transparent background");
    }
}

package dev.graphassembly.client.gui;

import java.util.Map;

/** Dynamic text/geometry defaults, deliberately not an artist-facing JSON format. */
public final class GuiColors {
    private GuiColors() {}
    private static final Map<String, Integer> COLORS = Map.ofEntries(
        Map.entry("canvas.background", 0x50FFFFFF),
        Map.entry("button.edge.light", 0xFF9AC6D6),
        Map.entry("button.edge.dark", 0xFF203743),
        Map.entry("button.shade.pressed", 0x44000000),
        Map.entry("text.primary", 0xFFFFFFFF),
        Map.entry("text.secondary", 0xFFB6C7CE),
        Map.entry("text.disabled", 0xFF687D89),
        Map.entry("text.hint", 0xFF94AEBC),
        Map.entry("text.body", 0xFFDDEAF1),
        Map.entry("node.start", 0xFF71D4AF),
        Map.entry("node.finish", 0xFFC88071),
        Map.entry("node.checkpoint", 0xFFC5A9E2),
        Map.entry("node.normal", 0xFF82AFDC),
        Map.entry("node.selected", 0xFFF2C66D),
        Map.entry("node.back", 0xFF354653),
        Map.entry("edge.normal", 0xFF6A98AE),
        Map.entry("edge.selected", 0xFFF2C66D),
        Map.entry("edge.back", 0xFF2D404B),
        // JEI owns the light-gray footer, unlike the dark graph canvas.
        Map.entry("footer.input", 0xFF26343B),
        Map.entry("footer.success", 0xFF18553D),
        Map.entry("footer.scrap", 0xFF73372C),
        Map.entry("filter.title", 0xFF592424),
        Map.entry("filter.choice", 0xFFF3EBDE),
        Map.entry("filter.label", 0xFF45382E),
        Map.entry("filter.hint", 0xFF5A4A3F),
        Map.entry("filter.fallback.title", 0xFF592424),
        Map.entry("filter.fallback.label", 0xFF594B40),
        Map.entry("filter.fallback.hint", 0xFF796A5C),
        Map.entry("filter.error", 0xFFAA2222));
    public static int color(String key) {
        var value = COLORS.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown GUI color: " + key);
        return value;
    }
}

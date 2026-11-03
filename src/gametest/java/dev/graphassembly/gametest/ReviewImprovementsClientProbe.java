package dev.graphassembly.gametest;

import com.google.gson.*;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.*;
import dev.graphassembly.client.gui.GraphGuiAssets;
import dev.graphassembly.client.view.PreviewLifecycle;
import dev.graphassembly.compat.jei.GraphJeiPlugin;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Opt-in real-client checks, never shipped. F7: actual widget lifecycle; F9: diagnostic pack. */
@EventBusSubscriber(modid=GraphAssembly.ID,value=Dist.CLIENT)
public final class ReviewImprovementsClientProbe {
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Post event) {
        if(!Boolean.getBoolean("graph_assembly.reviewVerification")) return;
        try {
            if(event.getKeyCode()==GLFW.GLFW_KEY_F7) lifecycle();
            if(event.getKeyCode()==GLFW.GLFW_KEY_F9) diagnosticPack();
        } catch(Exception|AssertionError e) { GraphAssembly.LOGGER.error("REVIEW_PROBE FAILED",e); }
    }
    private static void lifecycle() throws Exception {
        var runtimeMethod=GraphJeiPlugin.class.getDeclaredMethod("runtime"); runtimeMethod.setAccessible(true);
        var runtime=(mezz.jei.api.runtime.IJeiRuntime)runtimeMethod.invoke(null);
        if(runtime==null) throw new AssertionError("JEI runtime missing");
        var json=JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget","nodes":[],"edges":[]}
            """).getAsJsonObject();
        for(int i=0;i<1000;i++) {
            var node=JsonParser.parseString("""
                {"id":"n%d","recipe":{"type":"create:pressing"},"position":[%d,0],"sphere_position":[0,0,-1]}
                """.formatted(i,i==0?0:10000+i)).getAsJsonObject();
            if(i==0) node.addProperty("start",true);
            json.getAsJsonArray("nodes").add(node);
        }
        var graph=GraphCodec.parse("test:review_large",json);
        var clazz=Class.forName("dev.graphassembly.compat.jei.GraphWidget");
        var constructor=clazz.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var widget=constructor.newInstance(graph,runtime.getJeiHelpers().getGuiHelper(),runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
        var project=clazz.getDeclaredMethod("project"); project.setAccessible(true);
        var tick=clazz.getMethod("tick"); tick.setAccessible(true);
        set(widget,"sphere",false); project.invoke(widget);
        check(count(widget)==0,"1000 collapsed nodes constructed native layouts");
        var first=field(widget,"surfaces"); var edges=field(widget,"edgePaths");
        for(int i=0;i<100;i++) project.invoke(widget);
        check(first==field(widget,"surfaces") && edges==field(widget,"edgePaths"),"Static view rebuilt geometry");
        set(widget,"zoom",1.3); set(widget,"geometryDirty",true); project.invoke(widget);
        check(count(widget)==1,"Only the visible node should prepare a native layout");
        set(widget,"panX",100000.0); set(widget,"geometryDirty",true); project.invoke(widget);
        for(int i=0;i<PreviewLifecycle.IDLE_TICKS;i++) tick.invoke(widget);
        check(count(widget)==0,"Offscreen native layout was not released");
        set(widget,"sphere",true); set(widget,"geometryDirty",true); project.invoke(widget);
        check(count(widget)==0,"Back-facing nodes constructed native layouts");
        set(widget,"sphere",false); set(widget,"panX",0.0); set(widget,"geometryDirty",true); project.invoke(widget);
        check(count(widget)==1,"Visible node failed to recreate after eviction");
        GraphAssembly.LOGGER.info("REVIEW_PROBE PASS nodes=1000 collapsedLayouts=0 visibleLayouts=1 staticGeometryReused=true offscreenLayouts=0 backLayouts=0 recreated=1");
    }
    private static Object field(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        var field=owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner,value);
    }
    private static int count(Object widget) throws Exception { return ((Map<?,?>)field(field(widget,"previews"),"values")).size(); }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private static void diagnosticPack() throws Exception {
        var mc=Minecraft.getInstance(); String id="file/review-panels";
        var repo=mc.getResourcePackRepository();
        boolean enabled=repo.getSelectedIds().contains(id);
        if(enabled) repo.removePack(id);
        else {
            var root=mc.gameDirectory.toPath().resolve("resourcepacks/review-panels");
            var png=root.resolve("assets/graph_assembly/textures/gui/graph_gui.png"); Files.createDirectories(png.getParent());
            Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":34,\"description\":\"Review-only translucent panel contract\"}}");
            // A new deterministic diagnostic image, not a rewrite of the artist's PNG.
            var image=new java.awt.image.BufferedImage(1024,512,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(var sprite:List.of(GraphGuiAssets.Sprite.GRAPH_PANEL)) {
                boolean graph=sprite==GraphGuiAssets.Sprite.GRAPH_PANEL;
                for(int y=0;y<sprite.height();y++) for(int x=0;x<sprite.width();x++) {
                    boolean border=x<3||y<3||x>=sprite.width()-3||y>=sprite.height()-3;
                    image.setRGB(sprite.x()+x,sprite.y()+y,border ? (graph?0xFFFF6020:0xFF2080FF) : (graph?0x40FF6020:0x402080FF));
                }
            }
            javax.imageio.ImageIO.write(image,"png",png.toFile());
            repo.reload(); repo.addPack(id);
        }
        mc.reloadResourcePacks().thenRun(()->GraphAssembly.LOGGER.info("REVIEW_PROBE diagnosticPack={}",!enabled));
    }
}

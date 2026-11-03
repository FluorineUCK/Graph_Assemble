package dev.graphassembly.client.view;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewReloadsTest {
    @Test void resourcesSignalReleasesIdlePreviewsBeforeAnyFurtherPreviewAccess() {
        var previews = new PreviewLifecycle<String, Object>();
        var views = new RecipeViewStateCache<String, Object>();
        var camera = views.get("graph", Object::new);
        previews.prepare("node", true, Object::new);
        Consumer<PreviewReloads.Change> release = change -> {
            assertEquals(PreviewReloads.Change.RESOURCES, change);
            previews.clear();
        };
        PreviewReloads.installLifecycle(release);
        try {
            long before = PreviewReloads.generation();
            PreviewReloads.resourcesChanged();
            assertNull(previews.get("node"), "The reload signal must release an idle owner's previews synchronously");
            assertSame(camera, views.get("graph", Object::new), "Resource reload must preserve pure camera state");
            assertEquals(before + 1, PreviewReloads.generation());
        } finally {
            PreviewReloads.removeLifecycle(release);
        }
    }

    @Test void recipeSignalNotifiesTheActiveOwnerWithTheNewEpochBeforeReturning() {
        var previews = new PreviewLifecycle<String, Object>();
        previews.prepare("node", true, Object::new);
        long before = PreviewReloads.recipes();
        Consumer<PreviewReloads.Change> release = change -> {
            assertEquals(PreviewReloads.Change.RECIPES, change);
            assertEquals(before + 1, PreviewReloads.recipes());
            previews.clear();
        };
        PreviewReloads.installLifecycle(release);
        try {
            PreviewReloads.recipesChanged();
            assertNull(previews.get("node"), "Recipe synchronization must release idle previews before returning");
        } finally {
            PreviewReloads.removeLifecycle(release);
        }
    }

    @Test void terminalSignalDetachesBeforeNotificationAndReleasesSessionState() {
        var previews = new PreviewLifecycle<String, Object>();
        var views = new RecipeViewStateCache<String, Object>();
        var camera = views.get("graph", Object::new);
        var runtime = new AtomicReference<>(new Object());
        var changes = new ArrayList<PreviewReloads.Change>();
        previews.prepare("node", true, Object::new);
        Consumer<PreviewReloads.Change> release = change -> {
            changes.add(change);
            if (change == PreviewReloads.Change.SESSION_ENDED) {
                previews.clear();
                views.clear();
                runtime.set(null);
                PreviewReloads.resourcesChanged();
            }
        };
        PreviewReloads.installLifecycle(release);
        try {
            PreviewReloads.sessionEnded();
            assertNull(runtime.get(), "Terminal cleanup must not wait for another runtime lookup");
            assertNull(previews.get("node"));
            assertNotSame(camera, views.get("graph", Object::new));
            PreviewReloads.recipesChanged();
            PreviewReloads.sessionEnded();
            assertEquals(List.of(PreviewReloads.Change.SESSION_ENDED), changes,
                "A terminal signal must remove the callback before invoking it");
        } finally {
            PreviewReloads.removeLifecycle(release);
        }
    }

    @Test void unregisteringAnOldOwnerDoesNotRemoveItsReplacement() {
        var changes = new ArrayList<PreviewReloads.Change>();
        Consumer<PreviewReloads.Change> old = change -> fail("Replaced owner must not receive signals");
        Consumer<PreviewReloads.Change> replacement = changes::add;
        PreviewReloads.installLifecycle(old);
        PreviewReloads.installLifecycle(replacement);
        PreviewReloads.removeLifecycle(old);
        try {
            PreviewReloads.resourcesChanged();
            assertEquals(List.of(PreviewReloads.Change.RESOURCES), changes);
            PreviewReloads.removeLifecycle(replacement);
            PreviewReloads.recipesChanged();
            assertEquals(List.of(PreviewReloads.Change.RESOURCES), changes);
        } finally {
            PreviewReloads.removeLifecycle(replacement);
        }
    }
}

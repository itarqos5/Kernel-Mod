package dev.kernel.fabric.verification;

import dev.kernel.fabric.config.KernelRendererSettings;
import dev.kernel.fabric.config.RendererFeature;
import dev.kernel.fabric.frame.FrameSync;
import dev.kernel.fabric.render.ChunkFrameBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.locks.LockSupport;

/** Controlled render-thread load plus real chunk movement; timing observations are not gameplay benchmarks. */
public final class ChunkUploadProbe {
    private static final long STARTED = System.nanoTime();
    private static final ArrayList<Long> samples = new ArrayList<>();
    private static int stage, stressFrames, uploadFrames;
    private static long changedAt, minimumBudget = Long.MAX_VALUE, maximumUpload;
    private static boolean advancing;
    private static volatile boolean stress;

    public static void addControlledWork() {
        if (Boolean.getBoolean("kernel.guiProbe.chunkBudget") && stress) LockSupport.parkNanos(18_000_000L);
    }
    static void frame(Minecraft minecraft, long ready) {
        if (stage == 10 || advancing) return;
        advancing = true;
        try { advance(minecraft, ready); }
        catch (Exception exception) { stage = 10; throw new AssertionError("Chunk upload probe failed", exception); }
        catch (Error error) { stage = 10; throw error; }
        finally { advancing = false; }
    }
    private static void advance(Minecraft minecraft, long ready) throws Exception {
        if (System.nanoTime() - STARTED > 240_000_000_000L) throw new AssertionError("Chunk upload probe timed out at " + stage);
        if (ready < 0) return;
        //? if >=26.2 {
        if (minecraft.gui.overlay() != null) return;
        //? } else {
        /*if (minecraft.getOverlay() != null) return;
        *///? }
        minecraft.getFramerateLimitTracker().onInputReceived();
        long elapsed = System.nanoTime() - changedAt;
        boolean supported = KernelRendererSettings.supported(RendererFeature.CHUNK_UPLOAD);
        if (stage == 0) {
            FrameSync.save(false); minecraft.options.enableVsync().set(false); minecraft.options.framerateLimit().set(260);
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "menu.singleplayer")); next(1); return;
        }
        if (stage == 1) {
            if (GuiProbe.screen(minecraft) instanceof SelectWorldScreen) { GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "selectWorld.create")); return; }
            if (GuiProbe.screen(minecraft) instanceof CreateWorldScreen create) {
                var state = create.getUiState(); state.setName("Kernel Chunk Upload Probe"); state.setSeed("173241");
                state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                state.setWorldType(state.getNormalPresetList().stream().filter(type -> type.preset() != null && type.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());
                GuiProbe.click(GuiProbe.find(create, "selectWorld.create")); next(2);
            }
            return;
        }
        if (stage == 2 && minecraft.level != null && minecraft.player != null && GuiProbe.screen(minecraft) == null
            && elapsed > 5_000_000_000L && minecraft.levelRenderer.hasRenderedAllSections()) {
            var server = minecraft.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(256.5, player.getY(), 256.5);
            });
            stress = supported; next(3); return;
        }
        if (stage == 3) {
            if (supported) {
                samples.add(ChunkFrameBudget.lastWorkNanos());
                minimumBudget = Math.min(minimumBudget, ChunkFrameBudget.lastBudgetNanos());
                long upload = ChunkFrameBudget.lastUploadNanos();
                maximumUpload = Math.max(maximumUpload, upload); if (upload > 0) uploadFrames++;
            }
            if (++stressFrames < 150 || minecraft.player == null || minecraft.player.getX() < 200) return;
            stress = false;
            boolean adaptive = Boolean.parseBoolean(System.getProperty("kernel.chunkUpload.adaptive", "true"));
            if (supported && (ChunkFrameBudget.frames() == 0 || uploadFrames == 0 || minimumBudget != (adaptive ? 250_000L : 2_000_000L))) {
                throw new AssertionError("Native upload/accounting/budget response failed: min=" + minimumBudget + ", uploadFrames=" + uploadFrames);
            }
            if (!supported && ChunkFrameBudget.frames() != 0) throw new AssertionError("Legacy upload control activated on the modern staging renderer");
            next(4); return;
        }
        if (stage == 4 && elapsed > 5_000_000_000L && minecraft.levelRenderer.hasRenderedAllSections()) {
            if (supported && ChunkFrameBudget.lastBudgetNanos() != 2_000_000L) throw new AssertionError("Chunk upload budget did not recover");
            Collections.sort(samples);
            String detail = supported ? "min budget=" + minimumBudget + " ns, upload frames=" + uploadFrames + ", max upload=" + maximumUpload
                + " ns, controlled-load work p50/p95/p99=" + percentile(0.50) + "/" + percentile(0.95) + "/" + percentile(0.99) + " ns"
                : "native modern staging preserved";
            System.out.println("Kernel chunk upload probe passed: " + detail + "; full visible-section completion and recovery passed.");
            Files.writeString(minecraft.gameDirectory.toPath().resolve("chunk-upload-complete.txt"), detail + "\n");
            stage = 10; minecraft.execute(minecraft::stop);
        }
    }
    private static long percentile(double fraction) { return samples.get(Math.min(samples.size() - 1, (int) Math.ceil(samples.size() * fraction) - 1)); }
    private static void next(int value) { stage = value; changedAt = System.nanoTime(); }
}

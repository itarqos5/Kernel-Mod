package dev.kernel.fabric.verification;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Generates new isolated worlds and fingerprints remote, non-ticking chunks on the server thread. */
final class WorldGenerationProbe {
    private static int stage;
    private static boolean advancing;
    private static long started = System.nanoTime();
    private static volatile String completed;
    private static volatile Throwable failure;

    static void frame(Minecraft minecraft, long readyMillis) {
        if (stage == 4 || advancing) return;
        if (failure != null) { stage = 4; throw new AssertionError("World-generation probe", failure); }
        if (System.nanoTime() - started > 240_000_000_000L) { stage = 4; throw new AssertionError("World-generation probe timed out at " + stage); }
        if (readyMillis < 0) return;
        //? if >=26.2 {
        if (minecraft.gui.overlay() != null) return;
        //? } else {
        /*if (minecraft.getOverlay() != null) return;
        *///? }
        advancing = true;
        try {
            if (stage == 0) {
                GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "menu.singleplayer")); stage = 1; return;
            }
            if (stage == 1) {
                if (GuiProbe.screen(minecraft) instanceof SelectWorldScreen) {
                    GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "selectWorld.create")); return;
                }
                if (GuiProbe.screen(minecraft) instanceof CreateWorldScreen create) {
                    var state = create.getUiState();
                    state.setName("Kernel World Generation Probe"); state.setSeed("2718281828");
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    GuiProbe.click(GuiProbe.find(create, "selectWorld.create")); stage = 2;
                }
                return;
            }
            if (stage == 2 && minecraft.level != null && minecraft.player != null && GuiProbe.screen(minecraft) == null) {
                var server = minecraft.getSingleplayerServer();
                if (server == null) throw new AssertionError("Missing integrated server");
                stage = 3;
                server.execute(() -> {
                    try {
                        long start = System.nanoTime();
                        // Prepare the halo, then apply neighboring decoration in a fixed order. Otherwise
                        // overlapping native deposits can differ between two cache-disabled launches too.
                        for (var status : new ChunkStatus[]{ChunkStatus.CARVERS, ChunkStatus.FEATURES}) {
                            for (int x = 1023; x < 1028; x++) for (int z = 1023; z < 1028; z++) {
                                server.overworld().getChunkSource().getChunk(x, z, status, true);
                            }
                        }
                        for (int x = 1024; x < 1027; x++) for (int z = 1024; z < 1027; z++) server.overworld().getChunk(x, z);
                        var digest = MessageDigest.getInstance("SHA-256");
                        try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                            for (int x = 1024; x < 1027; x++) for (int z = 1024; z < 1027; z++) {
                                var chunk = server.overworld().getChunk(x, z);
                                output.writeInt(x); output.writeInt(z);
                                for (var section : chunk.getSections()) {
                                    for (int sy = 0; sy < 16; sy++) for (int sx = 0; sx < 16; sx++) for (int sz = 0; sz < 16; sz++) {
                                        output.writeInt(Block.getId(section.getBlockState(sx, sy, sz)));
                                    }
                                    for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) for (int sz = 0; sz < 4; sz++) {
                                        // ResourceKey.toString includes both registry and stable biome identifier on all targets.
                                        output.writeUTF(section.getBiomes().get(sx, sy, sz).unwrapKey().orElseThrow().toString());
                                    }
                                }
                            }
                        }
                        String hash = HexFormat.of().formatHex(digest.digest());
                        Files.writeString(minecraft.gameDirectory.toPath().resolve("world-generation-sha256.txt"), hash + "\n");
                        System.out.println("Kernel world probe: 9 remote chunks, blocks and biomes SHA-256=" + hash
                            + "; generation+fingerprint ms=" + (System.nanoTime() - start) / 1_000_000L);
                        completed = hash;
                    } catch (Throwable exception) { failure = exception; }
                });
            }
            if (stage == 3 && completed != null) { stage = 4; minecraft.execute(minecraft::stop); }
        } catch (RuntimeException | Error exception) { stage = 4; throw exception; }
        finally { advancing = false; }
    }
}

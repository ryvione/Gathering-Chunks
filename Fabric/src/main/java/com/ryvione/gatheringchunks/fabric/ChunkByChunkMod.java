package com.ryvione.gatheringchunks.fabric;

import com.ryvione.gatheringchunks.common.CommonEventHandler;
import com.ryvione.gatheringchunks.common.CommonRegistry;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.util.ConfigUtil;
import com.ryvione.gatheringchunks.common.network.C2SSaveConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2COpenConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.system.ConfigSystem;
import com.ryvione.gatheringchunks.interop.Services;
import com.ryvione.gatheringchunks.common.CauldronRainFiller;
import com.ryvione.gatheringchunks.server.ChunkBoundaryEnforcer;
import com.ryvione.gatheringchunks.server.MobLootHandler;
import com.ryvione.gatheringchunks.server.ServerEventHandler;
import com.ryvione.gatheringchunks.server.commands.ChestsCommand;
import com.ryvione.gatheringchunks.server.commands.GatheringChunksCommand;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import com.ryvione.gatheringchunks.common.util.ChunkUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChunkByChunkMod implements ModInitializer {

    private static final Logger LOGGER = LogManager.getLogger(GatheringChunksConstants.MOD_ID);
    private static final Set<UUID> INITIAL_SPAWNED_PLAYERS = new HashSet<>();

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES,
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE,
                        ResourceLocation.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "world_fragment_ore"))
        );
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES,
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE,
                        ResourceLocation.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "deepslate_world_fragment_ore"))
        );

        PayloadTypeRegistry.playS2C().register(S2COpenConfigPacket.TYPE, S2COpenConfigPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(S2CSyncConfigPacket.TYPE, S2CSyncConfigPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(C2SSaveConfigPacket.TYPE, C2SSaveConfigPacket.CODEC);

        LOGGER.info("Fabric mod initializing");

        Path gameDir = FabricLoader.getInstance().getGameDir();
        ConfigSystem.initCentralConfigDir(gameDir);
        ConfigUtil.startConfigWatcher();
        ConfigUtil.loadDefaultConfig();
        LOGGER.info("[ChunkByChunkMod] Centralized config directory initialized and loaded");

        CommonRegistry.registerAll();

        ServerPlayNetworking.registerGlobalReceiver(C2SSaveConfigPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal("§c[Gathering Chunks] No permission to modify server config."));
                    return;
                }
                try {
                    com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
                    ChunkByChunkConfig incoming = gson.fromJson(packet.configJson(), ChunkByChunkConfig.class);
                    if (incoming == null) return;

                    applyConfig(incoming);

                    ConfigUtil.saveDefaultConfig(context.server());

                    String syncJson = gson.toJson(ChunkByChunkConfig.get().getGatheringChunksConfig());
                    S2CSyncConfigPacket syncPacket = new S2CSyncConfigPacket(syncJson);
                    for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                        Services.PLATFORM.sendConfigSyncPacket(p, syncPacket);
                    }

                    GatheringChunksConstants.LOGGER.info("[Fabric] Config saved by {}", player.getName().getString());
                    player.sendSystemMessage(Component.literal("§a[Gathering Chunks] Config saved to server."));
                } catch (Exception e) {
                    GatheringChunksConstants.LOGGER.error("[Fabric] Failed to apply config from client", e);
                }
            });
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            INITIAL_SPAWNED_PLAYERS.clear();
            ServerEventHandler.onServerStarted(server);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(ServerEventHandler::onServerStarting);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[ChunkByChunkMod] Saving config before server shutdown...");
            ConfigUtil.saveDefaultConfig();
        });

        ServerTickEvents.END_SERVER_TICK.register(ServerEventHandler::onLevelTick);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ChunkBoundaryEnforcer.checkPlayerBoundaries(player);
                }
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(CauldronRainFiller::tick);

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            MobLootHandler.onMobDeath(entity, entity.level());
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof AbstractPiglin piglin)) return;
            if (!world.dimension().equals(Level.NETHER)) return;
            if (!(world.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) return;
            CompoundTag tag = new CompoundTag();
            piglin.save(tag);
            if (tag.getByte("IsImmuneToZombification") != 1) {
                tag.putByte("IsImmuneToZombification", (byte) 1);
                piglin.load(tag);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GatheringChunksCommand.register(dispatcher);
            ChestsCommand.register(dispatcher);
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            if (!world.dimension().equals(Level.OVERWORLD) && !world.dimension().equals(Level.NETHER)) return;
            if (!(world.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) return;
            if (!INITIAL_SPAWNED_PLAYERS.contains(player.getUUID())) return;
            ServerEventHandler.onPlayerArrived(player, (ServerLevel) world);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!ChunkByChunkConfig.get().getGeneration().isEnabled())
                return;

            if (newPlayer.getRespawnPosition() != null)
                return;

            ServerLevel level = newPlayer.serverLevel();
            if (!level.dimension().equals(Level.OVERWORLD))
                return;

            if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator) {
                BlockPos spawnPos = level.getSharedSpawnPos();
                ChunkPos spawnChunk = new ChunkPos(spawnPos);

                LOGGER.debug("Forcing respawn to spawn chunk [{},{}]", spawnChunk.x, spawnChunk.z);

                int safeY = level.getMaxBuildHeight();
                LevelChunk spawnLevelChunk = level.getChunkAt(spawnChunk.getMiddleBlockPosition(0));
                if (spawnLevelChunk != null) {
                    int candidateY = ChunkUtil.getSafeSpawnHeight(spawnLevelChunk, spawnChunk.getMiddleBlockX(), spawnChunk.getMiddleBlockZ());
                    if (candidateY > level.getMinBuildHeight() + 10) {
                        safeY = candidateY;
                    } else {
                        safeY = spawnLevelChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8) + 1;
                    }
                }

                newPlayer.teleportTo(
                        level,
                        spawnChunk.getMiddleBlockX() + 0.5,
                        safeY,
                        spawnChunk.getMiddleBlockZ() + 0.5,
                        newPlayer.getYRot(),
                        newPlayer.getXRot());
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ChunkByChunkConfig.get().getGeneration().isEnabled())
                return;

            ServerPlayer player = handler.getPlayer();

            try {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
                String configJson = gson.toJson(ChunkByChunkConfig.get().getGatheringChunksConfig());
                S2CSyncConfigPacket packet = new S2CSyncConfigPacket(configJson);
                Services.PLATFORM.sendConfigSyncPacket(player, packet);
                LOGGER.debug("[ChunkByChunkMod] Synced config to player {} on join", player.getName().getString());
                
                ServerEventHandler.giveStarterBookIfMissing(player);
            } catch (Exception e) {

                LOGGER.warn("[ChunkByChunkMod] Failed to sync config to player on join: {}", e.getMessage());
            }

            ServerLevel level = player.serverLevel();
            if (!level.dimension().equals(Level.OVERWORLD))
                return;

            if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator) {
                boolean isFirstJoin = !player.getTags().contains("gatheringchunks.spawned");

                if (isFirstJoin) {
                    player.addTag("gatheringchunks.spawned");
                    INITIAL_SPAWNED_PLAYERS.add(player.getUUID());

                    final UUID firstJoinUUID = player.getUUID();
                    final int MAX_WAIT_TICKS = 600;

                    server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
                        ServerPlayer p = server.getPlayerList().getPlayer(firstJoinUUID);
                        if (p == null) return;

                        ServerLevel lvl = p.serverLevel();
                        if (!lvl.dimension().equals(Level.OVERWORLD)) return;
                        if (!(lvl.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) return;

                        BlockPos currentSpawn = lvl.getSharedSpawnPos();
                        ChunkPos spawnChunk = new ChunkPos(currentSpawn);
                        boolean chunkReady = !SpawnChunkHelper.isEmptyChunk(lvl, spawnChunk);

                        if (chunkReady) {
                            LOGGER.info("[FirstJoin] Spawn chunk [{},{}] already ready - teleporting to surface", spawnChunk.x, spawnChunk.z);
                            LevelChunk spawnLevelChunk = lvl.getChunkAt(spawnChunk.getMiddleBlockPosition(0));
                            int safeY = (spawnLevelChunk != null)
                                    ? Math.max(ChunkUtil.getSafeSpawnHeight(spawnLevelChunk, spawnChunk.getMiddleBlockX(), spawnChunk.getMiddleBlockZ()),
                                               lvl.getMinBuildHeight() + 10)
                                    : lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                                               spawnChunk.getMiddleBlockX(), spawnChunk.getMiddleBlockZ()) + 1;
                            p.teleportTo(lvl,
                                    spawnChunk.getMiddleBlockX() + 0.5, safeY,
                                    spawnChunk.getMiddleBlockZ() + 0.5,
                                    p.getYRot(), p.getXRot());
                        } else {
                            int holdY = lvl.getMaxBuildHeight() - 1;
                            LOGGER.info("[FirstJoin] Spawn chunk not ready - parking player at Y={} above [{},{}]", holdY, spawnChunk.x, spawnChunk.z);
                            p.teleportTo(lvl,
                                    spawnChunk.getMiddleBlockX() + 0.5, holdY,
                                    spawnChunk.getMiddleBlockZ() + 0.5,
                                    p.getYRot(), p.getXRot());
                            scheduleSpawnTeleport(server, firstJoinUUID, server.getTickCount(), MAX_WAIT_TICKS);
                        }
                    }));
                }
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            BlockPos placePos = pos.relative(hitResult.getDirection());
            if (!CommonEventHandler.isBlockPlacementAllowed(placePos, player, world)) {
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        });

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return ResourceLocation.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "server_data");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        ServerEventHandler.onResourceManagerReload(resourceManager);
                    }
                });
    }

    private static void applyConfig(ChunkByChunkConfig incoming) {
        ChunkByChunkConfig current = ChunkByChunkConfig.get();

        current.getGeneration().setEnabled(incoming.getGeneration().isEnabled());
        current.getGeneration().setSealWorld(incoming.getGeneration().sealWorld());
        current.getGeneration().setSynchNether(incoming.getGeneration().isSynchNether());
        current.getGeneration().setUseBedrockChest(incoming.getGeneration().useBedrockChest());
        current.getGeneration().setChestContents(incoming.getGeneration().getChestContents());
        current.getGeneration().setChestQuantity(incoming.getGeneration().getChestQuantity());
        current.getGeneration().setChestsPerChunk(incoming.getGeneration().getChestsPerChunk());
        current.getGeneration().setChunkSpawnerMode(incoming.getGeneration().getChunkSpawnerMode());
        current.getGeneration().setMinChestSpawnDepth(incoming.getGeneration().getMinChestSpawnDepth());
        current.getGeneration().setMaxChestSpawnDepth(incoming.getGeneration().getMaxChestSpawnDepth());
        current.getGeneration().setInitialChunks(incoming.getGeneration().getInitialChunks());
        current.getGeneration().setChunkLayerSpawnRate(incoming.getGeneration().getChunkLayerSpawnRate());
        current.getGeneration().setInitialChunkBiomes(incoming.getGeneration().getInitialChunkBiomes());

        current.getDifficulty().getHardMode().setEnabled(incoming.getDifficulty().getHardMode().isEnabled());
        current.getDifficulty().getHardMode().setEnforceChunkBoundaries(incoming.getDifficulty().getHardMode().isEnforceChunkBoundaries());
        current.getDifficulty().getHardMode().setDisableVillages(incoming.getDifficulty().getHardMode().isDisableVillages());
        current.getDifficulty().getHardMode().setSpawnInitialEngine(incoming.getDifficulty().getHardMode().isSpawnInitialEngine());
        current.getDifficulty().getHardMode().setInitialEngineFuel(incoming.getDifficulty().getHardMode().isInitialEngineFuel());
        current.getDifficulty().getHardMode().setDisableChestsCommand(incoming.getDifficulty().getHardMode().isDisableChestsCommand());
        current.getDifficulty().setEngineRequiresFuel(incoming.getDifficulty().isEngineRequiresFuel());
        current.getDifficulty().setExperimentalChunkLimit(incoming.getDifficulty().isExperimentalChunkLimit());
        current.getDifficulty().setEnableProgressionHelper(incoming.getDifficulty().isEnableProgressionHelper());
        current.getDifficulty().setStartRestriction(incoming.getDifficulty().getStartRestriction());
        current.getDifficulty().setStartingBiome(incoming.getDifficulty().getStartingBiome());
        current.getDifficulty().setAlwaysSpawnVillage(incoming.getDifficulty().isAlwaysSpawnVillage());
        current.getDifficulty().setSpawnNewChunkChest(incoming.getDifficulty().spawnNewChunkChest());
        current.getDifficulty().setSpawnChunkStrip(incoming.getDifficulty().isSpawnChunkStrip());
        current.getDifficulty().setSpawnChestInInitialChunkOnly(incoming.getDifficulty().spawnChestInInitialChunkOnly());

        current.getGameplayConfig().setBlockPlacementAllowedOutsideSpawnedChunks(incoming.getGameplayConfig().isBlockPlacementAllowedOutsideSpawnedChunks());
        current.getGameplayConfig().setChunkSpawnLeafDecayDisabled(incoming.getGameplayConfig().isChunkSpawnLeafDecayDisabled());
        current.getGameplayConfig().setEnableChunkBarriers(incoming.getGameplayConfig().isEnableChunkBarriers());
        current.getGameplayConfig().setUnstableChunkChance(incoming.getGameplayConfig().getUnstableChunkChance());
        current.getGameplayConfig().setEnableWorldFragmentOre(incoming.getGameplayConfig().isEnableWorldFragmentOre());
        current.getGameplayConfig().setEnableCaveScanner(incoming.getGameplayConfig().isEnableCaveScanner());

        current.getGatheringChunksConfig().setMobsDropFragments(incoming.getGatheringChunksConfig().isMobsDropFragments());
        current.getGatheringChunksConfig().setFragmentDropChance(incoming.getGatheringChunksConfig().getFragmentDropChance());
        current.getGatheringChunksConfig().setMinFragmentDrop(incoming.getGatheringChunksConfig().getMinFragmentDrop());
        current.getGatheringChunksConfig().setMaxFragmentDrop(incoming.getGatheringChunksConfig().getMaxFragmentDrop());

        current.getGeneration().setFragmentOreEnabled(incoming.getGeneration().isFragmentOreEnabled());
        current.getGeneration().setFragmentOreMinHeight(incoming.getGeneration().getFragmentOreMinHeight());
        current.getGeneration().setFragmentOreMaxHeight(incoming.getGeneration().getFragmentOreMaxHeight());
        current.getGeneration().setFragmentOreVeinSize(incoming.getGeneration().getFragmentOreVeinSize());
        current.getGeneration().setFragmentOreCount(incoming.getGeneration().getFragmentOreCount());

        current.getGatheringChunksConfig().setAutoSpawnTrees(incoming.getGatheringChunksConfig().isAutoSpawnTrees());
        current.getGatheringChunksConfig().setPreventFluidFlowIntoVoid(incoming.getGatheringChunksConfig().isPreventFluidFlowIntoVoid());
    }

    private static void scheduleSpawnTeleport(net.minecraft.server.MinecraftServer server, UUID playerUUID,
            int startTick, int maxWaitTicks) {
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 5, () -> {
            if (!server.isRunning()) return;

            ServerPlayer p = server.getPlayerList().getPlayer(playerUUID);
            if (p == null) return;

            ServerLevel lvl = p.serverLevel();
            if (!lvl.dimension().equals(Level.OVERWORLD)) return;
            if (!(lvl.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) return;

            BlockPos spawnPos = lvl.getSharedSpawnPos();
            ChunkPos spawnChunk = new ChunkPos(spawnPos);

            boolean chunkReady = !SpawnChunkHelper.isEmptyChunk(lvl, spawnChunk);

            int ticksWaited = server.getTickCount() - startTick;

            if (!chunkReady && ticksWaited < maxWaitTicks) {
                if (ticksWaited % 20 == 0) {
                    LOGGER.info("[FirstJoin] Waiting for spawn chunk [{},{}] to be placed... ({}t elapsed)",
                            spawnChunk.x, spawnChunk.z, ticksWaited);
                }
                scheduleSpawnTeleport(server, playerUUID, startTick, maxWaitTicks);
                return;
            }

            if (!chunkReady) {
                LOGGER.warn("[FirstJoin] Spawn chunk still empty after {}t — teleporting anyway", ticksWaited);
            } else {
                LOGGER.info("[FirstJoin] Spawn chunk [{},{}] is ready after {}t — teleporting player",
                        spawnChunk.x, spawnChunk.z, ticksWaited);
            }

            int safeY;
            LevelChunk spawnLevelChunk = lvl.getChunkAt(spawnChunk.getMiddleBlockPosition(0));
            if (spawnLevelChunk != null) {
                int candidateY = ChunkUtil.getSafeSpawnHeight(
                        spawnLevelChunk, spawnChunk.getMiddleBlockX(), spawnChunk.getMiddleBlockZ());
                if (candidateY > lvl.getMinBuildHeight() + 10) {
                    safeY = candidateY;
                } else {
                    safeY = spawnLevelChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8) + 1;
                }
            } else {
                safeY = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        spawnChunk.getMiddleBlockX(), spawnChunk.getMiddleBlockZ()) + 1;
            }

            p.teleportTo(lvl,
                    spawnChunk.getMiddleBlockX() + 0.5,
                    safeY,
                    spawnChunk.getMiddleBlockZ() + 0.5,
                    p.getYRot(),
                    p.getXRot());
        }));
    }
}
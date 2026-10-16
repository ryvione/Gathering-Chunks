/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.mixins;

import com.ryvione.gatheringchunks.client.CreateWorldSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Optional;


@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {

    @Inject(method = "createNewWorld", at = @At("HEAD"))
    private void gatheringChunksBeginNewWorld(LayeredRegistryAccess<RegistryLayer> registries, WorldData data, CallbackInfoReturnable<Boolean> cir) {
        CreateWorldSettings.beginNewWorld((CreateWorldScreen) (Object) this);
    }

    @Inject(method = "createNewWorldDirectory", at = @At("RETURN"))
    private static void gatheringChunksSaveNewWorld(Minecraft minecraft, String name, Path tempDataPackDir,
                                                    CallbackInfoReturnable<Optional<LevelStorageAccess>> cir) {
        Optional<LevelStorageAccess> access = cir.getReturnValue();
        if (access.isPresent()) {
            CreateWorldSettings.saveToNewWorld(access.get().getLevelPath(LevelResource.ROOT));
        } else {
            CreateWorldSettings.cancelNewWorld();
        }
    }
}

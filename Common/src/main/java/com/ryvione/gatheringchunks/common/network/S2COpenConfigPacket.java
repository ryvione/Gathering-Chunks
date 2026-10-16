/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;

public record S2COpenConfigPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2COpenConfigPacket> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "open_config"));
    public static final StreamCodec<ByteBuf, S2COpenConfigPacket> CODEC = StreamCodec
            .unit(new S2COpenConfigPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
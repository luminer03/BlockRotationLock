package com.blockrotationlock;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class PlacementPacket {

    private PacketByteBuf placementData;

    private PlacementPacket(PacketByteBuf data) {
        placementData = data;
    }

    public static PlacementPacket fromPacket(PacketByteBuf input){
        return new PlacementPacket(input);
    }

    public void toPacket(PacketByteBuf output)
    {
        output.writeVarInt(13);
        output.writeBytes(placementData);
    }

    public record Payload(PlacementPacket data) implements CustomPayload
    {
        public static final Id<Payload> ID = new Id<>(Identifier.of("servux", "tweaks"));
        public static final PacketCodec<PacketByteBuf, Payload> CODEC = CustomPayload.codecOf(Payload::write, Payload::new);

        public Payload(PacketByteBuf input)
        {
            this(fromPacket(input));
        }

        private void write(PacketByteBuf output)
        {
            data.toPacket(output);
        }

        @Override
        public Id<? extends CustomPayload> getId()
        {
            return ID;
        }
    }
}
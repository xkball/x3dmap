package com.xkball.x3dmap.network;

import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.network.c2s.RequestServerChunk;
import com.xkball.x3dmap.network.s2c.SentChunkToClient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber
public class X3dMapNetwork {
    
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var register = event.registrar(X3dMap.MODID);
        register.playToServer(RequestServerChunk.TYPE, RequestServerChunk.STREAM_CODEC, RequestServerChunk::handle);
        register.playToClient(SentChunkToClient.TYPE, SentChunkToClient.STREAM_CODEC, SentChunkToClient::handle);
    }
}

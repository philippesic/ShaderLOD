package com.pp.shaderlod.experiment.bsl;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.security.MessageDigest;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.irisshaders.iris.mixin.texture.SpriteContentsAccessor;
import net.irisshaders.iris.pbr.texture.PBRAtlasTexture;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import net.irisshaders.iris.pbr.texture.PBRType;
import net.irisshaders.iris.pbr.texture.TextureAtlasExtension;

/** Finite DEV discovery on actual loaded objects. Does not load a holder, create
 * tickers, upload anything, or admit a certificate. Archive/source/options/model
 * joins and all lifecycle epochs remain the explicit integrating caller's job. */
public final class BslPrefixSpriteDiscovery {
    public record Sprite(Object pbrSprite,Object baseSprite,Object winningResource,Object resourceSource,
                         String sourcePackId,String name,String resourceSha256,
                         BslPrefixCertificate.Rect rect) {}
    public record Found(TextureAtlas base,PBRAtlasTexture normal,List<Sprite> sprites) {}
    public static Found current(TextureAtlas base,ResourceManager resources) throws Exception {
        var holder=PBRTextureManager.INSTANCE.getHolder(base.getId()); // never getOrLoadHolder
        var atlasHolder=((TextureAtlasExtension)base).getPBRHolder();
        var expectedAtlasId=ResourceLocation.fromNamespaceAndPath(base.location().getNamespace(),
            base.location().getPath().replace(".png", "")+"_n.png");
        if(holder==null||!(holder.normalTexture() instanceof PBRAtlasTexture normal)
           ||normal.getType()!=PBRType.NORMAL||!TextureAtlas.LOCATION_BLOCKS.equals(base.location())
           ||!expectedAtlasId.equals(normal.getAtlasId())||atlasHolder==null||atlasHolder.getNormalAtlas()!=normal)
            throw new IllegalStateException("actual normal atlas unavailable");
        String[] names={"stone","cobblestone","oak_planks"};
        String[] pins={"bb90d452d9faee0e3d8fe6d3808a635de080d9c3ee17a29ddb50cbf1e41d272d",
                       "848867dfaee7312905fc706fdd25d0509bf5dd593a1e8231c631bae08c94b8f6",
                       "d737192a591b494f5a1e6611203a0a6a90da5ec833374d89077b0e6a4d79c3af"};
        List<Sprite> selected=new ArrayList<>(3);
        for(int i=0;i<3;i++) {
            var name=ResourceLocation.fromNamespaceAndPath("minecraft","block/"+names[i]+"_n");
            var sprite=normal.getSprite(name);
            var expectedBase=base.getSprite(ResourceLocation.fromNamespaceAndPath("minecraft","block/"+names[i]));
            if(sprite==null||sprite.getBaseSprite()!=expectedBase
               ||!name.equals(sprite.contents().name())
               ||((SpriteContentsAccessor)sprite.contents()).getAnimatedTexture()!=null
               ||((SpriteContentsAccessor)expectedBase.contents()).getAnimatedTexture()!=null)
                throw new IllegalStateException("unknown/replaced/animated actual sprite");
            var path=ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/"+names[i]+"_n.png");
            var winner=resources.getResource(path).orElseThrow();
            byte[] bytes;try(var stream=winner.open()){bytes=stream.readAllBytes();}
            var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!pins[i].equals(hash))throw new IllegalStateException("winning normal resource differs from pinned Replete");
            int x=sprite.getX(),y=sprite.getY(),w=sprite.contents().width(),h=sprite.contents().height();
            if(w<1||h<1||w>64||h>64)throw new IllegalStateException("unexpected actual sprite size");
            selected.add(new Sprite(sprite,expectedBase,winner,winner.source(),winner.sourcePackId(),
                name.toString(),hash,new BslPrefixCertificate.Rect(x,y,x+w-1,y+h-1)));
        }
        return new Found(base,normal,List.copyOf(selected));
    }
    private BslPrefixSpriteDiscovery() {}
}

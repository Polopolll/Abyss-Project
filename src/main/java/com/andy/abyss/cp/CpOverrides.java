package com.andy.abyss.cp;

import com.andy.abyss.Abyss;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Datapack overrides: data/&lt;namespace&gt;/abyss_cp/&lt;any&gt;.json with
 * {"overrides": [{"entity": "iceandfire:fire_dragon", "multiply": 1.5}, {"tag": "forge:bosses", "add": 500}, {"entity": "x:y", "set": 9000}]}.
 */
public class CpOverrides extends SimpleJsonResourceReloadListener {
    record Rule(ResourceLocation entity, TagKey<EntityType<?>> tag, double multiply, double add, double set) {
        boolean matches(LivingEntity e) {
            if (entity != null) return entity.equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
            return tag != null && e.getType().is(tag);
        }
    }
    private static final List<Rule> RULES = new ArrayList<>();

    public CpOverrides() { super(new Gson(), "abyss_cp"); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller p) {
        RULES.clear();
        for (var en : map.entrySet()) {
            try {
                JsonObject o = en.getValue().getAsJsonObject();
                for (JsonElement x : GsonHelper.getAsJsonArray(o, "overrides")) {
                    JsonObject r = x.getAsJsonObject();
                    ResourceLocation ent = r.has("entity") ? new ResourceLocation(GsonHelper.getAsString(r, "entity")) : null;
                    TagKey<EntityType<?>> tag = null;
                    if (r.has("tag")) {
                        String t = GsonHelper.getAsString(r, "tag");
                        if (t.startsWith("#")) t = t.substring(1);
                        tag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(t));
                    }
                    RULES.add(new Rule(ent, tag, GsonHelper.getAsDouble(r, "multiply", 1), GsonHelper.getAsDouble(r, "add", 0), GsonHelper.getAsDouble(r, "set", -1)));
                }
            } catch (Exception ex) { Abyss.LOG.warn("Bad abyss_cp file {}: {}", en.getKey(), ex.getMessage()); }
        }
        Abyss.LOG.info("Abyss: loaded {} Combat Power overrides", RULES.size());
    }

    static double multiplier(LivingEntity e) { double m = 1; for (Rule r : RULES) if (r.matches(e)) m *= r.multiply; return m; }
    static double add(LivingEntity e) { double a = 0; for (Rule r : RULES) if (r.matches(e)) a += r.add; return a; }
    static double set(LivingEntity e) { double s = -1; for (Rule r : RULES) if (r.matches(e) && r.set > 0) s = r.set; return s; }
}

package gg.moonflower.pollen.api.datagen.provider.tags;

import com.google.gson.JsonObject;
import gg.moonflower.pollen.api.datagen.provider.ConditionalDataProvider;
import gg.moonflower.pollen.api.resource.condition.PollinatedResourceConditionProvider;
import gg.moonflower.pollen.api.util.PollinatedModContainer;
import gg.moonflower.pollen.core.mixin.data.TagsProviderAccessor;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps {@link TagsProvider} to enable optional tags while still extending {@link EntityTypeTagsProvider}.
 *
 * @author Ocelot
 * @since 1.0.0
 */
public class PollinatedEntityTypeTagsProvider extends EntityTypeTagsProvider implements ConditionalDataProvider {

    private final Map<ResourceLocation, List<PollinatedResourceConditionProvider>> providers;
    private final String domain;

    public PollinatedEntityTypeTagsProvider(DataGenerator dataGenerator, PollinatedModContainer container) {
        super(dataGenerator);
        this.providers = new HashMap<>();
        this.domain = container.getId();
    }

    /**
     * Adds a condition to the specified tag.
     *
     * @param tag       The tag to add conditions to
     * @param providers The conditions to add
     */
    public void addConditions(TagKey<EntityType<?>> tag, PollinatedResourceConditionProvider... providers) {
        this.addConditions(tag.location(), providers);
    }

    @Override
    public void addConditions(ResourceLocation id, PollinatedResourceConditionProvider... providers) {
        if (providers.length == 0)
            return;
        this.providers.computeIfAbsent(id, __ -> new ArrayList<>()).addAll(Arrays.asList(providers));
    }

    @Override
    public void injectConditions(ResourceLocation id, JsonObject json) {
        if (this.providers.containsKey(id))
            PollinatedResourceConditionProvider.write(json, this.providers.get(id).toArray(new PollinatedResourceConditionProvider[0]));
    }

    @Override
    protected PollinatedTagsProvider.PollinatedTagAppender<EntityType<?>> tag(TagKey<EntityType<?>> tag) {
        return new PollinatedTagsProvider.PollinatedTagAppender<>(this.getOrCreateRawBuilder(tag), this.registry, this.domain);
    }

    @Override
    protected Tag.Builder getOrCreateRawBuilder(TagKey<EntityType<?>> tag) {
        return ((TagsProviderAccessor) this).getBuilders().computeIfAbsent(tag.location(), __ -> new Tag.Builder());
    }
}

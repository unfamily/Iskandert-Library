package net.unfamily.iskalib.fluid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.Event;

/**
 * Events fired by {@link StickyFluidEffects}. Consumer mods (e.g. Utilities) may award advancements
 * or apply extra effects without owning the sticky/crude config lists.
 */
public final class StickyFluidEvents {
    private StickyFluidEvents() {}

    /** Fired on the logical server when an entity is standing in a configured crude oil fluid. */
    public static final class InCrudeOil extends Event {
        private final Entity entity;
        private final FluidState fluidState;

        public InCrudeOil(Entity entity, FluidState fluidState) {
            this.entity = entity;
            this.fluidState = fluidState;
        }

        public Entity getEntity() {
            return entity;
        }

        public FluidState getFluidState() {
            return fluidState;
        }
    }
}

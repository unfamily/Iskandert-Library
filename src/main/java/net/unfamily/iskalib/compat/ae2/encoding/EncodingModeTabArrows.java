package net.unfamily.iskalib.compat.ae2.encoding;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import appeng.util.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.TabButton;

/**
 * Adaptive UP/DOWN arrow buttons for AE2 Pattern Encoding mode tabs when more than 4 modes
 * are registered. Arrows replace the first/last column slots; there is no scroll bar/wheel UI.
 *
 * <p>Optional AE2 integration — call only when AE2 is loaded.
 */
public final class EncodingModeTabArrows {

    private static final int SLOT_COUNT = 4;
    private static final WeakHashMap<PatternEncodingTermScreen<?>, State> STATES = new WeakHashMap<>();

    private EncodingModeTabArrows() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded("ae2");
    }

    /**
     * Attach arrow management to a Pattern Encoding Terminal screen.
     *
     * @param ae2Tabs mode tabs already created by AE2, in display order (typically enum order)
     * @return UP/DOWN arrow buttons the caller must add as screen widgets ({@code addRenderableWidget})
     */
    @Nullable
    public static ArrowPair attach(PatternEncodingTermScreen<?> screen, List<TabButton> ae2Tabs) {
        if (!isAvailable() || screen == null || ae2Tabs == null || ae2Tabs.isEmpty()) {
            return null;
        }
        State state = new State(screen, new ArrayList<>(ae2Tabs));
        STATES.put(screen, state);
        state.captureSlotsIfNeeded();
        state.layout();
        return new ArrowPair(state.upArrow, state.downArrow);
    }

    /** UP/DOWN arrow widgets created by {@link #attach}. */
    public record ArrowPair(IconButton up, IconButton down) {
    }

    /** Register an extra encoding tab (e.g. Matter) after {@link #attach}. */
    public static void registerExtraTab(PatternEncodingTermScreen<?> screen, TabButton tab) {
        State state = STATES.get(screen);
        if (state == null || tab == null) {
            return;
        }
        if (!state.extraTabs.contains(tab)) {
            state.extraTabs.add(tab);
        }
        state.layout();
    }

    /** Recompute visibility/positions. Call from {@code updateBeforeRender}. */
    public static void layout(PatternEncodingTermScreen<?> screen) {
        State state = STATES.get(screen);
        if (state != null) {
            state.layout();
        }
    }

    private static final class State {
        private final PatternEncodingTermScreen<?> screen;
        private final List<TabButton> ae2Tabs;
        private final List<TabButton> extraTabs = new ArrayList<>();
        private final ArrowBtn upArrow;
        private final ArrowBtn downArrow;
        /** Slot positions relative to guiLeft / guiTop. */
        private final int[] slotRelX = new int[SLOT_COUNT];
        private final int[] slotRelY = new int[SLOT_COUNT];
        private boolean slotsCaptured;
        private int offset;

        private State(PatternEncodingTermScreen<?> screen, List<TabButton> ae2Tabs) {
            this.screen = screen;
            this.ae2Tabs = ae2Tabs;
            this.upArrow = new ArrowBtn(Icon.ARROW_UP, btn -> {
                if (offset > 0) {
                    offset--;
                    layout();
                }
            });
            this.downArrow = new ArrowBtn(Icon.ARROW_DOWN, btn -> {
                if (offset < maxOffset()) {
                    offset++;
                    layout();
                }
            });
            this.upArrow.visible = false;
            this.downArrow.visible = false;
        }

        private List<TabButton> allTabs() {
            List<TabButton> all = new ArrayList<>(ae2Tabs.size() + extraTabs.size());
            all.addAll(ae2Tabs);
            all.addAll(extraTabs);
            return all;
        }

        /**
         * Capture AE2 tab positions relative to the GUI origin. Deferred until tabs are
         * actually laid out (constructor often still has 0,0 absolute coords).
         */
        private void captureSlotsIfNeeded() {
            if (slotsCaptured || ae2Tabs.isEmpty()) {
                return;
            }
            int guiLeft = screen.getGuiLeft();
            int guiTop = screen.getGuiTop();
            // AE2 styles tabs after init; until then getX/getY stay at origin.
            if (guiLeft == 0 && guiTop == 0) {
                boolean allAtOrigin = true;
                for (TabButton tab : ae2Tabs) {
                    if (tab.getX() != 0 || tab.getY() != 0) {
                        allAtOrigin = false;
                        break;
                    }
                }
                if (allAtOrigin) {
                    return;
                }
            }

            int n = Math.min(SLOT_COUNT, ae2Tabs.size());
            for (int i = 0; i < n; i++) {
                if (ae2Tabs.get(i).getWidth() <= 0) {
                    return;
                }
            }

            boolean allAbsOrigin = true;
            for (int i = 0; i < n; i++) {
                TabButton tab = ae2Tabs.get(i);
                if (tab.getX() != 0 || tab.getY() != 0) {
                    allAbsOrigin = false;
                    break;
                }
            }
            if (allAbsOrigin) {
                return;
            }

            for (int i = 0; i < SLOT_COUNT; i++) {
                int src = Math.min(i, ae2Tabs.size() - 1);
                TabButton tab = ae2Tabs.get(src);
                slotRelX[i] = tab.getX() - guiLeft;
                if (i < ae2Tabs.size()) {
                    slotRelY[i] = ae2Tabs.get(i).getY() - guiTop;
                } else {
                    slotRelY[i] = (tab.getY() - guiTop) + (i - src) * 21;
                }
            }
            if (ae2Tabs.size() >= 2) {
                int step = ae2Tabs.get(1).getY() - ae2Tabs.get(0).getY();
                if (step != 0) {
                    int baseRelX = ae2Tabs.get(0).getX() - guiLeft;
                    int baseRelY = ae2Tabs.get(0).getY() - guiTop;
                    for (int i = ae2Tabs.size(); i < SLOT_COUNT; i++) {
                        slotRelX[i] = baseRelX;
                        slotRelY[i] = baseRelY + step * i;
                    }
                }
            }
            slotsCaptured = true;
        }

        private int maxOffset() {
            int total = allTabs().size();
            if (total <= SLOT_COUNT) {
                return 0;
            }
            // With both arrows visible, two middle slots show modes.
            return Math.max(0, total - 2);
        }

        private void layout() {
            captureSlotsIfNeeded();
            // Before a valid capture, leave AE2 vanilla tab positions alone.
            if (!slotsCaptured) {
                return;
            }

            List<TabButton> all = allTabs();
            int total = all.size();

            for (TabButton tab : all) {
                tab.visible = false;
            }
            upArrow.visible = false;
            downArrow.visible = false;
            upArrow.active = false;
            downArrow.active = false;

            if (total <= SLOT_COUNT) {
                offset = 0;
                for (int i = 0; i < total; i++) {
                    place(all.get(i), i);
                }
                return;
            }

            offset = Math.max(0, Math.min(offset, maxOffset()));

            boolean showUp = offset > 0;
            int avail = SLOT_COUNT - (showUp ? 1 : 0);
            int remaining = total - offset;
            boolean showDown = remaining > avail;
            int modeSlots = showDown ? avail - 1 : Math.min(avail, remaining);

            while (offset > 0 && offset + Math.max(modeSlots, 0) > total) {
                offset--;
                showUp = offset > 0;
                avail = SLOT_COUNT - (showUp ? 1 : 0);
                remaining = total - offset;
                showDown = remaining > avail;
                modeSlots = showDown ? avail - 1 : Math.min(avail, remaining);
            }
            if (modeSlots < 0) {
                modeSlots = 0;
            }

            int slot = 0;
            if (showUp) {
                placeArrow(upArrow, slot++);
            }
            for (int i = 0; i < modeSlots; i++) {
                int idx = offset + i;
                if (idx >= 0 && idx < total) {
                    place(all.get(idx), slot++);
                }
            }
            if (showDown) {
                placeArrow(downArrow, Math.min(slot, SLOT_COUNT - 1));
            }
        }

        private void place(TabButton tab, int slotIndex) {
            if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
                return;
            }
            tab.setX(screen.getGuiLeft() + slotRelX[slotIndex]);
            tab.setY(screen.getGuiTop() + slotRelY[slotIndex]);
            tab.visible = true;
            tab.active = true;
        }

        private void placeArrow(ArrowBtn arrow, int slotIndex) {
            if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
                return;
            }
            arrow.setX(screen.getGuiLeft() + slotRelX[slotIndex] + 3);
            arrow.setY(screen.getGuiTop() + slotRelY[slotIndex] + 3);
            arrow.visible = true;
            arrow.active = true;
        }
    }

    private static final class ArrowBtn extends IconButton {
        private final Icon icon;

        private ArrowBtn(Icon icon, Button.OnPress onPress) {
            super(onPress);
            this.icon = icon;
            setMessage(icon == Icon.ARROW_UP
                    ? Component.translatable("gui.iska_lib.encoding_arrow.up")
                    : Component.translatable("gui.iska_lib.encoding_arrow.down"));
        }

        @Override
        protected Icon getIcon() {
            return icon;
        }
    }
}

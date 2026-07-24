package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.tangosHudLib.api.HudLibrary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class PlayerHudLayoutScreen extends Screen {
    private static final String NO_LAYOUT = "";

    private final Screen parent;
    private String selectedLayout = NO_LAYOUT;
    private Component status = Component.empty();
    private EditBox name;
    private boolean layoutMenuOpen;

    private PlayerHudLayoutScreen(Screen parent) {
        super(Component.translatable("marketguard.hud.layouts.title"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        //? if >=26.2 {
        client.gui.setScreen(new PlayerHudLayoutScreen(parent));
        //?} else {
        /*client.setScreen(new PlayerHudLayoutScreen(parent));*/
        //?}
    }

    @Override
    protected void init() {
        List<String> layouts = HudLibrary.layouts(MarketGuard.MOD_ID);
        if (!layouts.contains(selectedLayout)) {
            selectedLayout = layouts.isEmpty() ? NO_LAYOUT : layouts.getFirst();
        }

        int x = width / 2 - 155;
        addRenderableWidget(Button.builder(layoutSelectorLabel(), button -> {
                    layoutMenuOpen = !layoutMenuOpen;
                    rebuildWidgets();
                })
                .bounds(x, 42, 310, 20)
                .build());
        if (layoutMenuOpen) {
            LayoutList layoutList = new LayoutList(minecraft, 310,
                    Math.min(120, Math.max(24, layouts.size() * 22)), 64, this);
            layoutList.setX(x);
            layouts.forEach(layoutList::addLayout);
            addRenderableWidget(layoutList);
            return;
        }

        name = new EditBox(font, x, 70, 310, 20, Component.translatable("marketguard.hud.layouts.name"));
        name.setMaxLength(32);
        name.setHint(Component.translatable("marketguard.hud.layouts.name_hint"));
        if (!selectedLayout.isEmpty()) {
            name.setValue(selectedLayout);
        }
        addRenderableWidget(name);

        addAction(x, 98, "load", this::loadSelected);
        addAction(x + 157, 98, "edit", this::editSelected);
        addAction(x, 122, "save", this::saveNamed);
        addAction(x + 157, 122, "delete", this::deleteSelected);
        addAction(x, 146, "share", this::shareSelected);
        addAction(x + 157, 146, "import", this::importNamed);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, height - 28, 310, 20)
                .build());
    }

    private void addAction(int x, int y, String key, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable("marketguard.hud.layouts." + key), button -> action.run())
                .bounds(x, y, 153, 20)
                .build());
    }

    private Component layoutName(String value) {
        return value.isEmpty()
                ? Component.translatable("marketguard.hud.layouts.none")
                : Component.literal(value);
    }

    private Component layoutSelectorLabel() {
        return Component.translatable("marketguard.hud.layouts.saved")
                .append(": ")
                .append(layoutName(selectedLayout))
                .append(" ▾");
    }

    private void select(String value) {
        selectedLayout = value;
        layoutMenuOpen = false;
        if (!value.isEmpty()) {
            result(HudLibrary.loadLayout(MarketGuard.MOD_ID, value), "loaded", "load_failed");
        }
        rebuildWidgets();
    }

    private void loadSelected() {
        if (selectedLayout.isEmpty()) {
            fail("select_first");
            return;
        }
        result(HudLibrary.loadLayout(MarketGuard.MOD_ID, selectedLayout), "loaded", "load_failed");
    }

    private void editSelected() {
        if (selectedLayout.isEmpty() || !HudLibrary.loadLayout(MarketGuard.MOD_ID, selectedLayout)) {
            fail("select_first");
            return;
        }
        if (!HudLibrary.openEditor(MarketGuard.MOD_ID)) {
            fail("edit_failed");
            return;
        }
        success("editing");
    }

    private void saveNamed() {
        String layoutName = name.getValue().trim();
        if (!HudLibrary.saveLayout(MarketGuard.MOD_ID, layoutName)) {
            fail("invalid_name");
            return;
        }
        selectedLayout = layoutName;
        success("saved");
        rebuildWidgets();
    }

    private void deleteSelected() {
        if (selectedLayout.isEmpty() || !HudLibrary.deleteLayout(MarketGuard.MOD_ID, selectedLayout)) {
            fail("delete_failed");
            return;
        }
        selectedLayout = NO_LAYOUT;
        success("deleted");
        rebuildWidgets();
    }

    private void shareSelected() {
        String exported = selectedLayout.isEmpty() ? null : HudLibrary.exportLayout(MarketGuard.MOD_ID, selectedLayout);
        if (exported == null) {
            fail("share_failed");
            return;
        }
        minecraft.keyboardHandler.setClipboard(exported);
        success("copied");
    }

    private void importNamed() {
        String layoutName = name.getValue().trim();
        if (!HudLibrary.importLayout(MarketGuard.MOD_ID, layoutName, minecraft.keyboardHandler.getClipboard())) {
            fail("import_failed");
            return;
        }
        selectedLayout = layoutName;
        HudLibrary.loadLayout(MarketGuard.MOD_ID, layoutName);
        success("imported");
        rebuildWidgets();
    }

    private void result(boolean successful, String successKey, String failureKey) {
        if (successful) {
            success(successKey);
        } else {
            fail(failureKey);
        }
    }

    private void success(String key) {
        status = Component.translatable("marketguard.hud.layouts.status." + key).withStyle(ChatFormatting.GREEN);
    }

    private void fail(String key) {
        status = Component.translatable("marketguard.hud.layouts.status." + key).withStyle(ChatFormatting.RED);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        if (!status.getString().isEmpty()) {
            graphics.centeredText(font, status, width / 2, 174, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        //? if >=26.2 {
        minecraft.gui.setScreen(parent);
        //?} else {
        /*minecraft.setScreen(parent);*/
        //?}
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (layoutMenuOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            layoutMenuOpen = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class LayoutList extends ContainerObjectSelectionList<LayoutEntry> {
        private final PlayerHudLayoutScreen screen;

        private LayoutList(Minecraft minecraft, int width, int height, int y, PlayerHudLayoutScreen screen) {
            super(minecraft, width, height, y, 22);
            this.screen = screen;
            centerListVertically = false;
        }

        private void addLayout(String name) {
            addEntry(new LayoutEntry(this, name));
        }

        @Override
        public int getRowWidth() {
            return Math.max(40, getWidth() - 12);
        }
    }

    private static final class LayoutEntry extends ContainerObjectSelectionList.Entry<LayoutEntry> {
        private final LayoutList owner;
        private final String name;

        private LayoutEntry(LayoutList owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int background = hovered ? 0xAA555555 : 0xDD222222;
            graphics.fill(getContentX(), getContentY(), getContentRight(), getContentBottom() - 1, background);
            graphics.text(Minecraft.getInstance().font, Component.literal(name),
                    getContentX() + 8, getContentY() + 7, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return false;
            }
            owner.screen.select(name);
            return true;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}

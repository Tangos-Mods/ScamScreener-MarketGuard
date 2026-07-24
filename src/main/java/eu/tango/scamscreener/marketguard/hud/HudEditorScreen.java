package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.screen.HudScreenGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class HudEditorScreen extends Screen {
    private static final int SCREEN_BUTTON_WIDTH = 110;
    private static final int MIN_SCREEN_BUTTON_WIDTH = 90;
    private static final int SCREEN_BUTTON_GAP = 4;
    private static final int TABLE_GAP = 12;

    private final Screen parent;
    private final HudCustomization.HudId hud;
    private int rowsTop;
    private RowList visibleRows;
    private RowList hiddenRows;
    private RowEntry draggingEntry;
    private DropTarget dropTarget;

    private HudEditorScreen(Screen parent, HudCustomization.HudId hud) {
        super(Component.translatable("marketguard.hud.editor.title",
                Component.translatable("marketguard.hud." + hud.key())));
        this.parent = parent;
        this.hud = hud;
    }

    public static void open(Screen parent, HudCustomization.HudId hud) {
        Minecraft client = Minecraft.getInstance();
        //? if >=26.2 {
        client.gui.setScreen(new HudEditorScreen(parent, hud));
        //?} else {
        /*client.setScreen(new HudEditorScreen(parent, hud));*/
        //?}
    }

    @Override
    protected void init() {
        int columns = calculateScreenColumns(width);
        int screenButtonWidth = Math.min(SCREEN_BUTTON_WIDTH,
                (width - 20 - (columns - 1) * SCREEN_BUTTON_GAP) / columns);
        int gridWidth = columns * screenButtonWidth + (columns - 1) * SCREEN_BUTTON_GAP;
        int gridX = (width - gridWidth) / 2;

        int index = 0;
        for (HudScreenGroup group : HudScreenGroup.values()) {
            int x = gridX + (index % columns) * (screenButtonWidth + SCREEN_BUTTON_GAP);
            int y = 42 + (index / columns) * 24;
            Button button = Button.builder(screenLabel(group), pressed -> {
                        HudCustomization.toggleScreen(hud, group);
                        pressed.setMessage(screenLabel(group));
                    })
                    .bounds(x, y, screenButtonWidth, 20)
                    .build();
            addRenderableWidget(button);
            index++;
        }

        int baseRowsTop = calculateRowsTop(width);
        if (hud == HudCustomization.HudId.PLAYER) {
            addRenderableWidget(CycleButton.builder(this::presetLabel, MarketGuardConfig.getPlayerHudPreset())
                    .withValues(List.of("trade", "compact", "profile", "all"))
                    .create(width / 2 - 155, baseRowsTop - 34, 310, 20,
                            Component.translatable("marketguard.hud.player_preset"),
                            (button, value) -> setPlayerPreset(button, value)));
        }

        rowsTop = baseRowsTop + (hud == HudCustomization.HudId.PLAYER ? 28 : 0);
        int listHeight = calculateListHeight(height, rowsTop);
        int tableWidth = Math.min(920, width - 40);
        int columnWidth = (tableWidth - TABLE_GAP) / 2;
        int tableX = (width - tableWidth) / 2;

        visibleRows = new RowList(minecraft, columnWidth, listHeight, rowsTop, hud, true, this);
        visibleRows.setX(tableX);
        HudCustomization.editableRows(hud, true).forEach(visibleRows::addRow);
        addRenderableWidget(visibleRows);

        hiddenRows = new RowList(minecraft, columnWidth, listHeight, rowsTop, hud, false, this);
        hiddenRows.setX(tableX + columnWidth + TABLE_GAP);
        HudCustomization.editableRows(hud, false).forEach(hiddenRows::addRow);
        addRenderableWidget(hiddenRows);

        if (hud == HudCustomization.HudId.PLAYER) {
            addRenderableWidget(Button.builder(Component.translatable("marketguard.hud.layouts"),
                            button -> PlayerHudLayoutScreen.open(this))
                    .bounds(width / 2 - 154, height - 28, 98, 20)
                    .build());
            addRenderableWidget(resetButton(width / 2 - 51, 98));
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                    .bounds(width / 2 + 56, height - 28, 98, 20)
                    .build());
        } else {
            addRenderableWidget(resetButton(width / 2 - 154, 150));
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                    .bounds(width / 2 + 4, height - 28, 150, 20)
                    .build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("marketguard.hud.screens"), width / 2, 29, 0xFFAAAAAA);
        graphics.centeredText(font, Component.translatable("marketguard.hud.rows.drag_columns"), width / 2, rowsTop - 31, 0xFFAAAAAA);
        graphics.centeredText(font, Component.translatable("marketguard.hud.visible_rows"),
                visibleRows.getX() + visibleRows.getWidth() / 2, rowsTop - 15, 0xFF55FFFF);
        graphics.centeredText(font, Component.translatable("marketguard.hud.hidden_rows"),
                hiddenRows.getX() + hiddenRows.getWidth() / 2, rowsTop - 15, 0xFFAAAAAA);
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
    public boolean isPauseScreen() {
        return false;
    }

    private Component screenLabel(HudScreenGroup group) {
        Component screen = Component.translatable("marketguard.hud.screen." + group.name().toLowerCase());
        boolean enabled = HudCustomization.screenEnabled(hud, group);
        return Component.literal(enabled ? "✓ " : "✗ ")
                .withStyle(enabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED)
                .append(screen);
    }

    private Component presetLabel(String preset) {
        return Component.translatable("marketguard.midnightconfig.enum.PlayerHudPreset." + preset);
    }

    private void setPlayerPreset(CycleButton<String> button, String preset) {
        String previous = MarketGuardConfig.getPlayerHudPreset();
        MarketGuardConfig.setPlayerHudPreset(preset);
        PlayerHud.setPreset(preset);
        if (!MarketGuardConfig.save()) {
            MarketGuardConfig.setPlayerHudPreset(previous);
            PlayerHud.setPreset(previous);
            button.setValue(previous);
        }
    }

    private Button resetButton(int x, int buttonWidth) {
        return Button.builder(Component.translatable("marketguard.hud.reset"), button -> {
                    HudCustomization.reset(hud);
                    rebuildWidgets();
                })
                .bounds(x, height - 28, buttonWidth, 20)
                .build();
    }

    static int calculateScreenColumns(int screenWidth) {
        return Math.max(1, Math.min(4, (screenWidth - 20 + SCREEN_BUTTON_GAP)
                / (MIN_SCREEN_BUTTON_WIDTH + SCREEN_BUTTON_GAP)));
    }

    static int calculateRowsTop(int screenWidth) {
        int columns = calculateScreenColumns(screenWidth);
        int screenRows = (HudScreenGroup.values().length + columns - 1) / columns;
        return 42 + screenRows * 24 + 38;
    }

    static int calculateListHeight(int screenHeight, int rowsTop) {
        return Math.max(30, screenHeight - rowsTop - 40);
    }

    private void beginDrag(RowEntry entry) {
        draggingEntry = entry;
        updateDropTarget(entry.owner, entry.owner.children().indexOf(entry));
    }

    private void updateDrag(MouseButtonEvent event) {
        if (draggingEntry == null) {
            return;
        }

        DropTarget target = visibleRows.dropTarget(event.x(), event.y());
        if (target == null) {
            target = hiddenRows.dropTarget(event.x(), event.y());
        }
        dropTarget = target;
    }

    private void updateDropTarget(RowList rows, int index) {
        dropTarget = new DropTarget(rows, index);
    }

    private void finishDrag(MouseButtonEvent event) {
        if (draggingEntry == null) {
            return;
        }

        updateDrag(event);
        RowEntry entry = draggingEntry;
        DropTarget target = dropTarget;
        draggingEntry = null;
        dropTarget = null;
        if (target == null) {
            return;
        }

        int targetIndex = target.index();
        if (entry.owner == target.rows()) {
            int sourceIndex = entry.owner.children().indexOf(entry);
            if (sourceIndex < targetIndex) {
                targetIndex--;
            }
        }
        HudCustomization.placeRow(hud, entry.row, target.rows().enabled, targetIndex);
        rebuildWidgets();
    }

    private boolean isDragging(RowEntry entry) {
        return draggingEntry == entry;
    }

    private boolean isDropTarget(RowList rows) {
        return dropTarget != null && dropTarget.rows() == rows;
    }

    private int dropIndex(RowList rows) {
        return isDropTarget(rows) ? dropTarget.index() : -1;
    }

    private record DropTarget(RowList rows, int index) {}

    private static final class RowList extends ContainerObjectSelectionList<RowEntry> {
        private final HudCustomization.HudId hud;
        private final boolean enabled;
        private final HudEditorScreen editor;

        private RowList(Minecraft minecraft, int width, int height, int y, HudCustomization.HudId hud,
                        boolean enabled, HudEditorScreen editor) {
            super(minecraft, width, height, y, 26);
            this.hud = hud;
            this.enabled = enabled;
            this.editor = editor;
            centerListVertically = false;
        }

        private void addRow(String row) {
            addEntry(new RowEntry(this, row));
        }

        private DropTarget dropTarget(double mouseX, double mouseY) {
            if (!isMouseOver(mouseX, mouseY)) {
                return null;
            }

            RowEntry entry = getEntryAtPosition(mouseX, mouseY);
            if (entry == null) {
                return new DropTarget(this, children().size());
            }
            int index = children().indexOf(entry);
            if (mouseY >= entry.getContentYMiddle()) {
                index++;
            }
            return new DropTarget(this, index);
        }

        @Override
        public int getRowWidth() {
            return Math.max(40, getWidth() - 12);
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            if (children().isEmpty()) {
                graphics.centeredText(minecraft.font, Component.translatable("marketguard.hud.drop_here"),
                        getX() + getWidth() / 2, getY() + 12, 0xFF777777);
            }
            if (!editor.isDropTarget(this)) {
                return;
            }

            graphics.outline(getX(), getY(), getRight(), getBottom(), 0xFF55FFFF);
            int index = editor.dropIndex(this);
            int lineY = index >= children().size()
                    ? (children().isEmpty() ? getY() + 4 : children().getLast().getContentBottom())
                    : children().get(index).getContentY();
            graphics.fill(getRowLeft(), lineY - 1, getRowRight(), lineY + 1, 0xFF55FFFF);
        }
    }

    private static final class RowEntry extends ContainerObjectSelectionList.Entry<RowEntry> {
        private final RowList owner;
        private final String row;

        private RowEntry(RowList owner, String row) {
            this.owner = owner;
            this.row = row;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int background = owner.editor.isDragging(this) ? 0xAA3A6680 : hovered ? 0x88444444 : 0x66000000;
            graphics.fill(getContentX(), getContentY(), getContentRight(), getContentBottom() - 2, background);
            graphics.text(Minecraft.getInstance().font, Component.literal("↕"),
                    getContentX() + 8, getContentY() + 7, 0xFFAAAAAA);

            Font font = Minecraft.getInstance().font;
            Component example = HudCustomization.example(owner.hud, row);
            List<FormattedCharSequence> lines = font.split(example, Math.max(10, getContentWidth() - 38));
            graphics.text(font, lines.getFirst(), getContentX() + 28, getContentY() + 7, 0xFFFFFFFF);

            if (hovered && owner.editor.draggingEntry == null) {
                Component rowName = Component.translatable("marketguard.hud.row." + row);
                graphics.setTooltipForNextFrame(font,
                        Component.translatable("marketguard.hud.row.tooltip", rowName), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }
            owner.editor.beginDrag(this);
            return true;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (!owner.editor.isDragging(this)) {
                return false;
            }
            owner.editor.updateDrag(event);
            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (!owner.editor.isDragging(this)) {
                return false;
            }
            owner.editor.finishDrag(event);
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

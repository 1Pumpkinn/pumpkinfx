package net.saturn.pumpkinfx.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.saturn.pumpkinfx.client.EffectPreview;
import net.saturn.pumpkinfx.PumpkinFxClient;
import net.saturn.pumpkinfx.export.ExportService;
import net.saturn.pumpkinfx.export.PackExporter;
import net.saturn.pumpkinfx.model.DirectionMode;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EmitterDefinition;
import net.saturn.pumpkinfx.model.EmitterShape;
import net.saturn.pumpkinfx.storage.EffectStore;

import java.io.IOException;

public final class EditorScreen extends Screen {
    private enum Page {
        GENERAL("General"),
        SHAPE("Shape"),
        MOTION("Motion"),
        LOOK("Look"),
        CURVES("Curves");

        private final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private enum CurveTarget {
        RATE("Rate"),
        SPEED("Speed"),
        SIZE("Size");

        private final String label;

        CurveTarget(String label) {
            this.label = label;
        }

        private CurveTarget next() {
            CurveTarget[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private static final int LEFT_W = 104;
    private static final int LIST_TOP = 34;
    private static final int ROW_H = 22;
    private static final int PANEL_X = 120;
    private static final int WHITE = 0xFFFFFFFF;

    private static EffectDefinition session;
    private static int selected;
    private static int listOffset;
    private static Page page = Page.GENERAL;
    private static CurveTarget curveTarget = CurveTarget.RATE;

    private FormBuilder form;
    private String status = "";

    public EditorScreen() {
        super(Component.literal("PumpkinFX Editor"));
    }

    public static void open(EffectDefinition effect) {
        session = effect;
        selected = 0;
        listOffset = 0;
        Minecraft.getInstance().gui.setScreen(new EditorScreen());
    }

    public static void openLast() {
        if (session == null) {
            session = EffectDefinition.createDefault("untitled");
        }
        Minecraft.getInstance().gui.setScreen(new EditorScreen());
    }

    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (session == null) {
            session = EffectDefinition.createDefault("untitled");
        }
        if (session.emitters.isEmpty()) {
            session.addEmitter();
        }
        selected = Math.max(0, Math.min(selected, session.emitters.size() - 1));

        buildTopBar();
        buildEmitterList();
        buildTabs();
        buildPage();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Ticks", 124, 11, FormBuilder.LABEL_COLOR, false);
        graphics.text(this.font, "Emitters", 8, 25, FormBuilder.HEADER_COLOR, false);
        if (form != null) {
            form.render(graphics);
        }
        if (!status.isEmpty()) {
            graphics.text(this.font, status, PANEL_X, this.height - 14, WHITE, false);
        }
    }

    private void buildTopBar() {
        EditBox idBox = new EditBox(this.font, 8, 6, 110, 18, Component.literal("Effect id"));
        idBox.setMaxLength(32);
        idBox.setValue(session.id);
        FormBuilder.bind(idBox, s -> s.matches("[a-zA-Z0-9_\\-]*"), s -> session.id = s);
        addRenderableWidget(idBox);

        EditBox duration = new EditBox(this.font, 152, 6, 36, 18, Component.literal("Duration"));
        duration.setMaxLength(5);
        duration.setValue(Integer.toString(session.duration));
        FormBuilder.bind(duration, s -> s.matches(FormBuilder.UNSIGNED_INT), s -> {
            try {
                session.duration = Math.max(1, Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(duration);

        addRenderableWidget(Button.builder(Component.literal(loopLabel()), b -> {
            session.loop = !session.loop;
            b.setMessage(Component.literal(loopLabel()));
        }).bounds(192, 6, 54, 18).build());

        int w = 36;
        int x = this.width - 8 - w;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(x, 6, w, 18).build());
        x -= 48;
        addRenderableWidget(Button.builder(Component.literal("Export"), b -> export())
                .bounds(x, 6, 44, 18).build());
        x -= w + 4;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x, 6, w, 18).build());
        x -= w + 4;
        addRenderableWidget(Button.builder(Component.literal("View"), b -> {
            play();
            onClose();
        }).bounds(x, 6, w, 18).build());
        x -= w + 4;
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> EffectPreview.stop())
                .bounds(x, 6, w, 18).build());
        x -= w + 4;
        addRenderableWidget(Button.builder(Component.literal("Play"), b -> play())
                .bounds(x, 6, w, 18).build());
    }

    private String loopLabel() {
        return "Loop: " + (session.loop ? "On" : "Off");
    }

    private void buildEmitterList() {
        int bottomButtonsY = this.height - 26;
        int scrollY = this.height - 48;
        int visible = Math.max(1, (scrollY - LIST_TOP - 2) / ROW_H);
        int count = session.emitters.size();
        listOffset = Math.max(0, Math.min(listOffset, Math.max(0, count - visible)));

        for (int row = 0; row < visible && listOffset + row < count; row++) {
            int index = listOffset + row;
            EmitterDefinition emitter = session.emitters.get(index);
            String name = emitter.name.length() > 13 ? emitter.name.substring(0, 13) : emitter.name;
            String prefix = index == selected ? "> " : (emitter.enabled ? "" : "- ");
            addRenderableWidget(Button.builder(Component.literal(prefix + name), b -> {
                selected = index;
                rebuildWidgets();
            }).bounds(8, LIST_TOP + row * ROW_H, LEFT_W, 20).build());
        }

        Button up = Button.builder(Component.literal("Up"), b -> {
            listOffset--;
            rebuildWidgets();
        }).bounds(8, scrollY, 50, 18).build();
        up.active = listOffset > 0;
        addRenderableWidget(up);

        Button down = Button.builder(Component.literal("Down"), b -> {
            listOffset++;
            rebuildWidgets();
        }).bounds(62, scrollY, 50, 18).build();
        down.active = listOffset + visible < count;
        addRenderableWidget(down);

        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            session.addEmitter();
            selected = session.emitters.size() - 1;
            listOffset = Integer.MAX_VALUE;
            rebuildWidgets();
        }).bounds(8, bottomButtonsY, 30, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Dup"), b -> {
            if (session.duplicateEmitter(selected) != null) {
                selected++;
            }
            rebuildWidgets();
        }).bounds(42, bottomButtonsY, 36, 18).build());

        Button delete = Button.builder(Component.literal("Del"), b -> {
            session.removeEmitter(selected);
            rebuildWidgets();
        }).bounds(82, bottomButtonsY, 30, 18).build();
        delete.active = count > 1;
        addRenderableWidget(delete);
    }

    private void buildTabs() {
        Page[] pages = Page.values();
        int total = this.width - PANEL_X - 8;
        int tabW = total / pages.length;
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            String label = target == page ? "[" + target.label + "]" : target.label;
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                page = target;
                rebuildWidgets();
            }).bounds(PANEL_X + i * tabW, LIST_TOP, tabW - 2, 18).build());
        }
    }

    private void buildPage() {
        form = new FormBuilder(this.font, this::addRenderableWidget, PANEL_X, LIST_TOP + 26, this.width - PANEL_X - 8);
        EmitterDefinition e = session.emitters.get(selected);
        switch (page) {
            case GENERAL -> buildGeneral(e);
            case SHAPE -> buildShape(e);
            case MOTION -> buildMotion(e);
            case LOOK -> buildLook(e);
            case CURVES -> buildCurves(e);
        }
    }

    private void buildGeneral(EmitterDefinition e) {
        form.text("Name", e.name, v -> e.name = v, 24);
        form.toggle("Enabled", e.enabled, v -> e.enabled = v, null);
        form.particle(e.particle, v -> e.particle = v);
        form.integer("Delay", e.startDelay, v -> e.startDelay = v);
        form.integer("Duration", e.duration, v -> e.duration = v);
        form.toggle("Loop", e.loop, v -> e.loop = v, null);
        form.gap(0);
        form.integer("Burst", e.burstCount, v -> e.burstCount = v);
        form.integer("Burst tick", e.burstTick, v -> e.burstTick = v);
        form.note("Delay, duration and burst tick are in game ticks (20 per second).");
    }

    private void buildShape(EmitterDefinition e) {
        form.cycle("Shape", () -> e.shape.displayName(), () -> {
            e.shape = e.shape.next();
            rebuildWidgets();
        });
        form.toggle("Surface only", e.surfaceOnly, v -> e.surfaceOnly = v, null);
        form.number("Size X", e.shapeX, v -> e.shapeX = v);
        form.number("Size Y", e.shapeY, v -> e.shapeY = v);
        form.number("Size Z", e.shapeZ, v -> e.shapeZ = v);
        if (e.shape == EmitterShape.CONE) {
            form.number("Cone angle", e.coneAngle, v -> e.coneAngle = v);
        }
        form.section("Offset from origin");
        form.number("Offset X", e.offsetX, v -> e.offsetX = v);
        form.number("Offset Y", e.offsetY, v -> e.offsetY = v);
        form.number("Offset Z", e.offsetZ, v -> e.offsetZ = v);
    }

    private void buildMotion(EmitterDefinition e) {
        form.cycle("Direction", () -> e.direction.displayName(), () -> {
            e.direction = e.direction.next();
            rebuildWidgets();
        });
        form.number("Speed jitter", e.speedRandom, v -> e.speedRandom = v);
        if (e.direction == DirectionMode.FIXED) {
            form.number("Dir X", e.directionX, v -> e.directionX = v);
            form.number("Dir Y", e.directionY, v -> e.directionY = v);
            form.number("Dir Z", e.directionZ, v -> e.directionZ = v);
        }
        form.note("Rate and speed over time are edited on the Curves tab.");
    }

    private void buildLook(EmitterDefinition e) {
        form.toggle("Use colour", e.useColor, v -> e.useColor = v, this::rebuildWidgets);
        form.note("Colour and size only apply to minecraft:dust.");
        if (e.useColor) {
            form.section("Colour over life");
            form.gradient(e.color, this::rebuildWidgets);
        }
    }

    private void buildCurves(EmitterDefinition e) {
        form.cycle("Curve", () -> curveTarget.label, () -> {
            curveTarget = curveTarget.next();
            rebuildWidgets();
        });
        form.endRow();
        switch (curveTarget) {
            case RATE -> {
                form.note("Particles per tick, over the emitter's duration.");
                form.curve(e.rate, this::rebuildWidgets);
            }
            case SPEED -> {
                form.note("Blocks per tick, over the emitter's duration.");
                form.curve(e.speed, this::rebuildWidgets);
            }
            case SIZE -> {
                form.note("Dust scale, over the emitter's duration.");
                form.curve(e.size, this::rebuildWidgets);
            }
        }
    }

    private void play() {
        session.normalize();
        EffectPreview.play(session);
        rebuildWidgets();
    }

    private void save() {
        session.normalize();
        try {
            EffectStore.save(session);
            status = "Saved '" + session.id + "'";
        } catch (IOException e) {
            status = "Save failed: " + e.getMessage();
        }
        rebuildWidgets();
    }

    private void export() {
        session.normalize();
        try {
            EffectStore.save(session);
            PackExporter.Result result = ExportService.exportAll(ExportService.lastName());
            status = "Exported " + result.effectCount() + " effects, " + result.textureCount()
                    + " textures to " + result.zip().getParent();
            if (!result.warnings().isEmpty()) {
                status += " (" + result.warnings().size() + " warnings, see log)";
                result.warnings().forEach(w -> PumpkinFxClient.LOGGER.warn("Export: {}", w));
            }
        } catch (IOException e) {
            status = "Export failed: " + e.getMessage();
        }
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }
}

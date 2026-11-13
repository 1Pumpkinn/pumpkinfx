package net.saturn.pumpkinfx.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.saturn.pumpkinfx.client.EffectPreview;
import net.saturn.pumpkinfx.client.ParticleResolver;
import net.saturn.pumpkinfx.PumpkinFxClient;
import net.saturn.pumpkinfx.export.ExportService;
import net.saturn.pumpkinfx.export.PackExporter;
import net.saturn.pumpkinfx.model.DirectionMode;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EffectHistory;
import net.saturn.pumpkinfx.model.EffectValidator;
import net.saturn.pumpkinfx.model.EmitterDefinition;
import net.saturn.pumpkinfx.model.EmitterShape;
import net.saturn.pumpkinfx.storage.EffectStore;

import java.io.IOException;
import java.util.List;

public final class EditorScreen extends Screen {
    private enum Page {
        GENERAL("General"),
        SHAPE("Shape"),
        MOTION("Motion"),
        LOOK("Look"),
        CURVES("Curves"),
        TIMELINE("Time"),
        ISSUES("Issues");

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

    private static final long COMMIT_INTERVAL_NANOS = 400_000_000L;

    private static EffectDefinition session;
    private static EffectHistory history;
    private static int selected;
    private static int listOffset;
    private static Page page = Page.GENERAL;
    private static CurveTarget curveTarget = CurveTarget.RATE;

    private FormBuilder form;
    private String status = "";
    private long lastCommit;

    public EditorScreen() {
        super(Component.literal("PumpkinFX Editor"));
    }

    public static void open(EffectDefinition effect) {
        session = effect;
        history = new EffectHistory(effect);
        selected = 0;
        listOffset = 0;
        Minecraft.getInstance().gui.setScreen(new EditorScreen());
    }

    public static void openLast() {
        if (session == null) {
            session = EffectDefinition.createDefault("untitled");
            history = new EffectHistory(session);
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
        if (history == null) {
            history = new EffectHistory(session);
        }
        if (session.emitters.isEmpty()) {
            session.addEmitter();
        }
        selected = Math.max(0, Math.min(selected, session.emitters.size() - 1));

        buildTopBar();
        buildEmitterList();
        buildTabs();
        buildPage();
        buildBottomBar();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Ticks", 124, 11, FormBuilder.LABEL_COLOR, false);
        graphics.text(this.font, "Emitters", 8, 25, FormBuilder.HEADER_COLOR, false);
        if (form != null) {
            form.render(graphics);
        }
        if (page == Page.TIMELINE) {
            drawTimeline(graphics);
        }
        graphics.text(this.font, "Seed", PANEL_X + 150, this.height - 29, FormBuilder.LABEL_COLOR, false);
        long now = System.nanoTime();
        if (now - lastCommit > COMMIT_INTERVAL_NANOS) {
            history.commit(session);
            lastCommit = now;
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
                commitNow();
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
            case TIMELINE -> buildTimeline();
            case ISSUES -> buildIssues();
        }
    }

    private void buildBottomBar() {
        int y = this.height - 34;
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undo())
                .bounds(PANEL_X, y, 36, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Redo"), b -> redo())
                .bounds(PANEL_X + 40, y, 36, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Up"), b -> moveSelected(-1))
                .bounds(PANEL_X + 84, y, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Dn"), b -> moveSelected(1))
                .bounds(PANEL_X + 116, y, 28, 18).build());

        EditBox seed = new EditBox(this.font, PANEL_X + 176, y, 74, 18, Component.literal("Seed"));
        seed.setMaxLength(18);
        seed.setValue(Long.toString(session.seed));
        FormBuilder.bind(seed, s -> s.matches("\\d{0,18}"), s -> {
            try {
                session.seed = s.isEmpty() ? 0L : Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(seed);
    }

    private void commitNow() {
        history.commit(session);
        lastCommit = System.nanoTime();
    }

    private void undo() {
        commitNow();
        applyHistory(history.undo(), "Nothing to undo");
    }

    private void redo() {
        commitNow();
        applyHistory(history.redo(), "Nothing to redo");
    }

    private void applyHistory(EffectDefinition restored, String emptyMessage) {
        if (restored == null) {
            status = emptyMessage;
            return;
        }
        session = restored;
        if (session.emitters.isEmpty()) {
            session.addEmitter();
        }
        selected = Math.max(0, Math.min(selected, session.emitters.size() - 1));
        status = "";
        rebuildWidgets();
    }

    private void moveSelected(int delta) {
        commitNow();
        if (session.moveEmitter(selected, selected + delta)) {
            selected += delta;
        }
        rebuildWidgets();
    }

    private void buildTimeline() {
        form.note("Bars show when each emitter runs across the effect's ticks.");
        form.note("Light = looping, gold tick = burst. Edit timings on the General tab.");
    }

    private void buildIssues() {
        List<EffectValidator.Issue> issues = EffectValidator.validate(session, ParticleResolver::isKnown);
        if (issues.isEmpty()) {
            form.note("No problems found.", 0xFF55FF55);
            return;
        }
        int maxChars = Math.max(20, (this.width - PANEL_X - 8) / 6);
        int shown = 0;
        for (EffectValidator.Issue issue : issues) {
            if (shown++ >= 14) {
                form.note("... and " + (issues.size() - 14) + " more", FormBuilder.NOTE_COLOR);
                break;
            }
            int color = switch (issue.severity()) {
                case ERROR -> 0xFFFF5555;
                case WARNING -> 0xFFFFD27F;
                case INFO -> FormBuilder.NOTE_COLOR;
            };
            String text = issue.toString();
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars - 3) + "...";
            }
            form.note(text, color);
        }
    }

    private void drawTimeline(GuiGraphicsExtractor graphics) {
        int labelW = 70;
        int top = LIST_TOP + 26 + 28;
        int barX = PANEL_X + labelW;
        int barW = Math.max(20, this.width - 8 - barX);
        int total = Math.max(1, session.duration);

        graphics.text(this.font, "0", barX, top, FormBuilder.NOTE_COLOR, false);
        String end = Integer.toString(total);
        graphics.text(this.font, end, barX + barW - end.length() * 6, top, FormBuilder.NOTE_COLOR, false);

        for (int i = 0; i < session.emitters.size(); i++) {
            int rowY = top + 14 + i * 16;
            if (rowY > this.height - 46) {
                break;
            }
            EmitterDefinition e = session.emitters.get(i);
            String name = e.name.length() > 11 ? e.name.substring(0, 11) : e.name;
            int nameColor = i == selected ? FormBuilder.HEADER_COLOR
                    : (e.enabled ? FormBuilder.LABEL_COLOR : FormBuilder.NOTE_COLOR);
            graphics.text(this.font, name, PANEL_X, rowY + 2, nameColor, false);
            graphics.fill(barX, rowY, barX + barW, rowY + 12, 0xFF1E1E1E);

            int start = Math.min(total, e.startDelay);
            int firstEnd = Math.min(total, e.startDelay + e.duration);
            int solid = !e.enabled ? 0xFF555555 : (i == selected ? 0xFF55AAFF : 0xFF3A7ACC);
            int faded = !e.enabled ? 0xFF3A3A3A : 0xFF2A5A8F;
            int xs = barX + (int) ((long) start * barW / total);
            int xe = barX + (int) ((long) firstEnd * barW / total);
            if (start < total) {
                graphics.fill(xs, rowY, Math.max(xs + 1, xe), rowY + 12, solid);
                if (e.loop && firstEnd < total) {
                    graphics.fill(xe, rowY, barX + barW, rowY + 12, faded);
                }
            }
            if (e.burstCount > 0 && e.startDelay + e.burstTick < total) {
                int bx = barX + (int) ((long) (e.startDelay + e.burstTick) * barW / total);
                graphics.fill(bx, rowY - 2, bx + 1, rowY + 14, 0xFFFFD27F);
            }
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

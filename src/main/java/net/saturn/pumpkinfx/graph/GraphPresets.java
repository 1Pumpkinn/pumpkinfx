package net.saturn.pumpkinfx.graph;

import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EmitterDefinition;
import net.saturn.pumpkinfx.model.Gradient;

public final class GraphPresets {
    private GraphPresets() {
    }

    public static EffectDefinition helix(String id) {
        EffectDefinition effect = new EffectDefinition(id, id);
        effect.duration = 120;
        effect.loop = true;

        EmitterDefinition emitter = new EmitterDefinition("Helix");
        emitter.particle = "minecraft:dust";
        emitter.duration = 120;
        emitter.loop = true;
        emitter.speed = net.saturn.pumpkinfx.model.Curve.constant(0f);
        emitter.speedRandom = 0f;
        emitter.rate = net.saturn.pumpkinfx.model.Curve.constant(2f);

        GraphDefinition g = emitter.graph;
        GraphNode tick = g.add(NodeType.TICK, 0, 0);
        GraphNode index = g.add(NodeType.INDEX, 0, 60);
        GraphNode time = g.add(NodeType.TIME, 0, 120);

        GraphNode spin = g.add(NodeType.MULTIPLY, 140, 0);
        setDefault(spin, 1, 0.4);
        g.link(spin.id, 0, tick.id);

        GraphNode arm = g.add(NodeType.MULTIPLY, 140, 60);
        setDefault(arm, 1, Math.PI);
        g.link(arm.id, 0, index.id);

        GraphNode angle = g.add(NodeType.ADD, 280, 30);
        g.link(angle.id, 0, spin.id);
        g.link(angle.id, 1, arm.id);

        GraphNode sin = g.add(NodeType.SIN, 420, 0);
        g.link(sin.id, 0, angle.id);
        GraphNode cos = g.add(NodeType.COS, 420, 60);
        g.link(cos.id, 0, angle.id);

        GraphNode x = g.add(NodeType.MULTIPLY, 560, 0);
        setDefault(x, 1, 1.5);
        g.link(x.id, 0, sin.id);
        GraphNode z = g.add(NodeType.MULTIPLY, 560, 60);
        setDefault(z, 1, 1.5);
        g.link(z.id, 0, cos.id);
        GraphNode y = g.add(NodeType.MULTIPLY, 560, 120);
        setDefault(y, 1, 3.0);
        g.link(y.id, 0, time.id);

        GraphNode position = g.add(NodeType.COMBINE, 700, 60);
        g.link(position.id, 0, x.id);
        g.link(position.id, 1, y.id);
        g.link(position.id, 2, z.id);
        GraphNode outPosition = g.add(NodeType.OUT_POSITION, 840, 60);
        g.link(outPosition.id, 0, position.id);

        GraphNode gradient = g.add(NodeType.GRADIENT, 560, 200);
        gradient.gradient = Gradient.between(0xFF8A1F, 0x2F6BFF);
        g.link(gradient.id, 0, time.id);
        GraphNode outColor = g.add(NodeType.OUT_COLOR, 700, 200);
        g.link(outColor.id, 0, gradient.id);

        GraphNode outSize = g.add(NodeType.OUT_SIZE, 700, 260);
        setDefault(outSize, 0, 1.2);

        effect.emitters.add(emitter);
        return effect;
    }

    /** A dust emitter whose particles are tinted with screen-space bands by the particle shader. */
    public static EffectDefinition shaderTint(String id) {
        EffectDefinition effect = new EffectDefinition(id, id);
        effect.duration = 60;
        effect.loop = true;

        EmitterDefinition emitter = new EmitterDefinition("Tint");
        emitter.particle = "minecraft:dust";
        emitter.duration = 60;
        emitter.loop = true;
        emitter.shape = net.saturn.pumpkinfx.model.EmitterShape.SPHERE;
        emitter.speed = net.saturn.pumpkinfx.model.Curve.constant(0.02f);

        GraphDefinition g = emitter.graph;
        GraphNode screen = g.add(NodeType.SH_FRAG_POS, 0, 0);
        GraphNode base = g.add(NodeType.SH_BASE_COLOR, 0, 80);

        GraphNode y = g.add(NodeType.SPLIT_Y, 140, 0);
        g.link(y.id, 0, screen.id);
        GraphNode scale = g.add(NodeType.MULTIPLY, 280, 0);
        setDefault(scale, 1, 0.04);
        g.link(scale.id, 0, y.id);
        GraphNode band = g.add(NodeType.FRACT, 420, 0);
        g.link(band.id, 0, scale.id);
        GraphNode gradient = g.add(NodeType.GRADIENT, 560, 0);
        gradient.gradient = Gradient.between(0xFF8A1F, 0x2F6BFF);
        g.link(gradient.id, 0, band.id);
        GraphNode tint = g.add(NodeType.MULTIPLY, 700, 40);
        g.link(tint.id, 0, base.id);
        g.link(tint.id, 1, gradient.id);
        GraphNode outColor = g.add(NodeType.SH_OUT_COLOR, 840, 40);
        g.link(outColor.id, 0, tint.id);

        effect.emitters.add(emitter);
        return effect;
    }

    private static void setDefault(GraphNode node, int slot, double value) {
        Slot s = node.inputs.get(slot);
        s.x = value;
        s.y = value;
        s.z = value;
    }
}

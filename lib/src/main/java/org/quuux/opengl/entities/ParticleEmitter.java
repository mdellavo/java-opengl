package org.quuux.opengl.entities;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import org.quuux.opengl.lib.BufferType;
import org.quuux.opengl.lib.ShaderProgram;
import org.quuux.opengl.lib.Texture2D;
import org.quuux.opengl.lib.ArrayObject;
import org.quuux.opengl.lib.BufferObject;
import org.quuux.opengl.renderer.Command;
import org.quuux.opengl.renderer.CommandList;
import org.quuux.opengl.renderer.commands.*;
import org.quuux.opengl.renderer.states.*;
import org.quuux.opengl.scenes.Camera;
import org.quuux.opengl.scenes.Scene;
import org.quuux.opengl.util.GLUtil;
import org.quuux.opengl.util.RandomUtil;
import org.quuux.opengl.util.ResourceUtil;


public class ParticleEmitter implements Entity {
    private static final Logger LOGGER = Logger.getLogger( ParticleEmitter.class.getName() );

    private static final int NUM_PARATICLES = 1000;
    private static final int TOTAL_PARTICLES = NUM_PARATICLES * 10;
    private static final int PARTICLE_SIZE = 64;
    private static final int PARTICLE_LIFESPAN = 75;

    long ticks;

    Matrix4d model = new Matrix4d().identity();
    Matrix4f mvp = new Matrix4f();
    FloatBuffer mvpBuffer = GLUtil.floatBuffer(16);

    Vector3d position = new Vector3d();

    List<Particle> particles = new ArrayList<>();
    List<Particle> pool = new ArrayList<>();

    FloatBuffer vertexBuffer = GLUtil.floatBuffer(8 * TOTAL_PARTICLES);

    BufferObject vbo = new BufferObject();
    ArrayObject vao = new ArrayObject();

    Texture2D texture = new Texture2D(ResourceUtil.getPNGResource("particle1"));
    ShaderProgram shader = new ShaderProgram();

    Command displayList;

    private Particle allocateParticle() {
        if (particles.size() >= TOTAL_PARTICLES)
            return null;
        Particle p = (pool.size() > 0) ? pool.remove(pool.size() - 1) : new Particle();
        particles.add(p);
        return p;
    }

    private void recycleParticle(Particle p) {
        particles.remove(p);
        pool.add(p);
    }

    private void seedParticles() {
        for (int i=0; i<NUM_PARATICLES; i++) {
            Particle p = allocateParticle();
            p.emitsTrail = true;
            Vector3d position = new Vector3d();
            position.set(this.position);

            Vector3d acceleration = new Vector3d(RandomUtil.randomRange(-1, 1), RandomUtil.randomRange(-1, 1), RandomUtil.randomRange(-1, 1));
            acceleration.mul(.0001);

            Vector3d velocity = new Vector3d(RandomUtil.randomRange(-1, 1), RandomUtil.randomRange(-1, 1), RandomUtil.randomRange(-1, 1));
            velocity.mul(.02f);

            Vector3d color = new Vector3d(RandomUtil.randomRange(0, 1), 1, 1);

            int lifespan = RandomUtil.randomInt(PARTICLE_LIFESPAN / 2, PARTICLE_LIFESPAN * 2);
            p.recycle(position, velocity, acceleration, color, lifespan);
        }
    }

    private void trail(Particle p) {
        Particle t = allocateParticle();
        if (t == null)
            return;
        t.emitsTrail = false;
        Vector3d position = new Vector3d(p.position);
        Vector3d velocity = new Vector3d();
        velocity.add(
                RandomUtil.randomRange(.0001, .01),
                RandomUtil.randomRange(.0001, .01),
                RandomUtil.randomRange(.0001, .01)
                );
        Vector3d acceleration = new Vector3d();
        t.recycle(position, velocity, acceleration, p.color, p.lifespan / 4);
    }

    @Override
    public void update(long t) {
        for (int i=0; i<particles.size(); i++) {
            Particle p = particles.get(i);
            p.update(t);
            if (!p.isAlive()) {
                recycleParticle(p);
            } else if (p.emitsTrail) {
                trail(p);
            }
        }

        if (particles.size() == 0) {
            seedParticles();
        }

        ticks++;

        Scene.get().getCamera().modelViewProjectionMatrix(model, mvp);
        mvp.get(mvpBuffer);

        updateVertices(vertexBuffer);
    }

    @Override
    public Command dispose() {
        return null;
    }

    @Override
    public Command initialize() {
        CommandList rv = new CommandList();
        rv.add(new GenerateArray(vao));
        rv.add(new GenerateBuffer(vbo));

        rv.add(ShaderProgram.build(shader,
                ResourceUtil.getStringResource("shaders/particle.vert.glsl"),
                ResourceUtil.getStringResource("shaders/particle.frag.glsl")));

        BatchState ctx = new BatchState(
                new UseProgram(shader),
                new BindBuffer(BufferType.ArrayBuffer, vbo),
                new BindArray(vao)
        );
        rv.add(ctx);

        rv.add(texture.initialize(0));

        ctx.add(new SetUniform(shader, "texture", SetUniform.Type.INT, 0));

        ctx.add(new VertexAttribPointer(0, 3, VertexAttribPointer.Type.Float, false, 8 * 4, 0));
        ctx.add(new EnableVertexAttribArray(0));

        ctx.add(new VertexAttribPointer(1, 4, VertexAttribPointer.Type.Float, false, 8 * 4, 3 * 4));
        ctx.add(new EnableVertexAttribArray(1));

        ctx.add(new VertexAttribPointer(2, 1, VertexAttribPointer.Type.Float, false, 8 * 4, 7 * 4));
        ctx.add(new EnableVertexAttribArray(2));

        return rv;
    }

    @Override
    public Command draw() {
        if (displayList == null) {
            BatchState rv = new BatchState(new UseProgram(shader), new BindBuffer(BufferType.ArrayBuffer, vbo),  new BindArray(vao), texture.bind(0));
            rv.add(new SetUniformMatrix(shader, "mvp", 1, false, mvpBuffer));
            rv.add(new BufferData(BufferType.ArrayBuffer, vertexBuffer.capacity() * 4, vertexBuffer, BufferData.Usage.StreamDraw));
            rv.add(new DrawParticles());
            displayList = rv;
        }

        return displayList;
    }

    private float colorComponent(int rgb, int shift) {
        int value = (rgb >> shift) & 0xFF;
        return (float)((double)value/255.);
    }

    private void updateVertices(FloatBuffer vertexBuffer) {
        Collections.sort(particles, particleComparator);

        for (int i=0; i<particles.size(); i++) {
            Particle p = particles.get(i);
            int offset = 8 * i;
            float agePercentile = (float) ((double) p.age / (double) p.lifespan);
            vertexBuffer.put(offset, (float) p.position.x);
            vertexBuffer.put(offset + 1, (float) p.position.y);
            vertexBuffer.put(offset + 2, (float) p.position.z);

            double hue = p.color.x + agePercentile;
            if (hue > 1)
                hue -= 1;

            int rgb = HSBtoRGB(
                    (float)hue,
                    (float)1 - agePercentile,
                    (float)1 - agePercentile
            );

            vertexBuffer.put(offset + 3, colorComponent(rgb, 16));
            vertexBuffer.put(offset + 4, colorComponent(rgb, 8));
            vertexBuffer.put(offset + 5, colorComponent(rgb, 0));
            vertexBuffer.put(offset + 6,  (1 - agePercentile));

            double distance = new Vector3d().distance(p.position);
            double size = (PARTICLE_SIZE/distance) * agePercentile;
            if (size > PARTICLE_SIZE)
                size = PARTICLE_SIZE;
            vertexBuffer.put(offset + 7, (float) size);
        }
    }

    public static int HSBtoRGB(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float h = (hue - (float)Math.floor(hue)) * 6.0f;
            float f = h - (float)java.lang.Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            switch ((int) h) {
                case 0:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (t * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 1:
                    r = (int) (q * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 2:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (t * 255.0f + 0.5f);
                    break;
                case 3:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (q * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 4:
                    r = (int) (t * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 5:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (q * 255.0f + 0.5f);
                    break;
            }
        }
        return 0xff000000 | (r << 16) | (g << 8) | (b << 0);
    }

    static class Particle {
        int age = 0;
        int lifespan;
        Vector3d position;
        Vector3d velocity;
        Vector3d acceleration;
        Vector3d color;
        boolean emitsTrail;

        boolean isAlive() {
            return age < lifespan;
        }

        void recycle(Vector3d position, Vector3d velocity, Vector3d acceleration, Vector3d color, int lifespan) {
            age = 0;
            this.position = position;
            this.velocity = velocity;
            this.acceleration = acceleration;
            this.color = color;
            this.lifespan = lifespan;
        }

        void update(long t) {
            age += 1;
            if (isAlive()) {
                this.velocity.add(this.acceleration);
                this.position.add(this.velocity);
            }
        }
    }

    Comparator<Particle> particleComparator = new Comparator<Particle>() {
        @Override
        public int compare(Particle o1, Particle o2) {
            Camera camera = Scene.get().getCamera();
            double d1 = camera.position.distance(o1.position);
            double d2 = camera.position.distance(o2.position);
            return -Double.compare(d1, d2);
        }
    };

    class DrawParticles extends DrawArrays {

        public DrawParticles() {
            super(DrawMode.Points, 0, particles.size());
        }

        @Override
        public int getCount() {
            return particles.size();
        }
    }
}

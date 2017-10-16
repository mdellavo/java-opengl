package org.quuux.opengl.renderer.commands;

import org.quuux.opengl.renderer.Command;
import org.quuux.opengl.renderer.Renderer;

public class Clear implements Command {

    private final int mask;

    public Clear(int mask) {
        this.mask = mask;
    }

    @Override
    public void run(final Renderer renderer) {
        renderer.run(this);
    }

    public int getMask() {
        return mask;
    }
}

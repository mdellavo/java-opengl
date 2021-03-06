package org.quuux.opengl.renderer.states;

import org.quuux.opengl.renderer.Renderer;

public class ActivateTexture extends State {

    private final int textureUnit;

    public ActivateTexture(int textureUnit) {
        this.textureUnit = textureUnit;
    }

    @Override
    public void set(final Renderer renderer) {
        renderer.set(this);
    }

    @Override
    public void clear(final Renderer renderer) {
        renderer.clear(this);
    }

    public int getTextureUnit() {
        return textureUnit;
    }
}

// fragment.glsl
#extension GL_OES_EGL_image_external : require // Required for camera textures
precision mediump float;                       // Precision qualifier
varying vec2 vTexCoord;                        // Texture coordinates from vertex shader
uniform samplerExternalOES uTexture;           // Texture sampler for camera input

void main() {
    // Sample the texture and output the color
    gl_FragColor = texture2D(uTexture, vTexCoord);
}
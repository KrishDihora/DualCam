// vertex.glsl
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
uniform mat4 uTextureMatrix; // Transformation matrix from SurfaceTexture

void main() {
    gl_Position = aPosition;
    vec4 transformedTexCoord = uTextureMatrix * vec4(aTexCoord, 0.0, 1.0);
    vTexCoord = transformedTexCoord.xy;
}
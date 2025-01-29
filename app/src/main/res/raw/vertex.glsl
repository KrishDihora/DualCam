// vertex.glsl
attribute vec4 aPosition;    // Vertex position attribute
attribute vec2 aTexCoord;    // Texture coordinate attribute
varying vec2 vTexCoord;      // Pass texture coordinates to fragment shader

void main() {
    gl_Position = aPosition; // Directly use the input position
    vTexCoord = aTexCoord;   // Pass texture coordinates to fragment shader
}
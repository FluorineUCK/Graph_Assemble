#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 tileCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 pixel = floor(fract(tileCoord) * size);
    fragColor = texture(Sampler0, (pixel + 0.5) / size) * vertexColor * ColorModulator;
}

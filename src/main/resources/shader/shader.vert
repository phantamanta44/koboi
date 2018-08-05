#version 330

layout (location = 0) in vec2 position;
layout (location = 3) in vec3 colourIn;

out vec3 colour;


void main() {
    gl_Position = vec4(position, 0, 1);
    colour = colourIn;
}
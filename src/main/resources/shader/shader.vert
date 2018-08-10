#version 330 core

layout (location = 0) in vec2 pos;
layout (location = 1) in vec2 texture_pos;
out vec2 texture_coords;

void main() {
    gl_Position = vec4(pos, 0, 1);
    texture_coords = texture_pos;
}
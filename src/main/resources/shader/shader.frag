#version 330 core

in vec2 texture_coords;
layout (location = 0) out vec3 colour;

uniform sampler2D texture0;
uniform sampler2D texture1;

void main() {
    colour = (texture(texture0, texture_coords).rgb + texture(texture1, texture_coords).rgb) / 2;
}
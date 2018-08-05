#version 330

in vec3 colourIn;

layout (location = 0) out vec4 colour;


void main()
{
    // We simply pad the interpolatedColor to vec4
    outputColor = vec4(interpolatedColor, 1);
}
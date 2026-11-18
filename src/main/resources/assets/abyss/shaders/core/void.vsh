#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 worldDir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
    // camera-relative position in world axes: the galaxy is looked up by world direction, so it stays put in the
    // sky when the camera turns (a window into a fixed space, not a picture glued to the screen)
    worldDir = IViewRotMat * Position;
}

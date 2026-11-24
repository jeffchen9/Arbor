/*
 *     Arbor: Procedural Voxel Tree Generation.
 *     Copyright (C) 2026  Jeff Chen
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#version 330 core

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec3 vTint;

uniform sampler2D uAtlas;

out vec4 fragColor;

// @lighting

void main() {
    vec4 texel = texture(uAtlas, vUv);
    if (texel.a < 0.5) {
        discard;
    }
    // A zero normal marks a quad that takes no face shading, such as a crossed plant.
    vec3 normal = vec3(0.0);
    if (dot(vNormal, vNormal) > 0.0001) {
        normal = normalize(vNormal);
        if (!gl_FrontFacing) {
            normal = -normal;
        }
    }
    float shade;
    if (uRealistic == 1) {
        shade = realisticBrightness(vWorld, normal);
    } else {
        shade = dot(normal, normal) > 0.0 ? faceBrightness(normal) : 1.0;
    }
    fragColor = vec4(min(texel.rgb * vTint * shade, vec3(1.0)), 1.0);
}

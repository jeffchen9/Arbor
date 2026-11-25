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

uniform vec3 uColor;

out vec4 fragColor;

// @lighting

void main() {
    vec3 normal = normalize(vNormal);
    float shade = uRealistic == 1 ? realisticBrightness(vWorld, normal) : faceBrightness(normal);
    fragColor = vec4(min(uColor * (shade + transmittedLight(vWorld, normal)), vec3(1.0)), 1.0);
}
